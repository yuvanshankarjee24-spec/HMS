package com.hostel.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.time.LocalDateTime;
import java.util.List;

public class OnlineAllocationDtos {

    public record OnlineAllocationAccessResponse(
        boolean roundOpen,
        String roundStatus,
        String accessStatus,
        String message,
        String leaderRegisterNumber,
        String leaderName,
        Integer groupSize,
        String allowedRoomNumber,
        List<String> friendRegisterNumbers,
        String allocatedRoomNumber,
        String currentRoom,
        Integer currentBed
    ) {}

    public record GroupBookingRequest(
        @NotNull(message = "Room ID is required")
        Long roomId,
        List<String> friendRegisterNumbers
    ) {}

    public record MemberAllocationResult(
        String registerNumber,
        String studentName,
        String roomNumber,
        Integer bedNumber
    ) {}

    public record GroupBookingResponse(
        boolean success,
        String roomNumber,
        List<MemberAllocationResult> allocatedMembers,
        String message
    ) {}

    public record WhitelistItemResponse(
        Long id,
        String leaderRegisterNumber,
        String leaderName,
        String targetRoomNumber,
        Integer groupSize,
        String friendRegisterNumbers,
        String status,
        String allocatedRoomNumber,
        LocalDateTime allocatedAt,
        String notes,
        LocalDateTime createdAt
    ) {}

    public record AddWhitelistRequest(
        @NotBlank(message = "Leader register number is required")
        String leaderRegisterNumber,
        String leaderName,
        String targetRoomNumber,
        Integer groupSize,
        String friendRegisterNumbers,
        String notes
    ) {}

    public record CsvUploadSummary(
        int totalRows,
        int importedCount,
        List<String> errors,
        String message
    ) {}

    public record ToggleRoundRequest(
        boolean open
    ) {}
}
