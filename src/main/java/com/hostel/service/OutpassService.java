package com.hostel.service;

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
    private final AuditService auditService;

    public OutpassService(OutpassRepository outpassRepository,
                          StudentRepository studentRepository,
                          BedRepository bedRepository,
                          AttendanceRepository attendanceRepository,
                          AuditService auditService) {
        this.outpassRepository = outpassRepository;
        this.studentRepository = studentRepository;
        this.bedRepository = bedRepository;
        this.attendanceRepository = attendanceRepository;
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
                    activeOutpassStatus
            );
        }

        return new OutpassEligibilityResponse(
                true,
                "ELIGIBLE",
                "You are eligible to apply for an outpass.",
                List.of(),
                isResident,
                hasActiveOutpass,
                activeOutpassStatus
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

    public List<OutpassResponse> getAllOutpasses(String statusFilter) {
        List<Outpass> list;
        if (statusFilter != null && !statusFilter.isBlank() && !"ALL".equalsIgnoreCase(statusFilter)) {
            try {
                Outpass.Status status = Outpass.Status.valueOf(statusFilter.trim().toUpperCase());
                list = outpassRepository.findByStatusOrderByCreatedAtDesc(status);
            } catch (IllegalArgumentException e) {
                list = outpassRepository.findAllByOrderByCreatedAtDesc();
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
