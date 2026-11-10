package com.hostel.controller;

import com.hostel.entity.User;
import com.hostel.repository.UserRepository;
import com.hostel.service.MaintenanceService;
import com.hostel.service.ParcelService;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/parcels")
public class ParcelAttachmentController {

    private final ParcelService parcelService;
    private final UserRepository userRepository;

    public ParcelAttachmentController(ParcelService parcelService, UserRepository userRepository) {
        this.parcelService = parcelService;
        this.userRepository = userRepository;
    }

    @GetMapping("/attachments/{id}")
    public ResponseEntity<Resource> getAttachment(
            @AuthenticationPrincipal UserDetails userDetails,
            @PathVariable Long id) {
        User actor = userRepository.findByEmail(userDetails.getUsername())
                .orElseThrow(() -> new RuntimeException("Authenticated user not found"));
        MaintenanceService.LoadedAttachment loaded = parcelService.loadAttachment(actor, id);
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(loaded.contentType()))
                .header(HttpHeaders.CACHE_CONTROL, "max-age=3600")
                .header(HttpHeaders.CONTENT_DISPOSITION, "inline; filename=\"" + loaded.originalFilename() + "\"")
                .body(loaded.resource());
    }
}

