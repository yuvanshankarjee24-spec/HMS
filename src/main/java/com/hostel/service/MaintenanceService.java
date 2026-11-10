package com.hostel.service;

import com.hostel.dto.MaintenanceDtos.*;
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

import java.io.IOException;
import java.net.MalformedURLException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.*;
import java.util.stream.Collectors;

@Service
public class MaintenanceService {

    private static final long MAX_IMAGE_BYTES = 2 * 1024 * 1024;
    private static final int MAX_STUDENT_IMAGES = 3;
    private static final Set<String> ALLOWED_IMAGE_TYPES = Set.of(
            "image/jpeg", "image/jpg", "image/png", "image/webp"
    );

    private final MaintenanceRequestRepository requestRepository;
    private final RequestAttachmentRepository attachmentRepository;
    private final RequestStatusHistoryRepository historyRepository;
    private final RequestRatingRepository ratingRepository;
    private final StudentRepository studentRepository;
    private final WorkerProfileRepository workerProfileRepository;
    private final StaffProfileRepository staffProfileRepository;
    private final BedRepository bedRepository;
    private final NotificationService notificationService;
    private final AuditService auditService;

    @Value("${app.upload.dir:./storage/photos}")
    private String uploadDir;

    public MaintenanceService(MaintenanceRequestRepository requestRepository,
                              RequestAttachmentRepository attachmentRepository,
                              RequestStatusHistoryRepository historyRepository,
                              RequestRatingRepository ratingRepository,
                              StudentRepository studentRepository,
                              WorkerProfileRepository workerProfileRepository,
                              StaffProfileRepository staffProfileRepository,
                              BedRepository bedRepository,
                              NotificationService notificationService,
                              AuditService auditService) {
        this.requestRepository = requestRepository;
        this.attachmentRepository = attachmentRepository;
        this.historyRepository = historyRepository;
        this.ratingRepository = ratingRepository;
        this.studentRepository = studentRepository;
        this.workerProfileRepository = workerProfileRepository;
        this.staffProfileRepository = staffProfileRepository;
        this.bedRepository = bedRepository;
        this.notificationService = notificationService;
        this.auditService = auditService;
    }

    // =====================================================
    // STUDENT ACTIONS
    // =====================================================

    public Map<String, String> getStudentDefaultLocation(User studentUser) {
        Student student = getStudentByUser(studentUser);
        Optional<Bed> bedOpt = bedRepository.findByStudentId(student.getId());
        String roomNumber = bedOpt.map(b -> b.getRoom().getRoomNumber()).orElse("");
        String block = bedOpt.map(b -> "Block A (Floor " + b.getRoom().getFloor() + ")").orElse("Main Block");
        return Map.of(
                "hostelBlock", block,
                "roomNumber", roomNumber
        );
    }

    @Transactional
    public MaintenanceDetailResponse createRequest(User studentUser,
                                                   String categoryStr,
                                                   String title,
                                                   String description,
                                                   String hostelBlock,
                                                   String roomNumber,
                                                   String priorityStr,
                                                   String preferredTimeSlot,
                                                   MultipartFile[] files) {
        Student student = getStudentByUser(studentUser);

        if (title == null || title.isBlank() || title.trim().length() > 150) {
            throw new BadRequestException("Title is required and must be at most 150 characters");
        }
        if (description == null || description.isBlank()) {
            throw new BadRequestException("Detailed description is required");
        }
        if (hostelBlock == null || hostelBlock.isBlank() || hostelBlock.trim().length() > 100) {
            throw new BadRequestException("Hostel/Block is required");
        }
        if (roomNumber == null || roomNumber.isBlank() || roomNumber.trim().length() > 50) {
            throw new BadRequestException("Room number is required");
        }

        MaintenanceRequest.Category category = parseCategory(categoryStr);
        MaintenanceRequest.Priority priority = (priorityStr == null || priorityStr.isBlank())
                ? MaintenanceRequest.Priority.MEDIUM
                : parsePriority(priorityStr);

        List<MultipartFile> validFiles = filterAndValidateImages(files, MAX_STUDENT_IMAGES);

        MaintenanceRequest req = new MaintenanceRequest();
        req.setRequestCode(generateUniqueRequestCode());
        req.setStudent(student);
        req.setCategory(category);
        req.setTitle(title.trim());
        req.setDescription(description.trim());
        req.setHostelBlock(hostelBlock.trim());
        req.setRoomNumber(roomNumber.trim());
        req.setPriority(priority);
        req.setPreferredTimeSlot(preferredTimeSlot != null && !preferredTimeSlot.isBlank() ? preferredTimeSlot.trim() : null);
        req.setStatus(MaintenanceRequest.Status.PENDING);

        MaintenanceRequest saved = requestRepository.save(req);

        for (MultipartFile file : validFiles) {
            storeAttachment(saved, file, RequestAttachment.AttachmentType.STUDENT_INITIAL, studentUser);
        }

        recordHistory(saved, null, MaintenanceRequest.Status.PENDING.name(), studentUser, "Request raised by student");

        notificationService.notifyRole(
                "WARDEN",
                "New Maintenance Request: " + saved.getRequestCode(),
                student.getFullName() + " (" + saved.getHostelBlock() + ", Room " + saved.getRoomNumber() + ") raised a "
                        + saved.getPriority() + " request: " + saved.getTitle(),
                "MAINTENANCE_REQUEST",
                saved.getId()
        );

        auditService.log(studentUser, "CREATE_MAINTENANCE_REQUEST", saved.getRequestCode(), "SUCCESS");
        return toDetailResponse(saved);
    }

