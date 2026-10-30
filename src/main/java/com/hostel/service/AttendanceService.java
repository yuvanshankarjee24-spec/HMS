package com.hostel.service;

import com.hostel.dto.AttendanceDtos.*;
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
public class AttendanceService {

    private final AttendanceRepository attendanceRepository;
    private final StudentRepository studentRepository;
    private final BedRepository bedRepository;
    private final OutpassRepository outpassRepository;
    private final AuditService auditService;

    public AttendanceService(AttendanceRepository attendanceRepository,
                             StudentRepository studentRepository,
                             BedRepository bedRepository,
                             OutpassRepository outpassRepository,
                             AuditService auditService) {
        this.attendanceRepository = attendanceRepository;
        this.studentRepository = studentRepository;
        this.bedRepository = bedRepository;
        this.outpassRepository = outpassRepository;
        this.auditService = auditService;
    }

    public List<StudentAttendanceItem> getDailyAttendance(LocalDate date, String query, String statusFilter) {
        LocalDate targetDate = (date != null) ? date : LocalDate.now();

        // Find all allocated beds/students
        List<Bed> occupiedBeds = bedRepository.findAll().stream()
                .filter(b -> b.getStudent() != null)
                .collect(Collectors.toList());

        // Get existing attendance records for target date
        List<Attendance> attendances = attendanceRepository.findByAttendanceDate(targetDate);
        Map<Long, Attendance> attendanceMap = attendances.stream()
                .collect(Collectors.toMap(a -> a.getStudent().getId(), a -> a, (k1, k2) -> k1));

        // Get active approved outpasses
        List<Outpass> approvedOutpasses = outpassRepository.findByStatusOrderByCreatedAtDesc(Outpass.Status.APPROVED);

        List<StudentAttendanceItem> items = new ArrayList<>();
        for (Bed bed : occupiedBeds) {
            Student s = bed.getStudent();

            // Filter by search query if provided
            if (query != null && !query.isBlank()) {
                String q = query.trim().toLowerCase();
                boolean matches = s.getFullName().toLowerCase().contains(q) ||
                                  s.getRegisterNumber().toLowerCase().contains(q) ||
                                  bed.getRoom().getRoomNumber().toLowerCase().contains(q);
                if (!matches) continue;
            }

            // Check if active approved outpass covers targetDate
            String activeOutpassInfo = null;
            boolean hasActiveOutpass = false;
            for (Outpass op : approvedOutpasses) {
                if (op.getStudent().getId().equals(s.getId())) {
                    LocalDate dep = op.getDepartureDateTime().toLocalDate();
                    LocalDate ret = op.getReturnDateTime().toLocalDate();
                    if (!dep.isAfter(targetDate) && !ret.isBefore(targetDate)) {
                        activeOutpassInfo = "🏡 Gone to Home (" + op.getPassType() + " to " + (op.getDestination() != null ? op.getDestination() : "Home") + ")";
                        hasActiveOutpass = true;
                        break;
                    }
                }
            }

            Attendance att = attendanceMap.get(s.getId());
            String status = "PRESENT";
            String remarks = null;
            LocalDateTime updatedAt = null;

            if (att != null) {
                status = att.getStatus().name();
                remarks = att.getRemarks();
                updatedAt = att.getUpdatedAt();
            } else if (hasActiveOutpass) {
                status = "ON_OUTPASS";
                remarks = "Gone to Home (Active Outpass)";
            }

            // Filter by status if requested
            if (statusFilter != null && !statusFilter.isBlank() && !"ALL".equalsIgnoreCase(statusFilter)) {
                if (!statusFilter.trim().equalsIgnoreCase(status)) {
                    continue;
                }
            }

            items.add(new StudentAttendanceItem(
                    s.getId(),
                    s.getFullName(),
                    s.getRegisterNumber(),
                    bed.getRoom().getRoomNumber(),
                    bed.getBedNumber(),
                    s.getDepartment(),
                    s.getYear(),
                    status,
                    remarks,
                    activeOutpassInfo,
                    updatedAt
            ));
        }

        // Sort by room number, bed number
        items.sort(Comparator.comparing(StudentAttendanceItem::roomNumber)
                .thenComparing(StudentAttendanceItem::bedNumber));

        return items;
    }

