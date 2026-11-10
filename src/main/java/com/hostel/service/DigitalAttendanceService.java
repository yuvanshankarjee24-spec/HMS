package com.hostel.service;

import com.hostel.dto.DigitalAttendanceDtos.*;
import com.hostel.entity.*;
import com.hostel.exception.CustomExceptions.*;
import com.hostel.repository.*;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.*;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Collectors;

@Service
public class DigitalAttendanceService {

    private static final DateTimeFormatter TIME_12H = DateTimeFormatter.ofPattern("hh:mm a");

    private final AttendanceSessionRepository sessionRepository;
    private final DigitalAttendanceRecordRepository recordRepository;
    private final AttendanceNotificationLogRepository notifLogRepository;
    private final OutpassLockHistoryRepository lockHistoryRepository;
    private final StudentRepository studentRepository;
    private final BedRepository bedRepository;
    private final OutpassRepository outpassRepository;
    private final SystemSettingRepository settingRepository;
    private final NotificationService notificationService;
    private final AuditService auditService;

    public DigitalAttendanceService(AttendanceSessionRepository sessionRepository,
                                    DigitalAttendanceRecordRepository recordRepository,
                                    AttendanceNotificationLogRepository notifLogRepository,
                                    OutpassLockHistoryRepository lockHistoryRepository,
                                    StudentRepository studentRepository,
                                    BedRepository bedRepository,
                                    OutpassRepository outpassRepository,
                                    SystemSettingRepository settingRepository,
                                    NotificationService notificationService,
                                    AuditService auditService) {
        this.sessionRepository = sessionRepository;
        this.recordRepository = recordRepository;
        this.notifLogRepository = notifLogRepository;
        this.lockHistoryRepository = lockHistoryRepository;
        this.studentRepository = studentRepository;
        this.bedRepository = bedRepository;
        this.outpassRepository = outpassRepository;
        this.settingRepository = settingRepository;
        this.notificationService = notificationService;
        this.auditService = auditService;
    }

    // =====================================================
    // SESSION CONFIGURATION & GLOBAL DEFAULTS
    // =====================================================

    public List<AttendanceSessionResponse> listAllSessions() {
        return sessionRepository.findAllByOrderByStartTimeAsc().stream()
                .map(this::toSessionResponse)
                .collect(Collectors.toList());
    }

    @Transactional
    public AttendanceSessionResponse createSession(AttendanceSessionRequest req, User actor) {
        if (!req.startTime().isBefore(req.endTime())) {
            throw new BadRequestException("Start time must be before end time (deadline)");
        }
        AttendanceDefaultsDto defaults = getDefaults();

        AttendanceSession s = new AttendanceSession();
        s.setName(req.name().trim());
        s.setHostelBlock(req.hostelBlock().trim());
        s.setStartTime(req.startTime());
        s.setEndTime(req.endTime());
        s.setActiveDays(normalizeActiveDays(req.activeDays()));
        s.setEnabled(req.enabled() != null ? req.enabled() : true);
        s.setReminderLeadMinutes(req.reminderLeadMinutes() != null ? req.reminderLeadMinutes() : defaults.reminderLeadMinutes());
        s.setSecondReminderMinutes(req.secondReminderMinutes() != null ? req.secondReminderMinutes() : defaults.secondReminderMinutes());
        boolean geo = req.geofenceEnabled() != null ? req.geofenceEnabled() : defaults.geofenceEnabled();
        s.setGeofenceEnabled(geo);
        s.setHostelLatitude(req.hostelLatitude() != null ? req.hostelLatitude() : defaults.hostelLatitude());
        s.setHostelLongitude(req.hostelLongitude() != null ? req.hostelLongitude() : defaults.hostelLongitude());
        s.setGeofenceRadiusMeters(req.geofenceRadiusMeters() != null ? req.geofenceRadiusMeters() : defaults.geofenceRadiusMeters());
        s.setCreatedBy(actor);

        if (s.isGeofenceEnabled() && (s.getHostelLatitude() == null || s.getHostelLongitude() == null)) {
            throw new BadRequestException("Hostel latitude and longitude are required when geofence is enabled");
        }

        AttendanceSession saved = sessionRepository.save(s);
        auditService.log(actor, "CREATE_ATTENDANCE_SESSION", saved.getName(), "SUCCESS");
        return toSessionResponse(saved);
    }

    @Transactional
    public AttendanceSessionResponse updateSession(Long id, AttendanceSessionRequest req, User actor) {
        AttendanceSession s = sessionRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Attendance session not found"));
        if (!req.startTime().isBefore(req.endTime())) {
            throw new BadRequestException("Start time must be before end time (deadline)");
        }

        s.setName(req.name().trim());
        s.setHostelBlock(req.hostelBlock().trim());
        s.setStartTime(req.startTime());
        s.setEndTime(req.endTime());
        s.setActiveDays(normalizeActiveDays(req.activeDays()));
        if (req.enabled() != null) s.setEnabled(req.enabled());
        if (req.reminderLeadMinutes() != null) s.setReminderLeadMinutes(req.reminderLeadMinutes());
        s.setSecondReminderMinutes(req.secondReminderMinutes());
        if (req.geofenceEnabled() != null) s.setGeofenceEnabled(req.geofenceEnabled());
        s.setHostelLatitude(req.hostelLatitude());
        s.setHostelLongitude(req.hostelLongitude());
        if (req.geofenceRadiusMeters() != null) s.setGeofenceRadiusMeters(req.geofenceRadiusMeters());

        if (s.isGeofenceEnabled() && (s.getHostelLatitude() == null || s.getHostelLongitude() == null)) {
            throw new BadRequestException("Hostel latitude and longitude are required when geofence is enabled");
        }

        AttendanceSession saved = sessionRepository.save(s);
        auditService.log(actor, "UPDATE_ATTENDANCE_SESSION", "SessionId=" + id, "SUCCESS");
        return toSessionResponse(saved);
    }

