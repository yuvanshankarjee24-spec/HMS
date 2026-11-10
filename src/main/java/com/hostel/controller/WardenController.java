package com.hostel.controller;

import com.hostel.dto.StudentDtos.RoomRequestResponse;
import com.hostel.dto.WardenDtos.*;
import com.hostel.entity.Bed;
import com.hostel.entity.Room;
import com.hostel.entity.RoomRequest;
import com.hostel.entity.User;
import com.hostel.repository.UserRepository;
import com.hostel.service.WardenService;
import jakarta.validation.Valid;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import com.hostel.dto.AttendanceDtos.*;
import com.hostel.dto.OutpassDtos.*;
import com.hostel.service.AttendanceService;
import com.hostel.service.OutpassService;
import java.time.LocalDate;

@RestController
@RequestMapping("/api/warden")
@PreAuthorize("hasAnyRole('WARDEN', 'ADMIN')")
public class WardenController {

    private final WardenService wardenService;
    private final UserRepository userRepository;
    private final AttendanceService attendanceService;
    private final OutpassService outpassService;
    private final com.hostel.repository.StudentRepository studentRepository;

    public WardenController(WardenService wardenService,
                            UserRepository userRepository,
                            AttendanceService attendanceService,
                            OutpassService outpassService,
                            com.hostel.repository.StudentRepository studentRepository) {
        this.wardenService = wardenService;
        this.userRepository = userRepository;
        this.attendanceService = attendanceService;
        this.outpassService = outpassService;
        this.studentRepository = studentRepository;
    }

    private User getAuthenticatedUser(UserDetails userDetails) {
        return userRepository.findByEmail(userDetails.getUsername())
                .orElseThrow(() -> new RuntimeException("Authenticated user not found"));
    }

    @GetMapping("/summary")
    public ResponseEntity<WardenSummaryResponse> getSummary() {
        return ResponseEntity.ok(wardenService.getSummary());
    }

    @GetMapping("/rooms")
    public ResponseEntity<List<RoomListItem>> getRooms() {
        return ResponseEntity.ok(wardenService.getAllRooms());
    }

    @GetMapping("/students/summary")
    public ResponseEntity<Map<String, Object>> getStudentsSummary() {
        WardenSummaryResponse ws = wardenService.getSummary();
        long totalStudents = studentRepository.count();
        long allocated = ws.occupiedBeds();
        long unallocated = Math.max(0, totalStudents - allocated);
        return ResponseEntity.ok(Map.of(
                "totalStudents", totalStudents,
                "allocatedStudents", allocated,
                "unallocatedStudents", unallocated,
                "pendingRoomRequests", ws.pendingRequests()
        ));
    }

    @GetMapping("/students")
    public ResponseEntity<Page<StudentWardenView>> getStudents(
            @RequestParam(value = "query", required = false) String query,
            @RequestParam(value = "page", defaultValue = "0") int page,
            @RequestParam(value = "size", defaultValue = "10") int size) {
        Pageable pageable = PageRequest.of(page, size, Sort.by("fullName").ascending());
        return ResponseEntity.ok(wardenService.getStudents(query, pageable));
    }

    @GetMapping("/room-requests")
    public ResponseEntity<List<RoomRequestResponse>> getRoomRequests(
            @RequestParam(value = "status", required = false) String status) {
        RoomRequest.Status reqStatus = null;
        if (status != null && !status.isBlank()) {
            reqStatus = RoomRequest.Status.valueOf(status.trim().toUpperCase());
        }
        List<RoomRequest> list = wardenService.getRoomRequests(reqStatus);
        List<RoomRequestResponse> dtos = list.stream().map(r -> new RoomRequestResponse(
                r.getId(),
                r.getStudent().getId(),
                r.getStudent().getFullName(),
                r.getStudent().getRegisterNumber(),
                r.getPreferredRoom() != null ? r.getPreferredRoom().getId() : null,
                r.getPreferredRoom() != null ? r.getPreferredRoom().getRoomNumber() : null,
                r.getPreferredFloor(),
                r.getNote(),
                r.getStatus().name(),
                r.getDecisionNote(),
                r.getCreatedAt(),
                r.getDecidedAt()
        )).collect(Collectors.toList());
        return ResponseEntity.ok(dtos);
    }

