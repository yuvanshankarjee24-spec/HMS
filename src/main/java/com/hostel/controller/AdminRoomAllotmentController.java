package com.hostel.controller;

import com.hostel.dto.RoomAllocationDtos.*;
import com.hostel.dto.WardenDtos.RejectRequest;
import com.hostel.entity.*;
import com.hostel.exception.CustomExceptions.BadRequestException;
import com.hostel.exception.CustomExceptions.ConflictException;
import com.hostel.exception.CustomExceptions.ResourceNotFoundException;
import com.hostel.repository.*;
import com.hostel.service.AuditService;
import com.hostel.service.NotificationService;
import com.hostel.service.RoomAllocationService;
import jakarta.validation.Valid;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.*;

@RestController
@RequestMapping("/api/admin/rooms")
@PreAuthorize("hasRole('ADMIN')")
public class AdminRoomAllotmentController {

    private final RoomAllocationService roomAllocationService;
    private final RoomRequestRepository roomRequestRepository;
    private final RoomRequestStatusHistoryRepository roomRequestStatusHistoryRepository;
    private final RoomRepository roomRepository;
    private final BedRepository bedRepository;
    private final RoomAllocationRepository roomAllocationRepository;
    private final UserRepository userRepository;
    private final NotificationService notificationService;
    private final AuditService auditService;

    public AdminRoomAllotmentController(RoomAllocationService roomAllocationService,
                                        RoomRequestRepository roomRequestRepository,
                                        RoomRequestStatusHistoryRepository roomRequestStatusHistoryRepository,
                                        RoomRepository roomRepository,
                                        BedRepository bedRepository,
                                        RoomAllocationRepository roomAllocationRepository,
                                        UserRepository userRepository,
                                        NotificationService notificationService,
                                        AuditService auditService) {
        this.roomAllocationService = roomAllocationService;
        this.roomRequestRepository = roomRequestRepository;
        this.roomRequestStatusHistoryRepository = roomRequestStatusHistoryRepository;
        this.roomRepository = roomRepository;
        this.bedRepository = bedRepository;
        this.roomAllocationRepository = roomAllocationRepository;
        this.userRepository = userRepository;
        this.notificationService = notificationService;
        this.auditService = auditService;
    }

    private User getActor(UserDetails userDetails) {
        return userRepository.findByIdentifier(userDetails.getUsername())
                .orElseThrow(() -> new IllegalArgumentException("Admin user not found: " + userDetails.getUsername()));
    }

    private boolean isRoomGenderCompatible(Room room, Student student) {
        if (student == null || student.getGender() == null || student.getGender().isBlank()) {
            return true;
        }
        String studentGender = student.getGender().trim().toLowerCase();
        List<Bed> beds = bedRepository.findByRoomIdOrderByBedNumberAsc(room.getId());
        for (Bed b : beds) {
            if (b.getStudent() != null && b.getStudent().getGender() != null && !b.getStudent().getGender().isBlank()) {
                if (!b.getStudent().getGender().trim().equalsIgnoreCase(studentGender)) {
                    return false;
                }
            }
        }
        return true;
    }

    @GetMapping("/summary")
    public ResponseEntity<AdminRoomSummary> getAdminSummary() {
        return ResponseEntity.ok(roomAllocationService.getAdminSummary());
    }

    @GetMapping("/floor-stats")
    public ResponseEntity<List<FloorStatsItem>> getFloorStats() {
        return ResponseEntity.ok(roomAllocationService.getFloorStats());
    }

    @GetMapping("/students/search")
    public ResponseEntity<Page<StudentSearchResult>> searchStudents(
            @RequestParam(required = false) String query,
            @RequestParam(defaultValue = "true") boolean unallocatedOnly,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size) {
        Pageable pageable = PageRequest.of(page, size, Sort.by("registerNumber").ascending());
        return ResponseEntity.ok(roomAllocationService.searchStudents(query, unallocatedOnly, pageable));
    }

    @GetMapping("/occupancy")
    public ResponseEntity<List<RoomOccupancyItem>> getRoomOccupancy(
            @RequestParam(required = false) String query,
            @RequestParam(required = false) Integer floor) {
        return ResponseEntity.ok(roomAllocationService.getOccupancyList(query, floor));
    }