    @Transactional
    public AttendanceSessionResponse toggleSession(Long id, boolean enabled, User actor) {
        AttendanceSession s = sessionRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Attendance session not found"));
        s.setEnabled(enabled);
        AttendanceSession saved = sessionRepository.save(s);
        auditService.log(actor, "TOGGLE_ATTENDANCE_SESSION", "SessionId=" + id + " Enabled=" + enabled, "SUCCESS");
        return toSessionResponse(saved);
    }

    public AttendanceDefaultsDto getDefaults() {
        int lead = Integer.parseInt(getSetting("attendance.reminder_lead_minutes", "30"));
        int second = Integer.parseInt(getSetting("attendance.second_reminder_minutes", "5"));
        boolean geo = Boolean.parseBoolean(getSetting("attendance.geofence_enabled", "false"));
        double lat = Double.parseDouble(getSetting("attendance.hostel_latitude", "11.0698"));
        double lon = Double.parseDouble(getSetting("attendance.hostel_longitude", "77.0928"));
        int rad = Integer.parseInt(getSetting("attendance.geofence_radius_meters", "200"));
        return new AttendanceDefaultsDto(lead, second, geo, lat, lon, rad);
    }

    @Transactional
    public AttendanceDefaultsDto updateDefaults(AttendanceDefaultsDto dto, User actor) {
        saveSetting("attendance.reminder_lead_minutes", String.valueOf(dto.reminderLeadMinutes()));
        saveSetting("attendance.second_reminder_minutes", String.valueOf(dto.secondReminderMinutes()));
        saveSetting("attendance.geofence_enabled", String.valueOf(dto.geofenceEnabled()));
        saveSetting("attendance.hostel_latitude", String.valueOf(dto.hostelLatitude()));
        saveSetting("attendance.hostel_longitude", String.valueOf(dto.hostelLongitude()));
        saveSetting("attendance.geofence_radius_meters", String.valueOf(dto.geofenceRadiusMeters()));
        auditService.log(actor, "UPDATE_ATTENDANCE_DEFAULTS", "Lead=" + dto.reminderLeadMinutes(), "SUCCESS");
        return getDefaults();
    }

    // =====================================================
    // SCHEDULED JOBS (IDEMPOTENT, ASIA/KOLKATA)
    // =====================================================

    @Transactional
    public void processAttendanceCycle(LocalDateTime now) {
        initializeSessionsForDate(now);
        sendRemindersAt(now);
        processDeadlinesAt(now);
    }

    @Transactional
    public int initializeSessionsForDate(LocalDateTime now) {
        LocalDate today = now.toLocalDate();
        LocalTime nowTime = now.toLocalTime();
        String todayDay = dayAbbr(today.getDayOfWeek());

        List<AttendanceSession> sessions = sessionRepository.findByEnabledTrueOrderByStartTimeAsc();
        List<Student> activeStudents = getActiveEligibleStudents();
        List<Outpass> activePasses = getActiveOutpasses();

        int created = 0;
        for (AttendanceSession session : sessions) {
            if (!isSessionActiveOnDay(session, todayDay)) continue;
            if (nowTime.isBefore(session.getStartTime())) continue;

            for (Student student : activeStudents) {
                if (!matchesBlock(student, session.getHostelBlock())) continue;
                Optional<DigitalAttendanceRecord> existing =
                        recordRepository.findByStudentIdAndSessionIdAndAttendanceDate(student.getId(), session.getId(), today);
                if (existing.isPresent()) continue;

                DigitalAttendanceRecord rec = new DigitalAttendanceRecord();
                rec.setStudent(student);
                rec.setSession(session);
                rec.setAttendanceDate(today);

                if (isStudentOnOutpass(student.getId(), today, session, activePasses)) {
                    rec.setStatus(DigitalAttendanceRecord.Status.ON_OUTPASS);
                    rec.setRemarks("Excluded automatically (Approved/Active Outpass)");
                } else {
                    rec.setStatus(DigitalAttendanceRecord.Status.PENDING);
                }
                recordRepository.save(rec);
                created++;
            }
        }
        return created;
    }

    @Transactional
    public int sendRemindersAt(LocalDateTime now) {
        LocalDate today = now.toLocalDate();
        LocalTime nowTime = now.toLocalTime();
        String todayDay = dayAbbr(today.getDayOfWeek());

        List<AttendanceSession> sessions = sessionRepository.findByEnabledTrueOrderByStartTimeAsc();
        List<Outpass> activePasses = getActiveOutpasses();
        int sentCount = 0;

        for (AttendanceSession session : sessions) {
            if (!isSessionActiveOnDay(session, todayDay)) continue;
            if (!nowTime.isBefore(session.getEndTime())) continue;

            LocalTime firstTrigger = session.getEndTime().minusMinutes(session.getReminderLeadMinutes());
            if (!nowTime.isBefore(firstTrigger)) {
                sentCount += sendReminderBatch(session, today, nowTime, AttendanceNotificationLog.ReminderType.FIRST_REMINDER, activePasses);
            }

            Integer secondMins = session.getSecondReminderMinutes();
            if (secondMins != null && secondMins > 0) {
                LocalTime secondTrigger = session.getEndTime().minusMinutes(secondMins);
                if (!nowTime.isBefore(secondTrigger)) {
                    sentCount += sendReminderBatch(session, today, nowTime, AttendanceNotificationLog.ReminderType.SECOND_REMINDER, activePasses);
                }
            }
        }
        return sentCount;
    }

