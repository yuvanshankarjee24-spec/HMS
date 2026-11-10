package com.hostel.controller;

import com.hostel.dto.ParcelDtos.AdminParcelReportResponse;
import com.hostel.dto.ParcelDtos.ParcelResponse;
import com.hostel.service.ParcelService;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;

@RestController
@RequestMapping("/api/admin/parcels")
@PreAuthorize("hasRole('ADMIN')")
public class AdminParcelController {

    private final ParcelService parcelService;

    public AdminParcelController(ParcelService parcelService) {
        this.parcelService = parcelService;
    }

    @GetMapping("/reports")
    public ResponseEntity<AdminParcelReportResponse> getReport() {
        return ResponseEntity.ok(parcelService.getAdminReport());
    }

    @GetMapping
    public ResponseEntity<Page<ParcelResponse>> listAll(
            @RequestParam(value = "status", required = false) String status,
            @RequestParam(value = "courier", required = false) String courier,
            @RequestParam(value = "fromDate", required = false) LocalDate fromDate,
            @RequestParam(value = "toDate", required = false) LocalDate toDate,
            @RequestParam(value = "query", required = false) String query,
            @RequestParam(value = "page", defaultValue = "0") int page,
            @RequestParam(value = "size", defaultValue = "10") int size) {
        return ResponseEntity.ok(parcelService.searchParcels(
                status, courier, fromDate, toDate, query, PageRequest.of(page, size)));
    }
}