    @GetMapping("/available")
    public ResponseEntity<List<Map<String, Object>>> getAvailableRooms(
            @RequestParam(required = false) Integer floor,
            @RequestParam(required = false) String block) {
        return ResponseEntity.ok(roomAllocationService.getAvailableRooms(floor, block));
    }

    @PostMapping("/allocate")
    public ResponseEntity<Map<String, Object>> allocateRoom(
            @AuthenticationPrincipal UserDetails userDetails,
            @Valid @RequestBody AllocateRoomRequest req) {
        User actor = getActor(userDetails);
        RoomAllocation alloc = roomAllocationService.allocate(req.studentId(), req.roomId(), req.bedLabel(), req.remarks(), actor);
        return ResponseEntity.ok(Map.of(
                "allocationId", alloc.getId(),
                "roomNumber", alloc.getRoom().getRoomNumber(),
                "bedLabel", alloc.getBed().getBedLabel(),
                "message", "Room " + alloc.getRoom().getRoomNumber() + ", Bed " + alloc.getBed().getBedLabel() + " allotted successfully"
        ));
    }

    @PostMapping("/change")
    public ResponseEntity<Map<String, Object>> changeRoom(
            @AuthenticationPrincipal UserDetails userDetails,
            @Valid @RequestBody ChangeRoomRequest req) {
        User actor = getActor(userDetails);
        RoomAllocation alloc = roomAllocationService.changeRoom(req.studentId(), req.targetRoomId(), req.targetBedLabel(), req.remarks(), actor);
        return ResponseEntity.ok(Map.of(
                "allocationId", alloc.getId(),
                "roomNumber", alloc.getRoom().getRoomNumber(),
                "bedLabel", alloc.getBed().getBedLabel(),
                "message", "Transferred student to Room " + alloc.getRoom().getRoomNumber() + ", Bed " + alloc.getBed().getBedLabel() + " successfully"
        ));
    }

    @PostMapping("/vacate/{studentId}")
    public ResponseEntity<Map<String, String>> vacateStudent(
            @AuthenticationPrincipal UserDetails userDetails,
            @PathVariable Long studentId,
            @RequestBody(required = false) VacateStudentRequest req) {
        User actor = getActor(userDetails);
        String remarks = (req != null && req.remarks() != null) ? req.remarks() : "Vacated by Administrator";
        roomAllocationService.vacate(studentId, remarks, actor);
        return ResponseEntity.ok(Map.of("message", "Student vacated successfully and bed released."));
    }

    @PatchMapping("/{roomId}/maintenance")
    public ResponseEntity<Map<String, Object>> toggleRoomMaintenance(
            @AuthenticationPrincipal UserDetails userDetails,
            @PathVariable Long roomId) {
        User actor = getActor(userDetails);
        Room updated = roomAllocationService.toggleRoomMaintenance(roomId, actor);
        return ResponseEntity.ok(Map.of(
                "roomId", updated.getId(),
                "roomNumber", updated.getRoomNumber(),
                "status", updated.getStatus().name(),
                "message", "Room " + updated.getRoomNumber() + " status updated to " + updated.getStatus().name()
        ));
    }

    @PatchMapping("/beds/{bedId}/maintenance")
    public ResponseEntity<Map<String, Object>> toggleBedMaintenance(
            @AuthenticationPrincipal UserDetails userDetails,
            @PathVariable Long bedId) {
        User actor = getActor(userDetails);
        Bed updated = roomAllocationService.toggleBedMaintenance(bedId, actor);
        return ResponseEntity.ok(Map.of(
                "bedId", updated.getId(),
                "bedLabel", updated.getBedLabel(),
                "status", updated.getStatus(),
                "message", "Bed " + updated.getBedLabel() + " status updated to " + updated.getStatus()
        ));
    }