    public Page<MaintenanceSummaryResponse> getStudentRequests(User studentUser,
                                                               String statusStr,
                                                               String categoryStr,
                                                               LocalDate fromDate,
                                                               LocalDate toDate,
                                                               Pageable pageable) {
        Student student = getStudentByUser(studentUser);
        MaintenanceRequest.Status status = parseOptionalStatus(statusStr);
        MaintenanceRequest.Category category = parseOptionalCategory(categoryStr);
        LocalDateTime from = (fromDate != null) ? fromDate.atStartOfDay() : null;
        LocalDateTime to = (toDate != null) ? toDate.atTime(LocalTime.MAX) : null;

        return requestRepository.findByStudentFiltered(student.getId(), status, category, from, to, pageable)
                .map(this::toSummaryResponse);
    }

    public MaintenanceDetailResponse getStudentRequestDetail(User studentUser, Long requestId) {
        MaintenanceRequest req = getRequestOrThrow(requestId);
        assertStudentOwnership(req, studentUser);
        return toDetailResponse(req);
    }

    @Transactional
    public MaintenanceDetailResponse cancelRequest(User studentUser, Long requestId) {
        MaintenanceRequest req = getRequestOrThrow(requestId);
        assertStudentOwnership(req, studentUser);

        if (req.getStatus() != MaintenanceRequest.Status.PENDING) {
            throw new BadRequestException("Only PENDING requests can be cancelled by the student. Current status: " + req.getStatus());
        }

        String oldStatus = req.getStatus().name();
        req.setStatus(MaintenanceRequest.Status.CANCELLED);
        MaintenanceRequest saved = requestRepository.save(req);

        recordHistory(saved, oldStatus, MaintenanceRequest.Status.CANCELLED.name(), studentUser, "Cancelled by student");
        auditService.log(studentUser, "CANCEL_MAINTENANCE_REQUEST", saved.getRequestCode(), "SUCCESS");
        return toDetailResponse(saved);
    }

    @Transactional
    public MaintenanceDetailResponse confirmResolved(User studentUser, Long requestId, ConfirmResolvedRequest dto) {
        MaintenanceRequest req = getRequestOrThrow(requestId);
        assertStudentOwnership(req, studentUser);

        if (req.getStatus() != MaintenanceRequest.Status.COMPLETED) {
            throw new BadRequestException("Only COMPLETED requests can be confirmed as resolved. Current status: " + req.getStatus());
        }

        String oldStatus = req.getStatus().name();
        req.setStatus(MaintenanceRequest.Status.RESOLVED);
        req.setResolvedAt(LocalDateTime.now());
        MaintenanceRequest saved = requestRepository.save(req);

        if (!ratingRepository.existsByRequestId(saved.getId())) {
            RequestRating rating = new RequestRating();
            rating.setRequest(saved);
            rating.setStudent(req.getStudent());
            rating.setWorker(req.getAssignedWorker());
            rating.setRating(dto.rating());
            rating.setFeedback(dto.feedback() != null && !dto.feedback().isBlank() ? dto.feedback().trim() : null);
            ratingRepository.save(rating);
        }

        String note = "Confirmed resolved by student (" + dto.rating() + "/5 stars)"
                + (dto.feedback() != null && !dto.feedback().isBlank() ? ": " + dto.feedback().trim() : "");
        recordHistory(saved, oldStatus, MaintenanceRequest.Status.RESOLVED.name(), studentUser, note);

        if (saved.getAssignedWorker() != null) {
            notificationService.notifyUser(
                    saved.getAssignedWorker().getUser(),
                    "Work Confirmed & Rated: " + saved.getRequestCode(),
                    "Student confirmed resolution with " + dto.rating() + "/5 stars.",
                    "MAINTENANCE_REQUEST",
                    saved.getId()
            );
        }

        auditService.log(studentUser, "RESOLVE_MAINTENANCE_REQUEST", saved.getRequestCode() + " Rating=" + dto.rating(), "SUCCESS");
        return toDetailResponse(saved);
    }

    @Transactional
    public MaintenanceDetailResponse reopenByStudent(User studentUser, Long requestId, ReopenMaintenanceRequest dto) {
        MaintenanceRequest req = getRequestOrThrow(requestId);
        assertStudentOwnership(req, studentUser);

        if (req.getStatus() != MaintenanceRequest.Status.COMPLETED) {
            throw new BadRequestException("Only COMPLETED requests can be reopened by the student. Current status: " + req.getStatus());
        }

        String oldStatus = req.getStatus().name();
        req.setStatus(MaintenanceRequest.Status.REOPENED);
        req.setReopenReason(dto.reason().trim());
        MaintenanceRequest saved = requestRepository.save(req);

        recordHistory(saved, oldStatus, MaintenanceRequest.Status.REOPENED.name(), studentUser, "Reopened by student: " + dto.reason().trim());

        String notifMsg = req.getStudent().getFullName() + " reopened request " + saved.getRequestCode() + ": " + dto.reason().trim();
        notificationService.notifyRole("WARDEN", "Request Reopened: " + saved.getRequestCode(), notifMsg, "MAINTENANCE_REQUEST", saved.getId());
        if (saved.getAssignedWorker() != null) {
            notificationService.notifyUser(saved.getAssignedWorker().getUser(), "Task Reopened: " + saved.getRequestCode(), notifMsg, "MAINTENANCE_REQUEST", saved.getId());
        }

        auditService.log(studentUser, "REOPEN_MAINTENANCE_REQUEST", saved.getRequestCode(), "SUCCESS");
        return toDetailResponse(saved);
    }

    // =====================================================
    // WARDEN & ADMIN ACTIONS
    // =====================================================