    private int sendReminderBatch(AttendanceSession session,
                                  LocalDate today,
                                  LocalTime nowTime,
                                  AttendanceNotificationLog.ReminderType reminderType,
                                  List<Outpass> activePasses) {
        List<DigitalAttendanceRecord> pendingRecords = recordRepository
                .findBySessionIdAndAttendanceDateAndStatus(session.getId(), today, DigitalAttendanceRecord.Status.PENDING);

        int sent = 0;
        long minsRemaining = Math.max(1, Duration.between(nowTime, session.getEndTime()).toMinutes());
        String deadlineStr = session.getEndTime().format(TIME_12H);

        for (DigitalAttendanceRecord rec : pendingRecords) {
            Student s = rec.getStudent();
            if (isStudentOnOutpass(s.getId(), today, session, activePasses)) {
                rec.setStatus(DigitalAttendanceRecord.Status.ON_OUTPASS);
                rec.setRemarks("Excluded automatically (Active Outpass)");
                recordRepository.save(rec);
                continue;
            }
            boolean alreadySent = notifLogRepository.existsByStudentIdAndSessionIdAndAttendanceDateAndReminderType(
                    s.getId(), session.getId(), today, reminderType);
            if (alreadySent) continue;

            String title = reminderType == AttendanceNotificationLog.ReminderType.SECOND_REMINDER
                    ? "Final Attendance Reminder: " + session.getName()
                    : "Attendance Reminder: " + session.getName();
            String msg = "Your attendance closes at " + deadlineStr + ". Please mark it within " + minsRemaining + " minutes.";
            notificationService.notifyUser(s.getUser(), title, msg, "DIGITAL_ATTENDANCE", session.getId());

            AttendanceNotificationLog log = new AttendanceNotificationLog();
            log.setStudent(s);
            log.setSession(session);
            log.setAttendanceDate(today);
            log.setReminderType(reminderType);
            notifLogRepository.save(log);
            sent++;
        }
        return sent;
    }

    @Transactional
    public int processDeadlinesAt(LocalDateTime now) {
        LocalDate today = now.toLocalDate();
        LocalTime nowTime = now.toLocalTime();
        String todayDay = dayAbbr(today.getDayOfWeek());

        List<AttendanceSession> sessions = sessionRepository.findByEnabledTrueOrderByStartTimeAsc();
        List<Outpass> activePasses = getActiveOutpasses();
        int missedCount = 0;

        for (AttendanceSession session : sessions) {
            if (!isSessionActiveOnDay(session, todayDay)) continue;
            if (nowTime.isBefore(session.getEndTime())) continue;

            List<DigitalAttendanceRecord> pendingRecords = recordRepository
                    .findBySessionIdAndAttendanceDateAndStatus(session.getId(), today, DigitalAttendanceRecord.Status.PENDING);

            for (DigitalAttendanceRecord rec : pendingRecords) {
                Student s = rec.getStudent();
                if (isStudentOnOutpass(s.getId(), today, session, activePasses)) {
                    rec.setStatus(DigitalAttendanceRecord.Status.ON_OUTPASS);
                    rec.setRemarks("Excluded automatically (Approved/Active Outpass)");
                    recordRepository.save(rec);
                    continue;
                }

                rec.setStatus(DigitalAttendanceRecord.Status.MISSED);
                rec.setRemarks("Not Pressed before deadline (" + session.getEndTime().format(TIME_12H) + ")");
                recordRepository.save(rec);
                missedCount++;

                // Lock student outpass permanently until manually unlocked by Warden/Admin
                String lockReason = "Missed " + session.getName() + " on " + today;
                s.setLocked(true);
                s.setLockReason(lockReason);
                s.setLockedAt(now);
                studentRepository.save(s);

                OutpassLockHistory lockHist = new OutpassLockHistory();
                lockHist.setStudent(s);
                lockHist.setAction(OutpassLockHistory.Action.LOCKED);
                lockHist.setReason(lockReason);
                lockHist.setSession(session);
                lockHist.setAttendanceDate(today);
                lockHistoryRepository.save(lockHist);

                boolean alreadyNotified = notifLogRepository.existsByStudentIdAndSessionIdAndAttendanceDateAndReminderType(
                        s.getId(), session.getId(), today, AttendanceNotificationLog.ReminderType.MISSED_LOCK);
                if (!alreadyNotified) {
                    notificationService.notifyUser(
                            s.getUser(),
                            "Attendance Missed - Outpass Locked",
                            "You missed " + session.getName() + " on " + today + ". Your outpass privileges have been locked. Please contact the warden.",
                            "OUTPASS_LOCK",
                            s.getId()
                    );
                    notificationService.notifyRole(
                            "WARDEN",
                            "Missed Attendance & Outpass Locked: " + s.getRegisterNumber(),
                            s.getFullName() + " (" + s.getRegisterNumber() + ") missed " + session.getName() + " on " + today + ". Outpass locked.",
                            "OUTPASS_LOCK",
                            s.getId()
                    );
                    AttendanceNotificationLog log = new AttendanceNotificationLog();
                    log.setStudent(s);
                    log.setSession(session);
                    log.setAttendanceDate(today);
                    log.setReminderType(AttendanceNotificationLog.ReminderType.MISSED_LOCK);
                    notifLogRepository.save(log);
                }
            }
        }
        return missedCount;
    }

    // =====================================================
    // STUDENT SIDE: TODAY, MARK ATTENDANCE, HISTORY
    // =====================================================

    @Transactional
    public StudentTodayAttendanceResponse getStudentToday(User studentUser) {
        return getStudentTodayAt(studentUser, LocalDateTime.now());
    }

