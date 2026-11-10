package com.hostel.controller;

import com.hostel.service.MessTokenService;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/mess")
public class MessAttachmentController {

    private final MessTokenService messService;

    public MessAttachmentController(MessTokenService messService) {
        this.messService = messService;
    }

    @GetMapping("/food-photos/{id}")
    public ResponseEntity<Resource> getFoodPhoto(@PathVariable Long id) {
        Resource r = messService.loadFoodPhoto(id);
        return ResponseEntity.ok()
                .contentType(MediaType.IMAGE_JPEG)
                .header(HttpHeaders.CACHE_CONTROL, "max-age=3600")
                .body(r);
    }
}

