package com.hostel.dto;

import jakarta.validation.constraints.*;
import java.time.LocalDateTime;
import java.util.List;

public class CircularDtos {

    public record CircularAttachmentDto(
        Long id,
        String url,
        String originalFilename,
        String contentType,
        long fileSize
    ) {}

    public record CircularVersionDto(
        int versionNumber,
        String title,
        String description,
        String rulesAndRegulations,
        String editedByName,
        String editNote,
        LocalDateTime createdAt
    ) {}

    public record CircularResponse(
        Long id,
        String circularCode,
        String type,
        String title,
        String description,
        String rulesAndRegulations,
        LocalDateTime eventDateTime,
        String venue,
        String priority,
        String status,
        boolean requireAcknowledgement,
        String targetScope,
        String hostelBlock,
        LocalDateTime scheduledPublishAt,
        LocalDateTime expiresAt,
        LocalDateTime publishedAt,
        int version,
        boolean updatedFlag,
        String createdByName,
        LocalDateTime createdAt,
        boolean read,
        LocalDateTime readAt,
        boolean acknowledged,
        LocalDateTime acknowledgedAt,
        String myRsvpStatus,
        long attendingCount,
        long notAttendingCount,
        long totalTargetedCount,
        long readCount,
        long acknowledgedCount,
        List<CircularAttachmentDto> attachments,
        List<CircularVersionDto> versions
    ) {}

    public record RsvpRequest(
        @NotBlank(message = "RSVP status is required (ATTENDING or NOT_ATTENDING)")
        String rsvpStatus
    ) {}

    public record CircularTrackerRow(
        Long studentId,
        String fullName,
        String registerNumber,
        String roomNumber,
        String hostelBlock,
        boolean read,
        LocalDateTime readAt,
        boolean acknowledged,
        LocalDateTime acknowledgedAt,
        String rsvpStatus
    ) {}

    public record CircularTrackingResponse(
        Long circularId,
        String circularCode,
        String title,
        long totalTargeted,
        long readCount,
        long acknowledgedCount,
        long attendingCount,
        List<CircularTrackerRow> students
    ) {}

    public record WardenCircularSummaryResponse(
        long publishedCount,
        long draftCount,
        long scheduledCount,
        long unreadOrUnackCount,
        long upcomingEventsCount
    ) {}
}

