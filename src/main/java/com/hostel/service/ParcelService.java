package com.hostel.service;

import com.hostel.dto.ParcelDtos.*;
import com.hostel.entity.*;
import com.hostel.exception.CustomExceptions.*;
import com.hostel.repository.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.core.io.UrlResource;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.io.IOException;
import java.math.BigDecimal;
import java.net.MalformedURLException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.stream.Collectors;

@Service
public class ParcelService {

    private static final long MAX_IMAGE_BYTES = 2 * 1024 * 1024;
    private static final Set<String> ALLOWED_IMAGE_TYPES = Set.of("image/jpeg", "image/jpg", "image/png", "image/webp");
    private static final SecureRandom SECURE_RANDOM = new SecureRandom();
    private static final DateTimeFormatter TS_FMT = DateTimeFormatter.ofPattern("dd MMM yyyy hh:mm a");

    private final ParcelRepository parcelRepository;
    private final ParcelQrTokenRepository qrTokenRepository;
    private final ParcelStatusHistoryRepository historyRepository;
    private final ParcelAttachmentRepository attachmentRepository;
    private final StudentRepository studentRepository;
    private final StaffProfileRepository staffProfileRepository;
    private final BedRepository bedRepository;
    private final SystemSettingRepository settingRepository;
    private final NotificationService notificationService;
    private final AuditService auditService;

    @Value("${app.jwt.secret}")
    private String hmacSecret;

    @Value("${app.upload.dir:./storage/photos}")
    private String uploadDir;

    public ParcelService(ParcelRepository parcelRepository,
                         ParcelQrTokenRepository qrTokenRepository,
                         ParcelStatusHistoryRepository historyRepository,
                         ParcelAttachmentRepository attachmentRepository,
                         StudentRepository studentRepository,
                         StaffProfileRepository staffProfileRepository,
                         BedRepository bedRepository,
                         SystemSettingRepository settingRepository,
                         NotificationService notificationService,
                         AuditService auditService) {
        this.parcelRepository = parcelRepository;
        this.qrTokenRepository = qrTokenRepository;
        this.historyRepository = historyRepository;
        this.attachmentRepository = attachmentRepository;
        this.studentRepository = studentRepository;
        this.staffProfileRepository = staffProfileRepository;
        this.bedRepository = bedRepository;
        this.settingRepository = settingRepository;
        this.notificationService = notificationService;
        this.auditService = auditService;
    }

    // =====================================================
    // WARDEN: REGISTER PARCEL & STUDENT SEARCH
    // =====================================================

    public List<StudentSearchItem> searchStudents(String query) {
        String q = (query != null && !query.isBlank()) ? query.trim().toLowerCase() : null;
        return studentRepository.findAll().stream()
                .filter(s -> s.getUser() != null && s.getUser().getStatus() == User.Status.ACTIVE)
                .map(s -> {
                    Optional<Bed> bedOpt = bedRepository.findByStudentId(s.getId());
                    String room = bedOpt.map(b -> b.getRoom().getRoomNumber()).orElse("-");
                    String block = bedOpt.map(b -> "Floor " + b.getRoom().getFloor()).orElse("Main Block");
                    return new StudentSearchItem(s.getId(), s.getFullName(), s.getRegisterNumber(), room, block, s.getPhone());
                })
                .filter(item -> q == null ||
                        item.fullName().toLowerCase().contains(q) ||
                        item.registerNumber().toLowerCase().contains(q) ||
                        item.roomNumber().toLowerCase().contains(q))
                .sorted(Comparator.comparing(StudentSearchItem::fullName))
                .limit(25)
                .collect(Collectors.toList());
    }

