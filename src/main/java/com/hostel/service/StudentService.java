package com.hostel.service;

import com.hostel.dto.StudentDtos.*;
import com.hostel.entity.*;
import com.hostel.exception.CustomExceptions.*;
import com.hostel.repository.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.core.io.UrlResource;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.net.MalformedURLException;
import java.nio.file.*;
import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

@Service
public class StudentService {

    private final StudentRepository studentRepository;
    private final BedRepository bedRepository;
    private final RoomRepository roomRepository;
    private final RoomRequestRepository roomRequestRepository;
    private final AuditService auditService;

    @Value("${app.upload.dir:./storage/photos}")
    private String uploadDir;

    public StudentService(StudentRepository studentRepository,
                          BedRepository bedRepository,
                          RoomRepository roomRepository,
                          RoomRequestRepository roomRequestRepository,
                          AuditService auditService) {
        this.studentRepository = studentRepository;
        this.bedRepository = bedRepository;
        this.roomRepository = roomRepository;
        this.roomRequestRepository = roomRequestRepository;
        this.auditService = auditService;
    }

    public Student getStudentByUser(User user) {
        return studentRepository.findByUserId(user.getId())
                .orElseThrow(() -> new ResourceNotFoundException("Student profile not found for user: " + user.getEmail()));
    }

    public StudentProfileResponse getProfile(User user) {
        Student student = getStudentByUser(user);
        Optional<Bed> bedOpt = bedRepository.findByStudentId(student.getId());

        String allocationStatus;
        String roomNumber = null;
        Integer bedNumber = null;

        if (bedOpt.isPresent()) {
            allocationStatus = "ALLOCATED";
            roomNumber = bedOpt.get().getRoom().getRoomNumber();
            bedNumber = bedOpt.get().getBedNumber();
        } else if (roomRequestRepository.existsByStudentIdAndStatus(student.getId(), RoomRequest.Status.PENDING)) {
            allocationStatus = "REQUEST PENDING";
        } else {
            allocationStatus = "NOT ALLOCATED";
        }

        String photoUrl = student.getPhotoPath() != null ? "/api/student/photo" : null;

        return new StudentProfileResponse(
                student.getId(),
                user.getId(),
                student.getFullName(),
                student.getRegisterNumber(),
                student.getCollegeEmail(),
                student.getPhone(),
                student.getDepartment(),
                student.getYear(),
                photoUrl,
                roomNumber,
                bedNumber,
                allocationStatus
        );
    }

    @Transactional
    public String uploadPhoto(User user, MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new BadRequestException("No file provided");
        }

        String contentType = file.getContentType();
        if (contentType == null || (!contentType.equals("image/jpeg") && !contentType.equals("image/png") && !contentType.equals("image/jpg"))) {
            throw new BadRequestException("Only JPG and PNG images are allowed");
        }

        if (file.getSize() > 2 * 1024 * 1024) {
            throw new BadRequestException("Photo size exceeds 2 MB");
        }

        Student student = getStudentByUser(user);