    public WardenStatsResponse getWardenStats() {
        long pending = requestRepository.countByStatus(MaintenanceRequest.Status.PENDING);
        long assigned = requestRepository.countByStatus(MaintenanceRequest.Status.ASSIGNED);
        long inProgress = requestRepository.countByStatus(MaintenanceRequest.Status.IN_PROGRESS);
        long completed = requestRepository.countByStatus(MaintenanceRequest.Status.COMPLETED);
        long reopened = requestRepository.countByStatus(MaintenanceRequest.Status.REOPENED);
        long onHold = requestRepository.countByStatus(MaintenanceRequest.Status.ON_HOLD);
        long resolved = requestRepository.countByStatus(MaintenanceRequest.Status.RESOLVED);
        long total = requestRepository.count();
        long activeWorkers = workerProfileRepository.countByActiveTrue();
        return new WardenStatsResponse(pending, assigned, inProgress, completed, reopened, onHold, resolved, total, activeWorkers);
    }

    public Page<MaintenanceSummaryResponse> searchAllRequests(String statusStr,
                                                              String categoryStr,
                                                              String priorityStr,
                                                              String block,
                                                              String room,
                                                              LocalDate fromDate,
                                                              LocalDate toDate,
                                                              String query,
                                                              Pageable pageable) {
        MaintenanceRequest.Status status = parseOptionalStatus(statusStr);
        MaintenanceRequest.Category category = parseOptionalCategory(categoryStr);
        MaintenanceRequest.Priority priority = parseOptionalPriority(priorityStr);
        String blk = (block != null && !block.isBlank()) ? block.trim() : null;
        String rm = (room != null && !room.isBlank()) ? room.trim() : null;
        String q = (query != null && !query.isBlank()) ? query.trim() : null;
        LocalDateTime from = (fromDate != null) ? fromDate.atStartOfDay() : null;
        LocalDateTime to = (toDate != null) ? toDate.atTime(LocalTime.MAX) : null;

        return requestRepository.searchAllFiltered(status, category, priority, blk, rm, from, to, q, pageable)
                .map(this::toSummaryResponse);
    }

    public MaintenanceDetailResponse getRequestDetailForStaff(Long requestId) {
        return toDetailResponse(getRequestOrThrow(requestId));
    }

    @Transactional
    public MaintenanceDetailResponse assignWorker(User actor, Long requestId, AssignWorkerRequest dto) {
        MaintenanceRequest req = getRequestOrThrow(requestId);

        if (req.getStatus() != MaintenanceRequest.Status.PENDING &&
            req.getStatus() != MaintenanceRequest.Status.ASSIGNED &&
            req.getStatus() != MaintenanceRequest.Status.ON_HOLD &&
            req.getStatus() != MaintenanceRequest.Status.REOPENED) {
            throw new BadRequestException("Cannot assign worker when request is in status: " + req.getStatus());
        }

        WorkerProfile worker = workerProfileRepository.findById(dto.workerId())
                .orElseThrow(() -> new ResourceNotFoundException("Worker not found"));
        if (!worker.isActive() || worker.getUser().getStatus() != User.Status.ACTIVE) {
            throw new BadRequestException("Selected worker is inactive and cannot be assigned tasks");
        }

        boolean isReassign = req.getAssignedWorker() != null && !req.getAssignedWorker().getId().equals(worker.getId());
        String oldStatus = req.getStatus().name();

        req.setAssignedWorker(worker);
        req.setWardenInstruction(dto.instruction() != null && !dto.instruction().isBlank() ? dto.instruction().trim() : null);
        req.setExpectedCompletionDate(dto.expectedCompletionDate());
        req.setOnHoldReason(null);
        req.setStatus(MaintenanceRequest.Status.ASSIGNED);
        MaintenanceRequest saved = requestRepository.save(req);

        String remarks = (isReassign ? "Reassigned" : "Assigned") + " to " + worker.getFullName() + " (" + worker.getWorkerType() + ")"
                + (saved.getWardenInstruction() != null ? " - Note: " + saved.getWardenInstruction() : "");
        recordHistory(saved, oldStatus, MaintenanceRequest.Status.ASSIGNED.name(), actor, remarks);

        notificationService.notifyUser(
                worker.getUser(),
                (isReassign ? "Reassigned Task: " : "New Task Assigned: ") + saved.getRequestCode(),
                "Room " + saved.getRoomNumber() + " (" + saved.getHostelBlock() + ") - " + saved.getTitle()
                        + (saved.getWardenInstruction() != null ? ". Note: " + saved.getWardenInstruction() : ""),
                "MAINTENANCE_REQUEST",
                saved.getId()
        );
        notificationService.notifyUser(
                saved.getStudent().getUser(),
                "Worker Assigned: " + saved.getRequestCode(),
                "Your request has been assigned to " + worker.getFullName() + " (" + worker.getWorkerType() + ").",
                "MAINTENANCE_REQUEST",
                saved.getId()
        );

        auditService.log(actor, "ASSIGN_MAINTENANCE_WORKER", saved.getRequestCode() + " -> " + worker.getEmployeeId(), "SUCCESS");
        return toDetailResponse(saved);
    }