    @Transactional
    public ParcelResponse createParcel(User warden,
                                       Long studentId,
                                       String courierName,
                                       String trackingNumber,
                                       String parcelTypeStr,
                                       String description,
                                       boolean isCod,
                                       BigDecimal codAmount,
                                       String storageLocation,
                                       MultipartFile photo) {
        if (studentId == null) {
            throw new BadRequestException("Student is required");
        }
        if (courierName == null || courierName.isBlank()) {
            throw new BadRequestException("Courier / sender name is required");
        }
        Student student = studentRepository.findById(studentId)
                .orElseThrow(() -> new ResourceNotFoundException("Student not found"));

        Parcel.ParcelType parcelType = parseParcelType(parcelTypeStr);
        if (parcelType == Parcel.ParcelType.COD) {
            isCod = true;
        }
        if (isCod && (codAmount == null || codAmount.compareTo(BigDecimal.ZERO) <= 0)) {
            throw new BadRequestException("COD amount must be greater than 0 when COD is enabled");
        }

        if (photo != null && !photo.isEmpty()) {
            validateImage(photo);
        }

        String block = bedRepository.findByStudentId(student.getId())
                .map(b -> "Floor " + b.getRoom().getFloor())
                .orElse("Main Block");

        LocalDateTime now = LocalDateTime.now();
        Parcel parcel = new Parcel();
        parcel.setParcelCode(generateUniqueParcelCode());
        parcel.setStudent(student);
        parcel.setHostelBlock(block);
        parcel.setCourierName(courierName.trim());
        parcel.setTrackingNumber(trackingNumber != null && !trackingNumber.isBlank() ? trackingNumber.trim() : null);
        parcel.setParcelType(parcelType);
        parcel.setDescription(description != null && !description.isBlank() ? description.trim() : null);
        parcel.setCod(isCod);
        parcel.setCodAmount(isCod ? codAmount : null);
        parcel.setCodPaymentPending(isCod);
        parcel.setCodCollected(false);
        parcel.setStorageLocation(storageLocation != null && !storageLocation.isBlank() ? storageLocation.trim() : null);
        parcel.setStatus(Parcel.Status.RECEIVED);
        parcel.setReceivedAt(now);
        parcel.setReceivedBy(warden);

        Parcel saved = parcelRepository.save(parcel);
        recordHistory(saved, null, Parcel.Status.RECEIVED.name(), warden, "Parcel registered at warden office");

        if (photo != null && !photo.isEmpty()) {
            storeAttachment(saved, photo, warden);
        }

        // Generate single-use unguessable HMAC-signed QR token + 6-digit fallback code
        ParcelQrToken token = new ParcelQrToken();
        token.setParcel(saved);
        token.setStudent(student);
        token.setQrToken(generateSignedQrToken(saved.getId(), student.getId()));
        token.setPickupCode(generateUniquePickupCode());
        token.setUsed(false);
        qrTokenRepository.save(token);

        // Transition RECEIVED -> NOTIFIED and send in-app notification
        saved.setStatus(Parcel.Status.NOTIFIED);
        saved = parcelRepository.save(saved);
        recordHistory(saved, Parcel.Status.RECEIVED.name(), Parcel.Status.NOTIFIED.name(), warden, "Student notified for pickup");

        String codNote = isCod ? " (COD Amount: Rs. " + codAmount + ")" : "";
        notificationService.notifyUser(
                student.getUser(),
                "Parcel Arrived: " + saved.getCourierName(),
                "A parcel from " + saved.getCourierName() + " (" + saved.getParcelCode() + ")" + codNote
                        + " has arrived for you. Show your QR code at the warden office to collect it.",
                "PARCEL",
                saved.getId()
        );

        auditService.log(warden, "REGISTER_PARCEL", saved.getParcelCode() + " Student=" + student.getRegisterNumber(), "SUCCESS");
        return toResponse(saved, true);
    }

    // =====================================================
    // STUDENT SIDE: MY PARCELS & UNREAD COUNT
    // =====================================================

    public Page<ParcelResponse> getStudentParcels(User studentUser, String tab, Pageable pageable) {
        Student student = getStudentByUser(studentUser);
        List<Parcel.Status> statuses;
        String t = tab != null ? tab.trim().toUpperCase() : "WAITING";
        if ("DELIVERED".equals(t)) {
            statuses = List.of(Parcel.Status.DELIVERED);
        } else if ("ALL".equals(t)) {
            statuses = Arrays.asList(Parcel.Status.values());
        } else {
            statuses = List.of(Parcel.Status.RECEIVED, Parcel.Status.NOTIFIED);
        }
        return parcelRepository.findByStudentAndStatuses(student.getId(), statuses, pageable)
                .map(p -> toResponse(p, true));
    }

    public ParcelResponse getStudentParcelDetail(User studentUser, Long parcelId) {
        Parcel p = parcelRepository.findById(parcelId)
                .orElseThrow(() -> new ResourceNotFoundException("Parcel not found"));
        assertStudentOwnership(p, studentUser);
        return toResponse(p, true);
    }

    public long getStudentWaitingCount(User studentUser) {
        Student student = getStudentByUser(studentUser);
        return parcelRepository.countByStudentIdAndStatusIn(
                student.getId(), List.of(Parcel.Status.RECEIVED, Parcel.Status.NOTIFIED));
    }

    // =====================================================
    // WARDEN: QR SCAN VERIFICATION & DELIVERY CONFIRMATION
    // =====================================================

