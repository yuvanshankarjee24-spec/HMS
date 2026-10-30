package com.hostel.controller;

import com.hostel.dto.AdminDtos.*;
import com.hostel.entity.User;
import com.hostel.repository.UserRepository;
import com.hostel.service.AdminService;
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

import java.util.Map;

@RestController
@RequestMapping("/api/admin")
@PreAuthorize("hasRole('ADMIN')")
public class AdminController {

    private final AdminService adminService;
    private final UserRepository userRepository;

    public AdminController(AdminService adminService, UserRepository userRepository) {
        this.adminService = adminService;
        this.userRepository = userRepository;
    }

    private User getAuthenticatedUser(UserDetails userDetails) {
        return userRepository.findByEmail(userDetails.getUsername())
                .orElseThrow(() -> new RuntimeException("Authenticated admin not found"));
    }

    @PostMapping("/students")
    public ResponseEntity<UserCreationResponse> createStudent(
            @AuthenticationPrincipal UserDetails userDetails,
            @Valid @RequestBody CreateStudentRequest request) {
        User user = getAuthenticatedUser(userDetails);
        return ResponseEntity.ok(adminService.createStudent(request, user));
    }

    @PostMapping("/staff")
    public ResponseEntity<UserCreationResponse> createStaff(
            @AuthenticationPrincipal UserDetails userDetails,
            @Valid @RequestBody CreateStaffRequest request) {
        User user = getAuthenticatedUser(userDetails);
        return ResponseEntity.ok(adminService.createStaff(request, user));
    }

    @GetMapping("/users")
    public ResponseEntity<Page<AdminUserItem>> getUsers(
            @RequestParam(value = "role", required = false) String role,
            @RequestParam(value = "query", required = false) String query,
            @RequestParam(value = "page", defaultValue = "0") int page,
            @RequestParam(value = "size", defaultValue = "10") int size) {
        Pageable pageable = PageRequest.of(page, size, Sort.by("createdAt").descending());
        return ResponseEntity.ok(adminService.searchUsers(role, query, pageable));
    }

    @PatchMapping("/users/{id}/status")
    public ResponseEntity<Map<String, String>> updateUserStatus(
            @AuthenticationPrincipal UserDetails userDetails,
            @PathVariable Long id,
            @Valid @RequestBody UpdateUserStatusRequest request) {
        User user = getAuthenticatedUser(userDetails);
        adminService.updateUserStatus(id, request, user);
        return ResponseEntity.ok(Map.of("message", "User status updated to " + request.status()));
    }

    @PostMapping("/users/{id}/reset-password")
    public ResponseEntity<Map<String, String>> resetPassword(
            @AuthenticationPrincipal UserDetails userDetails,
            @PathVariable Long id) {
        User user = getAuthenticatedUser(userDetails);
        String tempPass = adminService.resetPassword(id, user);
        return ResponseEntity.ok(Map.of(
                "temporaryPassword", tempPass,
                "message", "Password reset successfully. The user must change their password on next login."
        ));
    }
}
