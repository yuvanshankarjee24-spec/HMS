package com.hostel.controller;

import com.hostel.dto.DigitalAttendanceDtos.*;
import com.hostel.entity.User;
import com.hostel.repository.UserRepository;
import com.hostel.service.DigitalAttendanceService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/student/digital-attendance")
@PreAuthorize("hasRole('STUDENT')")
public class StudentDigitalAttendanceController {

    private final DigitalAttendanceService digitalAttendanceService;
    private final UserRepository userRepository;

    public StudentDigitalAttendanceController(DigitalAttendanceService digitalAttendanceService,
                                              UserRepository userRepository) {
        this.digitalAttendanceService = digitalAttendanceService;
        this.userRepository = userRepository;
    }

    private User getActor(UserDetails userDetails) {
        return userRepository.findByEmail(userDetails.getUsername())
                .orElseThrow(() -> new RuntimeException("Authenticated student not found"));
    }

    @GetMapping("/today")
    public ResponseEntity<StudentTodayAttendanceResponse> getToday(
            @AuthenticationPrincipal UserDetails userDetails) {
        return ResponseEntity.ok(digitalAttendanceService.getStudentToday(getActor(userDetails)));
    }

    @PostMapping("/mark")
    public ResponseEntity<AttendanceHistoryItem> markAttendance(
            @AuthenticationPrincipal UserDetails userDetails,
            @Valid @RequestBody MarkDigitalAttendanceRequest request,
            HttpServletRequest httpRequest) {
        String userAgent = httpRequest.getHeader("User-Agent");
        String ip = httpRequest.getRemoteAddr();
        return ResponseEntity.ok(digitalAttendanceService.markAttendance(
                getActor(userDetails), request, userAgent, ip));
    }

    @GetMapping("/history")
    public ResponseEntity<StudentAttendanceHistoryResponse> getHistory(
            @AuthenticationPrincipal UserDetails userDetails,
            @RequestParam(value = "year", required = false) Integer year,
            @RequestParam(value = "month", required = false) Integer month,
            @RequestParam(value = "page", defaultValue = "0") int page,
            @RequestParam(value = "size", defaultValue = "15") int size) {
        return ResponseEntity.ok(digitalAttendanceService.getStudentHistory(
                getActor(userDetails), year, month, PageRequest.of(page, size)));
    }
}

