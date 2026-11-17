package com.hostel.service;

import com.hostel.dto.DigitalAttendanceDtos.SecurityOutpassScanResponse;
import com.hostel.dto.OutpassDtos.*;
import com.hostel.entity.*;
import com.hostel.exception.CustomExceptions.*;
import com.hostel.repository.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

@Service
public class OutpassService {

    private final OutpassRepository outpassRepository;
    private final StudentRepository studentRepository;
    private final BedRepository bedRepository;
    private final AttendanceRepository attendanceRepository;
    private final OutpassLockRepository outpassLockRepository;
    private final OutpassScanLogRepository outpassScanLogRepository;
    private final AuditService auditService;

    public OutpassService(OutpassRepository outpassRepository,
                          StudentRepository studentRepository,
                          BedRepository bedRepository,
                          AttendanceRepository attendanceRepository,
                          OutpassLockRepository outpassLockRepository,
                          OutpassScanLogRepository outpassScanLogRepository,
                          AuditService auditService) {
        this.outpassRepository = outpassRepository;
        this.studentRepository = studentRepository;
        this.bedRepository = bedRepository;
        this.attendanceRepository = attendanceRepository;
        this.outpassLockRepository = outpassLockRepository;
        this.outpassScanLogRepository = outpassScanLogRepository;
        this.auditService = auditService;
    }

    public OutpassEligibilityResponse getOutpassEligibility(User studentUser) {
        Student student = studentRepository.findByUserId(studentUser.getId())
                .orElseThrow(() -> new ResourceNotFoundException("Student profile not found"));
        return checkOutpassEligibility(student);
    }

    public OutpassEligibilityResponse checkOutpassEligibility(Student student) {
        Optional<Bed> bedOpt = bedRepository.findByStudentId(student.getId());
        boolean isResident = bedOpt.isPresent();

        // 1. Check for unexcused absences
        List<Attendance> absentRecords = attendanceRepository.findByStudentAndStatus(student, Attendance.Status.ABSENT);
        List<String> defaultDates = absentRecords.stream()
                .map(a -> a.getAttendanceDate().toString())
                .sorted(Comparator.reverseOrder())
                .collect(Collectors.toList());

        // 2. Check if student currently has an active approved outpass (Gone to Home)
        List<Outpass> approvedOutpasses = outpassRepository.findByStudentOrderByCreatedAtDesc(student).stream()
                .filter(op -> op.getStatus() == Outpass.Status.APPROVED)
                .collect(Collectors.toList());

        LocalDateTime now = LocalDateTime.now();
        Optional<Outpass> activePassOpt = approvedOutpasses.stream()
                .filter(op -> !op.getDepartureDateTime().isAfter(now) && !op.getReturnDateTime().isBefore(now))
                .findFirst();

        boolean hasActiveOutpass = activePassOpt.isPresent();
        String activeOutpassStatus = null;
        if (hasActiveOutpass) {
            Outpass op = activePassOpt.get();
            activeOutpassStatus = "Gone to Home (" + op.getPassType() + " - " + (op.getDestination() != null ? op.getDestination() : "Home") + ")";
        }

        // 0. Check explicit persistent Digital Attendance outpass lock
        if (student.isLocked()) {
            String lockMsg = "Outpass locked due to missed attendance"
                    + (student.getLockReason() != null ? ": " + student.getLockReason() : "")
                    + ". Contact the warden to unlock.";
            return new OutpassEligibilityResponse(
                    false,
                    "LOCKED_MISSED_ATTENDANCE",
                    lockMsg,
                    defaultDates,
                    isResident,
                    hasActiveOutpass,
                    activeOutpassStatus,
                    true,
                    student.getLockReason(),
                    student.getLockedAt()
            );
        }

        // Rule: If resident student was absent / didn't mark attendance for a single date, outpass is disabled
        if (isResident && !defaultDates.isEmpty()) {
            String msg = "Outpass privileges disabled: You have unexcused absence on " + String.join(", ", defaultDates) +
                    " while resident at the hostel. Please contact the Warden to clear your attendance default before applying for an outpass.";
            return new OutpassEligibilityResponse(
                    false,
                    "DISABLED_ATTENDANCE_DEFAULT",
                    msg,
                    defaultDates,
                    isResident,
                    hasActiveOutpass,
                    activeOutpassStatus,
                    false,
                    null,
                    null
            );
        }

        return new OutpassEligibilityResponse(
                true,
                "ELIGIBLE",
                "You are eligible to apply for an outpass.",
                List.of(),
                isResident,
                hasActiveOutpass,
                activeOutpassStatus,
                false,
                null,
                null
        );
    }

