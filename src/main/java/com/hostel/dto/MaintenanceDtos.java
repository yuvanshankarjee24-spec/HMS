package com.hostel.dto;

import jakarta.validation.constraints.*;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

public class MaintenanceDtos {

    public record CreateWorkerRequest(
        @NotBlank(message = "Full name is required")
        @Size(max = 150)
        String fullName,

        @NotBlank(message = "Email is required")
        @Email(message = "Valid email is required")
        String email,

        @NotBlank(message = "Phone number is required")
        @Size(max = 20)
        String phone,

        @NotBlank(message = "Worker skill/type is required")
        String workerType,

        @NotBlank(message = "Employee ID is required")
        @Size(max = 50)
        String employeeId,

        @NotBlank(message = "Password is required")
        @Size(min = 6, message = "Password must be at least 6 characters")
        String password
    ) {}

    public record UpdateWorkerRequest(
        @NotBlank(message = "Full name is required")
        @Size(max = 150)
        String fullName,

        @NotBlank(message = "Phone number is required")
        @Size(max = 20)
        String phone,

        @NotBlank(message = "Worker skill/type is required")
        String workerType,

        @NotBlank(message = "Employee ID is required")
        @Size(max = 50)
        String employeeId,

        Boolean active,

        String newPassword
    ) {}

    public record WorkerResponse(
        Long id,
        Long userId,
        String employeeId,
        String email,
        String fullName,
        String phone,
        String workerType,
        boolean active,
        long tasksCompleted,
        long activeTasks,
        Double averageRating,
        LocalDateTime createdAt
    ) {}

    public record AssignWorkerRequest(
        @NotNull(message = "Worker ID is required")
        Long workerId,

        @Size(max = 500)
        String instruction,

        LocalDate expectedCompletionDate
    ) {}

    public record RejectMaintenanceRequest(
        @NotBlank(message = "Rejection reason is required")
        @Size(max = 500)
        String reason
    ) {}

    public record ChangePriorityRequest(
        @NotBlank(message = "Priority is required")
        String priority,

        @Size(max = 500)
        String remarks
    ) {}

    public record ReopenMaintenanceRequest(
        @NotBlank(message = "Reopen reason is required")
        @Size(max = 500)
        String reason
    ) {}

    public record CloseMaintenanceRequest(
        @Size(max = 500)
        String remarks
    ) {}

    public record NeedMaterialsRequest(
        @NotBlank(message = "Reason / required materials note is required")
        @Size(max = 500)
        String reason
    ) {}

    public record ConfirmResolvedRequest(
        @NotNull(message = "Rating is required (1 to 5 stars)")
        @Min(value = 1, message = "Rating must be at least 1")
        @Max(value = 5, message = "Rating must be at most 5")
        Integer rating,

        @Size(max = 500)
        String feedback
    ) {}

    public record AttachmentDto(
        Long id,
        String url,
        String originalFilename,
        String attachmentType,
        LocalDateTime createdAt
    ) {}

    public record StatusHistoryDto(
        Long id,
        String oldStatus,
        String newStatus,
        String changedByName,
        String changedByRole,
        String remarks,
        LocalDateTime createdAt
    ) {}

    public record RatingDto(
        int rating,
        String feedback,
        LocalDateTime createdAt
    ) {}

    public record MaintenanceSummaryResponse(
        Long id,
        String requestCode,
        Long studentId,
        String studentName,
        String studentRegNo,
        String studentPhone,
        String category,
        String title,
        String hostelBlock,
        String roomNumber,
        String priority,
        String status,
        Long assignedWorkerId,
        String assignedWorkerName,
        String assignedWorkerType,
        LocalDate expectedCompletionDate,
        LocalDateTime createdAt,
        LocalDateTime updatedAt,
        LocalDateTime completedAt,
        LocalDateTime resolvedAt,
        Integer rating
    ) {}

    public record MaintenanceDetailResponse(
        Long id,
        String requestCode,
        Long studentId,
        String studentName,
        String studentRegNo,
        String studentPhone,
        String category,
        String title,
        String description,
        String hostelBlock,
        String roomNumber,
        String priority,
        String preferredTimeSlot,
        String status,
        Long assignedWorkerId,
        String assignedWorkerName,
        String assignedWorkerEmployeeId,
        String assignedWorkerType,
        String assignedWorkerPhone,
        String wardenInstruction,
        LocalDate expectedCompletionDate,
        String rejectionReason,
        String onHoldReason,
        String workerRemarks,
        String reopenReason,
        LocalDateTime createdAt,
        LocalDateTime updatedAt,
        LocalDateTime completedAt,
        LocalDateTime resolvedAt,
        List<AttachmentDto> attachments,
        List<StatusHistoryDto> history,
        RatingDto rating
    ) {}

    public record WardenStatsResponse(
        long pending,
        long assigned,
        long inProgress,
        long completed,
        long reopened,
        long onHold,
        long resolved,
        long total,
        long activeWorkers
    ) {}

    public record WorkerStatsResponse(
        long assigned,
        long inProgress,
        long completedToday,
        long onHold,
        long totalCompleted,
        Double averageRating
    ) {}

    public record WorkerPerformanceItem(
        Long workerId,
        String employeeId,
        String fullName,
        String workerType,
        boolean active,
        long tasksCompleted,
        long activeTasks,
        Double averageRating,
        long ratingCount
    ) {}

    public record AdminReportResponse(
        long totalRequests,
        long openRequests,
        long resolvedRequests,
        double averageResolutionHours,
        long pendingOver48HoursCount,
        Map<String, Long> categoryCounts,
        Map<String, Long> statusCounts,
        List<WorkerPerformanceItem> workerPerformance,
        List<MaintenanceSummaryResponse> pendingOver48Hours
    ) {}
}