    @GetMapping("/requests")
    public ResponseEntity<List<Map<String, Object>>> getRoomRequests(
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String query,
            @RequestParam(required = false) String date) {
        RoomRequest.Status reqStatus = null;
        if (status != null && !status.isBlank() && !"ALL".equalsIgnoreCase(status)) {
            reqStatus = RoomRequest.Status.valueOf(status.trim().toUpperCase());
        }

        List<RoomRequest> requests = roomRequestRepository.searchRoomRequests(reqStatus, query);

        if (date != null && !date.isBlank()) {
            requests = requests.stream()
                    .filter(r -> r.getCreatedAt().toLocalDate().toString().equals(date.trim()))
                    .toList();
        }

        List<Map<String, Object>> list = new ArrayList<>();
        for (RoomRequest r : requests) {
            Student s = r.getStudent();
            Map<String, Object> map = new LinkedHashMap<>();
            map.put("id", r.getId());
            map.put("requestCode", r.getRequestCode());
            map.put("studentId", s.getId());
            map.put("studentName", s.getFullName());
            map.put("rollNumber", s.getRegisterNumber());
            map.put("department", s.getDepartment());
            map.put("year", s.getYear());
            map.put("gender", s.getGender() != null ? s.getGender() : "N/A");
            map.put("preferredFloor", r.getPreferredFloor() != null ? r.getPreferredFloor() : "Any");
            map.put("preferredRoom", r.getPreferredRoom() != null ? r.getPreferredRoom().getRoomNumber() : "Any");
            map.put("preferredRoomType", r.getPreferredRoomType() != null ? r.getPreferredRoomType() : "Standard");
            map.put("preferredRoommates", r.getPreferredRoommates() != null ? r.getPreferredRoommates() : "");
            map.put("note", r.getNote() != null ? r.getNote() : "");
            map.put("status", r.getStatus().name());
            map.put("decisionNote", r.getDecisionNote() != null ? r.getDecisionNote() : "");
            map.put("allottedRoomNumber", r.getAllottedRoom() != null ? r.getAllottedRoom().getRoomNumber() : null);
            map.put("allottedBedLabel", r.getAllottedBed() != null ? r.getAllottedBed().getBedLabel() : null);
            map.put("createdAt", r.getCreatedAt().toString());
            map.put("decidedAt", r.getDecidedAt() != null ? r.getDecidedAt().toString() : null);
            list.add(map);
        }

        return ResponseEntity.ok(list);
    }