    @Transactional
    public OutpassResponse createOutpass(User studentUser, CreateOutpassRequest req) {
        Student student = studentRepository.findByUserId(studentUser.getId())
                .orElseThrow(() -> new ResourceNotFoundException("Student profile not found"));

        // Verify outpass eligibility (fails if single unexcused absence exists)
        OutpassEligibilityResponse eligibility = checkOutpassEligibility(student);
        if (!eligibility.eligible()) {
            throw new BadRequestException(eligibility.message());
        }

        if (req.departureDateTime().isAfter(req.returnDateTime())) {
            throw new BadRequestException("Departure date & time cannot be after return date & time");
        }

        Outpass.PassType passType;
        try {
            passType = Outpass.PassType.valueOf(req.passType().trim().toUpperCase());
        } catch (IllegalArgumentException e) {
            throw new BadRequestException("Invalid pass type: " + req.passType());
        }

        Outpass outpass = new Outpass();
        outpass.setStudent(student);
        outpass.setPassType(passType);
        outpass.setDepartureDateTime(req.departureDateTime());
        outpass.setReturnDateTime(req.returnDateTime());
        outpass.setReason(req.reason().trim());
        outpass.setDestination(req.destination() != null ? req.destination().trim() : null);
        outpass.setEmergencyPhone(req.emergencyPhone() != null ? req.emergencyPhone().trim() : student.getPhone());
        outpass.setStatus(Outpass.Status.PENDING);

        Outpass saved = outpassRepository.save(outpass);
        auditService.log(studentUser, "CREATE_OUTPASS", "OutpassId=" + saved.getId() + " Type=" + passType, "SUCCESS");

        return mapToResponse(saved);
    }

    public List<OutpassResponse> getMyOutpasses(User studentUser) {
        Student student = studentRepository.findByUserId(studentUser.getId())
                .orElseThrow(() -> new ResourceNotFoundException("Student profile not found"));
        return outpassRepository.findByStudentOrderByCreatedAtDesc(student).stream()
                .map(this::mapToResponse)
                .collect(Collectors.toList());
    }

    public WardenOutpassSummaryResponse getWardenSummary() {
        LocalDateTime now = LocalDateTime.now();
        long pending = outpassRepository.countByStatus(Outpass.Status.PENDING);
        long approved = outpassRepository.countByStatus(Outpass.Status.APPROVED);
        long currentlyOut = outpassRepository.countByStatus(Outpass.Status.OUT);
        long lateReturns = outpassRepository.countByStatusAndReturnDateTimeBefore(Outpass.Status.OUT, now);
        long returned = outpassRepository.countByStatus(Outpass.Status.RETURNED);
        long rejected = outpassRepository.countByStatus(Outpass.Status.REJECTED);
        long total = outpassRepository.count();
        return new WardenOutpassSummaryResponse(pending, approved, currentlyOut, lateReturns, returned, rejected, total);
    }

    public List<OutpassResponse> getAllOutpasses(String statusFilter) {
        List<Outpass> list;
        if (statusFilter != null && !statusFilter.isBlank() && !"ALL".equalsIgnoreCase(statusFilter)) {
            if ("LATE".equalsIgnoreCase(statusFilter.trim())) {
                list = outpassRepository.findByStatusAndReturnDateTimeBeforeOrderByReturnDateTimeAsc(
                        Outpass.Status.OUT, LocalDateTime.now());
            } else {
                try {
                    Outpass.Status status = Outpass.Status.valueOf(statusFilter.trim().toUpperCase());
                    list = outpassRepository.findByStatusOrderByCreatedAtDesc(status);
                } catch (IllegalArgumentException e) {
                    list = outpassRepository.findAllByOrderByCreatedAtDesc();
                }
            }
        } else {
            list = outpassRepository.findAllByOrderByCreatedAtDesc();
        }
        return list.stream().map(this::mapToResponse).collect(Collectors.toList());
    }

