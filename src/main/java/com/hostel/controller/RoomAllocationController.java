package com.hostel.controller;

import com.hostel.dto.RoomAllocationDtos.*;
import com.hostel.entity.RoomAllocation;
import com.hostel.entity.User;
import com.hostel.exception.CustomExceptions.ResourceNotFoundException;
import com.hostel.repository.StudentRepository;
import com.hostel.repository.UserRepository;
import com.hostel.service.RoomAllocationService;
import jakarta.validation.Valid;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;

@RestController
public class RoomAllocationController {

    private final RoomAllocationService roomAllocationService;
    private final UserRepository userRepository;
    private final StudentRepository studentRepository;

    public RoomAllocationController(RoomAllocationService roomAllocationService,
                                    UserRepository userRepository,
                                    StudentRepository studentRepository) {
        this.roomAllocationService = roomAllocationService;
        this.userRepository = userRepository;
        this.studentRepository = studentRepository;
    }

    private User getActor(UserDetails userDetails) {
        return userRepository.findByIdentifier(userDetails.getUsername())
                .orElseThrow(() -> new IllegalArgumentException("User not found: " + userDetails.getUsername()));
    }

    @GetMapping("/api/warden/rooms/allocation-summary")
    @PreAuthorize("hasAnyRole('WARDEN', 'ADMIN')")
    public ResponseEntity<RoomAllocationSummary> getAllocationSummary() {
        return ResponseEntity.ok(roomAllocationService.getSummary());
    }

    @GetMapping("/api/warden/rooms/occupancy")
    @PreAuthorize("hasAnyRole('WARDEN', 'ADMIN')")
    public ResponseEntity<List<RoomOccupancyItem>> getRoomOccupancy(
            @RequestParam(required = false) String query,
            @RequestParam(required = false) Integer floor) {
        return ResponseEntity.ok(roomAllocationService.getOccupancyList(query, floor));
    }

    @GetMapping("/api/warden/rooms/available")
    @PreAuthorize("hasAnyRole('WARDEN', 'ADMIN')")
    public ResponseEntity<List<Map<String, Object>>> getAvailableRooms() {
        return ResponseEntity.ok(roomAllocationService.getAvailableRooms());
    }

    @PostMapping("/api/warden/rooms/allocate")
    @PreAuthorize("hasAnyRole('WARDEN', 'ADMIN')")
    public ResponseEntity<Map<String, Object>> allocateRoom(
            @AuthenticationPrincipal UserDetails userDetails,
            @Valid @RequestBody AllocateRoomRequest req) {
        User actor = getActor(userDetails);
        RoomAllocation alloc = roomAllocationService.allocate(req.studentId(), req.roomId(), req.bedLabel(), req.remarks(), actor);
        return ResponseEntity.ok(Map.of(
                "allocationId", alloc.getId(),
                "roomNumber", alloc.getRoom().getRoomNumber(),
                "bedLabel", alloc.getBed().getBedLabel(),
                "message", "Room " + alloc.getRoom().getRoomNumber() + " Bed " + alloc.getBed().getBedLabel() + " allocated successfully"
        ));
    }

    @PostMapping("/api/warden/rooms/change")
    @PreAuthorize("hasAnyRole('WARDEN', 'ADMIN')")
    public ResponseEntity<Map<String, Object>> changeRoom(
            @AuthenticationPrincipal UserDetails userDetails,
            @Valid @RequestBody ChangeRoomRequest req) {
        User actor = getActor(userDetails);
        RoomAllocation alloc = roomAllocationService.changeRoom(req.studentId(), req.targetRoomId(), req.targetBedLabel(), req.remarks(), actor);
        return ResponseEntity.ok(Map.of(
                "allocationId", alloc.getId(),
                "roomNumber", alloc.getRoom().getRoomNumber(),
                "bedLabel", alloc.getBed().getBedLabel(),
                "message", "Room transferred to Room " + alloc.getRoom().getRoomNumber() + " Bed " + alloc.getBed().getBedLabel() + " successfully"
        ));
    }

    @PostMapping("/api/warden/rooms/bulk-allocate")
    @PreAuthorize("hasAnyRole('WARDEN', 'ADMIN')")
    public ResponseEntity<BulkAllocationResponse> bulkAllocate(
            @AuthenticationPrincipal UserDetails userDetails,
            @RequestParam("file") MultipartFile file) {
        User actor = getActor(userDetails);
        BulkAllocationResponse resp = roomAllocationService.bulkAllocate(file, actor);
        return ResponseEntity.ok(resp);
    }

    @GetMapping("/api/warden/rooms/sample-csv")
    @PreAuthorize("hasAnyRole('WARDEN', 'ADMIN')")
    public ResponseEntity<byte[]> downloadSampleCsv() {
        String csv = roomAllocationService.generateSampleCsv();
        byte[] bytes = csv.getBytes(StandardCharsets.UTF_8);
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"sample_room_allocation.csv\"")
                .contentType(MediaType.parseMediaType("text/csv; charset=UTF-8"))
                .body(bytes);
    }

    @GetMapping("/api/student/allocation")
    @PreAuthorize("hasAnyRole('STUDENT', 'WARDEN', 'ADMIN')")
    public ResponseEntity<StudentAllocationDetail> getStudentAllocation(
            @AuthenticationPrincipal UserDetails userDetails,
            @RequestParam(required = false) Long studentId) {
        User actor = getActor(userDetails);
        Long targetStudentId = studentId;
        if (targetStudentId == null) {
            targetStudentId = studentRepository.findByUserId(actor.getId())
                    .map(com.hostel.entity.Student::getId)
                    .orElseThrow(() -> new ResourceNotFoundException("Student profile not found"));
        }
        return ResponseEntity.ok(roomAllocationService.getStudentAllocation(targetStudentId, actor));
    }
}
