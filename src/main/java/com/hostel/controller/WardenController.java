package com.hostel.controller;

import com.hostel.dto.StudentDtos.RoomRequestResponse;
import com.hostel.dto.WardenDtos.*;
import com.hostel.entity.Bed;
import com.hostel.entity.Room;
import com.hostel.entity.RoomRequest;
import com.hostel.entity.User;
import com.hostel.repository.UserRepository;
import com.hostel.service.WardenService;
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

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/warden")
@PreAuthorize("hasAnyRole('WARDEN', 'ADMIN')")
public class WardenController {

    private final WardenService wardenService;
    private final UserRepository userRepository;

    public WardenController(WardenService wardenService, UserRepository userRepository) {
        this.wardenService = wardenService;
        this.userRepository = userRepository;
    }

    private User getAuthenticatedUser(UserDetails userDetails) {
        return userRepository.findByEmail(userDetails.getUsername())
                .orElseThrow(() -> new RuntimeException("Authenticated user not found"));
    }

    @GetMapping("/summary")
    public ResponseEntity<WardenSummaryResponse> getSummary() {
        return ResponseEntity.ok(wardenService.getSummary());
    }

    @GetMapping("/rooms")
    public ResponseEntity<List<RoomListItem>> getRooms() {
        return ResponseEntity.ok(wardenService.getAllRooms());
    }

    @PostMapping("/rooms")
    public ResponseEntity<Room> createRoom(@AuthenticationPrincipal UserDetails userDetails,
                                           @Valid @RequestBody CreateRoomRequest request) {
        User user = getAuthenticatedUser(userDetails);
        Room room = wardenService.createRoom(request, user);
        return ResponseEntity.ok(room);
    }

    @PutMapping("/rooms/{id}")
    public ResponseEntity<Room> updateRoomCapacity(@AuthenticationPrincipal UserDetails userDetails,
                                                   @PathVariable Long id,
                                                   @Valid @RequestBody UpdateRoomCapacityRequest request) {
        User user = getAuthenticatedUser(userDetails);
        Room room = wardenService.updateRoomCapacity(id, request, user);
        return ResponseEntity.ok(room);
    }

    @GetMapping("/students")
    public ResponseEntity<Page<StudentWardenView>> getStudents(
            @RequestParam(value = "query", required = false) String query,
            @RequestParam(value = "page", defaultValue = "0") int page,
            @RequestParam(value = "size", defaultValue = "10") int size) {
        Pageable pageable = PageRequest.of(page, size, Sort.by("fullName").ascending());
        return ResponseEntity.ok(wardenService.getStudents(query, pageable));
    }

    @GetMapping("/room-requests")
    public ResponseEntity<List<RoomRequestResponse>> getRoomRequests(
            @RequestParam(value = "status", required = false) String status) {
        RoomRequest.Status reqStatus = null;
        if (status != null && !status.isBlank()) {
            reqStatus = RoomRequest.Status.valueOf(status.trim().toUpperCase());
        }
        List<RoomRequest> list = wardenService.getRoomRequests(reqStatus);
        List<RoomRequestResponse> dtos = list.stream().map(r -> new RoomRequestResponse(
                r.getId(),
                r.getStudent().getId(),
                r.getStudent().getFullName(),
                r.getStudent().getRegisterNumber(),
                r.getPreferredRoom() != null ? r.getPreferredRoom().getId() : null,
                r.getPreferredRoom() != null ? r.getPreferredRoom().getRoomNumber() : null,
                r.getPreferredFloor(),
                r.getNote(),
                r.getStatus().name(),
                r.getDecisionNote(),
                r.getCreatedAt(),
                r.getDecidedAt()
        )).collect(Collectors.toList());
        return ResponseEntity.ok(dtos);
    }

    @PostMapping("/room-requests/{id}/approve")
    public ResponseEntity<Map<String, Object>> approveRoomRequest(
            @AuthenticationPrincipal UserDetails userDetails,
            @PathVariable Long id,
            @RequestBody(required = false) ApproveRequest approveDto) {
        User user = getAuthenticatedUser(userDetails);
        Bed allocatedBed = wardenService.approveRoomRequest(id, approveDto, user);
        return ResponseEntity.ok(Map.of(
                "message", "Room request approved successfully",
                "bedId", allocatedBed.getId(),
                "roomNumber", allocatedBed.getRoom().getRoomNumber(),
                "bedNumber", allocatedBed.getBedNumber()
        ));
    }

    @PostMapping("/room-requests/{id}/reject")
    public ResponseEntity<Map<String, String>> rejectRoomRequest(
            @AuthenticationPrincipal UserDetails userDetails,
            @PathVariable Long id,
            @Valid @RequestBody RejectRequest rejectDto) {
        User user = getAuthenticatedUser(userDetails);
        wardenService.rejectRoomRequest(id, rejectDto, user);
        return ResponseEntity.ok(Map.of("message", "Room request rejected"));
    }

    @PostMapping("/students/{id}/vacate")
    public ResponseEntity<Map<String, String>> vacateStudent(
            @AuthenticationPrincipal UserDetails userDetails,
            @PathVariable Long id) {
        User user = getAuthenticatedUser(userDetails);
        wardenService.vacateStudent(id, user);
        return ResponseEntity.ok(Map.of("message", "Student vacated successfully and bed released"));
    }
}
