package com.hostel.service;

import com.hostel.dto.CircularDtos.*;
import com.hostel.entity.*;
import com.hostel.exception.CustomExceptions.*;
import com.hostel.repository.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.core.io.UrlResource;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.net.MalformedURLException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Collectors;

@Service
public class CircularService {

    private static final int MAX_ATTACHMENTS = 5;
    private static final long MAX_FILE_BYTES = 2 * 1024 * 1024;
    private static final Set<String> ALLOWED_TYPES = Set.of(
            "application/pdf", "image/jpeg", "image/jpg", "image/png", "image/webp"
    );

    private final CircularRepository circularRepository;
    private final CircularAttachmentRepository attachmentRepository;
    private final CircularTargetRepository targetRepository;
    private final CircularReadRepository readRepository;
    private final CircularVersionRepository versionRepository;
    private final EventRsvpRepository rsvpRepository;
    private final CircularEventReminderRepository eventReminderRepository;
    private final StudentRepository studentRepository;
    private final StaffProfileRepository staffProfileRepository;
    private final BedRepository bedRepository;
    private final NotificationService notificationService;
    private final AuditService auditService;

    @Value("${app.upload.dir:./storage/photos}")
    private String uploadDir;

    public CircularService(CircularRepository circularRepository,
                           CircularAttachmentRepository attachmentRepository,
                           CircularTargetRepository targetRepository,
                           CircularReadRepository readRepository,
                           CircularVersionRepository versionRepository,
                           EventRsvpRepository rsvpRepository,
                           CircularEventReminderRepository eventReminderRepository,
                           StudentRepository studentRepository,
                           StaffProfileRepository staffProfileRepository,
                           BedRepository bedRepository,
                           NotificationService notificationService,
                           AuditService auditService) {
        this.circularRepository = circularRepository;
        this.attachmentRepository = attachmentRepository;
        this.targetRepository = targetRepository;
        this.readRepository = readRepository;
        this.versionRepository = versionRepository;
        this.rsvpRepository = rsvpRepository;
        this.eventReminderRepository = eventReminderRepository;
        this.studentRepository = studentRepository;
        this.staffProfileRepository = staffProfileRepository;
        this.bedRepository = bedRepository;
        this.notificationService = notificationService;
        this.auditService = auditService;
    }

    // =====================================================
    // WARDEN / ADMIN PUBLISHING & MANAGEMENT
    // =====================================================

    @Transactional
    public CircularResponse createCircular(User actor,
                                           String typeStr,
                                           String title,
                                           String description,
                                           String rulesAndRegulations,
                                           LocalDateTime eventDateTime,
                                           String venue,
                                           String priorityStr,
                                           String targetScopeStr,
                                           String hostelBlock,
                                           String department,
                                           Integer studyYear,
                                           List<Long> studentIds,
                                           boolean requireAcknowledgement,
                                           String publishAction,
                                           LocalDateTime scheduledPublishAt,
                                           LocalDateTime expiresAt,
                                           MultipartFile[] files) {
        if (title == null || title.isBlank() || title.trim().length() > 200) {
            throw new BadRequestException("Title is required (max 200 chars)");
        }
        if (description == null || description.isBlank()) {
            throw new BadRequestException("Description is required");
        }

        Circular.Type type = parseEnum(Circular.Type.class, typeStr, "circular type");
        Circular.Priority priority = parseEnum(Circular.Priority.class,
                priorityStr != null && !priorityStr.isBlank() ? priorityStr : "NORMAL", "priority");
        Circular.TargetScope scope = parseEnum(Circular.TargetScope.class,
                targetScopeStr != null && !targetScopeStr.isBlank() ? targetScopeStr : "ALL", "target scope");

        if ("WARDEN".equals(actor.getRole()) && scope == Circular.TargetScope.ALL) {
            throw new ForbiddenException("Wardens can only publish to their own hostel/block; only Admin can broadcast to all hostels");
        }
        if (type == Circular.Type.EVENT && eventDateTime == null) {
            throw new BadRequestException("Event date/time is required for Event circulars");
        }

        List<MultipartFile> validFiles = validateFiles(files);

        String act = publishAction != null ? publishAction.trim().toUpperCase() : "DRAFT";
        Circular.Status status;
        LocalDateTime publishedAt = null;
        if ("PUBLISH".equals(act)) {
            status = Circular.Status.PUBLISHED;
            publishedAt = LocalDateTime.now();
        } else if ("SCHEDULE".equals(act)) {
            if (scheduledPublishAt == null) {
                throw new BadRequestException("Scheduled publish date/time is required");
            }
            status = Circular.Status.SCHEDULED;
        } else {
            status = Circular.Status.DRAFT;
        }

        Circular c = new Circular();
        c.setCircularCode(generateUniqueCode());
        c.setType(type);
        c.setTitle(title.trim());
        c.setDescription(description.trim());
        c.setRulesAndRegulations(rulesAndRegulations != null && !rulesAndRegulations.isBlank() ? rulesAndRegulations.trim() : null);
        c.setEventDateTime(eventDateTime);
        c.setVenue(venue != null && !venue.isBlank() ? venue.trim() : null);
        c.setPriority(priority);
        c.setStatus(status);
        c.setRequireAcknowledgement(requireAcknowledgement);
        c.setTargetScope(scope);
        c.setHostelBlock(hostelBlock != null && !hostelBlock.isBlank() ? hostelBlock.trim() : (scope == Circular.TargetScope.ALL ? "ALL" : "Main Block"));
        c.setScheduledPublishAt(scheduledPublishAt);
        c.setExpiresAt(expiresAt);
        c.setPublishedAt(publishedAt);
        c.setVersion(1);
        c.setUpdatedFlag(false);
        c.setCreatedBy(actor);

        Circular saved = circularRepository.save(c);
        saveTargets(saved, scope, c.getHostelBlock(), department, studyYear, studentIds);
        saveVersionSnapshot(saved, actor, "Initial version");

        for (MultipartFile f : validFiles) {
            storeAttachment(saved, f, actor);
        }

        if (saved.getStatus() == Circular.Status.PUBLISHED) {
            notifyTargetedStudentsOnPublish(saved);
        }

        auditService.log(actor, "CREATE_CIRCULAR", saved.getCircularCode() + " Status=" + saved.getStatus(), "SUCCESS");
        return toResponse(saved, null);
    }

