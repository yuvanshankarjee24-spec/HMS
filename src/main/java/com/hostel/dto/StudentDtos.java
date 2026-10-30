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
        String allocationStatus // ALLOCATED, REQUEST PENDING, NOT ALLOCATED
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
        String note
    ) {}

    public record RoomRequestResponse(
        Long id,
        Long studentId,
        String studentName,
        String registerNumber,
        Long preferredRoomId,
        String preferredRoomNumber,
        Integer preferredFloor,
        String note,
        String status,
        String decisionNote,
        LocalDateTime createdAt,
        LocalDateTime decidedAt
    ) {}
}