    @Transactional
    public StudentTodayAttendanceResponse getStudentTodayAt(User studentUser, LocalDateTime now) {
        Student student = getStudentByUser(studentUser);
        LocalDate today = now.toLocalDate();
        LocalTime nowTime = now.toLocalTime();
        String todayDay = dayAbbr(today.getDayOfWeek());

        // Ensure records exist for open sessions
        initializeSessionsForDate(now);

        List<AttendanceSession> sessions = sessionRepository.findByEnabledTrueOrderByStartTimeAsc();
        List<Outpass> activePasses = getActiveOutpasses();
        List<StudentTodaySessionItem> items = new ArrayList<>();

        for (AttendanceSession session : sessions) {
            if (!isSessionActiveOnDay(session, todayDay)) continue;
            if (!matchesBlock(student, session.getHostelBlock())) continue;

            boolean beforeStart = nowTime.isBefore(session.getStartTime());
            boolean afterDeadline = !nowTime.isBefore(session.getEndTime());
            boolean windowOpen = !beforeStart && !afterDeadline;
            long secondsUntilDeadline = windowOpen
                    ? Math.max(0, Duration.between(nowTime, session.getEndTime()).getSeconds())
                    : 0;

            Optional<DigitalAttendanceRecord> recOpt = recordRepository
                    .findByStudentIdAndSessionIdAndAttendanceDate(student.getId(), session.getId(), today);

            String status;
            LocalDateTime markedAt = null;
            String remarks = null;
            if (recOpt.isPresent()) {
                status = recOpt.get().getStatus().name();
                markedAt = recOpt.get().getMarkedAt();
                remarks = recOpt.get().getRemarks();
            } else if (isStudentOnOutpass(student.getId(), today, session, activePasses)) {
                status = DigitalAttendanceRecord.Status.ON_OUTPASS.name();
                remarks = "On Approved Outpass";
            } else if (afterDeadline) {
                status = DigitalAttendanceRecord.Status.MISSED.name();
            } else {
                status = DigitalAttendanceRecord.Status.PENDING.name();
            }

            items.add(new StudentTodaySessionItem(
                    session.getId(),
                    session.getName(),
                    session.getHostelBlock(),
                    session.getStartTime(),
                    session.getEndTime(),
                    windowOpen,
                    beforeStart,
                    afterDeadline,
                    secondsUntilDeadline,
                    session.isGeofenceEnabled(),
                    session.getHostelLatitude(),
                    session.getHostelLongitude(),
                    session.getGeofenceRadiusMeters(),
                    status,
                    markedAt,
                    remarks
            ));
        }

        return new StudentTodayAttendanceResponse(
                today,
                now,
                student.isLocked(),
                student.getLockReason(),
                student.getLockedAt(),
                items
        );
    }

    @Transactional
    public AttendanceHistoryItem markAttendance(User studentUser,
                                              MarkDigitalAttendanceRequest req,
                                              String deviceInfo,
                                              String ipAddress) {
        return markAttendanceAt(studentUser, req, deviceInfo, ipAddress, LocalDateTime.now());
    }

    @Transactional
    public AttendanceHistoryItem markAttendanceAt(User studentUser,
                                                MarkDigitalAttendanceRequest req,
                                                String deviceInfo,
                                                String ipAddress,
                                                LocalDateTime serverNow) {
        Student student = getStudentByUser(studentUser);
        AttendanceSession session = sessionRepository.findById(req.sessionId())
                .orElseThrow(() -> new ResourceNotFoundException("Attendance session not found"));

        if (!session.isEnabled()) {
            throw new BadRequestException("This attendance session is currently disabled");
        }

        LocalDate today = serverNow.toLocalDate();
        LocalTime nowTime = serverNow.toLocalTime();
        String todayDay = dayAbbr(today.getDayOfWeek());
        if (!isSessionActiveOnDay(session, todayDay)) {
            throw new BadRequestException("This attendance session is not scheduled for today");
        }

        if (nowTime.isBefore(session.getStartTime())) {
            throw new BadRequestException("Attendance window is not open yet. Opens at " + session.getStartTime().format(TIME_12H));
        }
        if (!nowTime.isBefore(session.getEndTime())) {
            throw new BadRequestException("Attendance deadline has passed (" + session.getEndTime().format(TIME_12H) + ")");
        }

        // Geofence enforcement
        if (session.isGeofenceEnabled()) {
            if (req.latitude() == null || req.longitude() == null) {
                throw new BadRequestException("Device GPS location is required to mark attendance when geofencing is enabled");
            }
            if (session.getHostelLatitude() != null && session.getHostelLongitude() != null) {
                double distance = haversineMeters(req.latitude(), req.longitude(),
                        session.getHostelLatitude(), session.getHostelLongitude());
                int allowedRadius = session.getGeofenceRadiusMeters() != null ? session.getGeofenceRadiusMeters() : 200;
                if (distance > allowedRadius) {
                    throw new BadRequestException("Outside hostel geofence (" + Math.round(distance)
                            + "m away; max allowed " + allowedRadius + "m). Please move inside campus/hostel bounds.");
                }
            }
        }

        DigitalAttendanceRecord rec = recordRepository
                .findByStudentIdAndSessionIdAndAttendanceDate(student.getId(), session.getId(), today)
                .orElseGet(() -> {
                    DigitalAttendanceRecord r = new DigitalAttendanceRecord();
                    r.setStudent(student);
                    r.setSession(session);
                    r.setAttendanceDate(today);
                    r.setStatus(DigitalAttendanceRecord.Status.PENDING);
                    return r;
                });

        if (rec.getStatus() == DigitalAttendanceRecord.Status.MARKED ||
            rec.getStatus() == DigitalAttendanceRecord.Status.MANUAL_PRESENT) {
            throw new BadRequestException("Attendance already marked for this session today");
        }
        if (rec.getStatus() == DigitalAttendanceRecord.Status.MISSED) {
            throw new BadRequestException("This session is already marked as Missed");
        }
        if (rec.getStatus() == DigitalAttendanceRecord.Status.ON_OUTPASS) {
            throw new BadRequestException("You are currently marked On Outpass for this session");
        }

        rec.setStatus(DigitalAttendanceRecord.Status.MARKED);
        rec.setMarkedAt(serverNow);
        rec.setLatitude(req.latitude());
        rec.setLongitude(req.longitude());
        rec.setDeviceInfo(deviceInfo != null && deviceInfo.length() > 250 ? deviceInfo.substring(0, 250) : deviceInfo);
        rec.setIpAddress(ipAddress);
        rec.setMarkedBy(studentUser);
        rec.setRemarks("Marked digitally by student");
        DigitalAttendanceRecord saved = recordRepository.save(rec);

        auditService.log(studentUser, "MARK_DIGITAL_ATTENDANCE", "Session=" + session.getName() + " Date=" + today, "SUCCESS");
        return toHistoryItem(saved);
    }