    @Transactional
    public MaintenanceDetailResponse rejectRequest(User actor, Long requestId, RejectMaintenanceRequest dto) {
        MaintenanceRequest req = getRequestOrThrow(requestId);
        if (req.getStatus() != MaintenanceRequest.Status.PENDING &&
            req.getStatus() != MaintenanceRequest.Status.REOPENED &&
            req.getStatus() != MaintenanceRequest.Status.ON_HOLD) {
            throw new BadRequestException("Cannot reject request from status: " + req.getStatus());
        }

        String oldStatus = req.getStatus().name();
        req.setStatus(MaintenanceRequest.Status.REJECTED);
        req.setRejectionReason(dto.reason().trim());
        MaintenanceRequest saved = requestRepository.save(req);

        recordHistory(saved, oldStatus, MaintenanceRequest.Status.REJECTED.name(), actor, "Rejected: " + dto.reason().trim());

        notificationService.notifyUser(
                saved.getStudent().getUser(),
                "Request Rejected: " + saved.getRequestCode(),
                "Your maintenance request was rejected. Reason: " + dto.reason().trim(),
                "MAINTENANCE_REQUEST",
                saved.getId()
        );

        auditService.log(actor, "REJECT_MAINTENANCE_REQUEST", saved.getRequestCode(), "SUCCESS");
        return toDetailResponse(saved);
    }

    @Transactional
    public MaintenanceDetailResponse changePriority(User actor, Long requestId, ChangePriorityRequest dto) {
        MaintenanceRequest req = getRequestOrThrow(requestId);
        MaintenanceRequest.Priority oldPriority = req.getPriority();
        MaintenanceRequest.Priority newPriority = parsePriority(dto.priority());

        req.setPriority(newPriority);
        MaintenanceRequest saved = requestRepository.save(req);

        String note = "Priority changed from " + oldPriority + " to " + newPriority
                + (dto.remarks() != null && !dto.remarks().isBlank() ? ": " + dto.remarks().trim() : "");
        recordHistory(saved, saved.getStatus().name(), saved.getStatus().name(), actor, note);
        return toDetailResponse(saved);
    }

    @Transactional
    public MaintenanceDetailResponse closeByWarden(User actor, Long requestId, CloseMaintenanceRequest dto) {
        MaintenanceRequest req = getRequestOrThrow(requestId);
        if (req.getStatus() == MaintenanceRequest.Status.CANCELLED ||
            req.getStatus() == MaintenanceRequest.Status.REJECTED ||
            req.getStatus() == MaintenanceRequest.Status.RESOLVED) {
            throw new BadRequestException("Request is already finalized in status: " + req.getStatus());
        }

        String oldStatus = req.getStatus().name();
        req.setStatus(MaintenanceRequest.Status.RESOLVED);
        if (req.getCompletedAt() == null) {
            req.setCompletedAt(LocalDateTime.now());
        }
        req.setResolvedAt(LocalDateTime.now());
        MaintenanceRequest saved = requestRepository.save(req);

        String note = "Closed by Warden/Admin" + (dto != null && dto.remarks() != null && !dto.remarks().isBlank() ? ": " + dto.remarks().trim() : "");
        recordHistory(saved, oldStatus, MaintenanceRequest.Status.RESOLVED.name(), actor, note);

        notificationService.notifyUser(
                saved.getStudent().getUser(),
                "Request Closed: " + saved.getRequestCode(),
                "Your maintenance request " + saved.getRequestCode() + " has been closed as Resolved.",
                "MAINTENANCE_REQUEST",
                saved.getId()
        );

        return toDetailResponse(saved);
    }

    @Transactional
    public MaintenanceDetailResponse reopenByWarden(User actor, Long requestId, ReopenMaintenanceRequest dto) {
        MaintenanceRequest req = getRequestOrThrow(requestId);
        if (req.getStatus() != MaintenanceRequest.Status.COMPLETED &&
            req.getStatus() != MaintenanceRequest.Status.RESOLVED &&
            req.getStatus() != MaintenanceRequest.Status.REJECTED) {
            throw new BadRequestException("Can only reopen COMPLETED, RESOLVED, or REJECTED requests. Current status: " + req.getStatus());
        }

        String oldStatus = req.getStatus().name();
        req.setStatus(MaintenanceRequest.Status.REOPENED);
        req.setReopenReason(dto.reason().trim());
        req.setResolvedAt(null);
        MaintenanceRequest saved = requestRepository.save(req);

        recordHistory(saved, oldStatus, MaintenanceRequest.Status.REOPENED.name(), actor, "Reopened by Warden/Admin: " + dto.reason().trim());

        notificationService.notifyUser(
                saved.getStudent().getUser(),
                "Request Reopened: " + saved.getRequestCode(),
                "Warden reopened request " + saved.getRequestCode() + ": " + dto.reason().trim(),
                "MAINTENANCE_REQUEST",
                saved.getId()
        );
        if (saved.getAssignedWorker() != null) {
            notificationService.notifyUser(
                    saved.getAssignedWorker().getUser(),
                    "Task Reopened: " + saved.getRequestCode(),
                    "Warden reopened task " + saved.getRequestCode() + ": " + dto.reason().trim(),
                    "MAINTENANCE_REQUEST",
                    saved.getId()
            );
        }

        return toDetailResponse(saved);
    }

    // =====================================================
    // WORKER ACTIONS
    // =====================================================