    public ParcelScanVerifyResponse verifyScan(String tokenOrCode, String expectedHostelBlock) {
        if (tokenOrCode == null || tokenOrCode.isBlank()) {
            throw new BadRequestException("Enter or scan a QR token, Parcel ID, or 6-digit pickup code");
        }
        String trimmed = tokenOrCode.trim();

        // Cryptographic signature check if it's a signed PRC1 token
        if (trimmed.startsWith("PRC1.") && !verifySignedQrTokenFormat(trimmed)) {
            return new ParcelScanVerifyResponse(false, "Invalid or tampered QR token signature.", null);
        }

        Optional<ParcelQrToken> tokenOpt = qrTokenRepository.findByQrToken(trimmed);
        if (tokenOpt.isEmpty()) {
            tokenOpt = qrTokenRepository.findByPickupCode(trimmed);
        }

        Parcel parcel;
        ParcelQrToken qrToken;
        if (tokenOpt.isPresent()) {
            qrToken = tokenOpt.get();
            parcel = qrToken.getParcel();
        } else {
            parcel = parcelRepository.findByParcelCode(trimmed.toUpperCase()).orElse(null);
            if (parcel == null) {
                return new ParcelScanVerifyResponse(false, "Invalid QR code or parcel not found: " + trimmed, null);
            }
            qrToken = qrTokenRepository.findByParcelId(parcel.getId()).orElse(null);
        }

        // Hostel block validation (if specified by warden)
        if (expectedHostelBlock != null && !expectedHostelBlock.isBlank() && !"ALL".equalsIgnoreCase(expectedHostelBlock.trim())) {
            if (!expectedHostelBlock.trim().equalsIgnoreCase(parcel.getHostelBlock())) {
                return new ParcelScanVerifyResponse(false,
                        "Parcel belongs to another hostel/block (" + parcel.getHostelBlock() + ").",
                        toResponse(parcel, false));
            }
        }

        if (parcel.getStatus() == Parcel.Status.DELIVERED) {
            String when = parcel.getDeliveredAt() != null ? parcel.getDeliveredAt().format(TS_FMT) : "earlier";
            return new ParcelScanVerifyResponse(false, "Already delivered on " + when, toResponse(parcel, false));
        }
        if (qrToken != null && qrToken.isUsed()) {
            String when = qrToken.getUsedAt() != null ? qrToken.getUsedAt().format(TS_FMT) : "earlier";
            return new ParcelScanVerifyResponse(false, "QR token already used on " + when, toResponse(parcel, false));
        }
        if (parcel.getStatus() == Parcel.Status.RETURNED_TO_SENDER) {
            return new ParcelScanVerifyResponse(false, "Parcel was returned to sender.", toResponse(parcel, false));
        }
        if (parcel.getStatus() == Parcel.Status.CANCELLED) {
            return new ParcelScanVerifyResponse(false, "Parcel record was cancelled.", toResponse(parcel, false));
        }
        if (parcel.getStatus() == Parcel.Status.UNCLAIMED) {
            return new ParcelScanVerifyResponse(false, "Parcel is marked Unclaimed.", toResponse(parcel, false));
        }
        if (qrToken != null && !qrToken.getStudent().getId().equals(parcel.getStudent().getId())) {
            return new ParcelScanVerifyResponse(false, "QR token does not match parcel owner.", null);
        }

        return new ParcelScanVerifyResponse(true, null, toResponse(parcel, false));
    }