        try {
            Path storageDirectory = Paths.get(uploadDir).toAbsolutePath().normalize();
            Files.createDirectories(storageDirectory);

            String extension = contentType.equals("image/png") ? ".png" : ".jpg";
            String filename = "student_" + student.getId() + "_" + System.currentTimeMillis() + extension;
            Path targetLocation = storageDirectory.resolve(filename);

            Files.copy(file.getInputStream(), targetLocation, StandardCopyOption.REPLACE_EXISTING);

            // If old photo exists, delete it
            if (student.getPhotoPath() != null) {
                try {
                    Files.deleteIfExists(Paths.get(student.getPhotoPath()));
                } catch (Exception ignored) {}
            }

            student.setPhotoPath(targetLocation.toString());
            studentRepository.save(student);

            auditService.log(user, "PHOTO_UPLOAD", student.getRegisterNumber(), "SUCCESS");
            return "/api/student/photo";
        } catch (IOException e) {
            throw new RuntimeException("Could not store photo: " + e.getMessage());
        }
    }

    public Resource loadPhoto(User user) {
        Student student = getStudentByUser(user);
        return getPhotoResource(student);
    }

    public Resource loadPhotoByStudentId(Long studentId) {
        Student student = studentRepository.findById(studentId)
                .orElseThrow(() -> new ResourceNotFoundException("Student not found"));
        return getPhotoResource(student);
    }

    private Resource getPhotoResource(Student student) {
        if (student.getPhotoPath() == null) {
            throw new ResourceNotFoundException("No photo on file for this student");
        }
        try {
            Path filePath = Paths.get(student.getPhotoPath()).normalize();
            Resource resource = new UrlResource(filePath.toUri());
            if (resource.exists() && resource.isReadable()) {
                return resource;
            } else {
                throw new ResourceNotFoundException("Photo file not found on disk");
            }
        } catch (MalformedURLException e) {
            throw new ResourceNotFoundException("Invalid file path");
        }
    }

    public List<RoomAvailabilityResponse> getAvailableRooms() {
        List<Room> rooms = roomRepository.findAllByOrderByRoomNumberAsc();
        return rooms.stream()
                .filter(r -> r.getStatus() == Room.Status.ACTIVE)
                .map(room -> {
                    long occupied = bedRepository.countByRoomIdAndStudentIsNotNull(room.getId());
                    int available = (int) Math.max(0, room.getCapacity() - occupied);
                    return new RoomAvailabilityResponse(
                            room.getId(),
                            room.getRoomNumber(),
                            room.getFloor(),
                            room.getCapacity(),
                            (int) occupied,
                            available,
                            room.getStatus().name()
                    );
                })
                .filter(r -> r.availableBeds() > 0)
                .collect(Collectors.toList());
    }

    @Transactional
    public RoomRequestResponse createRoomRequest(User user, RoomRequestCreate requestDto) {
        Student student = getStudentByUser(user);

        // Reject if student already has a bed
        if (bedRepository.findByStudentId(student.getId()).isPresent()) {
            throw new BadRequestException("You are already allocated to a room and bed.");
        }

        // Reject if student already has a pending request
        if (roomRequestRepository.existsByStudentIdAndStatus(student.getId(), RoomRequest.Status.PENDING)) {
            throw new BadRequestException("You already have a pending room request.");
        }

        Room preferredRoom = null;
        if (requestDto.preferredRoomId() != null) {
            preferredRoom = roomRepository.findById(requestDto.preferredRoomId())
                    .orElseThrow(() -> new ResourceNotFoundException("Preferred room not found"));
        }

        RoomRequest roomRequest = new RoomRequest();
        roomRequest.setStudent(student);
        roomRequest.setPreferredRoom(preferredRoom);
        roomRequest.setPreferredFloor(requestDto.preferredFloor());
        roomRequest.setNote(requestDto.note() != null ? requestDto.note().trim() : null);
        roomRequest.setStatus(RoomRequest.Status.PENDING);
        roomRequest.setCreatedAt(LocalDateTime.now());

        RoomRequest saved = roomRequestRepository.save(roomRequest);

        auditService.log(user, "CREATE_ROOM_REQUEST", "RequestId=" + saved.getId(), "SUCCESS");

        return toRequestResponse(saved);
    }

    public List<RoomRequestResponse> getMyRequests(User user) {
        Student student = getStudentByUser(user);
        return roomRequestRepository.findByStudentIdOrderByCreatedAtDesc(student.getId())
                .stream()
                .map(this::toRequestResponse)
                .collect(Collectors.toList());
    }

    @Transactional
    public void cancelRoomRequest(User user, Long requestId) {
        Student student = getStudentByUser(user);
        RoomRequest request = roomRequestRepository.findById(requestId)
                .orElseThrow(() -> new ResourceNotFoundException("Room request not found"));

        // Enforce IDOR protection: only own request
        if (!request.getStudent().getId().equals(student.getId())) {
            throw new ForbiddenException("You cannot cancel another student's request");
        }

        if (request.getStatus() != RoomRequest.Status.PENDING) {
            throw new BadRequestException("Only PENDING requests can be cancelled");
        }

        request.setStatus(RoomRequest.Status.CANCELLED);
        request.setDecidedAt(LocalDateTime.now());
        roomRequestRepository.save(request);

        auditService.log(user, "CANCEL_ROOM_REQUEST", "RequestId=" + requestId, "SUCCESS");
    }

    private RoomRequestResponse toRequestResponse(RoomRequest r) {
        return new RoomRequestResponse(
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
        );
    }
}
