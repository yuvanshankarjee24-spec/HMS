package com.hostel.dto;

import jakarta.validation.constraints.*;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

public class ParcelDtos {

    public record StudentSearchItem(
        Long id,
        String fullName,
        String registerNumber,
        String roomNumber,
        String hostelBlock,
        String phone
    ) {}

    public record ParcelStatusHistoryDto(
        Long id,
        String oldStatus,
        String newStatus,
        String changedByName,
        String changedByRole,
        String remarks,
        LocalDateTime createdAt
    ) {}

    public record ParcelAttachmentDto(
        Long id,
        String url,
        String originalFilename
    ) {}

    public record ParcelResponse(
        Long id,
        String parcelCode,
        Long studentId,
        String studentName,
        String studentRegNo,
        String studentRoom,
        String studentPhone,
        String studentPhotoUrl,
        String hostelBlock,
        String courierName,
        String trackingNumber,
        String parcelType,
        String description,
        boolean cod,
        BigDecimal codAmount,
        boolean codPaymentPending,
        boolean codCollected,
        String storageLocation,
        String status,
        LocalDateTime receivedAt,
        LocalDateTime deliveredAt,
        String deliveryRemarks,
        String handoverToName,
        String handoverReason,
        String returnOrUnclaimedReason,
        long daysWaiting,
        boolean longPending,
        String qrToken,
        String pickupCode,
        boolean tokenUsed,
        List<ParcelAttachmentDto> attachments,
        List<ParcelStatusHistoryDto> history
    ) {}

    public record ParcelScanVerifyResponse(
        boolean valid,
        String errorMessage,
        ParcelResponse parcel
    ) {}

    public record ConfirmDeliveryRequest(
        String tokenOrCode,
        Long parcelId,
        Boolean paymentCollected,
        @Size(max = 500)
        String remarks,
        @Size(max = 150)
        String handoverToName,
        @Size(max = 500)
        String handoverReason
    ) {}

    public record ParcelReasonRequest(
        @NotBlank(message = "Reason is required")
        @Size(max = 500)
        String reason
    ) {}

    public record WardenParcelSummaryResponse(
        long receivedToday,
        long waitingForPickup,
        long deliveredToday,
        long longPending,
        long codPending
    ) {}

    public record ParcelDailyCount(
        String period,
        long count
    ) {}

    public record AdminParcelReportResponse(
        long totalParcels,
        long waitingCount,
        long deliveredCount,
        long unclaimedCount,
        long returnedCount,
        double averagePickupHours,
        Map<String, Long> unclaimedByBlock,
        List<ParcelDailyCount> dailyCounts
    ) {}
}