    @Transactional
    public ParcelResponse confirmDelivery(User warden, ConfirmDeliveryRequest req) {
        Long parcelId = req.parcelId();
        if (parcelId == null) {
            if (req.tokenOrCode() == null || req.tokenOrCode().isBlank()) {
                throw new BadRequestException("Parcel ID or QR token/pickup code is required");
            }
            ParcelScanVerifyResponse verified = verifyScan(req.tokenOrCode(), null);
            if (!verified.valid() || verified.parcel() == null) {
                throw new BadRequestException(verified.errorMessage() != null ? verified.errorMessage() : "Invalid parcel token");
            }
            parcelId = verified.parcel().id();
        }

        // Acquire pessimistic DB write lock to eliminate double-scan race condition
        Parcel parcel = parcelRepository.findByIdWithLock(parcelId)
                .orElseThrow(() -> new ResourceNotFoundException("Parcel not found"));

        if (parcel.getStatus() == Parcel.Status.DELIVERED) {
            String when = parcel.getDeliveredAt() != null ? parcel.getDeliveredAt().format(TS_FMT) : "earlier";
            throw new ConflictException("Already delivered on " + when);
        }
        if (parcel.getStatus() != Parcel.Status.RECEIVED && parcel.getStatus() != Parcel.Status.NOTIFIED) {
            throw new BadRequestException("Cannot deliver parcel in status: " + parcel.getStatus());
        }

        ParcelQrToken token = qrTokenRepository.findByParcelId(parcel.getId()).orElse(null);
        if (token != null && token.isUsed()) {
            throw new ConflictException("QR token has already been used for delivery");
        }

        // COD Payment Confirmation Check
        if (parcel.isCod() && parcel.isCodPaymentPending() && !parcel.isCodCollected()) {
            if (req.paymentCollected() == null || !req.paymentCollected()) {
                throw new BadRequestException("COD payment of Rs. " + parcel.getCodAmount() + " must be confirmed as collected before delivery");
            }
            parcel.setCodCollected(true);
            parcel.setCodPaymentPending(false);
        }

        // Optional friend handover check
        boolean hasFriend = req.handoverToName() != null && !req.handoverToName().isBlank();
        if (hasFriend) {
            boolean allowFriend = Boolean.parseBoolean(getSetting("parcel.allow_friend_handover", "true"));
            if (!allowFriend) {
                throw new BadRequestException("Handover to a friend is currently disabled by hostel policy");
            }
            if (req.handoverReason() == null || req.handoverReason().isBlank()) {
                throw new BadRequestException("A documented reason is required when handing over to a friend");
            }
            parcel.setHandoverToName(req.handoverToName().trim());
            parcel.setHandoverReason(req.handoverReason().trim());
        }

        LocalDateTime now = LocalDateTime.now();
        String oldStatus = parcel.getStatus().name();
        parcel.setStatus(Parcel.Status.DELIVERED);
        parcel.setDeliveredAt(now);
        parcel.setDeliveredBy(warden);
        parcel.setDeliveryRemarks(req.remarks() != null && !req.remarks().isBlank() ? req.remarks().trim() : null);

        if (token != null) {
            token.setUsed(true);
            token.setUsedAt(now);
            qrTokenRepository.save(token);
        }

        Parcel saved = parcelRepository.save(parcel);

        String histNote = "Delivered" + (hasFriend ? " to friend (" + saved.getHandoverToName() + "): " + saved.getHandoverReason() : " in person")
                + (saved.getDeliveryRemarks() != null ? " — " + saved.getDeliveryRemarks() : "");
        recordHistory(saved, oldStatus, Parcel.Status.DELIVERED.name(), warden, histNote);

        notificationService.notifyUser(
                saved.getStudent().getUser(),
                "Parcel Delivered: " + saved.getParcelCode(),
                "Your parcel " + saved.getParcelCode() + " from " + saved.getCourierName() + " was delivered.",
                "PARCEL",
                saved.getId()
        );

        auditService.log(warden, "DELIVER_PARCEL", saved.getParcelCode(), "SUCCESS");
        return toResponse(saved, false);
    }

    @Transactional
    public ParcelResponse transitionStatus(User warden, Long parcelId, Parcel.Status targetStatus, String reason) {
        if (reason == null || reason.isBlank()) {
            throw new BadRequestException("Reason is mandatory for this status change");
        }
        Parcel parcel = parcelRepository.findByIdWithLock(parcelId)
                .orElseThrow(() -> new ResourceNotFoundException("Parcel not found"));

        if (parcel.getStatus() != Parcel.Status.RECEIVED && parcel.getStatus() != Parcel.Status.NOTIFIED) {
            throw new BadRequestException("Can only mark Return/Unclaimed/Cancel on waiting parcels. Current status: " + parcel.getStatus());
        }
        if (targetStatus != Parcel.Status.RETURNED_TO_SENDER &&
            targetStatus != Parcel.Status.UNCLAIMED &&
            targetStatus != Parcel.Status.CANCELLED) {
            throw new BadRequestException("Invalid target status: " + targetStatus);
        }

        String oldStatus = parcel.getStatus().name();
        parcel.setStatus(targetStatus);
        parcel.setReturnOrUnclaimedReason(reason.trim());
        Parcel saved = parcelRepository.save(parcel);

        qrTokenRepository.findByParcelId(saved.getId()).ifPresent(t -> {
            t.setUsed(true);
            t.setUsedAt(LocalDateTime.now());
            qrTokenRepository.save(t);
        });

        recordHistory(saved, oldStatus, targetStatus.name(), warden, reason.trim());

        notificationService.notifyUser(
                saved.getStudent().getUser(),
                "Parcel Status Updated: " + saved.getParcelCode(),
                "Your parcel " + saved.getParcelCode() + " was marked " + targetStatus.name().replace('_', ' ') + ": " + reason.trim(),
                "PARCEL",
                saved.getId()
        );

        auditService.log(warden, "PARCEL_" + targetStatus.name(), saved.getParcelCode(), "SUCCESS");
        return toResponse(saved, false);
    }

