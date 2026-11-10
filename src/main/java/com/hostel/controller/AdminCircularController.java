package com.hostel.controller;

import com.hostel.dto.CircularDtos.CircularResponse;
import com.hostel.service.CircularService;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/admin/circulars")
@PreAuthorize("hasRole('ADMIN')")
public class AdminCircularController {

    private final CircularService circularService;

    public AdminCircularController(CircularService circularService) {
        this.circularService = circularService;
    }

    @GetMapping
    public ResponseEntity<Page<CircularResponse>> listAll(
            @RequestParam(value = "status", required = false) String status,
            @RequestParam(value = "type", required = false) String type,
            @RequestParam(value = "query", required = false) String query,
            @RequestParam(value = "page", defaultValue = "0") int page,
            @RequestParam(value = "size", defaultValue = "10") int size) {
        return ResponseEntity.ok(circularService.listForStaff(status, type, query, PageRequest.of(page, size)));
    }
}