    @PostMapping("/requests/{id}/approve")
    @Transactional
    public ResponseEntity<Map<String, Object>> approveRoomRequest(
            @AuthenticationPrincipal UserDetails userDetails,
            @PathVariable Long id,
            @RequestBody(required = false) Map<String, Object> body) {
        User actor = getActor(userDetails);
        RoomRequest request = roomRequestRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Room request not found: " + id));

        if (request.getStatus() != RoomRequest.Status.PENDING) {
            throw new BadRequestException("Request #" + request.getRequestCode() + " is already " + request.getStatus() + " and cannot be modified.");
        }

        Student student = request.getStudent();
        if (bedRepository.findByStudentId(student.getId()).isPresent() ||
            roomAllocationRepository.findByStudentIdAndStatus(student.getId(), "active").isPresent()) {
            request.setStatus(RoomRequest.Status.REJECTED);
            request.setDecidedBy(actor);
            request.setDecisionNote("Student already has an active room allocation.");
            request.setDecidedAt(LocalDateTime.now());
            roomRequestRepository.save(request);
            throw new ConflictException("Student already has an active room allocation.");
        }

        Long targetRoomId = null;
        String targetBedLabel = null;
        String remarks = "Approved room request " + request.getRequestCode();

        if (body != null) {
            if (body.get("roomId") != null && !body.get("roomId").toString().isBlank()) {
                targetRoomId = Long.valueOf(body.get("roomId").toString());
            }
            if (body.get("bedLabel") != null && !body.get("bedLabel").toString().isBlank()) {
                targetBedLabel = body.get("bedLabel").toString().trim().toUpperCase();
            }
            if (body.get("remarks") != null && !body.get("remarks").toString().isBlank()) {
                remarks = body.get("remarks").toString().trim();
            }
        }

        // Auto-assign target room if not specified
        Room targetRoom;
        if (targetRoomId != null) {
            final Long finalTargetRoomId = targetRoomId;
            targetRoom = roomRepository.findByIdWithLock(targetRoomId)
                    .orElseThrow(() -> new ResourceNotFoundException("Room not found: " + finalTargetRoomId));
            if (targetBedLabel == null || targetBedLabel.isBlank()) {
                List<Bed> freeBeds = bedRepository.findAvailableBedsInRoomWithLock(targetRoom.getId());
                if (freeBeds.isEmpty()) {
                    throw new ConflictException("Room " + targetRoom.getRoomNumber() + " is fully occupied (" + targetRoom.getCapacity() + "/" + targetRoom.getCapacity() + ")");
                }
                targetBedLabel = freeBeds.get(0).getBedLabel();
            }
        } else {
            List<Bed> allBeds = bedRepository.findAllAvailableBedsWithLock();
            if (allBeds.isEmpty()) {
                throw new ConflictException("No beds available");
            }

            Bed chosenBed = null;
            // 1. Try preferred room if specified and gender-compatible
            if (request.getPreferredRoom() != null) {
                for (Bed b : allBeds) {
                    if (b.getRoom().getId().equals(request.getPreferredRoom().getId()) && isRoomGenderCompatible(b.getRoom(), student)) {
                        chosenBed = b;
                        break;
                    }
                }
            }
            // 2. Try preferred floor if specified and gender-compatible
            if (chosenBed == null && request.getPreferredFloor() != null) {
                for (Bed b : allBeds) {
                    if (b.getRoom().getFloor() == request.getPreferredFloor() && isRoomGenderCompatible(b.getRoom(), student)) {
                        chosenBed = b;
                        break;
                    }
                }
            }
            // 3. Fallback to any gender-compatible available room
            if (chosenBed == null) {
                for (Bed b : allBeds) {
                    if (isRoomGenderCompatible(b.getRoom(), student)) {
                        chosenBed = b;
                        break;
                    }
                }
            }

            if (chosenBed == null) {
                throw new ConflictException("No beds available" + (student.getGender() != null ? " compatible with student gender (" + student.getGender() + ")" : ""));
            }

            targetRoom = chosenBed.getRoom();
            if (targetBedLabel == null || targetBedLabel.isBlank()) {
                targetBedLabel = chosenBed.getBedLabel();
            }
        }

        RoomAllocation alloc = roomAllocationService.allocate(student.getId(), targetRoom.getId(), targetBedLabel, remarks, actor);

        // Update request
        request.setStatus(RoomRequest.Status.APPROVED);
        request.setAllottedRoom(targetRoom);
        request.setAllottedBed(alloc.getBed());
        request.setDecisionNote(remarks);
        request.setDecidedBy(actor);
        request.setDecidedAt(LocalDateTime.now());
        roomRequestRepository.save(request);

        // Status history
        roomRequestStatusHistoryRepository.save(new RoomRequestStatusHistory(
                request, "PENDING", "APPROVED", actor, remarks
        ));

        // Audit Log
        auditService.log(actor, "ROOM_REQUESTS", "APPROVE_REQUEST", request.getId(), "PENDING", "APPROVED",
                "Room=" + targetRoom.getRoomNumber() + ", Bed=" + alloc.getBed().getBedLabel(), "SUCCESS");

        // Notify Student
        notificationService.notifyUser(
                student.getUser(),
                "Room Request Approved",
                "Your room request is approved. Room " + targetRoom.getRoomNumber() + ", Bed " + alloc.getBed().getBedLabel() + ".",
                "ROOM_REQUEST",
                request.getId()
        );

        return ResponseEntity.ok(Map.of(
                "message", "Room request " + request.getRequestCode() + " approved successfully.",
                "roomNumber", targetRoom.getRoomNumber(),
                "bedLabel", alloc.getBed().getBedLabel()
        ));
    }

    @PostMapping("/requests/{id}/reject")
    @Transactional
    public ResponseEntity<Map<String, String>> rejectRoomRequest(
            @AuthenticationPrincipal UserDetails userDetails,
            @PathVariable Long id,
            @Valid @RequestBody RejectRequest rejectDto) {
        User actor = getActor(userDetails);
        RoomRequest request = roomRequestRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Room request not found: " + id));

        if (request.getStatus() != RoomRequest.Status.PENDING) {
            throw new BadRequestException("Request #" + request.getRequestCode() + " is already " + request.getStatus() + " and cannot be modified.");
        }

        String reason = rejectDto.reason().trim();
        request.setStatus(RoomRequest.Status.REJECTED);
        request.setDecisionNote(reason);
        request.setDecidedBy(actor);
        request.setDecidedAt(LocalDateTime.now());
        roomRequestRepository.save(request);

        roomRequestStatusHistoryRepository.save(new RoomRequestStatusHistory(
                request, "PENDING", "REJECTED", actor, reason
        ));

        auditService.log(actor, "ROOM_REQUESTS", "REJECT_REQUEST", request.getId(), "PENDING", "REJECTED", request.getRequestCode(), "SUCCESS");

        notificationService.notifyUser(
                request.getStudent().getUser(),
                "Room Request Rejected",
                "Your room request " + request.getRequestCode() + " was rejected: " + reason,
                "ROOM_REQUEST",
                request.getId()
        );

        return ResponseEntity.ok(Map.of("message", "Room request " + request.getRequestCode() + " rejected."));
    }