    // =====================================================
    // WARDEN & ADMIN DASHBOARD, SEARCH, REMINDERS & REPORTS
    // =====================================================

    public WardenParcelSummaryResponse getWardenSummary() {
        LocalDate today = LocalDate.now();
        LocalDateTime start = today.atStartOfDay();
        LocalDateTime end = today.atTime(LocalTime.MAX);
        List<Parcel.Status> waiting = List.of(Parcel.Status.RECEIVED, Parcel.Status.NOTIFIED);

        long receivedToday = parcelRepository.countByReceivedAtBetween(start, end);
        long waitingCount = parcelRepository.countByStatusIn(waiting);
        long deliveredToday = parcelRepository.countByDeliveredAtBetween(start, end);
        int longDays = Integer.parseInt(getSetting("parcel.long_pending_days", "7"));
        long longPending = parcelRepository.findByStatusInAndReceivedAtBefore(waiting, LocalDateTime.now().minusDays(longDays)).size();
        long codPending = parcelRepository.countByStatusInAndCodPaymentPendingTrueAndCodCollectedFalse(waiting);

        return new WardenParcelSummaryResponse(receivedToday, waitingCount, deliveredToday, longPending, codPending);
    }

    public Page<ParcelResponse> searchParcels(String statusStr,
                                              String courier,
                                              LocalDate fromDate,
                                              LocalDate toDate,
                                              String query,
                                              Pageable pageable) {
        Parcel.Status status = (statusStr != null && !statusStr.isBlank() && !"ALL".equalsIgnoreCase(statusStr))
                ? Parcel.Status.valueOf(statusStr.trim().toUpperCase()) : null;
        String c = (courier != null && !courier.isBlank()) ? courier.trim() : null;
        String q = (query != null && !query.isBlank()) ? query.trim() : null;
        LocalDateTime from = fromDate != null ? fromDate.atStartOfDay() : null;
        LocalDateTime to = toDate != null ? toDate.atTime(LocalTime.MAX) : null;

        return parcelRepository.searchFiltered(status, c, from, to, q, pageable)
                .map(p -> toResponse(p, false));
    }

    public List<ParcelResponse> getLongPendingParcels() {
        int longDays = Integer.parseInt(getSetting("parcel.long_pending_days", "7"));
        LocalDateTime cutoff = LocalDateTime.now().minusDays(longDays);
        return parcelRepository.findByStatusInAndReceivedAtBefore(
                List.of(Parcel.Status.RECEIVED, Parcel.Status.NOTIFIED), cutoff)
                .stream()
                .map(p -> toResponse(p, false))
                .collect(Collectors.toList());
    }

    public String exportParcelsCsv(LocalDate fromDate, LocalDate toDate) {
        LocalDateTime start = (fromDate != null ? fromDate : LocalDate.now().minusDays(30)).atStartOfDay();
        LocalDateTime end = (toDate != null ? toDate : LocalDate.now()).atTime(LocalTime.MAX);
        List<Parcel> list = parcelRepository.findByReceivedAtBetweenOrderByReceivedAtDesc(start, end);
        StringBuilder sb = new StringBuilder();
        sb.append("ParcelCode,StudentName,RegisterNumber,Block,Courier,TrackingNo,Type,CODAmount,Status,ReceivedAt,DeliveredAt,Storage\n");
        for (Parcel p : list) {
            sb.append(escapeCsv(p.getParcelCode())).append(',')
              .append(escapeCsv(p.getStudent().getFullName())).append(',')
              .append(escapeCsv(p.getStudent().getRegisterNumber())).append(',')
              .append(escapeCsv(p.getHostelBlock())).append(',')
              .append(escapeCsv(p.getCourierName())).append(',')
              .append(escapeCsv(p.getTrackingNumber())).append(',')
              .append(p.getParcelType().name()).append(',')
              .append(p.isCod() && p.getCodAmount() != null ? p.getCodAmount() : "0").append(',')
              .append(p.getStatus().name()).append(',')
              .append(p.getReceivedAt()).append(',')
              .append(p.getDeliveredAt() != null ? p.getDeliveredAt() : "").append(',')
              .append(escapeCsv(p.getStorageLocation())).append('\n');
        }
        return sb.toString();
    }

