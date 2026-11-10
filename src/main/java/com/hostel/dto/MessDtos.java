package com.hostel.dto;

import jakarta.validation.constraints.*;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;
import java.util.Map;

public class MessDtos {

    public record FoodItemResponse(
        Long id,
        String name,
        String category,
        BigDecimal price,
        String photoUrl,
        boolean active
    ) {}

    public record PeriodConfigDto(
        @NotBlank String period,
        @NotNull LocalTime startTime,
        @NotNull LocalTime endTime,
        @Min(0) @Max(720) int cutoffMinutesBefore,
        @Min(1) @Max(120) int qrOpenMinutesBefore
    ) {}

    public record MenuScheduleItemRequest(
        @NotNull LocalDate menuDate,
        @NotBlank String period,
        @NotNull Long foodItemId,
        @Min(1) int dailyStock,
        @Min(1) int maxPerStudent,
        boolean specialMenu,
        boolean messClosed
    ) {}

    public record MenuScheduleResponse(
        Long id,
        LocalDate menuDate,
        String period,
        Long foodItemId,
        String foodName,
        String category,
        BigDecimal price,
        String photoUrl,
        int dailyStock,
        int remainingStock,
        int maxPerStudent,
        boolean specialMenu,
        boolean messClosed,
        boolean bookingOpen,
        String bookingMessage
    ) {}

    public record BookItemQty(
        @NotNull Long menuScheduleId,
        @Min(1) int quantity
    ) {}

    public record BookTokenRequest(
        @NotNull LocalDate menuDate,
        @NotBlank String period,
        @NotEmpty List<BookItemQty> items
    ) {}

    public record MessTokenItemDto(
        Long id,
        Long foodItemId,
        String foodName,
        String category,
        int quantity,
        BigDecimal unitPrice,
        BigDecimal lineTotal,
        Integer deliveredQuantity,
        String undeliveredReason
    ) {}

    public record TokenHistoryDto(
        String oldStatus,
        String newStatus,
        String changedByName,
        String remarks,
        LocalDateTime createdAt
    ) {}

    public record MessTokenResponse(
        Long id,
        String tokenCode,
        Long studentId,
        String studentName,
        String studentRegNo,
        String studentRoom,
        String studentPhotoUrl,
        String hostelBlock,
        LocalDate menuDate,
        String period,
        BigDecimal totalAmount,
        String status,
        boolean qrCurrentlyActive,
        boolean qrLockedBeforeWindow,
        LocalDateTime qrOpensAt,
        LocalDateTime mealEndsAt,
        long secondsUntilQrOpens,
        String qrToken,
        String pickupCode,
        boolean tokenUsed,
        boolean tokenClosed,
        boolean cancellable,
        LocalDateTime deliveredAt,
        String deliveryRemarks,
        LocalDateTime createdAt,
        List<MessTokenItemDto> items,
        List<TokenHistoryDto> history
    ) {}

    public record MessScanVerifyResponse(
        boolean valid,
        String errorMessage,
        MessTokenResponse token
    ) {}

    public record DeliveredItemLine(
        @NotNull Long tokenItemId,
        @Min(0) int deliveredQuantity,
        @Size(max = 500) String undeliveredReason
    ) {}

    public record ConfirmMessDeliveryRequest(
        String tokenOrCode,
        Long tokenId,
        @Size(max = 500) String remarks,
        List<DeliveredItemLine> itemDeliveries
    ) {}

    public record ItemKitchenSummary(
        Long foodItemId,
        String foodName,
        String category,
        long totalBookedQty,
        long totalDeliveredQty
    ) {}

    public record WardenMessSummaryResponse(
        LocalDate date,
        String period,
        long totalTokens,
        long deliveredTokens,
        long pendingTokens,
        long expiredTokens,
        long cancelledTokens,
        BigDecimal totalRevenue,
        List<ItemKitchenSummary> kitchenSummary
    ) {}

    public record AdminMessReportResponse(
        long totalTokens30Days,
        long deliveredCount,
        long expiredNoShowCount,
        double noShowRatePercentage,
        BigDecimal totalRevenue30Days,
        Map<String, Long> itemConsumption,
        Map<String, Long> tokensByBlock
    ) {}
}