    @Transactional
    public CircularResponse updateCircular(User actor,
                                           Long id,
                                           String title,
                                           String description,
                                           String rulesAndRegulations,
                                           LocalDateTime eventDateTime,
                                           String venue,
                                           String priorityStr,
                                           Boolean requireAcknowledgement,
                                           LocalDateTime expiresAt,
                                           String editNote) {
        Circular c = getCircularOrThrow(id);
        assertWardenOrAdminCanManage(actor, c);

        if (title != null && !title.isBlank()) c.setTitle(title.trim());
        if (description != null && !description.isBlank()) c.setDescription(description.trim());
        c.setRulesAndRegulations(rulesAndRegulations != null && !rulesAndRegulations.isBlank() ? rulesAndRegulations.trim() : null);
        if (eventDateTime != null) c.setEventDateTime(eventDateTime);
        if (venue != null) c.setVenue(venue.isBlank() ? null : venue.trim());
        if (priorityStr != null && !priorityStr.isBlank()) {
            c.setPriority(parseEnum(Circular.Priority.class, priorityStr, "priority"));
        }
        if (requireAcknowledgement != null) c.setRequireAcknowledgement(requireAcknowledgement);
        if (expiresAt != null) c.setExpiresAt(expiresAt);

        c.setVersion(c.getVersion() + 1);
        if (c.getStatus() == Circular.Status.PUBLISHED) {
            c.setUpdatedFlag(true);
        }

        Circular saved = circularRepository.save(c);
        saveVersionSnapshot(saved, actor, editNote != null && !editNote.isBlank() ? editNote.trim() : "Updated circular");

        if (saved.getStatus() == Circular.Status.PUBLISHED) {
            notifyTargetedStudentsOnUpdate(saved);
        }

        auditService.log(actor, "UPDATE_CIRCULAR", saved.getCircularCode() + " v" + saved.getVersion(), "SUCCESS");
        return toResponse(saved, null);
    }

    @Transactional
    public CircularResponse publishNow(User actor, Long id) {
        Circular c = getCircularOrThrow(id);
        assertWardenOrAdminCanManage(actor, c);
        if (c.getStatus() == Circular.Status.PUBLISHED) {
            throw new BadRequestException("Circular is already published");
        }
        c.setStatus(Circular.Status.PUBLISHED);
        c.setPublishedAt(LocalDateTime.now());
        Circular saved = circularRepository.save(c);
        notifyTargetedStudentsOnPublish(saved);
        auditService.log(actor, "PUBLISH_CIRCULAR", saved.getCircularCode(), "SUCCESS");
        return toResponse(saved, null);
    }

    @Transactional
    public CircularResponse archiveCircular(User actor, Long id) {
        Circular c = getCircularOrThrow(id);
        assertWardenOrAdminCanManage(actor, c);
        c.setStatus(Circular.Status.ARCHIVED);
        Circular saved = circularRepository.save(c);
        auditService.log(actor, "ARCHIVE_CIRCULAR", saved.getCircularCode(), "SUCCESS");
        return toResponse(saved, null);
    }

    @Transactional
    public void deleteCircular(User actor, Long id) {
        Circular c = getCircularOrThrow(id);
        assertWardenOrAdminCanManage(actor, c);
        circularRepository.delete(c);
        auditService.log(actor, "DELETE_CIRCULAR", c.getCircularCode(), "SUCCESS");
    }

