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
@RequestMapping("/api/warden")
@PreAuthorize("hasAnyRole('WARDEN', 'ADMIN')")
public class WardenMaintenanceController {

    private final MaintenanceService maintenanceService;
    private final WorkerManagementService workerService;
    private final UserRepository userRepository;

    public WardenMaintenanceController(MaintenanceService maintenanceService,
                                       WorkerManagementService workerService,
                                       UserRepository userRepository) {
        this.maintenanceService = maintenanceService;
        this.workerService = workerService;
        this.userRepository = userRepository;
    }

    private User getActor(UserDetails userDetails) {
        return userRepository.findByEmail(userDetails.getUsername())
                .orElseThrow(() -> new RuntimeException("Authenticated user not found"));
    }

    @GetMapping("/maintenance/stats")
    public ResponseEntity<WardenStatsResponse> getStats() {
        return ResponseEntity.ok(maintenanceService.getWardenStats());
    }

    @GetMapping("/maintenance")
    public ResponseEntity<Page<MaintenanceSummaryResponse>> listRequests(
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

    @GetMapping("/maintenance/{id}")
    public ResponseEntity<MaintenanceDetailResponse> getDetail(@PathVariable Long id) {
        return ResponseEntity.ok(maintenanceService.getRequestDetailForStaff(id));
    }

    @PostMapping("/maintenance/{id}/assign")
    public ResponseEntity<MaintenanceDetailResponse> assignWorker(
            @AuthenticationPrincipal UserDetails userDetails,
            @PathVariable Long id,
            @Valid @RequestBody AssignWorkerRequest request) {
        return ResponseEntity.ok(maintenanceService.assignWorker(getActor(userDetails), id, request));
    }

    @PostMapping("/maintenance/{id}/reject")
    public ResponseEntity<MaintenanceDetailResponse> rejectRequest(
            @AuthenticationPrincipal UserDetails userDetails,
            @PathVariable Long id,
            @Valid @RequestBody RejectMaintenanceRequest request) {
        return ResponseEntity.ok(maintenanceService.rejectRequest(getActor(userDetails), id, request));
    }

    @PatchMapping("/maintenance/{id}/priority")
    public ResponseEntity<MaintenanceDetailResponse> changePriority(
            @AuthenticationPrincipal UserDetails userDetails,
            @PathVariable Long id,
            @Valid @RequestBody ChangePriorityRequest request) {
        return ResponseEntity.ok(maintenanceService.changePriority(getActor(userDetails), id, request));
    }

    @PostMapping("/maintenance/{id}/close")
    public ResponseEntity<MaintenanceDetailResponse> closeRequest(
            @AuthenticationPrincipal UserDetails userDetails,
            @PathVariable Long id,
            @RequestBody(required = false) CloseMaintenanceRequest request) {
        return ResponseEntity.ok(maintenanceService.closeByWarden(getActor(userDetails), id, request));
    }

    @PostMapping("/maintenance/{id}/reopen")
    public ResponseEntity<MaintenanceDetailResponse> reopenRequest(
            @AuthenticationPrincipal UserDetails userDetails,
            @PathVariable Long id,
            @Valid @RequestBody ReopenMaintenanceRequest request) {
        return ResponseEntity.ok(maintenanceService.reopenByWarden(getActor(userDetails), id, request));
    }

    // Worker Management for Warden & Admin
    @GetMapping("/workers")
    public ResponseEntity<List<WorkerResponse>> listWorkers(
            @RequestParam(value = "workerType", required = false) String workerType,
            @RequestParam(value = "active", required = false) Boolean active,
            @RequestParam(value = "query", required = false) String query) {
        return ResponseEntity.ok(workerService.listWorkers(workerType, active, query));
    }

    @GetMapping("/workers/matching")
    public ResponseEntity<List<WorkerResponse>> getMatchingWorkers(
            @RequestParam(value = "category", required = false) String category) {
        return ResponseEntity.ok(workerService.listActiveWorkersForCategory(category));
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