    @Transactional
    public OutpassResponse approveOutpass(Long outpassId, String remarks, User warden) {
        Outpass outpass = outpassRepository.findById(outpassId)
                .orElseThrow(() -> new ResourceNotFoundException("Outpass not found"));

        if (outpass.getStatus() != Outpass.Status.PENDING) {
            throw new BadRequestException("Only pending outpasses can be approved");
        }

        outpass.setStatus(Outpass.Status.APPROVED);
        outpass.setWardenRemarks(remarks != null && !remarks.isBlank() ? remarks.trim() : "Approved by Warden");
        outpass.setDecidedBy(warden);
        outpass.setDecidedAt(LocalDateTime.now());
        Outpass saved = outpassRepository.save(outpass);

        // Auto-update student attendance to ON_OUTPASS for today if departure is today or earlier
        LocalDate today = LocalDate.now();
        if (!outpass.getDepartureDateTime().toLocalDate().isAfter(today) &&
            !outpass.getReturnDateTime().toLocalDate().isBefore(today)) {
            Student student = outpass.getStudent();
            Attendance attendance = attendanceRepository.findByStudentAndAttendanceDate(student, today)
                    .orElseGet(() -> {
                        Attendance att = new Attendance();
                        att.setStudent(student);
                        att.setAttendanceDate(today);
                        return att;
                    });
            attendance.setStatus(Attendance.Status.ON_OUTPASS);
            attendance.setMarkedBy(warden);
            attendance.setRemarks("Gone to Home (Outpass #" + outpass.getId() + " - " + (outpass.getDestination() != null ? outpass.getDestination() : "Home") + ")");
            attendanceRepository.save(attendance);
        }

        auditService.log(warden, "APPROVE_OUTPASS", "OutpassId=" + outpassId + " Student=" + outpass.getStudent().getRegisterNumber(), "SUCCESS");
        return mapToResponse(saved);
    }

    @Transactional
    public OutpassResponse rejectOutpass(Long outpassId, String remarks, User warden) {
        Outpass outpass = outpassRepository.findById(outpassId)
                .orElseThrow(() -> new ResourceNotFoundException("Outpass not found"));

        if (outpass.getStatus() != Outpass.Status.PENDING) {
            throw new BadRequestException("Only pending outpasses can be rejected");
        }

        outpass.setStatus(Outpass.Status.REJECTED);
        outpass.setWardenRemarks(remarks != null && !remarks.isBlank() ? remarks.trim() : "Rejected by Warden");
        outpass.setDecidedBy(warden);
        outpass.setDecidedAt(LocalDateTime.now());
        Outpass saved = outpassRepository.save(outpass);

        auditService.log(warden, "REJECT_OUTPASS", "OutpassId=" + outpassId + " Student=" + outpass.getStudent().getRegisterNumber(), "SUCCESS");
        return mapToResponse(saved);
    }

    // =====================================================
    // SECURITY GATE OUTPASS VERIFICATION & EXIT
    // =====================================================

    public SecurityOutpassScanResponse scanOutpassForSecurity(String query) {
        if (query == null || query.isBlank()) {
            throw new BadRequestException("Enter Outpass ID or Student Register Number");
        }
        String trimmed = query.trim();
        // Remove leading # or OP- or OP or OUTPASS: prefix if present
        String idCandidate = trimmed.replaceAll("^(?i)(?:outpass[:#\\-\\s]*|otp[:#\\-\\s]*|op[:#\\-\\s]*|#+)", "");
        Outpass outpass = null;
        if (idCandidate.matches("\\d+")) {
            outpass = outpassRepository.findById(Long.parseLong(idCandidate)).orElse(null);
        }
        if (outpass == null && trimmed.matches("\\d+")) {
            outpass = outpassRepository.findById(Long.parseLong(trimmed)).orElse(null);
        }
        if (outpass == null) {
            Student s = studentRepository.findByRegisterNumber(trimmed.toUpperCase())
                    .orElseThrow(() -> new ResourceNotFoundException("No outpass or student found matching: " + trimmed));
            List<Outpass> passes = outpassRepository.findByStudentOrderByCreatedAtDesc(s);
            if (passes.isEmpty()) {
                throw new ResourceNotFoundException("No outpass requests found for student " + s.getRegisterNumber());
            }
            outpass = passes.stream()
                    .filter(p -> p.getStatus() == Outpass.Status.APPROVED || p.getStatus() == Outpass.Status.OUT)
                    .findFirst()
                    .orElse(passes.get(0));
        }
        return mapToSecurityScanResponse(outpass);
    }