    public Page<CircularResponse> listForStaff(String statusStr, String typeStr, String query, Pageable pageable) {
        Circular.Status st = (statusStr != null && !statusStr.isBlank() && !"ALL".equalsIgnoreCase(statusStr))
                ? parseEnum(Circular.Status.class, statusStr, "status") : null;
        Circular.Type tp = (typeStr != null && !typeStr.isBlank() && !"ALL".equalsIgnoreCase(typeStr))
                ? parseEnum(Circular.Type.class, typeStr, "type") : null;
        String q = (query != null && !query.isBlank()) ? query.trim() : null;
        return circularRepository.searchAdminOrWarden(st, tp, q, pageable)
                .map(c -> toResponse(c, null));
    }

    public CircularTrackingResponse getTracking(Long circularId) {
        Circular c = getCircularOrThrow(circularId);
        List<Student> targeted = resolveTargetedStudents(c);
        List<CircularRead> reads = readRepository.findByCircularId(circularId);
        Map<Long, CircularRead> readMap = reads.stream()
                .collect(Collectors.toMap(r -> r.getStudent().getId(), r -> r, (a, b) -> a));
        Map<Long, EventRsvp> rsvpMap = rsvpRepository.findByCircularId(circularId).stream()
                .collect(Collectors.toMap(r -> r.getStudent().getId(), r -> r, (a, b) -> a));

        List<CircularTrackerRow> rows = new ArrayList<>();
        long readCount = 0;
        long ackCount = 0;
        long attendingCount = 0;

        for (Student s : targeted) {
            CircularRead cr = readMap.get(s.getId());
            EventRsvp rsvp = rsvpMap.get(s.getId());
            boolean isRead = cr != null;
            boolean isAck = cr != null && cr.getAcknowledgedAt() != null;
            if (isRead) readCount++;
            if (isAck) ackCount++;
            if (rsvp != null && rsvp.getRsvpStatus() == EventRsvp.RsvpStatus.ATTENDING) attendingCount++;

            Optional<Bed> bedOpt = bedRepository.findByStudentId(s.getId());
            String room = bedOpt.map(b -> b.getRoom().getRoomNumber()).orElse("-");
            String block = bedOpt.map(b -> "Floor " + b.getRoom().getFloor()).orElse("Main Block");

            rows.add(new CircularTrackerRow(
                    s.getId(), s.getFullName(), s.getRegisterNumber(), room, block,
                    isRead, cr != null ? cr.getReadAt() : null,
                    isAck, cr != null ? cr.getAcknowledgedAt() : null,
                    rsvp != null ? rsvp.getRsvpStatus().name() : null
            ));
        }

        return new CircularTrackingResponse(
                c.getId(), c.getCircularCode(), c.getTitle(),
                targeted.size(), readCount, ackCount, attendingCount, rows
        );
    }

    @Transactional
    public int sendManualReminder(User actor, Long circularId) {
        Circular c = getCircularOrThrow(circularId);
        assertWardenOrAdminCanManage(actor, c);
        List<Student> targeted = resolveTargetedStudents(c);
        Map<Long, CircularRead> readMap = readRepository.findByCircularId(circularId).stream()
                .collect(Collectors.toMap(r -> r.getStudent().getId(), r -> r, (a, b) -> a));

        int sent = 0;
        for (Student s : targeted) {
            CircularRead cr = readMap.get(s.getId());
            boolean done = c.isRequireAcknowledgement()
                    ? (cr != null && cr.getAcknowledgedAt() != null)
                    : (cr != null);
            if (!done) {
                notificationService.notifyUser(
                        s.getUser(),
                        "Reminder: " + c.getTitle(),
                        "Please read" + (c.isRequireAcknowledgement() ? " and acknowledge" : "")
                                + " circular " + c.getCircularCode() + ": " + c.getTitle(),
                        "CIRCULAR",
                        c.getId()
                );
                sent++;
            }
        }
        auditService.log(actor, "CIRCULAR_REMINDER", c.getCircularCode() + " Sent=" + sent, "SUCCESS");
        return sent;
    }

    public String exportTrackingCsv(Long circularId) {
        CircularTrackingResponse tr = getTracking(circularId);
        StringBuilder sb = new StringBuilder();
        sb.append("RegisterNumber,FullName,Room,Block,Read,ReadAt,Acknowledged,AcknowledgedAt,RSVP\n");
        for (CircularTrackerRow r : tr.students()) {
            sb.append(escapeCsv(r.registerNumber())).append(',')
              .append(escapeCsv(r.fullName())).append(',')
              .append(escapeCsv(r.roomNumber())).append(',')
              .append(escapeCsv(r.hostelBlock())).append(',')
              .append(r.read()).append(',')
              .append(r.readAt() != null ? r.readAt() : "").append(',')
              .append(r.acknowledged()).append(',')
              .append(r.acknowledgedAt() != null ? r.acknowledgedAt() : "").append(',')
              .append(r.rsvpStatus() != null ? r.rsvpStatus() : "").append('\n');
        }
        return sb.toString();
    }

