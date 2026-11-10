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

import java.time.LocalDate;
import java.util.Map;

@RestController
@RequestMapping("/api/student/maintenance")
@PreAuthorize("hasRole('STUDENT')")
public class StudentMaintenanceController {

    private final MaintenanceService maintenanceService;
    private final UserRepository userRepository;

    public StudentMaintenanceController(MaintenanceService maintenanceService, UserRepository userRepository) {
        this.maintenanceService = maintenanceService;
        this.userRepository = userRepository;
    }

    private User getActor(UserDetails userDetails) {
        return userRepository.findByEmail(userDetails.getUsername())
                .orElseThrow(() -> new RuntimeException("Authenticated user not found"));
    }

    @GetMapping("/default-location")
    public ResponseEntity<Map<String, String>> getDefaultLocation(@AuthenticationPrincipal UserDetails userDetails) {
        return ResponseEntity.ok(maintenanceService.getStudentDefaultLocation(getActor(userDetails)));
    }

    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<MaintenanceDetailResponse> createRequest(
            @AuthenticationPrincipal UserDetails userDetails,
            @RequestParam("category") String category,
            @RequestParam("title") String title,
            @RequestParam("description") String description,
            @RequestParam("hostelBlock") String hostelBlock,
            @RequestParam("roomNumber") String roomNumber,
            @RequestParam(value = "priority", defaultValue = "MEDIUM") String priority,
            @RequestParam(value = "preferredTimeSlot", required = false) String preferredTimeSlot,
            @RequestParam(value = "files", required = false) MultipartFile[] files) {
        return ResponseEntity.ok(maintenanceService.createRequest(
                getActor(userDetails), category, title, description,
                hostelBlock, roomNumber, priority, preferredTimeSlot, files
        ));
    }

    @GetMapping
    public ResponseEntity<Page<MaintenanceSummaryResponse>> listMyRequests(
            @AuthenticationPrincipal UserDetails userDetails,
            @RequestParam(value = "status", required = false) String status,
            @RequestParam(value = "category", required = false) String category,
            @RequestParam(value = "fromDate", required = false) LocalDate fromDate,
            @RequestParam(value = "toDate", required = false) LocalDate toDate,
            @RequestParam(value = "page", defaultValue = "0") int page,
            @RequestParam(value = "size", defaultValue = "10") int size) {
        Pageable pageable = PageRequest.of(page, size, Sort.by("createdAt").descending());
        return ResponseEntity.ok(maintenanceService.getStudentRequests(
                getActor(userDetails), status, category, fromDate, toDate, pageable));
    }

    @GetMapping("/{id}")
    public ResponseEntity<MaintenanceDetailResponse> getMyRequestDetail(
            @AuthenticationPrincipal UserDetails userDetails,
            @PathVariable Long id) {
        return ResponseEntity.ok(maintenanceService.getStudentRequestDetail(getActor(userDetails), id));
    }

    @PostMapping("/{id}/cancel")
    public ResponseEntity<MaintenanceDetailResponse> cancelRequest(
            @AuthenticationPrincipal UserDetails userDetails,
            @PathVariable Long id) {
        return ResponseEntity.ok(maintenanceService.cancelRequest(getActor(userDetails), id));
    }

    @PostMapping("/{id}/confirm-resolved")
    public ResponseEntity<MaintenanceDetailResponse> confirmResolved(
            @AuthenticationPrincipal UserDetails userDetails,
            @PathVariable Long id,
            @Valid @RequestBody ConfirmResolvedRequest request) {
        return ResponseEntity.ok(maintenanceService.confirmResolved(getActor(userDetails), id, request));
    }

    @PostMapping("/{id}/reopen")
    public ResponseEntity<MaintenanceDetailResponse> reopenRequest(
            @AuthenticationPrincipal UserDetails userDetails,
            @PathVariable Long id,
            @Valid @RequestBody ReopenMaintenanceRequest request) {
        return ResponseEntity.ok(maintenanceService.reopenByStudent(getActor(userDetails), id, request));
    }
}