    public StudentAttendanceHistoryResponse getStudentHistory(User studentUser, Integer year, Integer month, Pageable pageable) {
        Student student = getStudentByUser(studentUser);
        LocalDate fromDate = null;
        LocalDate toDate = null;
        if (year != null && month != null) {
            YearMonth ym = YearMonth.of(year, month);
            fromDate = ym.atDay(1);
            toDate = ym.atEndOfMonth();
        }

        Page<DigitalAttendanceRecord> page = recordRepository.findStudentHistory(student.getId(), fromDate, toDate, pageable);
        List<DigitalAttendanceRecord> allForPeriod = recordRepository.findStudentHistoryAll(student.getId(), fromDate, toDate);

        long total = allForPeriod.size();
        long marked = allForPeriod.stream().filter(r -> r.getStatus() == DigitalAttendanceRecord.Status.MARKED || r.getStatus() == DigitalAttendanceRecord.Status.MANUAL_PRESENT).count();
        long missed = allForPeriod.stream().filter(r -> r.getStatus() == DigitalAttendanceRecord.Status.MISSED).count();
        long onOutpass = allForPeriod.stream().filter(r -> r.getStatus() == DigitalAttendanceRecord.Status.ON_OUTPASS).count();
        long denominator = marked + missed;
        double pct = denominator > 0 ? Math.round((marked * 10000.0) / denominator) / 100.0 : 100.0;

        List<AttendanceHistoryItem> items = page.getContent().stream().map(this::toHistoryItem).collect(Collectors.toList());
        return new StudentAttendanceHistoryResponse(
                total, marked, missed, onOutpass, pct, items,
                page.getNumber(), page.getTotalPages(), page.getTotalElements()
        );
    }

    // =====================================================
    // WARDEN & ADMIN MONITORING, UNLOCK, MANUAL MARK, REPORTS
    // =====================================================

    @Transactional
    public WardenDigitalSummaryResponse getWardenSummary(LocalDate date) {
        LocalDate target = (date != null) ? date : LocalDate.now();
        if (target.equals(LocalDate.now())) {
            initializeSessionsForDate(LocalDateTime.now());
        }
        List<DigitalAttendanceRecord> recs = recordRepository.findByAttendanceDate(target);
        long totalStudents = getActiveEligibleStudents().size();
        long marked = recs.stream().filter(r -> r.getStatus() == DigitalAttendanceRecord.Status.MARKED || r.getStatus() == DigitalAttendanceRecord.Status.MANUAL_PRESENT).count();
        long pending = recs.stream().filter(r -> r.getStatus() == DigitalAttendanceRecord.Status.PENDING).count();
        long missed = recs.stream().filter(r -> r.getStatus() == DigitalAttendanceRecord.Status.MISSED).count();
        long onOutpass = recs.stream().filter(r -> r.getStatus() == DigitalAttendanceRecord.Status.ON_OUTPASS).count();
        long locked = studentRepository.findAll().stream().filter(Student::isLocked).count();

        return new WardenDigitalSummaryResponse(target, totalStudents, marked, pending, missed, onOutpass, locked);
    }

    public Page<MissedOrPendingStudentItem> listByStatusFiltered(DigitalAttendanceRecord.Status status,
                                                                 LocalDate date,
                                                                 Long sessionId,
                                                                 String block,
                                                                 String room,
                                                                 String query,
                                                                 Pageable pageable) {
        String q = (query != null && !query.isBlank()) ? query.trim() : null;
        Page<DigitalAttendanceRecord> page = recordRepository.findByStatusFiltered(status, date, sessionId, q, pageable);

        LocalDate thirtyDaysAgo = LocalDate.now().minusDays(30);
        return page.map(r -> toMissedOrPendingItem(r, thirtyDaysAgo));
    }

    public List<MissedOrPendingStudentItem> listLockedStudents(String query) {
        LocalDate thirtyDaysAgo = LocalDate.now().minusDays(30);
        String q = (query != null && !query.isBlank()) ? query.trim().toLowerCase() : null;
        return studentRepository.findAll().stream()
                .filter(Student::isLocked)
                .filter(s -> q == null || s.getFullName().toLowerCase().contains(q) || s.getRegisterNumber().toLowerCase().contains(q))
                .map(s -> {
                    String roomNo = bedRepository.findByStudentId(s.getId()).map(b -> b.getRoom().getRoomNumber()).orElse("-");
                    String blk = bedRepository.findByStudentId(s.getId()).map(b -> "Block A (Floor " + b.getRoom().getFloor() + ")").orElse("Main Block");
                    long misses30 = recordRepository.countStudentByStatusSince(s.getId(), DigitalAttendanceRecord.Status.MISSED, thirtyDaysAgo);
                    return new MissedOrPendingStudentItem(
                            null, s.getId(), s.getFullName(), s.getRegisterNumber(), roomNo, blk,
                            null, null, s.getLockedAt() != null ? s.getLockedAt().toLocalDate() : LocalDate.now(),
                            s.getLockedAt(), "LOCKED", true, s.getLockReason(), s.getLockedAt(),
                            misses30, misses30 >= 3
                    );
                })
                .collect(Collectors.toList());
    }

