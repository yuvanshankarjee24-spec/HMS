package com.hostel.dto;

import jakarta.validation.constraints.NotNull;
import java.time.LocalDate;
import java.time.LocalDateTime;

public class AttendanceDtos {

    public record MarkAttendanceRequest(
        @NotNull(message = "Student ID is required")
        Long studentId,
        @NotNull(message = "Status is required (PRESENT, ABSENT, ON_OUTPASS, LATE)")
        String status,
        LocalDate date,
        String remarks
    ) {}

    public record StudentAttendanceItem(
        Long studentId,
        String studentName,
        String registerNumber,
        String roomNumber,
        Integer bedNumber,
        String department,
        int year,
        String status, // PRESENT, ABSENT, ON_OUTPASS, LATE
        String remarks,
        String activeOutpassInfo,
        LocalDateTime updatedAt
    ) {}

    public record AttendanceSummaryResponse(
        LocalDate date,
        int totalAllocated,
        int presentCount,
        int absentCount,
        int onOutpassCount,
        int lateCount
    ) {}
}
