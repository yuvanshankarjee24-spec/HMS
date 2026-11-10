package com.hostel.controller;

import com.hostel.dto.DigitalAttendanceDtos.SecurityOutpassScanResponse;
import com.hostel.entity.User;
import com.hostel.repository.UserRepository;
import com.hostel.service.OutpassService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/security/outpasses")
@PreAuthorize("hasRole('SECURITY')")
public class SecurityGateController {

    private final OutpassService outpassService;
    private final UserRepository userRepository;

    public SecurityGateController(OutpassService outpassService, UserRepository userRepository) {
        this.outpassService = outpassService;
        this.userRepository = userRepository;
    }

    private User getActor(UserDetails userDetails) {
        return userRepository.findByEmail(userDetails.getUsername())
                .orElseThrow(() -> new RuntimeException("Authenticated security user not found"));
    }

    @GetMapping("/verify")
    public ResponseEntity<SecurityOutpassScanResponse> verifyOutpass(@RequestParam("query") String query) {
        return ResponseEntity.ok(outpassService.scanOutpassForSecurity(query));
    }

    @PostMapping("/{id}/exit")
    public ResponseEntity<SecurityOutpassScanResponse> markExit(
            @AuthenticationPrincipal UserDetails userDetails,
            @PathVariable Long id) {
        return ResponseEntity.ok(outpassService.markSecurityExit(id, getActor(userDetails)));
    }
}