    public WorkerStatsResponse getWorkerStats(User workerUser) {
        WorkerProfile worker = getWorkerByUser(workerUser);
        long assigned = requestRepository.countByAssignedWorkerIdAndStatus(worker.getId(), MaintenanceRequest.Status.ASSIGNED);
        long inProgress = requestRepository.countByAssignedWorkerIdAndStatus(worker.getId(), MaintenanceRequest.Status.IN_PROGRESS);
        long onHold = requestRepository.countByAssignedWorkerIdAndStatus(worker.getId(), MaintenanceRequest.Status.ON_HOLD);
        LocalDate today = LocalDate.now();
        long completedToday = requestRepository.countByAssignedWorkerIdAndCompletedAtBetween(
                worker.getId(), today.atStartOfDay(), today.atTime(LocalTime.MAX));
        long totalCompleted = requestRepository.countByAssignedWorkerIdAndStatus(worker.getId(), MaintenanceRequest.Status.COMPLETED)
                + requestRepository.countByAssignedWorkerIdAndStatus(worker.getId(), MaintenanceRequest.Status.RESOLVED);
        Double avg = ratingRepository.findAverageRatingByWorkerId(worker.getId());
        Double roundedAvg = avg != null ? Math.round(avg * 100.0) / 100.0 : null;
        return new WorkerStatsResponse(assigned, inProgress, completedToday, onHold, totalCompleted, roundedAvg);
    }

    public Page<MaintenanceSummaryResponse> getWorkerRequests(User workerUser, String statusStr, Pageable pageable) {
        WorkerProfile worker = getWorkerByUser(workerUser);
        MaintenanceRequest.Status status = parseOptionalStatus(statusStr);
        return requestRepository.findByAssignedWorkerFiltered(worker.getId(), status, pageable)
                .map(this::toSummaryResponse);
    }

    public MaintenanceDetailResponse getWorkerRequestDetail(User workerUser, Long requestId) {
        MaintenanceRequest req = getRequestOrThrow(requestId);
        assertWorkerAssignment(req, workerUser);
        return toDetailResponse(req);
    }

    @Transactional
    public MaintenanceDetailResponse startWork(User workerUser, Long requestId) {
        MaintenanceRequest req = getRequestOrThrow(requestId);
        assertWorkerAssignment(req, workerUser);

        if (req.getStatus() != MaintenanceRequest.Status.ASSIGNED &&
            req.getStatus() != MaintenanceRequest.Status.REOPENED &&
            req.getStatus() != MaintenanceRequest.Status.ON_HOLD) {
            throw new BadRequestException("Cannot start work from status: " + req.getStatus());
        }

        String oldStatus = req.getStatus().name();
        req.setStatus(MaintenanceRequest.Status.IN_PROGRESS);
        req.setOnHoldReason(null);
        MaintenanceRequest saved = requestRepository.save(req);

        recordHistory(saved, oldStatus, MaintenanceRequest.Status.IN_PROGRESS.name(), workerUser, "Work started by " + req.getAssignedWorker().getFullName());

        notificationService.notifyUser(
                saved.getStudent().getUser(),
                "Work In Progress: " + saved.getRequestCode(),
                req.getAssignedWorker().getFullName() + " has started working on your request.",
                "MAINTENANCE_REQUEST",
                saved.getId()
        );
        notificationService.notifyRole(
                "WARDEN",
                "Work Started: " + saved.getRequestCode(),
                req.getAssignedWorker().getFullName() + " started " + saved.getRequestCode() + " (Room " + saved.getRoomNumber() + ").",
                "MAINTENANCE_REQUEST",
                saved.getId()
        );

        auditService.log(workerUser, "START_MAINTENANCE_WORK", saved.getRequestCode(), "SUCCESS");
        return toDetailResponse(saved);
    }

    @Transactional
    public MaintenanceDetailResponse markNeedMaterials(User workerUser, Long requestId, NeedMaterialsRequest dto) {
        MaintenanceRequest req = getRequestOrThrow(requestId);
        assertWorkerAssignment(req, workerUser);

        if (req.getStatus() != MaintenanceRequest.Status.ASSIGNED &&
            req.getStatus() != MaintenanceRequest.Status.IN_PROGRESS &&
            req.getStatus() != MaintenanceRequest.Status.REOPENED) {
            throw new BadRequestException("Cannot mark On Hold / Need Materials from status: " + req.getStatus());
        }

        String oldStatus = req.getStatus().name();
        req.setStatus(MaintenanceRequest.Status.ON_HOLD);
        req.setOnHoldReason(dto.reason().trim());
        MaintenanceRequest saved = requestRepository.save(req);

        recordHistory(saved, oldStatus, MaintenanceRequest.Status.ON_HOLD.name(), workerUser, "Unable to complete / Need materials: " + dto.reason().trim());

        notificationService.notifyRole(
                "WARDEN",
                "Materials Needed / On Hold: " + saved.getRequestCode(),
                req.getAssignedWorker().getFullName() + " marked " + saved.getRequestCode() + " On Hold: " + dto.reason().trim(),
                "MAINTENANCE_REQUEST",
                saved.getId()
        );

        auditService.log(workerUser, "ON_HOLD_MAINTENANCE_WORK", saved.getRequestCode(), "SUCCESS");
        return toDetailResponse(saved);
    }