    @Transactional
    public void unlockOutpass(Long studentId, String reason, User actor) {
        if (reason == null || reason.isBlank()) {
            throw new BadRequestException("Unlock reason / remark is mandatory");
        }
        Student student = studentRepository.findById(studentId)
                .orElseThrow(() -> new ResourceNotFoundException("Student not found"));

        student.setLocked(false);
        student.setUnlockedBy(actor);
        student.setUnlockedAt(LocalDateTime.now());
        student.setUnlockRemarks(reason.trim());
        studentRepository.save(student);

        OutpassLockHistory hist = new OutpassLockHistory();
        hist.setStudent(student);
        hist.setAction(OutpassLockHistory.Action.UNLOCKED);
        hist.setReason(reason.trim());
        hist.setActor(actor);
        lockHistoryRepository.save(hist);

        notificationService.notifyUser(
                student.getUser(),
                "Outpass Unlocked",
                "Your outpass privileges have been restored by the Warden/Admin. Remark: " + reason.trim(),
                "OUTPASS_LOCK",
                student.getId()
        );

        auditService.log(actor, "UNLOCK_OUTPASS", "Student=" + student.getRegisterNumber() + " Reason=" + reason.trim(), "SUCCESS");
    }

    @Transactional
    public int bulkUnlockOutpasses(List<Long> studentIds, String reason, User actor) {
        int count = 0;
        for (Long sid : studentIds) {
            unlockOutpass(sid, reason, actor);
            count++;
        }
        return count;
    }

    @Transactional
    public MissedOrPendingStudentItem manualMarkPresent(ManualMarkAttendanceRequest req, User actor) {
        Student student = studentRepository.findById(req.studentId())
                .orElseThrow(() -> new ResourceNotFoundException("Student not found"));
        AttendanceSession session = sessionRepository.findById(req.sessionId())
                .orElseThrow(() -> new ResourceNotFoundException("Attendance session not found"));

        DigitalAttendanceRecord rec = recordRepository
                .findByStudentIdAndSessionIdAndAttendanceDate(student.getId(), session.getId(), req.date())
                .orElseGet(() -> {
                    DigitalAttendanceRecord r = new DigitalAttendanceRecord();
                    r.setStudent(student);
                    r.setSession(session);
                    r.setAttendanceDate(req.date());
                    return r;
                });

        rec.setStatus(DigitalAttendanceRecord.Status.MANUAL_PRESENT);
        rec.setMarkedAt(LocalDateTime.now());
        rec.setMarkedBy(actor);
        rec.setRemarks("Manually marked by warden: " + req.reason().trim());
        DigitalAttendanceRecord saved = recordRepository.save(rec);

        // Unlock outpass automatically if this missed attendance was the sole lock reason
        if (student.isLocked()) {
            boolean noOtherMissed = !recordRepository.existsByStudentIdAndStatus(student.getId(), DigitalAttendanceRecord.Status.MISSED);
            boolean matchesReason = student.getLockReason() != null &&
                    student.getLockReason().contains(session.getName()) &&
                    student.getLockReason().contains(req.date().toString());
            if (noOtherMissed || matchesReason) {
                unlockOutpass(student.getId(), "Auto-unlocked: Manually marked by warden (" + req.reason().trim() + ")", actor);
            }
        }

        auditService.log(actor, "MANUAL_MARK_ATTENDANCE", "Student=" + student.getRegisterNumber() + " Date=" + req.date(), "SUCCESS");
        return toMissedOrPendingItem(saved, LocalDate.now().minusDays(30));
    }

    @Transactional
    public void relockOutpass(Long studentId, String reason, User actor) {
        if (reason == null || reason.isBlank()) {
            throw new BadRequestException("Lock reason is mandatory");
        }
        Student student = studentRepository.findById(studentId)
                .orElseThrow(() -> new ResourceNotFoundException("Student not found"));
        student.setLocked(true);
        student.setLockReason(reason.trim());
        student.setLockedAt(LocalDateTime.now());
        studentRepository.save(student);

        OutpassLockHistory hist = new OutpassLockHistory();
        hist.setStudent(student);
        hist.setAction(OutpassLockHistory.Action.RELOCKED);
        hist.setReason(reason.trim());
        hist.setActor(actor);
        lockHistoryRepository.save(hist);

        notificationService.notifyUser(
                student.getUser(),
                "Outpass Re-locked",
                "Your outpass has been locked by the Warden/Admin. Reason: " + reason.trim(),
                "OUTPASS_LOCK",
                student.getId()
        );
        auditService.log(actor, "RELOCK_OUTPASS", "Student=" + student.getRegisterNumber(), "SUCCESS");
    }