    @PostMapping("/room-requests/{id}/approve")
    public ResponseEntity<Map<String, Object>> approveRoomRequest(
            @AuthenticationPrincipal UserDetails userDetails,
            @PathVariable Long id,
            @RequestBody(required = false) ApproveRequest approveDto) {
        User user = getAuthenticatedUser(userDetails);
        Bed allocatedBed = wardenService.approveRoomRequest(id, approveDto, user);
        return ResponseEntity.ok(Map.of(
                "message", "Room request approved successfully",
                "bedId", allocatedBed.getId(),
                "roomNumber", allocatedBed.getRoom().getRoomNumber(),
                "bedNumber", allocatedBed.getBedNumber()
        ));
    }

    @PostMapping("/room-requests/{id}/reject")
    public ResponseEntity<Map<String, String>> rejectRoomRequest(
            @AuthenticationPrincipal UserDetails userDetails,
            @PathVariable Long id,
            @Valid @RequestBody RejectRequest rejectDto) {
        User user = getAuthenticatedUser(userDetails);
        wardenService.rejectRoomRequest(id, rejectDto, user);
        return ResponseEntity.ok(Map.of("message", "Room request rejected"));
    }

    @PostMapping("/students/{id}/vacate")
    public ResponseEntity<Map<String, String>> vacateStudent(
            @AuthenticationPrincipal UserDetails userDetails,
            @PathVariable Long id) {
        User user = getAuthenticatedUser(userDetails);
        wardenService.vacateStudent(id, user);
        return ResponseEntity.ok(Map.of("message", "Student vacated successfully and bed released"));
    }

    // ==========================================
    // ATTENDANCE MANAGEMENT
    // ==========================================

    @GetMapping("/attendance")
    public ResponseEntity<List<StudentAttendanceItem>> getDailyAttendance(
            @RequestParam(required = false) LocalDate date,
            @RequestParam(required = false) String query,
            @RequestParam(required = false) String status) {
        return ResponseEntity.ok(attendanceService.getDailyAttendance(date, query, status));
    }

    @GetMapping("/attendance/summary")
    public ResponseEntity<AttendanceSummaryResponse> getAttendanceSummary(
            @RequestParam(required = false) LocalDate date) {
        return ResponseEntity.ok(attendanceService.getDailySummary(date));
    }

    @PostMapping("/attendance/mark")
    public ResponseEntity<StudentAttendanceItem> markAttendance(
            @AuthenticationPrincipal UserDetails userDetails,
            @Valid @RequestBody MarkAttendanceRequest req) {
        User user = getAuthenticatedUser(userDetails);
        return ResponseEntity.ok(attendanceService.markAttendance(req, user));
    }

    @PostMapping("/attendance/mark-all-present")
    public ResponseEntity<Map<String, String>> markAllPresent(
            @AuthenticationPrincipal UserDetails userDetails,
            @RequestParam(required = false) LocalDate date) {
        User user = getAuthenticatedUser(userDetails);
        attendanceService.markAllPresent(date, user);
        return ResponseEntity.ok(Map.of("message", "All unmarked allocated students have been marked PRESENT"));
    }

    // ==========================================
    // OUTPASS MANAGEMENT
    // ==========================================

    @GetMapping("/outpasses/summary")
    public ResponseEntity<WardenOutpassSummaryResponse> getOutpassesSummary() {
        return ResponseEntity.ok(outpassService.getWardenSummary());
    }

    @GetMapping("/outpasses")
    public ResponseEntity<List<OutpassResponse>> getOutpasses(
            @RequestParam(required = false) String status) {
        return ResponseEntity.ok(outpassService.getAllOutpasses(status));
    }

    @PostMapping("/outpasses/{id}/approve")
    public ResponseEntity<OutpassResponse> approveOutpass(
            @AuthenticationPrincipal UserDetails userDetails,
            @PathVariable Long id,
            @RequestBody(required = false) OutpassDecisionRequest req) {
        User user = getAuthenticatedUser(userDetails);
        String remarks = (req != null) ? req.remarks() : null;
        return ResponseEntity.ok(outpassService.approveOutpass(id, remarks, user));
    }

    @PostMapping("/outpasses/{id}/reject")
    public ResponseEntity<OutpassResponse> rejectOutpass(
            @AuthenticationPrincipal UserDetails userDetails,
            @PathVariable Long id,
            @RequestBody(required = false) OutpassDecisionRequest req) {
        User user = getAuthenticatedUser(userDetails);
        String remarks = (req != null) ? req.remarks() : null;
        return ResponseEntity.ok(outpassService.rejectOutpass(id, remarks, user));
    }
}
