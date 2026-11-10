package com.hostel.controller;

import com.hostel.dto.ParcelDtos.ParcelResponse;
import com.hostel.entity.User;
import com.hostel.repository.UserRepository;
import com.hostel.service.ParcelService;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/student/parcels")
@PreAuthorize("hasRole('STUDENT')")
public class StudentParcelController {

    private final ParcelService parcelService;
    private final UserRepository userRepository;

    public StudentParcelController(ParcelService parcelService, UserRepository userRepository) {
        this.parcelService = parcelService;
        this.userRepository = userRepository;
    }

    private User getActor(UserDetails userDetails) {
        return userRepository.findByEmail(userDetails.getUsername())
                .orElseThrow(() -> new RuntimeException("Authenticated student not found"));
    }

    @GetMapping
    public ResponseEntity<Page<ParcelResponse>> getMyParcels(
            @AuthenticationPrincipal UserDetails userDetails,
            @RequestParam(value = "tab", defaultValue = "WAITING") String tab,
            @RequestParam(value = "page", defaultValue = "0") int page,
            @RequestParam(value = "size", defaultValue = "10") int size) {
        return ResponseEntity.ok(parcelService.getStudentParcels(getActor(userDetails), tab, PageRequest.of(page, size)));
    }

    @GetMapping("/waiting-count")
    public ResponseEntity<Map<String, Long>> getWaitingCount(@AuthenticationPrincipal UserDetails userDetails) {
        return ResponseEntity.ok(Map.of("waitingCount", parcelService.getStudentWaitingCount(getActor(userDetails))));
    }

    @GetMapping("/{id}")
    public ResponseEntity<ParcelResponse> getParcelDetail(
            @AuthenticationPrincipal UserDetails userDetails,
            @PathVariable Long id) {
        return ResponseEntity.ok(parcelService.getStudentParcelDetail(getActor(userDetails), id));
    }
}