    public List<AttendanceReportRow> getAttendanceReport(LocalDate fromDate, LocalDate toDate) {
        LocalDate end = (toDate != null) ? toDate : LocalDate.now();
        LocalDate start = (fromDate != null) ? fromDate : end.minusDays(30);
        List<DigitalAttendanceRecord> recs = recordRepository.findByAttendanceDateBetween(start, end);
        Map<Long, List<DigitalAttendanceRecord>> byStudent = recs.stream()
                .collect(Collectors.groupingBy(r -> r.getStudent().getId()));

        List<Student> students = studentRepository.findAll();
        List<AttendanceReportRow> rows = new ArrayList<>();
        for (Student s : students) {
            List<DigitalAttendanceRecord> sRecs = byStudent.getOrDefault(s.getId(), List.of());
            long total = sRecs.size();
            long marked = sRecs.stream().filter(r -> r.getStatus() == DigitalAttendanceRecord.Status.MARKED || r.getStatus() == DigitalAttendanceRecord.Status.MANUAL_PRESENT).count();
            long missed = sRecs.stream().filter(r -> r.getStatus() == DigitalAttendanceRecord.Status.MISSED).count();
            long out = sRecs.stream().filter(r -> r.getStatus() == DigitalAttendanceRecord.Status.ON_OUTPASS).count();
            long denom = marked + missed;
            double pct = denom > 0 ? Math.round((marked * 10000.0) / denom) / 100.0 : 100.0;
            String room = bedRepository.findByStudentId(s.getId()).map(b -> b.getRoom().getRoomNumber()).orElse("-");
            rows.add(new AttendanceReportRow(
                    s.getId(), s.getFullName(), s.getRegisterNumber(), room,
                    total, marked, missed, out, pct, s.isLocked(), missed >= 3
            ));
        }
        rows.sort(Comparator.comparingLong(AttendanceReportRow::missedCount).reversed()
                .thenComparing(AttendanceReportRow::fullName));
        return rows;
    }

    public String exportAttendanceReportCsv(LocalDate fromDate, LocalDate toDate) {
        List<AttendanceReportRow> rows = getAttendanceReport(fromDate, toDate);
        StringBuilder sb = new StringBuilder();
        sb.append("RegisterNumber,FullName,Room,TotalSessions,Marked,Missed,OnOutpass,AttendancePercentage,OutpassLocked,RepeatOffender\n");
        for (AttendanceReportRow r : rows) {
            sb.append(escapeCsv(r.registerNumber())).append(',')
              .append(escapeCsv(r.fullName())).append(',')
              .append(escapeCsv(r.roomNumber())).append(',')
              .append(r.totalSessions()).append(',')
              .append(r.markedCount()).append(',')
              .append(r.missedCount()).append(',')
              .append(r.onOutpassCount()).append(',')
              .append(r.attendancePercentage()).append(',')
              .append(r.outpassLocked()).append(',')
              .append(r.repeatOffender()).append('\n');
        }
        return sb.toString();
    }

    public AdminAttendanceAnalyticsResponse getAdminAnalytics() {
        LocalDate end = LocalDate.now();
        LocalDate start = end.minusDays(30);
        List<DigitalAttendanceRecord> recs = recordRepository.findByAttendanceDateBetween(start, end);
        List<Student> students = studentRepository.findAll();

        long locked = students.stream().filter(Student::isLocked).count();
        long markedAll = recs.stream().filter(r -> r.getStatus() == DigitalAttendanceRecord.Status.MARKED || r.getStatus() == DigitalAttendanceRecord.Status.MANUAL_PRESENT).count();
        long missedAll = recs.stream().filter(r -> r.getStatus() == DigitalAttendanceRecord.Status.MISSED).count();
        long denomAll = markedAll + missedAll;
        double overallPct = denomAll > 0 ? Math.round((markedAll * 10000.0) / denomAll) / 100.0 : 100.0;

        Map<String, List<DigitalAttendanceRecord>> byBlock = recs.stream().collect(Collectors.groupingBy(r -> {
            Optional<Bed> bed = bedRepository.findByStudentId(r.getStudent().getId());
            return bed.map(b -> "Floor " + b.getRoom().getFloor()).orElse("Unallocated");
        }));

        List<BlockAttendanceStat> blockStats = byBlock.entrySet().stream().map(e -> {
            long m = e.getValue().stream().filter(r -> r.getStatus() == DigitalAttendanceRecord.Status.MARKED || r.getStatus() == DigitalAttendanceRecord.Status.MANUAL_PRESENT).count();
            long ms = e.getValue().stream().filter(r -> r.getStatus() == DigitalAttendanceRecord.Status.MISSED).count();
            long d = m + ms;
            double pct = d > 0 ? Math.round((m * 10000.0) / d) / 100.0 : 100.0;
            return new BlockAttendanceStat(e.getKey(), e.getValue().size(), m, ms, pct);
        }).collect(Collectors.toList());

        List<AttendanceReportRow> mostMissed = getAttendanceReport(start, end).stream()
                .filter(r -> r.missedCount() > 0)
                .limit(10)
                .collect(Collectors.toList());

        List<DailyTrendPoint> trends = new ArrayList<>();
        for (int i = 6; i >= 0; i--) {
            LocalDate d = end.minusDays(i);
            List<DigitalAttendanceRecord> dayRecs = recs.stream().filter(r -> d.equals(r.getAttendanceDate())).toList();
            long m = dayRecs.stream().filter(r -> r.getStatus() == DigitalAttendanceRecord.Status.MARKED || r.getStatus() == DigitalAttendanceRecord.Status.MANUAL_PRESENT).count();
            long ms = dayRecs.stream().filter(r -> r.getStatus() == DigitalAttendanceRecord.Status.MISSED).count();
            long out = dayRecs.stream().filter(r -> r.getStatus() == DigitalAttendanceRecord.Status.ON_OUTPASS).count();
            trends.add(new DailyTrendPoint(d, m, ms, out));
        }

        return new AdminAttendanceAnalyticsResponse(
                students.size(), locked, overallPct, blockStats, mostMissed, trends
        );
    }

    // =====================================================
    // HELPERS
    // =====================================================

    private List<Student> getActiveEligibleStudents() {
        return studentRepository.findAll().stream()
                .filter(s -> s.getUser() != null && s.getUser().getStatus() == User.Status.ACTIVE)
                .collect(Collectors.toList());
    }

