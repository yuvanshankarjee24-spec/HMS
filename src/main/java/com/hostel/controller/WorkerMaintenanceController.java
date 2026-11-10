package com.hostel.controller;

import com.hostel.dto.MaintenanceDtos.*;
import com.hostel.entity.User;
import com.hostel.repository.UserRepository;
import com.hostel.service.MaintenanceService;
import jakarta.validation.Valid;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/worker/maintenance")
@PreAuthorize("hasRole('WORKER')")
public class WorkerMaintenanceController {

    private final MaintenanceService maintenanceService;
    private final UserRepository userRepository;

    public WorkerMaintenanceController(MaintenanceService maintenanceService, UserRepository userRepository) {
        this.maintenanceService = maintenanceService;
        this.userRepository = userRepository;
    }

    private User getActor(UserDetails userDetails) {
        return userRepository.findByEmail(userDetails.getUsername())
                .orElseThrow(() -> new RuntimeException("Authenticated worker not found"));
    }

    @GetMapping("/stats")
    public ResponseEntity<WorkerStatsResponse> getStats(@AuthenticationPrincipal UserDetails userDetails) {
        return ResponseEntity.ok(maintenanceService.getWorkerStats(getActor(userDetails)));
    }

    @GetMapping
    public ResponseEntity<Page<MaintenanceSummaryResponse>> listAssignedTasks(
            @AuthenticationPrincipal UserDetails userDetails,
            @RequestParam(value = "status", required = false) String status,
            @RequestParam(value = "page", defaultValue = "0") int page,
            @RequestParam(value = "size", defaultValue = "10") int size) {
        Pageable pageable = PageRequest.of(page, size, Sort.by("createdAt").descending());
        return ResponseEntity.ok(maintenanceService.getWorkerRequests(getActor(userDetails), status, pageable));
    }

    @GetMapping("/{id}")
    public ResponseEntity<MaintenanceDetailResponse> getTaskDetail(
            @AuthenticationPrincipal UserDetails userDetails,
            @PathVariable Long id) {
        return ResponseEntity.ok(maintenanceService.getWorkerRequestDetail(getActor(userDetails), id));
    }

    @PostMapping("/{id}/start")
    public ResponseEntity<MaintenanceDetailResponse> startWork(
            @AuthenticationPrincipal UserDetails userDetails,
            @PathVariable Long id) {
        return ResponseEntity.ok(maintenanceService.startWork(getActor(userDetails), id));
    }

    @PostMapping("/{id}/need-materials")
    public ResponseEntity<MaintenanceDetailResponse> markNeedMaterials(
            @AuthenticationPrincipal UserDetails userDetails,
            @PathVariable Long id,
            @Valid @RequestBody NeedMaterialsRequest request) {
        return ResponseEntity.ok(maintenanceService.markNeedMaterials(getActor(userDetails), id, request));
    }

    @PostMapping(value = "/{id}/complete", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<MaintenanceDetailResponse> completeWork(
            @AuthenticationPrincipal UserDetails userDetails,
            @PathVariable Long id,
            @RequestParam(value = "remarks", required = false) String remarks,
            @RequestParam(value = "afterPhoto", required = false) MultipartFile afterPhoto) {
        return ResponseEntity.ok(maintenanceService.completeWork(getActor(userDetails), id, remarks, afterPhoto));
    }
}