    public AdminParcelReportResponse getAdminReport() {
        List<Parcel> all = parcelRepository.findAll();
        long total = all.size();
        long waiting = all.stream().filter(p -> p.getStatus() == Parcel.Status.RECEIVED || p.getStatus() == Parcel.Status.NOTIFIED).count();
        long delivered = all.stream().filter(p -> p.getStatus() == Parcel.Status.DELIVERED).count();
        long unclaimed = all.stream().filter(p -> p.getStatus() == Parcel.Status.UNCLAIMED).count();
        long returned = all.stream().filter(p -> p.getStatus() == Parcel.Status.RETURNED_TO_SENDER).count();

        double totalHours = 0;
        long samples = 0;
        for (Parcel p : all) {
            if (p.getDeliveredAt() != null && p.getReceivedAt() != null && !p.getDeliveredAt().isBefore(p.getReceivedAt())) {
                totalHours += Duration.between(p.getReceivedAt(), p.getDeliveredAt()).toMinutes() / 60.0;
                samples++;
            }
        }
        double avgHours = samples > 0 ? Math.round((totalHours / samples) * 100.0) / 100.0 : 0.0;

        Map<String, Long> unclaimedByBlock = new LinkedHashMap<>();
        all.stream().filter(p -> p.getStatus() == Parcel.Status.UNCLAIMED)
                .forEach(p -> unclaimedByBlock.merge(p.getHostelBlock(), 1L, Long::sum));

        LocalDate today = LocalDate.now();
        List<ParcelDailyCount> daily = new ArrayList<>();
        for (int i = 6; i >= 0; i--) {
            LocalDate d = today.minusDays(i);
            long cnt = all.stream().filter(p -> p.getReceivedAt() != null && d.equals(p.getReceivedAt().toLocalDate())).count();
            daily.add(new ParcelDailyCount(d.toString(), cnt));
        }

        return new AdminParcelReportResponse(total, waiting, delivered, unclaimed, returned, avgHours, unclaimedByBlock, daily);
    }

    // =====================================================
    // SCHEDULED REMINDERS (24h & 3 DAYS, IDEMPOTENT)
    // =====================================================

    @Transactional
    public int sendPendingParcelReminders(LocalDateTime now) {
        int h24 = Integer.parseInt(getSetting("parcel.reminder_24h_hours", "24"));
        int h72 = Integer.parseInt(getSetting("parcel.reminder_3d_hours", "72"));
        List<Parcel> waiting = parcelRepository.findByStatusInAndReceivedAtBefore(
                List.of(Parcel.Status.RECEIVED, Parcel.Status.NOTIFIED), now.minusHours(h24));

        int sent = 0;
        for (Parcel p : waiting) {
            long hoursWaiting = Duration.between(p.getReceivedAt(), now).toHours();
            if (hoursWaiting >= h72 && !p.isReminder3dSent()) {
                notificationService.notifyUser(
                        p.getStudent().getUser(),
                        "Parcel Waiting 3+ Days: " + p.getParcelCode(),
                        "Your parcel from " + p.getCourierName() + " (" + p.getParcelCode()
                                + ") has been waiting for over 3 days. Please collect it soon.",
                        "PARCEL",
                        p.getId()
                );
                p.setReminder3dSent(true);
                p.setReminder24hSent(true);
                parcelRepository.save(p);
                sent++;
            } else if (hoursWaiting >= h24 && !p.isReminder24hSent()) {
                notificationService.notifyUser(
                        p.getStudent().getUser(),
                        "Parcel Pickup Reminder: " + p.getParcelCode(),
                        "Your parcel from " + p.getCourierName() + " (" + p.getParcelCode()
                                + ") arrived 24+ hours ago. Show your QR code at the warden office.",
                        "PARCEL",
                        p.getId()
                );
                p.setReminder24hSent(true);
                parcelRepository.save(p);
                sent++;
            }
        }
        return sent;
    }

    public MaintenanceService.LoadedAttachment loadAttachment(User actor, Long attachmentId) {
        ParcelAttachment att = attachmentRepository.findById(attachmentId)
                .orElseThrow(() -> new ResourceNotFoundException("Parcel attachment not found"));
        Parcel p = att.getParcel();

        if ("STUDENT".equals(actor.getRole())) {
            assertStudentOwnership(p, actor);
        } else if (!"WARDEN".equals(actor.getRole()) && !"ADMIN".equals(actor.getRole())) {
            throw new ForbiddenException("Access denied to parcel attachment");
        }

        try {
            Path path = Paths.get(att.getFilePath()).normalize();
            Resource res = new UrlResource(path.toUri());
            if (!res.exists() || !res.isReadable()) {
                throw new ResourceNotFoundException("Parcel image not found on disk");
            }
            return new MaintenanceService.LoadedAttachment(res, att.getContentType(), att.getOriginalFilename());
        } catch (MalformedURLException e) {
            throw new ResourceNotFoundException("Invalid attachment path");
        }
    }

