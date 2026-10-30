package com.hostel.controller;

import com.hostel.dto.StudentDtos.*;
import com.hostel.entity.User;
import com.hostel.repository.UserRepository;
import com.hostel.service.StudentService;
import jakarta.validation.Valid;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.List;
import java.util.Map;

import com.hostel.dto.OnlineAllocationDtos.*;
import com.hostel.dto.OutpassDtos.*;
import com.hostel.entity.Attendance;
import com.hostel.service.AttendanceService;
import com.hostel.service.OnlineAllocationService;
import com.hostel.service.OutpassService;

@RestController
@RequestMapping("/api/student")
public class StudentController {

    private final StudentService studentService;
    private final UserRepository userRepository;
    private final OutpassService outpassService;
    private final AttendanceService attendanceService;
    private final OnlineAllocationService onlineAllocationService;

    public StudentController(StudentService studentService,
                             UserRepository userRepository,
                             OutpassService outpassService,
                             AttendanceService attendanceService,
                             OnlineAllocationService onlineAllocationService) {
        this.studentService = studentService;
        this.userRepository = userRepository;
        this.outpassService = outpassService;
        this.attendanceService = attendanceService;
        this.onlineAllocationService = onlineAllocationService;
    }

    private User getAuthenticatedUser(UserDetails userDetails) {
        return userRepository.findByEmail(userDetails.getUsername())
                .orElseThrow(() -> new RuntimeException("Authenticated student not found"));
    }

    @GetMapping("/profile")
    @PreAuthorize("hasRole('STUDENT')")
    public ResponseEntity<StudentProfileResponse> getProfile(@AuthenticationPrincipal UserDetails userDetails) {
        User user = getAuthenticatedUser(userDetails);
        return ResponseEntity.ok(studentService.getProfile(user));
    }

    @PostMapping("/photo")
    @PreAuthorize("hasRole('STUDENT')")
    public ResponseEntity<Map<String, String>> uploadPhoto(@AuthenticationPrincipal UserDetails userDetails,
                                                          @RequestParam("file") MultipartFile file) {
        User user = getAuthenticatedUser(userDetails);
        String url = studentService.uploadPhoto(user, file);
        return ResponseEntity.ok(Map.of("photoUrl", url, "message", "Photo uploaded successfully"));
    }

    @GetMapping("/photo")
    @PreAuthorize("hasRole('STUDENT')")
    public ResponseEntity<Resource> getPhoto(@AuthenticationPrincipal UserDetails userDetails) {
        User user = getAuthenticatedUser(userDetails);
        Resource resource = studentService.loadPhoto(user);
        return createResourceResponse(resource);
    }

    @GetMapping("/photo/{studentId}")
    @PreAuthorize("hasAnyRole('WARDEN', 'ADMIN')")
    public ResponseEntity<Resource> getStudentPhotoById(@PathVariable Long studentId) {
        Resource resource = studentService.loadPhotoByStudentId(studentId);
        return createResourceResponse(resource);
    }

    @GetMapping("/rooms/available")
    @PreAuthorize("hasRole('STUDENT')")
    public ResponseEntity<List<RoomAvailabilityResponse>> getAvailableRooms() {
        return ResponseEntity.ok(studentService.getAvailableRooms());
    }

    @PostMapping("/room-requests")
    @PreAuthorize("hasRole('STUDENT')")
    public ResponseEntity<RoomRequestResponse> createRoomRequest(@AuthenticationPrincipal UserDetails userDetails,
                                                                 @Valid @RequestBody RoomRequestCreate requestDto) {
        User user = getAuthenticatedUser(userDetails);
        return ResponseEntity.ok(studentService.createRoomRequest(user, requestDto));
    }

    @GetMapping("/room-requests")
    @PreAuthorize("hasRole('STUDENT')")
    public ResponseEntity<List<RoomRequestResponse>> getMyRequests(@AuthenticationPrincipal UserDetails userDetails) {
        User user = getAuthenticatedUser(userDetails);
        return ResponseEntity.ok(studentService.getMyRequests(user));
    }

    @DeleteMapping("/room-requests/{id}")
    @PreAuthorize("hasRole('STUDENT')")
    public ResponseEntity<Void> cancelRoomRequest(@AuthenticationPrincipal UserDetails userDetails,
                                                 @PathVariable Long id) {
        User user = getAuthenticatedUser(userDetails);
        studentService.cancelRoomRequest(user, id);
        return ResponseEntity.ok().build();
    }

    @PostMapping("/outpasses")
    @PreAuthorize("hasRole('STUDENT')")
    public ResponseEntity<OutpassResponse> createOutpass(
            @AuthenticationPrincipal UserDetails userDetails,
            @Valid @RequestBody CreateOutpassRequest req) {
        User user = getAuthenticatedUser(userDetails);
        return ResponseEntity.ok(outpassService.createOutpass(user, req));
    }

    @GetMapping("/outpasses")
    @PreAuthorize("hasRole('STUDENT')")
    public ResponseEntity<List<OutpassResponse>> getMyOutpasses(
            @AuthenticationPrincipal UserDetails userDetails) {
        User user = getAuthenticatedUser(userDetails);
        return ResponseEntity.ok(outpassService.getMyOutpasses(user));
    }

    @GetMapping("/attendance")
    @PreAuthorize("hasRole('STUDENT')")
    public ResponseEntity<List<Attendance>> getMyAttendance(
            @AuthenticationPrincipal UserDetails userDetails) {
        User user = getAuthenticatedUser(userDetails);
        return ResponseEntity.ok(attendanceService.getStudentAttendanceHistory(user));
    }

    @GetMapping("/outpass-eligibility")
    @PreAuthorize("hasRole('STUDENT')")
    public ResponseEntity<OutpassEligibilityResponse> getOutpassEligibility(
            @AuthenticationPrincipal UserDetails userDetails) {
        User user = getAuthenticatedUser(userDetails);
        return ResponseEntity.ok(outpassService.getOutpassEligibility(user));
    }

    @GetMapping("/online-allocation/access")
    @PreAuthorize("hasRole('STUDENT')")
    public ResponseEntity<OnlineAllocationAccessResponse> getOnlineAllocationAccess(
            @AuthenticationPrincipal UserDetails userDetails) {
        User user = getAuthenticatedUser(userDetails);
        return ResponseEntity.ok(onlineAllocationService.getStudentAllocationAccess(user));
    }

    @PostMapping("/online-allocation/book")
    @PreAuthorize("hasRole('STUDENT')")
    public ResponseEntity<GroupBookingResponse> bookRoomWithFriends(
            @AuthenticationPrincipal UserDetails userDetails,
            @Valid @RequestBody GroupBookingRequest request) {
        User user = getAuthenticatedUser(userDetails);
        return ResponseEntity.ok(onlineAllocationService.bookRoomWithFriends(user, request));
    }

    private ResponseEntity<Resource> createResourceResponse(Resource resource) {
        try {
            String contentType = Files.probeContentType(Paths.get(resource.getFile().getAbsolutePath()));
            if (contentType == null) {
                contentType = "image/jpeg";
            }
            return ResponseEntity.ok()
                    .contentType(MediaType.parseMediaType(contentType))
                    .header(HttpHeaders.CACHE_CONTROL, "max-age=3600")
                    .body(resource);
        } catch (IOException e) {
            return ResponseEntity.ok()
                    .contentType(MediaType.IMAGE_JPEG)
                    .body(resource);
        }
    }
}
