package com.hostel.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.time.LocalDateTime;

public class OutpassDtos {

    public record CreateOutpassRequest(
        @NotBlank(message = "Pass type is required (DAY_PASS, WEEKEND_PASS, EMERGENCY_PASS, VACATION_PASS)")
        String passType,
        @NotNull(message = "Departure date time is required")
        LocalDateTime departureDateTime,
        @NotNull(message = "Return date time is required")
        LocalDateTime returnDateTime,
        @NotBlank(message = "Reason is required")
        String reason,
        String destination,
        String emergencyPhone
    ) {}

    public record OutpassDecisionRequest(
        String remarks
    ) {}

    public record OutpassResponse(
        Long id,
        Long studentId,
        String studentName,
        String registerNumber,
        String roomNumber,
        Integer bedNumber,
        String department,
        String phone,
        String passType,
        LocalDateTime departureDateTime,
        LocalDateTime returnDateTime,
        String reason,
        String destination,
        String emergencyPhone,
        String status,
        String wardenRemarks,
        LocalDateTime createdAt,
        LocalDateTime decidedAt
    ) {}

    public record OutpassEligibilityResponse(
        boolean eligible,
        String status,
        String message,
        java.util.List<String> defaultDates,
        boolean residentAtHostel,
        boolean hasActiveOutpass,
        String activeOutpassStatus,
        boolean outpassLocked,
        String lockReason,
        LocalDateTime lockedAt
    ) {}

    public record WardenOutpassSummaryResponse(
        long pending,
        long approved,
        long currentlyOut,
        long lateReturns,
        long returned,
        long rejected,
        long total
    ) {}
}