    public AttendanceSummaryResponse getDailySummary(LocalDate date) {
        LocalDate targetDate = (date != null) ? date : LocalDate.now();
        List<StudentAttendanceItem> list = getDailyAttendance(targetDate, null, null);

        int total = list.size();
        int present = (int) list.stream().filter(i -> "PRESENT".equalsIgnoreCase(i.status())).count();
        int absent = (int) list.stream().filter(i -> "ABSENT".equalsIgnoreCase(i.status())).count();
        int onOutpass = (int) list.stream().filter(i -> "ON_OUTPASS".equalsIgnoreCase(i.status())).count();
        int late = (int) list.stream().filter(i -> "LATE".equalsIgnoreCase(i.status())).count();

        return new AttendanceSummaryResponse(targetDate, total, present, absent, onOutpass, late);
    }

    @Transactional
    public StudentAttendanceItem markAttendance(MarkAttendanceRequest req, User warden) {
        Student student = studentRepository.findById(req.studentId())
                .orElseThrow(() -> new ResourceNotFoundException("Student not found"));

        LocalDate targetDate = (req.date() != null) ? req.date() : LocalDate.now();

        Attendance.Status newStatus;
        try {
            newStatus = Attendance.Status.valueOf(req.status().trim().toUpperCase());
        } catch (IllegalArgumentException e) {
            throw new BadRequestException("Invalid attendance status: " + req.status());
        }

        Attendance attendance = attendanceRepository.findByStudentAndAttendanceDate(student, targetDate)
                .orElseGet(() -> {
                    Attendance a = new Attendance();
                    a.setStudent(student);
                    a.setAttendanceDate(targetDate);
                    return a;
                });

        attendance.setStatus(newStatus);
        attendance.setMarkedBy(warden);
        if (req.remarks() != null && !req.remarks().isBlank()) {
            attendance.setRemarks(req.remarks().trim());
        }
        Attendance saved = attendanceRepository.save(attendance);

        auditService.log(warden, "MARK_ATTENDANCE",
                "Student=" + student.getRegisterNumber() + " Date=" + targetDate + " Status=" + newStatus, "SUCCESS");

        Optional<Bed> bedOpt = bedRepository.findByStudentId(student.getId());
        String roomNumber = bedOpt.map(b -> b.getRoom().getRoomNumber()).orElse("-");
        Integer bedNumber = bedOpt.map(Bed::getBedNumber).orElse(null);

        return new StudentAttendanceItem(
                student.getId(),
                student.getFullName(),
                student.getRegisterNumber(),
                roomNumber,
                bedNumber,
                student.getDepartment(),
                student.getYear(),
                saved.getStatus().name(),
                saved.getRemarks(),
                null,
                saved.getUpdatedAt()
        );
    }

    @Transactional
    public void markAllPresent(LocalDate date, User warden) {
        LocalDate targetDate = (date != null) ? date : LocalDate.now();

        List<Bed> occupiedBeds = bedRepository.findAll().stream()
                .filter(b -> b.getStudent() != null)
                .collect(Collectors.toList());

        for (Bed bed : occupiedBeds) {
            Student s = bed.getStudent();
            Optional<Attendance> existing = attendanceRepository.findByStudentAndAttendanceDate(s, targetDate);
            if (existing.isEmpty()) {
                Attendance a = new Attendance();
                a.setStudent(s);
                a.setAttendanceDate(targetDate);
                a.setStatus(Attendance.Status.PRESENT);
                a.setMarkedBy(warden);
                a.setRemarks("Batch marked present by Warden");
                attendanceRepository.save(a);
            }
        }

        auditService.log(warden, "BATCH_MARK_PRESENT", "Date=" + targetDate, "SUCCESS");
    }

    public List<Attendance> getStudentAttendanceHistory(User studentUser) {
        Student student = studentRepository.findByUserId(studentUser.getId())
                .orElseThrow(() -> new ResourceNotFoundException("Student profile not found"));
        return attendanceRepository.findByStudentOrderByAttendanceDateDesc(student);
    }
}
