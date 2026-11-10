package com.hostel.dto;

import java.time.LocalDateTime;

public class NotificationDtos {

    public record NotificationResponse(
        Long id,
        String title,
        String message,
        String referenceType,
        Long referenceId,
        boolean read,
        LocalDateTime createdAt
    ) {}
}