    @Transactional
    public MaintenanceDetailResponse completeWork(User workerUser, Long requestId, String remarks, MultipartFile afterPhoto) {
        MaintenanceRequest req = getRequestOrThrow(requestId);
        assertWorkerAssignment(req, workerUser);

        if (req.getStatus() != MaintenanceRequest.Status.IN_PROGRESS) {
            throw new BadRequestException("Work must be IN_PROGRESS before marking COMPLETED. Current status: " + req.getStatus());
        }

        List<MultipartFile> validFiles = (afterPhoto != null && !afterPhoto.isEmpty())
                ? filterAndValidateImages(new MultipartFile[]{afterPhoto}, 1)
                : List.of();

        String oldStatus = req.getStatus().name();
        req.setStatus(MaintenanceRequest.Status.COMPLETED);
        req.setCompletedAt(LocalDateTime.now());
        req.setWorkerRemarks(remarks != null && !remarks.isBlank() ? remarks.trim() : null);
        MaintenanceRequest saved = requestRepository.save(req);

        for (MultipartFile file : validFiles) {
            storeAttachment(saved, file, RequestAttachment.AttachmentType.WORKER_AFTER, workerUser);
        }

        String note = "Work completed by " + req.getAssignedWorker().getFullName()
                + (saved.getWorkerRemarks() != null ? " - Remarks: " + saved.getWorkerRemarks() : "");
        recordHistory(saved, oldStatus, MaintenanceRequest.Status.COMPLETED.name(), workerUser, note);

        notificationService.notifyUser(
                saved.getStudent().getUser(),
                "Work Completed: " + saved.getRequestCode(),
                req.getAssignedWorker().getFullName() + " marked your request as Completed. Please review and confirm.",
                "MAINTENANCE_REQUEST",
                saved.getId()
        );
        notificationService.notifyRole(
                "WARDEN",
                "Work Completed: " + saved.getRequestCode(),
                req.getAssignedWorker().getFullName() + " completed request " + saved.getRequestCode() + ".",
                "MAINTENANCE_REQUEST",
                saved.getId()
        );

        auditService.log(workerUser, "COMPLETE_MAINTENANCE_WORK", saved.getRequestCode(), "SUCCESS");
        return toDetailResponse(saved);
    }

    // =====================================================
    // ADMIN REPORTS & ATTACHMENTS
    // =====================================================

    public AdminReportResponse getAdminReport() {
        List<MaintenanceRequest> all = requestRepository.findAll();
        long total = all.size();
        Set<MaintenanceRequest.Status> openStatuses = Set.of(
                MaintenanceRequest.Status.PENDING,
                MaintenanceRequest.Status.ASSIGNED,
                MaintenanceRequest.Status.IN_PROGRESS,
                MaintenanceRequest.Status.ON_HOLD,
                MaintenanceRequest.Status.REOPENED
        );
        long open = all.stream().filter(r -> openStatuses.contains(r.getStatus())).count();
        long resolved = all.stream().filter(r -> r.getStatus() == MaintenanceRequest.Status.RESOLVED || r.getStatus() == MaintenanceRequest.Status.COMPLETED).count();

        double totalHours = 0;
        long resolutionSamples = 0;
        for (MaintenanceRequest r : all) {
            LocalDateTime end = r.getResolvedAt() != null ? r.getResolvedAt() : r.getCompletedAt();
            if (end != null && r.getCreatedAt() != null && !end.isBefore(r.getCreatedAt())) {
                totalHours += Duration.between(r.getCreatedAt(), end).toMinutes() / 60.0;
                resolutionSamples++;
            }
        }
        double avgHours = resolutionSamples > 0 ? Math.round((totalHours / resolutionSamples) * 100.0) / 100.0 : 0.0;

        Map<String, Long> categoryCounts = new LinkedHashMap<>();
        for (MaintenanceRequest.Category c : MaintenanceRequest.Category.values()) {
            categoryCounts.put(c.name(), 0L);
        }
        for (MaintenanceRequest r : all) {
            categoryCounts.merge(r.getCategory().name(), 1L, Long::sum);
        }

        Map<String, Long> statusCounts = new LinkedHashMap<>();
        for (MaintenanceRequest.Status s : MaintenanceRequest.Status.values()) {
            statusCounts.put(s.name(), 0L);
        }
        for (MaintenanceRequest r : all) {
            statusCounts.merge(r.getStatus().name(), 1L, Long::sum);
        }

        LocalDateTime cutoff = LocalDateTime.now().minusHours(48);
        List<MaintenanceRequest> pending48 = requestRepository.findByStatusInAndCreatedAtBeforeOrderByCreatedAtAsc(
                List.of(MaintenanceRequest.Status.PENDING, MaintenanceRequest.Status.ASSIGNED,
                        MaintenanceRequest.Status.IN_PROGRESS, MaintenanceRequest.Status.ON_HOLD,
                        MaintenanceRequest.Status.REOPENED),
                cutoff);
        List<MaintenanceSummaryResponse> pending48Summaries = pending48.stream()
                .map(this::toSummaryResponse)
                .collect(Collectors.toList());

        List<WorkerProfile> workers = workerProfileRepository.findAllByOrderByCreatedAtDesc();
        List<RequestRating> allRatings = ratingRepository.findAll();
        List<WorkerPerformanceItem> perfList = workers.stream().map(w -> {
            long completed = all.stream()
                    .filter(r -> r.getAssignedWorker() != null && r.getAssignedWorker().getId().equals(w.getId()))
                    .filter(r -> r.getStatus() == MaintenanceRequest.Status.COMPLETED || r.getStatus() == MaintenanceRequest.Status.RESOLVED)
                    .count();
            long active = all.stream()
                    .filter(r -> r.getAssignedWorker() != null && r.getAssignedWorker().getId().equals(w.getId()))
                    .filter(r -> r.getStatus() == MaintenanceRequest.Status.ASSIGNED || r.getStatus() == MaintenanceRequest.Status.IN_PROGRESS || r.getStatus() == MaintenanceRequest.Status.ON_HOLD || r.getStatus() == MaintenanceRequest.Status.REOPENED)
                    .count();
            List<RequestRating> workerRatings = allRatings.stream()
                    .filter(rt -> rt.getWorker() != null && rt.getWorker().getId().equals(w.getId()))
                    .toList();
            Double avg = workerRatings.isEmpty() ? null :
                    Math.round(workerRatings.stream().mapToInt(RequestRating::getRating).average().orElse(0.0) * 100.0) / 100.0;
            return new WorkerPerformanceItem(
                    w.getId(), w.getEmployeeId(), w.getFullName(), w.getWorkerType().name(),
                    w.isActive(), completed, active, avg, workerRatings.size()
            );
        }).collect(Collectors.toList());

        return new AdminReportResponse(
                total, open, resolved, avgHours, pending48Summaries.size(),
                categoryCounts, statusCounts, perfList, pending48Summaries
        );
    }