    @Transactional(noRollbackFor = ForbiddenException.class)
    public SecurityOutpassScanResponse markSecurityExit(Long outpassId, User securityUser) {
        return markSecurityExit(outpassId, securityUser, null);
    }

    @Transactional(noRollbackFor = ForbiddenException.class)
    public SecurityOutpassScanResponse markSecurityExit(Long outpassId, User securityUser, String remarks) {
        Outpass outpass = outpassRepository.findById(outpassId)
                .orElseThrow(() -> new ResourceNotFoundException("Outpass not found: " + outpassId));
        Student student = outpass.getStudent();

        boolean hasActiveLock = student.isLocked()
                || outpassLockRepository.findFirstByStudentIdAndActiveTrueOrderByLockedAtDesc(student.getId()).isPresent();
        if (hasActiveLock) {
            String denialMsg = "OUTPASS LOCKED - MISSED ATTENDANCE. Outpass locked due to missed attendance. Contact the warden.";
            OutpassScanLog deniedLog = new OutpassScanLog();
            deniedLog.setOutpass(outpass);
            deniedLog.setScannedBy(securityUser);
            deniedLog.setScanAction("denied");
            deniedLog.setScanResult("LOCKED_MISSED_ATTENDANCE");
            deniedLog.setNotes(remarks != null && !remarks.isBlank() ? denialMsg + " Remarks: " + remarks.trim() : denialMsg);
            outpassScanLogRepository.save(deniedLog);

            auditService.log(securityUser, "OUTPASS", "SECURITY_OUTPASS_EXIT_DENIED", outpass.getId(),
                    outpass.getStatus().name(), outpass.getStatus().name(),
                    "OutpassId=" + outpass.getId() + " Student=" + student.getRegisterNumber(), "DENIED_LOCKED");
            throw new ForbiddenException(denialMsg);
        }
        if (outpass.getStatus() != Outpass.Status.APPROVED) {
            throw new BadRequestException("Exit can only be marked for APPROVED outpasses. Current status: " + outpass.getStatus());
        }

        String oldStatus = outpass.getStatus().name();
        outpass.setStatus(Outpass.Status.OUT);
        outpass.setActualExitTime(LocalDateTime.now());
        if (remarks != null && !remarks.isBlank()) {
            String prev = outpass.getWardenRemarks() != null ? outpass.getWardenRemarks() + " | " : "";
            outpass.setWardenRemarks(prev + "Exit remarks: " + remarks.trim());
        }
        Outpass saved = outpassRepository.save(outpass);

        OutpassScanLog exitLog = new OutpassScanLog();
        exitLog.setOutpass(saved);
        exitLog.setScannedBy(securityUser);
        exitLog.setScanAction("exit");
        exitLog.setScanResult("ALLOWED");
        exitLog.setNotes(remarks != null && !remarks.isBlank() ? "Security gate exit recorded: " + remarks.trim() : "Security gate exit recorded");
        outpassScanLogRepository.save(exitLog);

        auditService.log(securityUser, "OUTPASS", "SECURITY_OUTPASS_EXIT", saved.getId(),
                oldStatus, Outpass.Status.OUT.name(),
                "OutpassId=" + saved.getId() + " Student=" + student.getRegisterNumber(), "SUCCESS");
        return mapToSecurityScanResponse(saved);
    }

