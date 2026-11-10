package com.hostel.dto;

import jakarta.validation.constraints.*;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;
import java.util.Map;

public class DigitalAttendanceDtos {

    public record AttendanceSessionRequest(
        @NotBlank(message = "Session name is required")
        @Size(max = 120)
        String name,

        @NotBlank(message = "Hostel block is required (e.g. ALL or Block A)")
        String hostelBlock,

        @NotNull(message = "Start time is required")
        LocalTime startTime,

        @NotNull(message = "End time (deadline) is required")
        LocalTime endTime,

        @NotBlank(message = "Active days are required (e.g. MON,TUE,...)")
        String activeDays,

        Boolean enabled,

        @Min(value = 1, message = "Reminder lead minutes must be >= 1")
        @Max(value = 180, message = "Reminder lead minutes must be <= 180")
        Integer reminderLeadMinutes,

        @Min(value = 0)
        @Max(value = 60)
        Integer secondReminderMinutes,

        Boolean geofenceEnabled,
        Double hostelLatitude,
        Double hostelLongitude,

        @Min(value = 10)
        @Max(value = 5000)
        Integer geofenceRadiusMeters
    ) {}

    public record AttendanceSessionResponse(
        Long id,
        String name,
        String hostelBlock,
        LocalTime startTime,
        LocalTime endTime,
        String activeDays,
        boolean enabled,
        int reminderLeadMinutes,
        Integer secondReminderMinutes,
        boolean geofenceEnabled,
        Double hostelLatitude,
        Double hostelLongitude,
        Integer geofenceRadiusMeters
    ) {}

    public record AttendanceDefaultsDto(
        int reminderLeadMinutes,
        int secondReminderMinutes,
        boolean geofenceEnabled,
        double hostelLatitude,
        double hostelLongitude,
        int geofenceRadiusMeters
    ) {}

    public record MarkDigitalAttendanceRequest(
        @NotNull(message = "Session ID is required")
        Long sessionId,
        Double latitude,
        Double longitude
    ) {}

    public record StudentTodaySessionItem(
        Long sessionId,
        String sessionName,
        String hostelBlock,
        LocalTime startTime,
        LocalTime endTime,
        boolean windowOpen,
        boolean beforeStart,
        boolean afterDeadline,
        long secondsUntilDeadline,
        boolean geofenceEnabled,
        Double hostelLatitude,
        Double hostelLongitude,
        Integer geofenceRadiusMeters,
        String status,
        LocalDateTime markedAt,
        String remarks
    ) {}

    public record StudentTodayAttendanceResponse(
        LocalDate date,
        LocalDateTime serverTime,
        boolean outpassLocked,
        String lockReason,
        LocalDateTime lockedAt,
        List<StudentTodaySessionItem> sessions
    ) {}

    public record AttendanceHistoryItem(
        Long recordId,
        LocalDate date,
        Long sessionId,
        String sessionName,
        String status,
        LocalDateTime markedAt,
        Double latitude,
        Double longitude,
        String deviceInfo,
        String remarks
    ) {}

    public record StudentAttendanceHistoryResponse(
        long totalSessions,
        long markedCount,
        long missedCount,
        long onOutpassCount,
        double attendancePercentage,
        List<AttendanceHistoryItem> items,
        int currentPage,
        int totalPages,
        long totalElements
    ) {}

    public record WardenDigitalSummaryResponse(
        LocalDate date,
        long totalStudents,
        long markedCount,
        long pendingCount,
        long missedCount,
        long onOutpassCount,
        long lockedStudentsCount
    ) {}

    public record MissedOrPendingStudentItem(
        Long recordId,
        Long studentId,
        String fullName,
        String registerNumber,
        String roomNumber,
        String hostelBlock,
        Long sessionId,
        String sessionName,
        LocalDate attendanceDate,
        LocalDateTime recordedAt,
        String attendanceStatus,
        boolean outpassLocked,
        String lockReason,
        LocalDateTime lockedAt,
        long missesLast30Days,
        boolean repeatOffender
    ) {}

    public record UnlockOutpassRequest(
        @NotBlank(message = "Unlock reason / remark is required")
        @Size(max = 500)
        String reason
    ) {}

    public record BulkUnlockRequest(
        @NotEmpty(message = "At least one student must be selected")
        List<Long> studentIds,

        @NotBlank(message = "Unlock reason / remark is required")
        @Size(max = 500)
        String reason
    ) {}

    public record ManualMarkAttendanceRequest(
        @NotNull(message = "Student ID is required")
        Long studentId,

        @NotNull(message = "Session ID is required")
        Long sessionId,

        @NotNull(message = "Attendance date is required")
        LocalDate date,

        @NotBlank(message = "Reason is required for manual marking")
        @Size(max = 500)
        String reason
    ) {}

    public record RelockOutpassRequest(
        @NotBlank(message = "Lock reason is required")
        @Size(max = 500)
        String reason
    ) {}

    public record AttendanceReportRow(
        Long studentId,
        String fullName,
        String registerNumber,
        String roomNumber,
        long totalSessions,
        long markedCount,
        long missedCount,
        long onOutpassCount,
        double attendancePercentage,
        boolean outpassLocked,
        boolean repeatOffender
    ) {}

    public record BlockAttendanceStat(
        String block,
        long totalRecords,
        long markedCount,
        long missedCount,
        double attendancePercentage
    ) {}

    public record DailyTrendPoint(
        LocalDate date,
        long markedCount,
        long missedCount,
        long onOutpassCount
    ) {}

    public record AdminAttendanceAnalyticsResponse(
        long totalStudents,
        long currentlyLockedStudents,
        double overallAttendancePercentage,
        List<BlockAttendanceStat> blockStats,
        List<AttendanceReportRow> mostMissedStudents,
        List<DailyTrendPoint> recentTrends
    ) {}

    public record SecurityOutpassScanResponse(
        Long outpassId,
        Long studentId,
        String studentName,
        String registerNumber,
        String department,
        String roomNumber,
        String passType,
        LocalDateTime departureDateTime,
        LocalDateTime returnDateTime,
        String reason,
        String destination,
        String outpassStatus,
        boolean outpassLocked,
        String lockReason,
        LocalDateTime lockedAt,
        boolean exitAllowed,
        String gateMessage
    ) {}
}