    // =====================================================
    // HELPERS
    // =====================================================

    private Student getStudentByUser(User user) {
        return studentRepository.findByUserId(user.getId())
                .orElseThrow(() -> new ResourceNotFoundException("Student profile not found"));
    }

    private void assertStudentOwnership(Parcel p, User studentUser) {
        if (!p.getStudent().getUser().getId().equals(studentUser.getId())) {
            throw new ForbiddenException("You can only access your own parcels");
        }
    }

    private void validateImage(MultipartFile f) {
        String ct = f.getContentType();
        if (ct == null || !ALLOWED_IMAGE_TYPES.contains(ct.toLowerCase())) {
            throw new BadRequestException("Only JPG, PNG, and WebP images are allowed for parcel photo");
        }
        if (f.getSize() > MAX_IMAGE_BYTES) {
            throw new BadRequestException("Parcel photo must be 2 MB or smaller");
        }
    }

    private void storeAttachment(Parcel parcel, MultipartFile file, User uploader) {
        try {
            Path dir = Paths.get(uploadDir, "parcels").toAbsolutePath().normalize();
            Files.createDirectories(dir);
            String ct = file.getContentType() != null ? file.getContentType().toLowerCase() : "image/jpeg";
            String ext = ct.contains("png") ? ".png" : (ct.contains("webp") ? ".webp" : ".jpg");
            String filename = parcel.getParcelCode() + "_" + UUID.randomUUID().toString().substring(0, 8) + ext;
            Path target = dir.resolve(filename);
            Files.copy(file.getInputStream(), target, StandardCopyOption.REPLACE_EXISTING);

            ParcelAttachment att = new ParcelAttachment();
            att.setParcel(parcel);
            att.setFilePath(target.toString());
            att.setOriginalFilename(file.getOriginalFilename() != null ? file.getOriginalFilename() : filename);
            att.setContentType(ct);
            att.setFileSize(file.getSize());
            att.setUploadedBy(uploader);
            attachmentRepository.save(att);
        } catch (IOException e) {
            throw new RuntimeException("Failed to store parcel photo: " + e.getMessage());
        }
    }

    private String generateUniqueParcelCode() {
        int year = LocalDate.now().getYear();
        String prefix = "PRC-" + year + "-";
        long seq = parcelRepository.countByParcelCodePrefix(prefix) + 1;
        String code = String.format("%s%04d", prefix, seq);
        while (parcelRepository.findByParcelCode(code).isPresent()) {
            seq++;
            code = String.format("%s%04d", prefix, seq);
        }
        return code;
    }

    private String generateUniquePickupCode() {
        for (int i = 0; i < 20; i++) {
            int num = 100000 + SECURE_RANDOM.nextInt(900000);
            String code = String.valueOf(num);
            if (!qrTokenRepository.existsByPickupCode(code)) {
                return code;
            }
        }
        return String.valueOf(100000 + SECURE_RANDOM.nextInt(900000));
    }

    public String generateSignedQrToken(Long parcelId, Long studentId) {
        byte[] rnd = new byte[12];
        SECURE_RANDOM.nextBytes(rnd);
        String nonce = HexFormat.of().formatHex(rnd);
        String payload = "PRC1." + parcelId + "." + studentId + "." + nonce;
        String sig = computeHmacHex(payload);
        return payload + "." + sig;
    }

    private boolean verifySignedQrTokenFormat(String token) {
        String[] parts = token.split("\\.");
        if (parts.length != 5 || !"PRC1".equals(parts[0])) return false;
        String payload = parts[0] + "." + parts[1] + "." + parts[2] + "." + parts[3];
        String expected = computeHmacHex(payload);
        return MessageDigest.isEqual(expected.getBytes(StandardCharsets.UTF_8), parts[4].getBytes(StandardCharsets.UTF_8));
    }