    @Transactional
    public SecurityOutpassScanResponse markSecurityEntry(Long outpassId, User securityUser, String remarks) {
        Outpass outpass = outpassRepository.findById(outpassId)
                .orElseThrow(() -> new ResourceNotFoundException("Outpass not found: " + outpassId));
        Student student = outpass.getStudent();

        if (outpass.getStatus() != Outpass.Status.OUT) {
            throw new BadRequestException("Entry can only be marked for students currently OUT. Current status: " + outpass.getStatus());
        }

        String oldStatus = outpass.getStatus().name();
        outpass.setStatus(Outpass.Status.RETURNED);
        if (remarks != null && !remarks.isBlank()) {
            String prev = outpass.getWardenRemarks() != null ? outpass.getWardenRemarks() + " | " : "";
            outpass.setWardenRemarks(prev + "Entry remarks: " + remarks.trim());
        }
        Outpass saved = outpassRepository.save(outpass);

        OutpassScanLog entryLog = new OutpassScanLog();
        entryLog.setOutpass(saved);
        entryLog.setScannedBy(securityUser);
        entryLog.setScanAction("entry");
        entryLog.setScanResult("ALLOWED");
        entryLog.setNotes(remarks != null && !remarks.isBlank() ? "Security gate entry recorded: " + remarks.trim() : "Security gate entry recorded");
        outpassScanLogRepository.save(entryLog);

        auditService.log(securityUser, "OUTPASS", "SECURITY_OUTPASS_ENTRY", saved.getId(),
                oldStatus, Outpass.Status.RETURNED.name(),
                "OutpassId=" + saved.getId() + " Student=" + student.getRegisterNumber(), "SUCCESS");
        return mapToSecurityScanResponse(saved);
    }

    private SecurityOutpassScanResponse mapToSecurityScanResponse(Outpass outpass) {
        Student s = outpass.getStudent();
        String roomNumber = bedRepository.findByStudentId(s.getId())
                .map(b -> b.getRoom().getRoomNumber())
                .orElse("-");

        boolean locked = s.isLocked()
                || outpassLockRepository.findFirstByStudentIdAndActiveTrueOrderByLockedAtDesc(s.getId()).isPresent();
        boolean exitAllowed;
        String gateMessage;

        if (locked) {
            exitAllowed = false;
            gateMessage = "OUTPASS LOCKED - MISSED ATTENDANCE. Contact the warden.";
        } else if (outpass.getStatus() == Outpass.Status.APPROVED) {
            exitAllowed = true;
            gateMessage = "Valid Approved Outpass. Exit permitted.";
        } else if (outpass.getStatus() == Outpass.Status.OUT) {
            exitAllowed = false;
            gateMessage = "Student is currently OUT. Ready for Entry check-in.";
        } else if (outpass.getStatus() == Outpass.Status.RETURNED) {
            exitAllowed = false;
            gateMessage = "Student has already returned.";
        } else {
            exitAllowed = false;
            gateMessage = "Outpass status is " + outpass.getStatus() + ". Exit not permitted.";
        }

        return new SecurityOutpassScanResponse(
                outpass.getId(),
                s.getId(),
                s.getFullName(),
                s.getRegisterNumber(),
                s.getDepartment(),
                roomNumber,
                outpass.getPassType().name(),
                outpass.getDepartureDateTime(),
                outpass.getReturnDateTime(),
                outpass.getReason(),
                outpass.getDestination(),
                outpass.getStatus().name(),
                locked,
                s.getLockReason(),
                s.getLockedAt(),
                exitAllowed,
                gateMessage
        );
    }

    private OutpassResponse mapToResponse(Outpass outpass) {
        Student s = outpass.getStudent();
        Optional<Bed> bedOpt = bedRepository.findByStudentId(s.getId());
        String roomNumber = bedOpt.map(b -> b.getRoom().getRoomNumber()).orElse("-");
        Integer bedNumber = bedOpt.map(Bed::getBedNumber).orElse(null);

        return new OutpassResponse(
                outpass.getId(),
                s.getId(),
                s.getFullName(),
                s.getRegisterNumber(),
                roomNumber,
                bedNumber,
                s.getDepartment(),
                s.getPhone(),
                outpass.getPassType().name(),
                outpass.getDepartureDateTime(),
                outpass.getReturnDateTime(),
                outpass.getReason(),
                outpass.getDestination(),
                outpass.getEmergencyPhone(),
                outpass.getStatus().name(),
                outpass.getWardenRemarks(),
                outpass.getCreatedAt(),
                outpass.getDecidedAt()
        );
    }
}
