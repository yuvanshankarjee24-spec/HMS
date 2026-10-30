package com.hostel.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import java.util.List;

public class WardenDtos {

    public record WardenSummaryResponse(
        long totalRooms,
        long totalBeds,
        long occupiedBeds,
        long availableBeds,
        long pendingRequests
    ) {}

    public record BedDetail(
        Long id,
        int bedNumber,
        Long studentId,
        String studentName,
        String registerNumber,
        String department,
        String phone
    ) {}

    public record RoomListItem(
        Long id,
        String roomNumber,
        int floor,
        int capacity,
        int occupied,
        int available,
        String status,
        List<BedDetail> beds
    ) {}

    public record CreateRoomRequest(
        @NotBlank(message = "Room number is required")
        String roomNumber,

        @Min(value = 1, message = "Floor must be at least 1")
        int floor,

        @Min(value = 1, message = "Capacity must be at least 1")
        int capacity
    ) {}

    public record UpdateRoomCapacityRequest(
        @Min(value = 1, message = "Capacity must be at least 1")
        int capacity
    ) {}

    public record StudentWardenView(
        Long id,
        Long userId,
        String fullName,
        String registerNumber,
        String email,
        String phone,
        String department,
        int year,
        String roomNumber,
        Integer bedNumber,
        Long bedId,
        String allocationStatus
    ) {}

    public record ApproveRequest(
        Long bedId // optional: if null, auto-pick first free bed in preferred room, or any room
    ) {}

    public record RejectRequest(
        @NotBlank(message = "Reason is required")
        String reason
    ) {}
}