    // =====================================================
    // STUDENT SIDE: TARGETED LIST, READ/ACK, BANNER, RSVP, ICS
    // =====================================================

    public Page<CircularResponse> getStudentCirculars(User studentUser,
                                                      String typeStr,
                                                      LocalDate fromDate,
                                                      LocalDate toDate,
                                                      String readFilter,
                                                      String query,
                                                      Pageable pageable) {
        Student student = getStudentByUser(studentUser);
        LocalDateTime now = LocalDateTime.now();

        Circular.Type typeFilter = (typeStr != null && !typeStr.isBlank() && !"ALL".equalsIgnoreCase(typeStr))
                ? parseEnum(Circular.Type.class, typeStr, "type") : null;
        String q = (query != null && !query.isBlank()) ? query.trim().toLowerCase() : null;
        String rf = readFilter != null ? readFilter.trim().toUpperCase() : "ALL";

        List<Circular> allPublished = circularRepository.findByStatusOrderByPublishedAtDescCreatedAtDesc(Circular.Status.PUBLISHED);
        List<CircularResponse> filtered = new ArrayList<>();

        for (Circular c : allPublished) {
            if (c.getExpiresAt() != null && c.getExpiresAt().isBefore(now)) continue;
            if (!isStudentTargeted(c, student)) continue;
            if (typeFilter != null && c.getType() != typeFilter) continue;
            if (fromDate != null && c.getPublishedAt() != null && c.getPublishedAt().toLocalDate().isBefore(fromDate)) continue;
            if (toDate != null && c.getPublishedAt() != null && c.getPublishedAt().toLocalDate().isAfter(toDate)) continue;
            if (q != null && !c.getTitle().toLowerCase().contains(q) && !c.getCircularCode().toLowerCase().contains(q)
                    && !c.getDescription().toLowerCase().contains(q)) continue;

            CircularResponse resp = toResponse(c, student);
            if ("UNREAD".equals(rf) && resp.read()) continue;
            if ("READ".equals(rf) && !resp.read()) continue;
            filtered.add(resp);
        }

        int start = (int) pageable.getOffset();
        int end = Math.min(start + pageable.getPageSize(), filtered.size());
        List<CircularResponse> slice = start >= filtered.size() ? List.of() : filtered.subList(start, end);
        return new PageImpl<>(slice, pageable, filtered.size());
    }

    public Map<String, Object> getStudentUnreadAndBanner(User studentUser) {
        Student student = getStudentByUser(studentUser);
        LocalDateTime now = LocalDateTime.now();
        List<Circular> allPublished = circularRepository.findByStatusOrderByPublishedAtDescCreatedAtDesc(Circular.Status.PUBLISHED);

        long unreadCount = 0;
        CircularResponse urgentUnread = null;
        CircularResponse latestImportant = null;

        for (Circular c : allPublished) {
            if (c.getExpiresAt() != null && c.getExpiresAt().isBefore(now)) continue;
            if (!isStudentTargeted(c, student)) continue;

            CircularResponse resp = toResponse(c, student);
            if (!resp.read()) {
                unreadCount++;
                if (c.getPriority() == Circular.Priority.URGENT && urgentUnread == null) {
                    urgentUnread = resp;
                }
            }
            if ((c.getPriority() == Circular.Priority.URGENT || c.getPriority() == Circular.Priority.IMPORTANT)
                    && latestImportant == null) {
                latestImportant = resp;
            }
        }

        Map<String, Object> result = new HashMap<>();
        result.put("unreadCount", unreadCount);
        result.put("urgentBanner", urgentUnread);
        result.put("importantBanner", latestImportant);
        return result;
    }

    @Transactional
    public CircularResponse getStudentDetailAndMarkRead(User studentUser, Long circularId) {
        Student student = getStudentByUser(studentUser);
        Circular c = getCircularOrThrow(circularId);
        if (c.getStatus() != Circular.Status.PUBLISHED) {
            throw new ForbiddenException("Circular is not published");
        }
        if (!isStudentTargeted(c, student)) {
            throw new ForbiddenException("You are not in the target audience for this circular");
        }

        if (readRepository.findByCircularIdAndStudentId(c.getId(), student.getId()).isEmpty()) {
            CircularRead cr = new CircularRead();
            cr.setCircular(c);
            cr.setStudent(student);
            cr.setReadAt(LocalDateTime.now());
            readRepository.save(cr);
        }

        return toResponse(c, student);
    }

