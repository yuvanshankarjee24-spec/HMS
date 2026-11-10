package com.hostel.controller;

import com.hostel.dto.CircularDtos.*;
import com.hostel.entity.User;
import com.hostel.repository.UserRepository;
import com.hostel.service.CircularService;
import jakarta.validation.Valid;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.Map;

@RestController
@RequestMapping("/api/student/circulars")
@PreAuthorize("hasRole('STUDENT')")
public class StudentCircularController {

    private final CircularService circularService;
    private final UserRepository userRepository;

    public StudentCircularController(CircularService circularService, UserRepository userRepository) {
        this.circularService = circularService;
        this.userRepository = userRepository;
    }

    private User getActor(UserDetails ud) {
        return userRepository.findByEmail(ud.getUsername())
                .orElseThrow(() -> new RuntimeException("Authenticated student not found"));
    }

    @GetMapping
    public ResponseEntity<Page<CircularResponse>> list(
            @AuthenticationPrincipal UserDetails ud,
            @RequestParam(value = "type", required = false) String type,
            @RequestParam(value = "fromDate", required = false) LocalDate fromDate,
            @RequestParam(value = "toDate", required = false) LocalDate toDate,
            @RequestParam(value = "readFilter", defaultValue = "ALL") String readFilter,
            @RequestParam(value = "query", required = false) String query,
            @RequestParam(value = "page", defaultValue = "0") int page,
            @RequestParam(value = "size", defaultValue = "10") int size) {
        return ResponseEntity.ok(circularService.getStudentCirculars(
                getActor(ud), type, fromDate, toDate, readFilter, query, PageRequest.of(page, size)));
    }

    @GetMapping("/banner-summary")
    public ResponseEntity<Map<String, Object>> getBannerAndUnread(@AuthenticationPrincipal UserDetails ud) {
        return ResponseEntity.ok(circularService.getStudentUnreadAndBanner(getActor(ud)));
    }

    @GetMapping("/{id}")
    public ResponseEntity<CircularResponse> getDetail(@AuthenticationPrincipal UserDetails ud, @PathVariable Long id) {
        return ResponseEntity.ok(circularService.getStudentDetailAndMarkRead(getActor(ud), id));
    }

    @PostMapping("/{id}/acknowledge")
    public ResponseEntity<CircularResponse> acknowledge(@AuthenticationPrincipal UserDetails ud, @PathVariable Long id) {
        return ResponseEntity.ok(circularService.acknowledgeCircular(getActor(ud), id));
    }

    @PostMapping("/{id}/rsvp")
    public ResponseEntity<CircularResponse> submitRsvp(
            @AuthenticationPrincipal UserDetails ud,
            @PathVariable Long id,
            @Valid @RequestBody RsvpRequest req) {
        return ResponseEntity.ok(circularService.submitRsvp(getActor(ud), id, req));
    }

    @GetMapping("/{id}/calendar.ics")
    public ResponseEntity<byte[]> downloadCalendar(@AuthenticationPrincipal UserDetails ud, @PathVariable Long id) {
        String ics = circularService.buildIcsContent(getActor(ud), id);
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType("text/calendar"))
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"event-" + id + ".ics\"")
                .body(ics.getBytes());
    }
}

