package com.hostel.controller;

import com.hostel.dto.DigitalAttendanceDtos.*;
import com.hostel.entity.User;
import com.hostel.repository.UserRepository;
import com.hostel.service.DigitalAttendanceService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/admin/digital-attendance")
@PreAuthorize("hasRole('ADMIN')")
public class AdminDigitalAttendanceController {

    private final DigitalAttendanceService digitalAttendanceService;
    private final UserRepository userRepository;

    public AdminDigitalAttendanceController(DigitalAttendanceService digitalAttendanceService,
                                            UserRepository userRepository) {
        this.digitalAttendanceService = digitalAttendanceService;
        this.userRepository = userRepository;
    }

    private User getActor(UserDetails userDetails) {
        return userRepository.findByEmail(userDetails.getUsername())
                .orElseThrow(() -> new RuntimeException("Authenticated admin not found"));
    }

    @GetMapping("/analytics")
    public ResponseEntity<AdminAttendanceAnalyticsResponse> getAnalytics() {
        return ResponseEntity.ok(digitalAttendanceService.getAdminAnalytics());
    }

    @GetMapping("/sessions")
    public ResponseEntity<List<AttendanceSessionResponse>> listSessions() {
        return ResponseEntity.ok(digitalAttendanceService.listAllSessions());
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
}