    @Transactional
    public CircularResponse acknowledgeCircular(User studentUser, Long circularId) {
        Student student = getStudentByUser(studentUser);
        Circular c = getCircularOrThrow(circularId);
        if (c.getStatus() != Circular.Status.PUBLISHED || !isStudentTargeted(c, student)) {
            throw new ForbiddenException("Access denied to this circular");
        }

        LocalDateTime now = LocalDateTime.now();
        CircularRead cr = readRepository.findByCircularIdAndStudentId(c.getId(), student.getId())
                .orElseGet(() -> {
                    CircularRead r = new CircularRead();
                    r.setCircular(c);
                    r.setStudent(student);
                    r.setReadAt(now);
                    return r;
                });
        if (cr.getAcknowledgedAt() == null) {
            cr.setAcknowledgedAt(now);
            readRepository.save(cr);
        }
        auditService.log(studentUser, "ACK_CIRCULAR", c.getCircularCode(), "SUCCESS");
        return toResponse(c, student);
    }

    @Transactional
    public CircularResponse submitRsvp(User studentUser, Long circularId, RsvpRequest req) {
        Student student = getStudentByUser(studentUser);
        Circular c = getCircularOrThrow(circularId);
        if (c.getStatus() != Circular.Status.PUBLISHED || !isStudentTargeted(c, student)) {
            throw new ForbiddenException("Access denied to this event");
        }
        if (c.getType() != Circular.Type.EVENT) {
            throw new BadRequestException("RSVP is only available for Event notices");
        }
        EventRsvp.RsvpStatus status = parseEnum(EventRsvp.RsvpStatus.class, req.rsvpStatus(), "RSVP status");
        EventRsvp rsvp = rsvpRepository.findByCircularIdAndStudentId(c.getId(), student.getId())
                .orElseGet(() -> {
                    EventRsvp e = new EventRsvp();
                    e.setCircular(c);
                    e.setStudent(student);
                    return e;
                });
        rsvp.setRsvpStatus(status);
        rsvp.setUpdatedAt(LocalDateTime.now());
        rsvpRepository.save(rsvp);
        return toResponse(c, student);
    }

    public String buildIcsContent(User studentUser, Long circularId) {
        Student student = getStudentByUser(studentUser);
        Circular c = getCircularOrThrow(circularId);
        if (!isStudentTargeted(c, student)) {
            throw new ForbiddenException("Access denied");
        }
        if (c.getEventDateTime() == null) {
            throw new BadRequestException("This circular has no event date/time");
        }
        DateTimeFormatter icsFmt = DateTimeFormatter.ofPattern("yyyyMMdd'T'HHmmss");
        String start = c.getEventDateTime().format(icsFmt);
        String end = c.getEventDateTime().plusHours(1).format(icsFmt);
        return "BEGIN:VCALENDAR\r\n" +
                "VERSION:2.0\r\n" +
                "PRODID:-//PSG iTech Hostel//Circulars//EN\r\n" +
                "BEGIN:VEVENT\r\n" +
                "UID:" + c.getCircularCode() + "@hostel.edu\r\n" +
                "DTSTART:" + start + "\r\n" +
                "DTEND:" + end + "\r\n" +
                "SUMMARY:" + c.getTitle().replace("\n", " ") + "\r\n" +
                "LOCATION:" + (c.getVenue() != null ? c.getVenue().replace("\n", " ") : "Hostel") + "\r\n" +
                "DESCRIPTION:" + c.getDescription().replace("\n", "\\n") + "\r\n" +
                "END:VEVENT\r\n" +
                "END:VCALENDAR\r\n";
    }

    public MaintenanceService.LoadedAttachment loadAttachment(User actor, Long attachmentId) {
        CircularAttachment att = attachmentRepository.findById(attachmentId)
                .orElseThrow(() -> new ResourceNotFoundException("Circular attachment not found"));
        Circular c = att.getCircular();
        if ("STUDENT".equals(actor.getRole())) {
            Student s = getStudentByUser(actor);
            if (c.getStatus() != Circular.Status.PUBLISHED || !isStudentTargeted(c, s)) {
                throw new ForbiddenException("Access denied to circular attachment");
            }
        } else if (!"WARDEN".equals(actor.getRole()) && !"ADMIN".equals(actor.getRole())) {
            throw new ForbiddenException("Access denied");
        }
        try {
            Path p = Paths.get(att.getFilePath()).normalize();
            Resource r = new UrlResource(p.toUri());
            if (!r.exists() || !r.isReadable()) {
                throw new ResourceNotFoundException("Attachment file not found");
            }
            return new MaintenanceService.LoadedAttachment(r, att.getContentType(), att.getOriginalFilename());
        } catch (MalformedURLException e) {
            throw new ResourceNotFoundException("Invalid attachment path");
        }
    }

    // =====================================================
    // SCHEDULED JOBS (PUBLISH, 24H UNACK, 1D & 1H EVENT REMINDERS)
    // =====================================================

