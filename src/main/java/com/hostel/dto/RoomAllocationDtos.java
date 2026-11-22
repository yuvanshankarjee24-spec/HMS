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

    public record AdminRoomSummary(
        long totalRooms,
        long totalBeds,
        long occupiedBeds,
        long availableBeds,
        long fullyOccupiedRooms,
        long pendingRequests,
        long studentsWithoutRoom
    ) {}

    public record FloorStatsItem(
        int floor,
        int totalRooms,
        int totalBeds,
        int occupiedBeds,
        int availableBeds,
        double occupancyPercentage
    ) {}

    public record BedOccupancyItem(
        Long id,
        int bedNumber,
        String bedLabel,
        String status,
        Long studentId,
        String studentName,
        String rollNumber,
        String department,
        Integer year,
        String gender,
        String photoPath
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

    public record BulkValidationRow(
        int rowNumber,
        String rollNumber,
        String studentName,
        String roomNumber,
        String bedLabel,
        String status, // "VALID" or "INVALID"
        String message
    ) {}

    public record BulkValidationResponse(
        int totalRows,
        int validCount,
        int errorCount,
        List<BulkValidationRow> rows
    ) {}

    public record StudentSearchResult(
        Long id,
        String rollNumber,
        String fullName,
        String department,
        int year,
        String gender,
        String phone,
        String photoPath,
        String currentRoomNumber,
        String currentBedLabel,
        boolean isAllotted
    ) {}

    public record AllocationHistoryItem(
        Long id,
        Long studentId,
        String rollNumber,
        String studentName,
        String department,
        String roomNumber,
        String bedLabel,
        String action, // "ALLOCATED", "TRANSFERRED", "VACATED"
        String allocatedBy,
        LocalDateTime timestamp,
        LocalDateTime vacatedAt,
        String status,
        String remarks
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

    public record BulkApproveRequestsRequest(
        List<Long> requestIds,
        String remarks
    ) {}

    public record BulkApproveResponse(
        int totalRequested,
        int approvedCount,
        int failedCount,
        List<String> messages
    ) {}
}