    private List<Outpass> getActiveOutpasses() {
        List<Outpass> list = new ArrayList<>();
        list.addAll(outpassRepository.findByStatusOrderByCreatedAtDesc(Outpass.Status.APPROVED));
        list.addAll(outpassRepository.findByStatusOrderByCreatedAtDesc(Outpass.Status.OUT));
        return list;
    }

    private boolean isStudentOnOutpass(Long studentId, LocalDate date, AttendanceSession session, List<Outpass> passes) {
        LocalDateTime sessionStart = LocalDateTime.of(date, session.getStartTime());
        LocalDateTime sessionEnd = LocalDateTime.of(date, session.getEndTime());
        for (Outpass op : passes) {
            if (op.getStudent().getId().equals(studentId)) {
                if (op.getStatus() == Outpass.Status.OUT) {
                    return true;
                }
                if (op.getStatus() == Outpass.Status.APPROVED &&
                    !op.getDepartureDateTime().isAfter(sessionEnd) &&
                    !op.getReturnDateTime().isBefore(sessionStart)) {
                    return true;
                }
            }
        }
        return false;
    }

    private boolean matchesBlock(Student student, String sessionBlock) {
        if (sessionBlock == null || sessionBlock.isBlank() || "ALL".equalsIgnoreCase(sessionBlock.trim())) {
            return true;
        }
        Optional<Bed> bedOpt = bedRepository.findByStudentId(student.getId());
        if (bedOpt.isEmpty()) return true;
        String studentBlock = "Floor " + bedOpt.get().getRoom().getFloor();
        return studentBlock.equalsIgnoreCase(sessionBlock.trim()) ||
               sessionBlock.trim().equalsIgnoreCase("Block A");
    }

    private boolean isSessionActiveOnDay(AttendanceSession session, String dayAbbr) {
        if (session.getActiveDays() == null) return true;
        return Arrays.stream(session.getActiveDays().split(","))
                .map(String::trim)
                .anyMatch(d -> d.equalsIgnoreCase(dayAbbr));
    }

    private String dayAbbr(DayOfWeek dow) {
        return dow.name().substring(0, 3);
    }

    private String normalizeActiveDays(String raw) {
        return Arrays.stream(raw.split(","))
                .map(String::trim)
                .map(String::toUpperCase)
                .filter(s -> !s.isBlank())
                .collect(Collectors.joining(","));
    }

    private Student getStudentByUser(User user) {
        return studentRepository.findByUserId(user.getId())
                .orElseThrow(() -> new ResourceNotFoundException("Student profile not found"));
    }

    private AttendanceSessionResponse toSessionResponse(AttendanceSession s) {
        return new AttendanceSessionResponse(
                s.getId(), s.getName(), s.getHostelBlock(),
                s.getStartTime(), s.getEndTime(), s.getActiveDays(), s.isEnabled(),
                s.getReminderLeadMinutes(), s.getSecondReminderMinutes(),
                s.isGeofenceEnabled(), s.getHostelLatitude(), s.getHostelLongitude(), s.getGeofenceRadiusMeters()
        );
    }

    private AttendanceHistoryItem toHistoryItem(DigitalAttendanceRecord r) {
        return new AttendanceHistoryItem(
                r.getId(), r.getAttendanceDate(), r.getSession().getId(), r.getSession().getName(),
                r.getStatus().name(), r.getMarkedAt(), r.getLatitude(), r.getLongitude(),
                r.getDeviceInfo(), r.getRemarks()
        );
    }

    private MissedOrPendingStudentItem toMissedOrPendingItem(DigitalAttendanceRecord r, LocalDate thirtyDaysAgo) {
        Student s = r.getStudent();
        Optional<Bed> bedOpt = bedRepository.findByStudentId(s.getId());
        String roomNo = bedOpt.map(b -> b.getRoom().getRoomNumber()).orElse("-");
        String blk = bedOpt.map(b -> "Floor " + b.getRoom().getFloor()).orElse("Main Block");
        long misses30 = recordRepository.countStudentByStatusSince(s.getId(), DigitalAttendanceRecord.Status.MISSED, thirtyDaysAgo);
        return new MissedOrPendingStudentItem(
                r.getId(), s.getId(), s.getFullName(), s.getRegisterNumber(), roomNo, blk,
                r.getSession().getId(), r.getSession().getName(), r.getAttendanceDate(),
                r.getUpdatedAt(), r.getStatus().name(),
                s.isLocked(), s.getLockReason(), s.getLockedAt(),
                misses30, misses30 >= 3
        );
    }

    private String getSetting(String key, String defaultVal) {
        return settingRepository.findById(key).map(SystemSetting::getValue).orElse(defaultVal);
    }

    private void saveSetting(String key, String value) {
        SystemSetting setting = settingRepository.findById(key).orElseGet(() -> new SystemSetting(key, value, key));
        setting.setValue(value);
        settingRepository.save(setting);
    }

    public static double haversineMeters(double lat1, double lon1, double lat2, double lon2) {
        final double R = 6371000.0;
        double dLat = Math.toRadians(lat2 - lat1);
        double dLon = Math.toRadians(lon2 - lon1);
        double a = Math.sin(dLat / 2) * Math.sin(dLat / 2)
                + Math.cos(Math.toRadians(lat1)) * Math.cos(Math.toRadians(lat2))
                * Math.sin(dLon / 2) * Math.sin(dLon / 2);
        double c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));
        return R * c;
    }

    private String escapeCsv(String val) {
        if (val == null) return "";
        if (val.contains(",") || val.contains("\"") || val.contains("\n")) {
            return "\"" + val.replace("\"", "\"\"") + "\"";
        }
        return val;
    }
}