    @Transactional
    public void processCircularJobs(LocalDateTime now) {
        // 1. Publish due scheduled circulars
        List<Circular> due = circularRepository.findByStatusAndScheduledPublishAtLessThanEqual(Circular.Status.SCHEDULED, now);
        for (Circular c : due) {
            c.setStatus(Circular.Status.PUBLISHED);
            c.setPublishedAt(now);
            circularRepository.save(c);
            notifyTargetedStudentsOnPublish(c);
        }

        // 2. 24h unacknowledged reminder & event reminders
        List<Circular> published = circularRepository.findByStatusOrderByPublishedAtDescCreatedAtDesc(Circular.Status.PUBLISHED);
        for (Circular c : published) {
            if (c.getExpiresAt() != null && c.getExpiresAt().isBefore(now)) continue;

            if (c.isRequireAcknowledgement() && c.getPublishedAt() != null
                    && !c.getPublishedAt().isAfter(now.minusHours(24))) {
                send24hUnackReminder(c);
            }

            if (c.getType() == Circular.Type.EVENT && c.getEventDateTime() != null
                    && c.getEventDateTime().isAfter(now)) {
                long mins = Duration.between(now, c.getEventDateTime()).toMinutes();
                if (mins <= 24 * 60 && mins > 60) {
                    sendEventReminderBatch(c, "1_DAY", "Event Tomorrow: " + c.getTitle());
                } else if (mins <= 60) {
                    sendEventReminderBatch(c, "1_HOUR", "Event in 1 Hour: " + c.getTitle());
                }
            }
        }
    }

    private void send24hUnackReminder(Circular c) {
        List<Student> targeted = resolveTargetedStudents(c);
        Map<Long, CircularRead> readMap = readRepository.findByCircularId(c.getId()).stream()
                .collect(Collectors.toMap(r -> r.getStudent().getId(), r -> r, (a, b) -> a));

        for (Student s : targeted) {
            CircularRead cr = readMap.get(s.getId());
            if (cr != null && (cr.getAcknowledgedAt() != null || cr.isReminder24hSent())) continue;
            if (eventReminderRepository.existsByCircularIdAndStudentIdAndReminderType(c.getId(), s.getId(), "UNACK_24H")) {
                continue;
            }
            notificationService.notifyUser(
                    s.getUser(),
                    "Acknowledgement Pending: " + c.getCircularCode(),
                    "Reminder: Please read and acknowledge circular \"" + c.getTitle() + "\".",
                    "CIRCULAR",
                    c.getId()
            );
            if (cr != null) {
                cr.setReminder24hSent(true);
                readRepository.save(cr);
            }
            CircularEventReminder log = new CircularEventReminder();
            log.setCircular(c);
            log.setStudent(s);
            log.setReminderType("UNACK_24H");
            eventReminderRepository.save(log);
        }
    }

    private void sendEventReminderBatch(Circular c, String reminderType, String title) {
        List<Student> targeted = resolveTargetedStudents(c);
        for (Student s : targeted) {
            if (eventReminderRepository.existsByCircularIdAndStudentIdAndReminderType(c.getId(), s.getId(), reminderType)) {
                continue;
            }
            notificationService.notifyUser(
                    s.getUser(),
                    title,
                    c.getTitle() + (c.getVenue() != null ? " at " + c.getVenue() : "") + ".",
                    "CIRCULAR",
                    c.getId()
            );
            CircularEventReminder log = new CircularEventReminder();
            log.setCircular(c);
            log.setStudent(s);
            log.setReminderType(reminderType);
            eventReminderRepository.save(log);
        }
    }

    // =====================================================
    // TARGETING & HELPERS
    // =====================================================

    public List<Student> resolveTargetedStudents(Circular c) {
        List<Student> activeStudents = studentRepository.findAll().stream()
                .filter(s -> s.getUser() != null && s.getUser().getStatus() == User.Status.ACTIVE)
                .collect(Collectors.toList());
        return activeStudents.stream()
                .filter(s -> isStudentTargeted(c, s))
                .collect(Collectors.toList());
    }

    public boolean isStudentTargeted(Circular c, Student student) {
        Circular.TargetScope scope = c.getTargetScope();
        if (scope == Circular.TargetScope.ALL) return true;

        List<CircularTarget> targets = targetRepository.findByCircularId(c.getId());
        if (scope == Circular.TargetScope.STUDENTS) {
            return targets.stream().anyMatch(t -> t.getStudent() != null && t.getStudent().getId().equals(student.getId()));
        }
        if (scope == Circular.TargetScope.BLOCK) {
            String targetBlk = c.getHostelBlock();
            if (targetBlk == null || "ALL".equalsIgnoreCase(targetBlk) || "Main Block".equalsIgnoreCase(targetBlk)) {
                return true;
            }
            String studentBlk = bedRepository.findByStudentId(student.getId())
                    .map(b -> "Floor " + b.getRoom().getFloor())
                    .orElse("Main Block");
            return targetBlk.equalsIgnoreCase(studentBlk);
        }
        if (scope == Circular.TargetScope.YEAR_DEPT) {
            for (CircularTarget t : targets) {
                boolean deptMatch = (t.getDepartment() == null || t.getDepartment().isBlank())
                        || student.getDepartment().equalsIgnoreCase(t.getDepartment().trim());
                boolean yearMatch = (t.getStudyYear() == null) || student.getYear() == t.getStudyYear();
                if (deptMatch && yearMatch) return true;
            }
            return false;
        }
        return false;
    }

