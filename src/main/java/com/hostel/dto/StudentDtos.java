package com.hostel.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import java.time.LocalDateTime;

public class StudentDtos {

    public record StudentProfileResponse(
        Long id,
        Long userId,
        String fullName,
        String registerNumber,
        String collegeEmail,
        String phone,
        String department,
        int year,
        String photoUrl,
        String roomNumber,
        Integer bedNumber,
        String allocationStatus, // ALLOCATED, REQUEST PENDING, NOT ALLOCATED
        String hostelBlock,
        Integer floor
    ) {}

    public record RoomAvailabilityResponse(
        Long id,
        String roomNumber,
        int floor,
        int capacity,
        int occupiedBeds,
        int availableBeds,
        String status
    ) {}

    public record RoomRequestCreate(
        Long preferredRoomId,
        @Min(value = 1, message = "Floor must be at least 1")
        @Max(value = 20, message = "Floor cannot exceed 20")
        Integer preferredFloor,
        String preferredRoomType,
        String preferredRoommates,
        String note
    ) {
        public RoomRequestCreate(Long preferredRoomId, Integer preferredFloor, String note) {
            this(preferredRoomId, preferredFloor, null, null, note);
        }
    }

    public record RoomRequestResponse(
        Long id,
        String requestCode,
        Long studentId,
        String studentName,
        String registerNumber,
        String department,
        Integer year,
        String gender,
        Long preferredRoomId,
        String preferredRoomNumber,
        Integer preferredFloor,
        String preferredRoomType,
        String preferredRoommates,
        String note,
        String status,
        String decisionNote,
        String allottedRoomNumber,
        String allottedBedLabel,
        LocalDateTime createdAt,
        LocalDateTime decidedAt
    ) {
        public static RoomRequestResponse fromEntity(com.hostel.entity.RoomRequest r) {
            return new RoomRequestResponse(
                r.getId(),
                r.getRequestCode(),
                r.getStudent() != null ? r.getStudent().getId() : null,
                r.getStudent() != null ? r.getStudent().getFullName() : null,
                r.getStudent() != null ? r.getStudent().getRegisterNumber() : null,
                r.getStudent() != null ? r.getStudent().getDepartment() : null,
                r.getStudent() != null ? r.getStudent().getYear() : null,
                r.getStudent() != null ? r.getStudent().getGender() : null,
                r.getPreferredRoom() != null ? r.getPreferredRoom().getId() : null,
                r.getPreferredRoom() != null ? r.getPreferredRoom().getRoomNumber() : null,
                r.getPreferredFloor(),
                r.getPreferredRoomType(),
                r.getPreferredRoommates(),
                r.getNote(),
                r.getStatus() != null ? r.getStatus().name() : null,
                r.getDecisionNote(),
                r.getAllottedRoom() != null ? r.getAllottedRoom().getRoomNumber() : null,
                r.getAllottedBed() != null ? r.getAllottedBed().getBedLabel() : null,
                r.getCreatedAt(),
                r.getDecidedAt()
            );
        }
    }
}
