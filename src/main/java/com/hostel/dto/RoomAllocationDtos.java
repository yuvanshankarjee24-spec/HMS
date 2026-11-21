package com.hostel.dto;

import jakarta.validation.constraints.NotNull;
import java.time.LocalDateTime;
import java.util.List;

public class RoomAllocationDtos {

    public record AllocateRoomRequest(
        @NotNull(message = "Student ID is required")
        Long studentId,

        @NotNull(message = "Room ID is required")
        Long roomId,

        String bedLabel, // Optional: "A", "B", "C", "D" (auto-assigned if null)
        String remarks
    ) {}

    public record ChangeRoomRequest(
        @NotNull(message = "Student ID is required")
        Long studentId,

        @NotNull(message = "Target Room ID is required")
        Long targetRoomId,

        String targetBedLabel,
        String remarks
    ) {}

    public record VacateStudentRequest(
        String remarks
    ) {}

    public record RoomAllocationSummary(
        long totalRooms,
        long totalBeds,
        long occupiedBeds,
        long availableBeds,
        long fullyOccupiedRooms
    ) {}

    public record BedOccupancyItem(
        Long id,
        int bedNumber,
        String bedLabel,
        String status,
        Long studentId,
        String studentName,
        String rollNumber,
        String department
    ) {}

    public record RoomOccupancyItem(
        Long id,
        String roomNumber,
        int floor,
        int capacity,
        int occupied,
        int available,
        String status,
        List<BedOccupancyItem> beds
    ) {}

    public record BulkAllocationRowError(
        int rowNumber,
        String rollNumber,
        String roomNumber,
        String reason
    ) {}

    public record BulkAllocationResponse(
        int totalRows,
        int successCount,
        int failedCount,
        List<BulkAllocationRowError> errors,
        String message
    ) {}

    public record StudentAllocationDetail(
        Long studentId,
        String rollNumber,
        String fullName,
        String roomNumber,
        String bedLabel,
        int floor,
        String hostelName,
        String status,
        LocalDateTime allocatedAt
    ) {}
}
