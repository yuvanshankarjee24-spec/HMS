package com.hostel.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.hostel.dto.RegistrationDtos.*;
import com.hostel.entity.User;
import com.hostel.service.AdminRegistrationService;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/admin/registration")
public class AdminRegistrationController {

    private final AdminRegistrationService registrationService;
    private final ObjectMapper objectMapper;

    public AdminRegistrationController(AdminRegistrationService registrationService, ObjectMapper objectMapper) {
        this.registrationService = registrationService;
        this.objectMapper = objectMapper;
    }

    @GetMapping("/hostels")
    @PreAuthorize("hasAnyRole('ADMIN', 'WARDEN')")
    public ResponseEntity<?> getHostels() {
        return ResponseEntity.ok(registrationService.getHostels());
    }

    @PostMapping(value = "/student", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize("hasAnyRole('ADMIN', 'WARDEN')")
    public ResponseEntity<RegistrationSuccessResponse> registerStudent(
            @RequestPart("data") String dataJson,
            @RequestPart(value = "photo", required = false) MultipartFile photo,
            @RequestPart(value = "idProof", required = false) MultipartFile idProof,
            @AuthenticationPrincipal User currentUser) throws Exception {

        StudentRegisterRequest req = objectMapper.readValue(dataJson, StudentRegisterRequest.class);
        RegistrationSuccessResponse resp = registrationService.registerStudent(req, photo, idProof, currentUser);
        return ResponseEntity.status(201).body(resp);
    }

    @PostMapping(value = "/staff", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<RegistrationSuccessResponse> registerStaff(
            @RequestPart("data") String dataJson,
            @RequestPart(value = "photo", required = false) MultipartFile photo,
            @AuthenticationPrincipal User currentUser) throws Exception {

        StaffRegisterRequest req = objectMapper.readValue(dataJson, StaffRegisterRequest.class);
        RegistrationSuccessResponse resp = registrationService.registerStaff(req, photo, currentUser);
        return ResponseEntity.status(201).body(resp);
    }

    @PostMapping(value = "/bulk", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize("hasAnyRole('ADMIN', 'WARDEN')")
    public ResponseEntity<BulkRegistrationResponse> bulkRegister(
            @RequestParam("file") MultipartFile file,
            @RequestParam(value = "type", defaultValue = "STUDENT") String type,
            @AuthenticationPrincipal User currentUser) {

        BulkRegistrationResponse resp = registrationService.bulkRegister(file, type, currentUser);
        return ResponseEntity.ok(resp);
    }

    @PostMapping("/reset-password/{userId}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<?> resetPassword(@PathVariable Long userId,
                                           @AuthenticationPrincipal User currentUser) {
        registrationService.resetPasswordToRollNumber(userId, currentUser);
        return ResponseEntity.ok().body("{\"message\":\"Password reset to roll number successfully.\"}");
    }

    @GetMapping("/sample-csv")
    @PreAuthorize("hasAnyRole('ADMIN', 'WARDEN')")
    public ResponseEntity<byte[]> downloadSampleCsv(@RequestParam(value = "type", defaultValue = "student") String type) {
        String csv = registrationService.generateSampleCsv(type);
        byte[] bytes = csv.getBytes(java.nio.charset.StandardCharsets.UTF_8);

        String filename = "sample_" + type.toLowerCase() + "_registration.csv";
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + filename + "\"")
                .contentType(MediaType.parseMediaType("text/csv; charset=UTF-8"))
                .body(bytes);
    }
}
