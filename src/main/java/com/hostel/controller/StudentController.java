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

@RestController
@RequestMapping("/api/student")
public class StudentController {

    private final StudentService studentService;
    private final UserRepository userRepository;

    public StudentController(StudentService studentService, UserRepository userRepository) {
        this.studentService = studentService;
        this.userRepository = userRepository;
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