    private void saveTargets(Circular c, Circular.TargetScope scope, String block, String dept, Integer year, List<Long> studentIds) {
        if (scope == Circular.TargetScope.BLOCK) {
            CircularTarget t = new CircularTarget();
            t.setCircular(c);
            t.setHostelBlock(block);
            targetRepository.save(t);
        } else if (scope == Circular.TargetScope.YEAR_DEPT) {
            CircularTarget t = new CircularTarget();
            t.setCircular(c);
            t.setDepartment(dept != null && !dept.isBlank() ? dept.trim() : null);
            t.setStudyYear(year);
            targetRepository.save(t);
        } else if (scope == Circular.TargetScope.STUDENTS && studentIds != null) {
            for (Long sid : studentIds) {
                studentRepository.findById(sid).ifPresent(st -> {
                    CircularTarget t = new CircularTarget();
                    t.setCircular(c);
                    t.setStudent(st);
                    targetRepository.save(t);
                });
            }
        }
    }

    private void notifyTargetedStudentsOnPublish(Circular c) {
        for (Student s : resolveTargetedStudents(c)) {
            notificationService.notifyUser(
                    s.getUser(),
                    "[" + c.getPriority() + "] " + c.getTitle(),
                    c.getCircularCode() + " published" + (c.isRequireAcknowledgement() ? " (Acknowledgement required)" : ""),
                    "CIRCULAR",
                    c.getId()
            );
        }
    }

    private void notifyTargetedStudentsOnUpdate(Circular c) {
        for (Student s : resolveTargetedStudents(c)) {
            notificationService.notifyUser(
                    s.getUser(),
                    "[Updated] " + c.getTitle(),
                    "Circular " + c.getCircularCode() + " was updated (v" + c.getVersion() + ").",
                    "CIRCULAR",
                    c.getId()
            );
        }
    }

    private void assertWardenOrAdminCanManage(User actor, Circular c) {
        if ("ADMIN".equals(actor.getRole())) return;
        if ("WARDEN".equals(actor.getRole())) {
            if (c.getTargetScope() == Circular.TargetScope.ALL) {
                throw new ForbiddenException("Wardens cannot modify college-wide Admin circulars");
            }
            return;
        }
        throw new ForbiddenException("Access denied");
    }

    private List<MultipartFile> validateFiles(MultipartFile[] files) {
        if (files == null || files.length == 0) return List.of();
        List<MultipartFile> list = new ArrayList<>();
        for (MultipartFile f : files) {
            if (f != null && !f.isEmpty()) list.add(f);
        }
        if (list.size() > MAX_ATTACHMENTS) {
            throw new BadRequestException("Maximum " + MAX_ATTACHMENTS + " attachments allowed");
        }
        for (MultipartFile f : list) {
            String ct = f.getContentType();
            if (ct == null || !ALLOWED_TYPES.contains(ct.toLowerCase())) {
                throw new BadRequestException("Only PDF, JPG, PNG, and WebP files are allowed");
            }
            if (f.getSize() > MAX_FILE_BYTES) {
                throw new BadRequestException("Each attachment must be 2 MB or smaller");
            }
        }
        return list;
    }

    private void storeAttachment(Circular c, MultipartFile file, User uploader) {
        try {
            Path dir = Paths.get(uploadDir, "circulars").toAbsolutePath().normalize();
            Files.createDirectories(dir);
            String ct = file.getContentType() != null ? file.getContentType().toLowerCase() : "application/pdf";
            String ext = ct.contains("pdf") ? ".pdf" : (ct.contains("png") ? ".png" : (ct.contains("webp") ? ".webp" : ".jpg"));
            String fname = c.getCircularCode() + "_" + UUID.randomUUID().toString().substring(0, 8) + ext;
            Path target = dir.resolve(fname);
            Files.copy(file.getInputStream(), target, StandardCopyOption.REPLACE_EXISTING);

            CircularAttachment att = new CircularAttachment();
            att.setCircular(c);
            att.setFilePath(target.toString());
            att.setOriginalFilename(file.getOriginalFilename() != null ? file.getOriginalFilename() : fname);
            att.setContentType(ct);
            att.setFileSize(file.getSize());
            att.setUploadedBy(uploader);
            attachmentRepository.save(att);
        } catch (IOException e) {
            throw new RuntimeException("Failed to store circular attachment: " + e.getMessage());
        }
    }

