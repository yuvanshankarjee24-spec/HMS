package com.hostel.controller;

import com.hostel.dto.CircularDtos.*;
import com.hostel.entity.User;
import com.hostel.repository.UserRepository;
import com.hostel.service.CircularService;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/warden/circulars")
@PreAuthorize("hasAnyRole('WARDEN', 'ADMIN')")
public class WardenCircularController {

    private final CircularService circularService;
    private final UserRepository userRepository;

    public WardenCircularController(CircularService circularService, UserRepository userRepository) {
        this.circularService = circularService;
        this.userRepository = userRepository;
    }

    private User getActor(UserDetails ud) {
        return userRepository.findByEmail(ud.getUsername())
                .orElseThrow(() -> new RuntimeException("Authenticated user not found"));
    }

    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<CircularResponse> create(
            @AuthenticationPrincipal UserDetails ud,
            @RequestParam("type") String type,
            @RequestParam("title") String title,
            @RequestParam("description") String description,
            @RequestParam(value = "rulesAndRegulations", required = false) String rulesAndRegulations,
            @RequestParam(value = "eventDateTime", required = false) LocalDateTime eventDateTime,
            @RequestParam(value = "venue", required = false) String venue,
            @RequestParam(value = "priority", defaultValue = "NORMAL") String priority,
            @RequestParam(value = "targetScope", defaultValue = "BLOCK") String targetScope,
            @RequestParam(value = "hostelBlock", required = false) String hostelBlock,
            @RequestParam(value = "department", required = false) String department,
            @RequestParam(value = "studyYear", required = false) Integer studyYear,
            @RequestParam(value = "studentIds", required = false) List<Long> studentIds,
            @RequestParam(value = "requireAcknowledgement", defaultValue = "false") boolean requireAcknowledgement,
            @RequestParam(value = "publishAction", defaultValue = "PUBLISH") String publishAction,
            @RequestParam(value = "scheduledPublishAt", required = false) LocalDateTime scheduledPublishAt,
            @RequestParam(value = "expiresAt", required = false) LocalDateTime expiresAt,
            @RequestParam(value = "files", required = false) MultipartFile[] files) {
        return ResponseEntity.ok(circularService.createCircular(
                getActor(ud), type, title, description, rulesAndRegulations,
                eventDateTime, venue, priority, targetScope, hostelBlock,
                department, studyYear, studentIds, requireAcknowledgement,
                publishAction, scheduledPublishAt, expiresAt, files
        ));
    }

    @PutMapping("/{id}")
    public ResponseEntity<CircularResponse> update(
            @AuthenticationPrincipal UserDetails ud,
            @PathVariable Long id,
            @RequestParam(value = "title", required = false) String title,
            @RequestParam(value = "description", required = false) String description,
            @RequestParam(value = "rulesAndRegulations", required = false) String rulesAndRegulations,
            @RequestParam(value = "eventDateTime", required = false) LocalDateTime eventDateTime,
            @RequestParam(value = "venue", required = false) String venue,
            @RequestParam(value = "priority", required = false) String priority,
            @RequestParam(value = "requireAcknowledgement", required = false) Boolean requireAcknowledgement,
            @RequestParam(value = "expiresAt", required = false) LocalDateTime expiresAt,
            @RequestParam(value = "editNote", required = false) String editNote) {
        return ResponseEntity.ok(circularService.updateCircular(
                getActor(ud), id, title, description, rulesAndRegulations,
                eventDateTime, venue, priority, requireAcknowledgement, expiresAt, editNote
        ));
    }

    @PostMapping("/{id}/publish")
    public ResponseEntity<CircularResponse> publishNow(@AuthenticationPrincipal UserDetails ud, @PathVariable Long id) {
        return ResponseEntity.ok(circularService.publishNow(getActor(ud), id));
    }

    @PostMapping("/{id}/archive")
    public ResponseEntity<CircularResponse> archive(@AuthenticationPrincipal UserDetails ud, @PathVariable Long id) {
        return ResponseEntity.ok(circularService.archiveCircular(getActor(ud), id));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Map<String, String>> delete(@AuthenticationPrincipal UserDetails ud, @PathVariable Long id) {
        circularService.deleteCircular(getActor(ud), id);
        return ResponseEntity.ok(Map.of("message", "Circular deleted"));
    }

    @GetMapping
    public ResponseEntity<Page<CircularResponse>> list(
            @RequestParam(value = "status", required = false) String status,
            @RequestParam(value = "type", required = false) String type,
            @RequestParam(value = "query", required = false) String query,
            @RequestParam(value = "page", defaultValue = "0") int page,
            @RequestParam(value = "size", defaultValue = "10") int size) {
        return ResponseEntity.ok(circularService.listForStaff(status, type, query, PageRequest.of(page, size)));
    }

    @GetMapping("/{id}/tracking")
    public ResponseEntity<CircularTrackingResponse> tracking(@PathVariable Long id) {
        return ResponseEntity.ok(circularService.getTracking(id));
    }

    @PostMapping("/{id}/remind")
    public ResponseEntity<Map<String, Object>> sendReminder(@AuthenticationPrincipal UserDetails ud, @PathVariable Long id) {
        int sent = circularService.sendManualReminder(getActor(ud), id);
        return ResponseEntity.ok(Map.of("sentCount", sent, "message", "Sent reminder to " + sent + " student(s)"));
    }

    @GetMapping("/{id}/tracking/export")
    public ResponseEntity<byte[]> exportTrackingCsv(@PathVariable Long id) {
        String csv = circularService.exportTrackingCsv(id);
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType("text/csv"))
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"circular-" + id + "-tracking.csv\"")
                .body(csv.getBytes());
    }
}

