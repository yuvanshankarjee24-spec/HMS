package com.hostel.controller;

import com.hostel.dto.DigitalAttendanceDtos.*;
import com.hostel.entity.DigitalAttendanceRecord;
import com.hostel.entity.User;
import com.hostel.repository.UserRepository;
import com.hostel.service.DigitalAttendanceService;
import jakarta.validation.Valid;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/warden/digital-attendance")
@PreAuthorize("hasAnyRole('WARDEN', 'ADMIN')")
public class WardenDigitalAttendanceController {

    private final DigitalAttendanceService digitalAttendanceService;
    private final UserRepository userRepository;

    public WardenDigitalAttendanceController(DigitalAttendanceService digitalAttendanceService,
                                             UserRepository userRepository) {
        this.digitalAttendanceService = digitalAttendanceService;
        this.userRepository = userRepository;
    }

    private User getActor(UserDetails userDetails) {
        return userRepository.findByEmail(userDetails.getUsername())
                .orElseThrow(() -> new RuntimeException("Authenticated user not found"));
    }

    @GetMapping("/summary")
    public ResponseEntity<WardenDigitalSummaryResponse> getSummary(
            @RequestParam(value = "date", required = false) LocalDate date) {
        return ResponseEntity.ok(digitalAttendanceService.getWardenSummary(date));
    }

    @GetMapping("/records")
    public ResponseEntity<Page<MissedOrPendingStudentItem>> getRecordsByStatus(
            @RequestParam(value = "status", defaultValue = "MISSED") String status,
            @RequestParam(value = "date", required = false) LocalDate date,
            @RequestParam(value = "sessionId", required = false) Long sessionId,
            @RequestParam(value = "block", required = false) String block,
            @RequestParam(value = "room", required = false) String room,
            @RequestParam(value = "query", required = false) String query,
            @RequestParam(value = "page", defaultValue = "0") int page,
            @RequestParam(value = "size", defaultValue = "10") int size) {
        DigitalAttendanceRecord.Status st = DigitalAttendanceRecord.Status.valueOf(status.trim().toUpperCase());
        return ResponseEntity.ok(digitalAttendanceService.listByStatusFiltered(
                st, date, sessionId, block, room, query, PageRequest.of(page, size)));
    }

    @GetMapping("/locked")
    public ResponseEntity<List<MissedOrPendingStudentItem>> getLockedStudents(
            @RequestParam(value = "query", required = false) String query) {
        return ResponseEntity.ok(digitalAttendanceService.listLockedStudents(query));
    }

    @PostMapping("/unlock/{studentId}")
    public ResponseEntity<Map<String, String>> unlockStudent(
            @AuthenticationPrincipal UserDetails userDetails,
            @PathVariable Long studentId,
            @Valid @RequestBody UnlockOutpassRequest request) {
        digitalAttendanceService.unlockOutpass(studentId, request.reason(), getActor(userDetails));
        return ResponseEntity.ok(Map.of("message", "Outpass unlocked successfully"));
    }

    @PostMapping("/unlock-bulk")
    public ResponseEntity<Map<String, Object>> bulkUnlock(
            @AuthenticationPrincipal UserDetails userDetails,
            @Valid @RequestBody BulkUnlockRequest request) {
        int count = digitalAttendanceService.bulkUnlockOutpasses(request.studentIds(), request.reason(), getActor(userDetails));
        return ResponseEntity.ok(Map.of("unlockedCount", count, "message", "Unlocked " + count + " student(s)"));
    }

    @PostMapping("/manual-mark")
    public ResponseEntity<MissedOrPendingStudentItem> manualMark(
            @AuthenticationPrincipal UserDetails userDetails,
            @Valid @RequestBody ManualMarkAttendanceRequest request) {
        return ResponseEntity.ok(digitalAttendanceService.manualMarkPresent(request, getActor(userDetails)));
    }

    @PostMapping("/relock/{studentId}")
    public ResponseEntity<Map<String, String>> relockStudent(
            @AuthenticationPrincipal UserDetails userDetails,
            @PathVariable Long studentId,
            @Valid @RequestBody RelockOutpassRequest request) {
        digitalAttendanceService.relockOutpass(studentId, request.reason(), getActor(userDetails));
        return ResponseEntity.ok(Map.of("message", "Outpass re-locked successfully"));
    }

    @GetMapping("/sessions")
    public ResponseEntity<List<AttendanceSessionResponse>> listSessions() {
        return ResponseEntity.ok(digitalAttendanceService.listAllSessions());
    }

    @PostMapping("/sessions")
    public ResponseEntity<AttendanceSessionResponse> createSession(
            @AuthenticationPrincipal UserDetails userDetails,
            @Valid @RequestBody AttendanceSessionRequest request) {
        return ResponseEntity.ok(digitalAttendanceService.createSession(request, getActor(userDetails)));
    }

    @PutMapping("/sessions/{id}")
    public ResponseEntity<AttendanceSessionResponse> updateSession(
            @AuthenticationPrincipal UserDetails userDetails,
            @PathVariable Long id,
            @Valid @RequestBody AttendanceSessionRequest request) {
        return ResponseEntity.ok(digitalAttendanceService.updateSession(id, request, getActor(userDetails)));
    }

    @PatchMapping("/sessions/{id}/toggle")
    public ResponseEntity<AttendanceSessionResponse> toggleSession(
            @AuthenticationPrincipal UserDetails userDetails,
            @PathVariable Long id,
            @RequestParam("enabled") boolean enabled) {
        return ResponseEntity.ok(digitalAttendanceService.toggleSession(id, enabled, getActor(userDetails)));
    }

    @GetMapping("/defaults")
    public ResponseEntity<AttendanceDefaultsDto> getDefaults() {
        return ResponseEntity.ok(digitalAttendanceService.getDefaults());
    }

    @PutMapping("/defaults")
    public ResponseEntity<AttendanceDefaultsDto> updateDefaults(
            @AuthenticationPrincipal UserDetails userDetails,
            @Valid @RequestBody AttendanceDefaultsDto request) {
        return ResponseEntity.ok(digitalAttendanceService.updateDefaults(request, getActor(userDetails)));
    }

    @GetMapping("/report")
    public ResponseEntity<List<AttendanceReportRow>> getReport(
            @RequestParam(value = "fromDate", required = false) LocalDate fromDate,
            @RequestParam(value = "toDate", required = false) LocalDate toDate) {
        return ResponseEntity.ok(digitalAttendanceService.getAttendanceReport(fromDate, toDate));
    }

    @GetMapping("/report/export")
    public ResponseEntity<byte[]> exportCsv(
            @RequestParam(value = "fromDate", required = false) LocalDate fromDate,
            @RequestParam(value = "toDate", required = false) LocalDate toDate) {
        String csv = digitalAttendanceService.exportAttendanceReportCsv(fromDate, toDate);
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType("text/csv"))
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"attendance-report.csv\"")
                .body(csv.getBytes());
    }
}