    private void saveVersionSnapshot(Circular c, User editor, String note) {
        CircularVersion v = new CircularVersion();
        v.setCircular(c);
        v.setVersionNumber(c.getVersion());
        v.setTitle(c.getTitle());
        v.setDescription(c.getDescription());
        v.setRulesAndRegulations(c.getRulesAndRegulations());
        v.setEditedBy(editor);
        v.setEditNote(note);
        versionRepository.save(v);
    }

    private String generateUniqueCode() {
        int year = LocalDate.now().getYear();
        String prefix = "CIR-" + year + "-";
        long seq = circularRepository.countByCodePrefix(prefix) + 1;
        String code = String.format("%s%04d", prefix, seq);
        while (circularRepository.findByCircularCode(code).isPresent()) {
            seq++;
            code = String.format("%s%04d", prefix, seq);
        }
        return code;
    }

    private CircularResponse toResponse(Circular c, Student studentContext) {
        List<CircularAttachmentDto> atts = attachmentRepository.findByCircularIdOrderByCreatedAtAsc(c.getId()).stream()
                .map(a -> new CircularAttachmentDto(a.getId(), "/api/circulars/attachments/" + a.getId(), a.getOriginalFilename(), a.getContentType(), a.getFileSize()))
                .collect(Collectors.toList());

        List<CircularVersionDto> vers = versionRepository.findByCircularIdOrderByVersionNumberDesc(c.getId()).stream()
                .map(v -> new CircularVersionDto(v.getVersionNumber(), v.getTitle(), v.getDescription(), v.getRulesAndRegulations(),
                        resolveActorName(v.getEditedBy()), v.getEditNote(), v.getCreatedAt()))
                .collect(Collectors.toList());

        CircularRead myRead = studentContext != null
                ? readRepository.findByCircularIdAndStudentId(c.getId(), studentContext.getId()).orElse(null)
                : null;
        EventRsvp myRsvp = studentContext != null
                ? rsvpRepository.findByCircularIdAndStudentId(c.getId(), studentContext.getId()).orElse(null)
                : null;

        long totalTarget = resolveTargetedStudents(c).size();
        long readCnt = readRepository.countByCircularId(c.getId());
        long ackCnt = readRepository.countByCircularIdAndAcknowledgedAtIsNotNull(c.getId());
        long attCnt = rsvpRepository.countByCircularIdAndRsvpStatus(c.getId(), EventRsvp.RsvpStatus.ATTENDING);
        long notAttCnt = rsvpRepository.countByCircularIdAndRsvpStatus(c.getId(), EventRsvp.RsvpStatus.NOT_ATTENDING);

        return new CircularResponse(
                c.getId(), c.getCircularCode(), c.getType().name(), c.getTitle(), c.getDescription(),
                c.getRulesAndRegulations(), c.getEventDateTime(), c.getVenue(), c.getPriority().name(),
                c.getStatus().name(), c.isRequireAcknowledgement(), c.getTargetScope().name(),
                c.getHostelBlock(), c.getScheduledPublishAt(), c.getExpiresAt(), c.getPublishedAt(),
                c.getVersion(), c.isUpdatedFlag(), resolveActorName(c.getCreatedBy()), c.getCreatedAt(),
                myRead != null, myRead != null ? myRead.getReadAt() : null,
                myRead != null && myRead.getAcknowledgedAt() != null,
                myRead != null ? myRead.getAcknowledgedAt() : null,
                myRsvp != null ? myRsvp.getRsvpStatus().name() : null,
                attCnt, notAttCnt, totalTarget, readCnt, ackCnt, atts, vers
        );
    }

    private String resolveActorName(User u) {
        if (u == null) return "System";
        return staffProfileRepository.findByUserId(u.getId()).map(StaffProfile::getFullName).orElse(u.getEmail());
    }

    private Circular getCircularOrThrow(Long id) {
        return circularRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Circular not found: " + id));
    }

    private Student getStudentByUser(User user) {
        return studentRepository.findByUserId(user.getId())
                .orElseThrow(() -> new ResourceNotFoundException("Student profile not found"));
    }

    private <E extends Enum<E>> E parseEnum(Class<E> cls, String raw, String label) {
        try {
            return Enum.valueOf(cls, raw.trim().toUpperCase());
        } catch (Exception e) {
            throw new BadRequestException("Invalid " + label + ": " + raw);
        }
    }

    private String escapeCsv(String val) {
        if (val == null) return "";
        if (val.contains(",") || val.contains("\"") || val.contains("\n")) {
            return "\"" + val.replace("\"", "\"\"") + "\"";
        }
        return val;
    }
}