    @PostMapping("/requests/bulk-approve")
    public ResponseEntity<BulkApproveResponse> bulkApprove(
            @AuthenticationPrincipal UserDetails userDetails,
            @RequestBody BulkApproveRequestsRequest req) {
        User actor = getActor(userDetails);
        int approved = 0;
        int failed = 0;
        List<String> messages = new ArrayList<>();

        if (req.requestIds() == null || req.requestIds().isEmpty()) {
            throw new BadRequestException("No request IDs provided");
        }

        for (Long id : req.requestIds()) {
            try {
                approveRoomRequest(userDetails, id, Map.of("remarks", req.remarks() != null ? req.remarks() : "Bulk approved by Admin"));
                approved++;
                messages.add("Request #" + id + ": Approved successfully");
            } catch (Exception e) {
                failed++;
                messages.add("Request #" + id + ": " + e.getMessage());
            }
        }

        return ResponseEntity.ok(new BulkApproveResponse(req.requestIds().size(), approved, failed, messages));
    }

    @PostMapping("/bulk-validate")
    public ResponseEntity<BulkValidationResponse> bulkValidate(
            @RequestParam("file") MultipartFile file) {
        return ResponseEntity.ok(roomAllocationService.bulkValidate(file));
    }

    @PostMapping("/bulk-allocate")
    public ResponseEntity<BulkAllocationResponse> bulkAllocate(
            @AuthenticationPrincipal UserDetails userDetails,
            @RequestParam("file") MultipartFile file) {
        User actor = getActor(userDetails);
        return ResponseEntity.ok(roomAllocationService.bulkAllocate(file, actor));
    }

    @GetMapping("/sample-csv")
    public ResponseEntity<byte[]> downloadSampleCsv() {
        String csv = roomAllocationService.generateSampleCsv();
        byte[] bytes = csv.getBytes(StandardCharsets.UTF_8);
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"sample_room_allotment.csv\"")
                .contentType(MediaType.parseMediaType("text/csv; charset=UTF-8"))
                .body(bytes);
    }

    @GetMapping("/history")
    public ResponseEntity<List<AllocationHistoryItem>> getHistory(
            @RequestParam(required = false) String query,
            @RequestParam(required = false) String status) {
        return ResponseEntity.ok(roomAllocationService.searchAllocationHistory(query, status));
    }

    @GetMapping("/export/allotments")
    public ResponseEntity<byte[]> exportAllotmentsCsv() {
        String csv = roomAllocationService.exportAllotmentsCsv();
        byte[] bytes = csv.getBytes(StandardCharsets.UTF_8);
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"active_room_allotments.csv\"")
                .contentType(MediaType.parseMediaType("text/csv; charset=UTF-8"))
                .body(bytes);
    }

    @GetMapping("/export/vacant-beds")
    public ResponseEntity<byte[]> exportVacantBedsCsv() {
        String csv = roomAllocationService.exportVacantBedsCsv();
        byte[] bytes = csv.getBytes(StandardCharsets.UTF_8);
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"vacant_beds_report.csv\"")
                .contentType(MediaType.parseMediaType("text/csv; charset=UTF-8"))
                .body(bytes);
    }

    @GetMapping("/export/history")
    public ResponseEntity<byte[]> exportHistoryCsv() {
        String csv = roomAllocationService.exportHistoryCsv();
        byte[] bytes = csv.getBytes(StandardCharsets.UTF_8);
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"room_allotment_history.csv\"")
                .contentType(MediaType.parseMediaType("text/csv; charset=UTF-8"))
                .body(bytes);
    }
}