    private String computeHmacHex(String payload) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            SecretKeySpec key = new SecretKeySpec(hmacSecret.getBytes(StandardCharsets.UTF_8), "HmacSHA256");
            mac.init(key);
            byte[] raw = mac.doFinal(payload.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(raw).substring(0, 24);
        } catch (Exception e) {
            throw new RuntimeException("Failed to sign parcel QR token", e);
        }
    }

    private void recordHistory(Parcel parcel, String oldStatus, String newStatus, User actor, String remarks) {
        ParcelStatusHistory h = new ParcelStatusHistory();
        h.setParcel(parcel);
        h.setOldStatus(oldStatus);
        h.setNewStatus(newStatus);
        h.setChangedBy(actor);
        h.setRemarks(remarks != null && remarks.length() > 500 ? remarks.substring(0, 500) : remarks);
        historyRepository.save(h);
    }

    private ParcelResponse toResponse(Parcel p, boolean includeSecretToken) {
        Student s = p.getStudent();
        String room = bedRepository.findByStudentId(s.getId()).map(b -> b.getRoom().getRoomNumber()).orElse("-");
        String studentPhotoUrl = s.getPhotoPath() != null ? "/api/student/photo/" + s.getId() : null;

        ParcelQrToken token = qrTokenRepository.findByParcelId(p.getId()).orElse(null);
        long daysWaiting = (p.getStatus() == Parcel.Status.RECEIVED || p.getStatus() == Parcel.Status.NOTIFIED)
                ? Math.max(0, ChronoUnit.DAYS.between(p.getReceivedAt().toLocalDate(), LocalDate.now()))
                : 0;
        int longDays = Integer.parseInt(getSetting("parcel.long_pending_days", "7"));
        boolean isLong = (p.getStatus() == Parcel.Status.RECEIVED || p.getStatus() == Parcel.Status.NOTIFIED) && daysWaiting >= longDays;

        List<ParcelAttachmentDto> attachments = attachmentRepository.findByParcelIdOrderByCreatedAtAsc(p.getId()).stream()
                .map(a -> new ParcelAttachmentDto(a.getId(), "/api/parcels/attachments/" + a.getId(), a.getOriginalFilename()))
                .collect(Collectors.toList());

        List<ParcelStatusHistoryDto> history = historyRepository.findByParcelIdOrderByCreatedAtAsc(p.getId()).stream()
                .map(h -> new ParcelStatusHistoryDto(
                        h.getId(),
                        h.getOldStatus(),
                        h.getNewStatus(),
                        resolveActorName(h.getChangedBy()),
                        h.getChangedBy() != null ? h.getChangedBy().getRole() : "SYSTEM",
                        h.getRemarks(),
                        h.getCreatedAt()
                )).collect(Collectors.toList());

        return new ParcelResponse(
                p.getId(),
                p.getParcelCode(),
                s.getId(),
                s.getFullName(),
                s.getRegisterNumber(),
                room,
                s.getPhone(),
                studentPhotoUrl,
                p.getHostelBlock(),
                p.getCourierName(),
                p.getTrackingNumber(),
                p.getParcelType().name(),
                p.getDescription(),
                p.isCod(),
                p.getCodAmount(),
                p.isCodPaymentPending(),
                p.isCodCollected(),
                p.getStorageLocation(),
                p.getStatus().name(),
                p.getReceivedAt(),
                p.getDeliveredAt(),
                p.getDeliveryRemarks(),
                p.getHandoverToName(),
                p.getHandoverReason(),
                p.getReturnOrUnclaimedReason(),
                daysWaiting,
                isLong,
                (includeSecretToken && token != null) ? token.getQrToken() : null,
                (includeSecretToken && token != null) ? token.getPickupCode() : null,
                token != null && token.isUsed(),
                attachments,
                history
        );
    }

    private String resolveActorName(User u) {
        if (u == null) return "System";
        if ("STUDENT".equals(u.getRole())) {
            return studentRepository.findByUserId(u.getId()).map(Student::getFullName).orElse(u.getEmail());
        }
        return staffProfileRepository.findByUserId(u.getId()).map(StaffProfile::getFullName).orElse(u.getEmail());
    }

    private Parcel.ParcelType parseParcelType(String str) {
        if (str == null || str.isBlank()) return Parcel.ParcelType.MEDIUM;
        try {
            return Parcel.ParcelType.valueOf(str.trim().toUpperCase());
        } catch (Exception e) {
            throw new BadRequestException("Invalid parcel type: " + str);
        }
    }

    private String getSetting(String key, String defaultVal) {
        return settingRepository.findById(key).map(SystemSetting::getValue).orElse(defaultVal);
    }

    private String escapeCsv(String val) {
        if (val == null) return "";
        if (val.contains(",") || val.contains("\"") || val.contains("\n")) {
            return "\"" + val.replace("\"", "\"\"") + "\"";
        }
        return val;
    }
}

