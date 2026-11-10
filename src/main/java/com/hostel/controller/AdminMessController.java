package com.hostel.controller;

import com.hostel.dto.MessDtos.AdminMessReportResponse;
import com.hostel.dto.MessDtos.MessTokenResponse;
import com.hostel.service.MessTokenService;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;

@RestController
@RequestMapping("/api/admin/mess")
@PreAuthorize("hasRole('ADMIN')")
public class AdminMessController {

    private final MessTokenService messService;

    public AdminMessController(MessTokenService messService) {
        this.messService = messService;
    }

    @GetMapping("/reports")
    public ResponseEntity<AdminMessReportResponse> getReport() {
        return ResponseEntity.ok(messService.getAdminReport());
    }

    @GetMapping("/tokens")
    public ResponseEntity<Page<MessTokenResponse>> listAllTokens(
            @RequestParam(value = "date", required = false) LocalDate date,
            @RequestParam(value = "period", required = false) String period,
            @RequestParam(value = "status", required = false) String status,
            @RequestParam(value = "query", required = false) String query,
            @RequestParam(value = "page", defaultValue = "0") int page,
            @RequestParam(value = "size", defaultValue = "10") int size) {
        return ResponseEntity.ok(messService.searchTokens(date, period, status, query, PageRequest.of(page, size)));
    }
}