    public LoadedAttachment loadAttachment(User actor, Long attachmentId) {
        RequestAttachment attachment = attachmentRepository.findById(attachmentId)
                .orElseThrow(() -> new ResourceNotFoundException("Attachment not found"));
        MaintenanceRequest req = attachment.getRequest();

        String role = actor.getRole();
        if ("STUDENT".equals(role)) {
            assertStudentOwnership(req, actor);
        } else if ("WORKER".equals(role)) {
            assertWorkerAssignment(req, actor);
        } else if (!"WARDEN".equals(role) && !"ADMIN".equals(role)) {
            throw new ForbiddenException("Access denied to maintenance attachment");
        }

        try {
            Path path = Paths.get(attachment.getFilePath()).normalize();
            Resource resource = new UrlResource(path.toUri());
            if (!resource.exists() || !resource.isReadable()) {
                throw new ResourceNotFoundException("Attachment file not found on disk");
            }
            return new LoadedAttachment(resource, attachment.getContentType(), attachment.getOriginalFilename());
        } catch (MalformedURLException e) {
            throw new ResourceNotFoundException("Invalid attachment path");
        }
    }

    public record LoadedAttachment(Resource resource, String contentType, String originalFilename) {}

    // =====================================================
    // HELPERS
    // =====================================================

    private Student getStudentByUser(User user) {
        return studentRepository.findByUserId(user.getId())
                .orElseThrow(() -> new ResourceNotFoundException("Student profile not found"));
    }

    private WorkerProfile getWorkerByUser(User user) {
        return workerProfileRepository.findByUserId(user.getId())
                .orElseThrow(() -> new ResourceNotFoundException("Worker profile not found"));
    }

    private MaintenanceRequest getRequestOrThrow(Long id) {
        return requestRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Maintenance request not found: " + id));
    }

    private void assertStudentOwnership(MaintenanceRequest req, User studentUser) {
        if (!req.getStudent().getUser().getId().equals(studentUser.getId())) {
            throw new ForbiddenException("You do not have permission to access or modify another student's request");
        }
    }

    private void assertWorkerAssignment(MaintenanceRequest req, User workerUser) {
        if (req.getAssignedWorker() == null || !req.getAssignedWorker().getUser().getId().equals(workerUser.getId())) {
            throw new ForbiddenException("You can only view or update maintenance requests assigned to you");
        }
    }

    private List<MultipartFile> filterAndValidateImages(MultipartFile[] files, int maxFiles) {
        if (files == null || files.length == 0) return List.of();
        List<MultipartFile> valid = new ArrayList<>();
        for (MultipartFile f : files) {
            if (f == null || f.isEmpty()) continue;
            valid.add(f);
        }
        if (valid.size() > maxFiles) {
            throw new BadRequestException("Maximum " + maxFiles + " image(s) allowed");
        }
        for (MultipartFile f : valid) {
            String contentType = f.getContentType();
            if (contentType == null || !ALLOWED_IMAGE_TYPES.contains(contentType.toLowerCase())) {
                throw new BadRequestException("Only JPG, PNG, and WebP images are allowed");
            }
            if (f.getSize() > MAX_IMAGE_BYTES) {
                throw new BadRequestException("Each image must be 2 MB or smaller");
            }
        }
        return valid;
    }

    private void storeAttachment(MaintenanceRequest req,
                                 MultipartFile file,
                                 RequestAttachment.AttachmentType type,
                                 User uploader) {
        try {
            Path dir = Paths.get(uploadDir, "maintenance").toAbsolutePath().normalize();
            Files.createDirectories(dir);

            String ct = file.getContentType() != null ? file.getContentType().toLowerCase() : "image/jpeg";
            String ext = ct.contains("png") ? ".png" : (ct.contains("webp") ? ".webp" : ".jpg");
            String filename = req.getRequestCode() + "_" + type.name() + "_" + UUID.randomUUID().toString().substring(0, 8) + ext;
            Path target = dir.resolve(filename);
            Files.copy(file.getInputStream(), target, StandardCopyOption.REPLACE_EXISTING);

            RequestAttachment attachment = new RequestAttachment();
            attachment.setRequest(req);
            attachment.setFilePath(target.toString());
            attachment.setOriginalFilename(file.getOriginalFilename() != null ? file.getOriginalFilename() : filename);
            attachment.setContentType(ct);
            attachment.setFileSize(file.getSize());
            attachment.setAttachmentType(type);
            attachment.setUploadedBy(uploader);
            attachmentRepository.save(attachment);
        } catch (IOException e) {
            throw new RuntimeException("Failed to store attachment: " + e.getMessage());
        }
    }

    private String generateUniqueRequestCode() {
        int year = LocalDate.now().getYear();
        String prefix = "REQ-" + year + "-";
        long seq = requestRepository.countByRequestCodePrefix(prefix) + 1;
        String code = String.format("%s%04d", prefix, seq);
        while (requestRepository.findByRequestCode(code).isPresent()) {
            seq++;
            code = String.format("%s%04d", prefix, seq);
        }
        return code;
    }

