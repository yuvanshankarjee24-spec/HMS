package com.hostel.controller;

import com.hostel.dto.MaintenanceDtos.*;
import com.hostel.entity.User;
import com.hostel.repository.UserRepository;
import com.hostel.service.MaintenanceService;
import com.hostel.service.WorkerManagementService;
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

import java.time.LocalDate;
import java.util.List;

@RestController
@RequestMapping("/api/admin")
@PreAuthorize("hasRole('ADMIN')")
public class AdminMaintenanceController {

    private final MaintenanceService maintenanceService;
    private final WorkerManagementService workerService;
    private final UserRepository userRepository;

    public AdminMaintenanceController(MaintenanceService maintenanceService,
                                      WorkerManagementService workerService,
                                      UserRepository userRepository) {
        this.maintenanceService = maintenanceService;
        this.workerService = workerService;
        this.userRepository = userRepository;
    }

    private User getActor(UserDetails userDetails) {
        return userRepository.findByEmail(userDetails.getUsername())
                .orElseThrow(() -> new RuntimeException("Authenticated admin not found"));
    }

    @GetMapping("/maintenance/reports")
    public ResponseEntity<AdminReportResponse> getReports() {
        return ResponseEntity.ok(maintenanceService.getAdminReport());
    }

    @GetMapping("/maintenance")
    public ResponseEntity<Page<MaintenanceSummaryResponse>> listAllRequests(
            @RequestParam(value = "status", required = false) String status,
            @RequestParam(value = "category", required = false) String category,
            @RequestParam(value = "priority", required = false) String priority,
            @RequestParam(value = "block", required = false) String block,
            @RequestParam(value = "room", required = false) String room,
            @RequestParam(value = "fromDate", required = false) LocalDate fromDate,
            @RequestParam(value = "toDate", required = false) LocalDate toDate,
            @RequestParam(value = "query", required = false) String query,
            @RequestParam(value = "page", defaultValue = "0") int page,
            @RequestParam(value = "size", defaultValue = "10") int size) {
        Pageable pageable = PageRequest.of(page, size, Sort.by("createdAt").descending());
        return ResponseEntity.ok(maintenanceService.searchAllRequests(
                status, category, priority, block, room, fromDate, toDate, query, pageable));
    }

    @GetMapping("/workers")
    public ResponseEntity<List<WorkerResponse>> listWorkers(
            @RequestParam(value = "workerType", required = false) String workerType,
            @RequestParam(value = "active", required = false) Boolean active,
            @RequestParam(value = "query", required = false) String query) {
        return ResponseEntity.ok(workerService.listWorkers(workerType, active, query));
    }

    @PostMapping("/workers")
    public ResponseEntity<WorkerResponse> createWorker(
            @AuthenticationPrincipal UserDetails userDetails,
            @Valid @RequestBody CreateWorkerRequest request) {
        return ResponseEntity.ok(workerService.createWorker(request, getActor(userDetails)));
    }

    @PutMapping("/workers/{id}")
    public ResponseEntity<WorkerResponse> updateWorker(
            @AuthenticationPrincipal UserDetails userDetails,
            @PathVariable Long id,
            @Valid @RequestBody UpdateWorkerRequest request) {
        return ResponseEntity.ok(workerService.updateWorker(id, request, getActor(userDetails)));
    }

    @PatchMapping("/workers/{id}/active")
    public ResponseEntity<WorkerResponse> toggleWorkerActive(
            @AuthenticationPrincipal UserDetails userDetails,
            @PathVariable Long id,
            @RequestParam("active") boolean active) {
        return ResponseEntity.ok(workerService.setWorkerActive(id, active, getActor(userDetails)));
    }
}

