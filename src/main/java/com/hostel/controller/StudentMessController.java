package com.hostel.controller;

import com.hostel.dto.MessDtos.*;
import com.hostel.entity.User;
import com.hostel.repository.UserRepository;
import com.hostel.service.MessTokenService;
import jakarta.validation.Valid;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;

@RestController
@RequestMapping("/api/student/mess")
@PreAuthorize("hasRole('STUDENT')")
public class StudentMessController {

    private final MessTokenService messService;
    private final UserRepository userRepository;

    public StudentMessController(MessTokenService messService, UserRepository userRepository) {
        this.messService = messService;
        this.userRepository = userRepository;
    }

    private User getActor(UserDetails ud) {
        return userRepository.findByEmail(ud.getUsername())
                .orElseThrow(() -> new RuntimeException("Authenticated student not found"));
    }

    @GetMapping("/periods")
    public ResponseEntity<List<PeriodConfigDto>> getPeriodConfigs() {
        return ResponseEntity.ok(messService.listPeriodConfigs());
    }

    @GetMapping("/menu")
    public ResponseEntity<List<MenuScheduleResponse>> getMenu(
            @RequestParam("date") LocalDate date,
            @RequestParam("period") String period) {
        return ResponseEntity.ok(messService.getMenuForDateAndPeriod(date, period));
    }

    @PostMapping("/book")
    public ResponseEntity<MessTokenResponse> bookToken(
            @AuthenticationPrincipal UserDetails ud,
            @Valid @RequestBody BookTokenRequest request) {
        return ResponseEntity.ok(messService.bookToken(getActor(ud), request));
    }

    @GetMapping("/tokens")
    public ResponseEntity<Page<MessTokenResponse>> myTokens(
            @AuthenticationPrincipal UserDetails ud,
            @RequestParam(value = "tab", defaultValue = "UPCOMING") String tab,
            @RequestParam(value = "page", defaultValue = "0") int page,
            @RequestParam(value = "size", defaultValue = "10") int size) {
        return ResponseEntity.ok(messService.getStudentTokens(getActor(ud), tab, PageRequest.of(page, size)));
    }

    @PostMapping("/tokens/{id}/cancel")
    public ResponseEntity<MessTokenResponse> cancelToken(
            @AuthenticationPrincipal UserDetails ud,
            @PathVariable Long id) {
        return ResponseEntity.ok(messService.cancelToken(getActor(ud), id));
    }
}