    private void recordHistory(MaintenanceRequest req, String oldStatus, String newStatus, User actor, String remarks) {
        RequestStatusHistory h = new RequestStatusHistory();
        h.setRequest(req);
        h.setOldStatus(oldStatus);
        h.setNewStatus(newStatus);
        h.setChangedBy(actor);
        h.setRemarks(remarks != null && remarks.length() > 500 ? remarks.substring(0, 500) : remarks);
        historyRepository.save(h);
    }

    private String resolveActorName(User user) {
        if ("STUDENT".equals(user.getRole())) {
            return studentRepository.findByUserId(user.getId()).map(Student::getFullName).orElse(user.getEmail());
        } else if ("WORKER".equals(user.getRole())) {
            return workerProfileRepository.findByUserId(user.getId()).map(WorkerProfile::getFullName).orElse(user.getEmail());
        }
        return staffProfileRepository.findByUserId(user.getId()).map(StaffProfile::getFullName).orElse(user.getEmail());
    }

    private MaintenanceSummaryResponse toSummaryResponse(MaintenanceRequest r) {
        Optional<RequestRating> ratingOpt = ratingRepository.findByRequestId(r.getId());
        return new MaintenanceSummaryResponse(
                r.getId(),
                r.getRequestCode(),
                r.getStudent().getId(),
                r.getStudent().getFullName(),
                r.getStudent().getRegisterNumber(),
                r.getStudent().getPhone(),
                r.getCategory().name(),
                r.getTitle(),
                r.getHostelBlock(),
                r.getRoomNumber(),
                r.getPriority().name(),
                r.getStatus().name(),
                r.getAssignedWorker() != null ? r.getAssignedWorker().getId() : null,
                r.getAssignedWorker() != null ? r.getAssignedWorker().getFullName() : null,
                r.getAssignedWorker() != null ? r.getAssignedWorker().getWorkerType().name() : null,
                r.getExpectedCompletionDate(),
                r.getCreatedAt(),
                r.getUpdatedAt(),
                r.getCompletedAt(),
                r.getResolvedAt(),
                ratingOpt.map(RequestRating::getRating).orElse(null)
        );
    }

    private MaintenanceDetailResponse toDetailResponse(MaintenanceRequest r) {
        List<AttachmentDto> attachments = attachmentRepository.findByRequestIdOrderByCreatedAtAsc(r.getId()).stream()
                .map(a -> new AttachmentDto(
                        a.getId(),
                        "/api/maintenance/attachments/" + a.getId(),
                        a.getOriginalFilename(),
                        a.getAttachmentType().name(),
                        a.getCreatedAt()
                )).collect(Collectors.toList());

        List<StatusHistoryDto> history = historyRepository.findByRequestIdOrderByCreatedAtAsc(r.getId()).stream()
                .map(h -> new StatusHistoryDto(
                        h.getId(),
                        h.getOldStatus(),
                        h.getNewStatus(),
                        resolveActorName(h.getChangedBy()),
                        h.getChangedBy().getRole(),
                        h.getRemarks(),
                        h.getCreatedAt()
                )).collect(Collectors.toList());

        RatingDto ratingDto = ratingRepository.findByRequestId(r.getId())
                .map(rt -> new RatingDto(rt.getRating(), rt.getFeedback(), rt.getCreatedAt()))
                .orElse(null);

        WorkerProfile w = r.getAssignedWorker();
        return new MaintenanceDetailResponse(
                r.getId(),
                r.getRequestCode(),
                r.getStudent().getId(),
                r.getStudent().getFullName(),
                r.getStudent().getRegisterNumber(),
                r.getStudent().getPhone(),
                r.getCategory().name(),
                r.getTitle(),
                r.getDescription(),
                r.getHostelBlock(),
                r.getRoomNumber(),
                r.getPriority().name(),
                r.getPreferredTimeSlot(),
                r.getStatus().name(),
                w != null ? w.getId() : null,
                w != null ? w.getFullName() : null,
                w != null ? w.getEmployeeId() : null,
                w != null ? w.getWorkerType().name() : null,
                w != null ? w.getPhone() : null,
                r.getWardenInstruction(),
                r.getExpectedCompletionDate(),
                r.getRejectionReason(),
                r.getOnHoldReason(),
                r.getWorkerRemarks(),
                r.getReopenReason(),
                r.getCreatedAt(),
                r.getUpdatedAt(),
                r.getCompletedAt(),
                r.getResolvedAt(),
                attachments,
                history,
                ratingDto
        );
    }

    private MaintenanceRequest.Category parseCategory(String str) {
        try {
            return MaintenanceRequest.Category.valueOf(str.trim().toUpperCase());
        } catch (Exception e) {
            throw new BadRequestException("Invalid category: " + str);
        }
    }

    private MaintenanceRequest.Category parseOptionalCategory(String str) {
        return (str != null && !str.isBlank() && !"ALL".equalsIgnoreCase(str)) ? parseCategory(str) : null;
    }

    private MaintenanceRequest.Priority parsePriority(String str) {
        try {
            return MaintenanceRequest.Priority.valueOf(str.trim().toUpperCase());
        } catch (Exception e) {
            throw new BadRequestException("Invalid priority: " + str);
        }
    }

    private MaintenanceRequest.Priority parseOptionalPriority(String str) {
        return (str != null && !str.isBlank() && !"ALL".equalsIgnoreCase(str)) ? parsePriority(str) : null;
    }

    private MaintenanceRequest.Status parseOptionalStatus(String str) {
        if (str == null || str.isBlank() || "ALL".equalsIgnoreCase(str)) return null;
        try {
            return MaintenanceRequest.Status.valueOf(str.trim().toUpperCase());
        } catch (Exception e) {
            throw new BadRequestException("Invalid status: " + str);
        }
    }
}

