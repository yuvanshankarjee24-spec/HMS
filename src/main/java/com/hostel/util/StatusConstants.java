package com.hostel.util;

import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * Shared system status constants and timezone conversion helpers.
 * Database always stores timestamps in UTC; UI/business deadlines resolve Asia/Kolkata where needed.
 */
public final class StatusConstants {

    private StatusConstants() {}

    public static final ZoneId UTC_ZONE = ZoneOffset.UTC;
    public static final ZoneId IST_ZONE = ZoneId.of("Asia/Kolkata");

    public static LocalDateTime nowUtc() {
        return LocalDateTime.now(UTC_ZONE);
    }

    public static LocalDateTime nowIst() {
        return LocalDateTime.now(IST_ZONE);
    }

    public static LocalDate todayIst() {
        return LocalDate.now(IST_ZONE);
    }

    // Normalized lowercase_snake_case database status constants
    public static final String USER_ACTIVE = "active";
    public static final String USER_LOCKED = "locked";
    public static final String USER_DISABLED = "disabled";

    public static final String OUTPASS_PENDING = "pending";
    public static final String OUTPASS_APPROVED = "approved";
    public static final String OUTPASS_OUT = "out";
    public static final String OUTPASS_RETURNED = "returned";
    public static final String OUTPASS_REJECTED = "rejected";
    public static final String OUTPASS_EXPIRED = "expired";

    public static final String ATT_PENDING = "pending";
    public static final String ATT_MARKED = "marked";
    public static final String ATT_MISSED = "missed";
    public static final String ATT_ON_OUTPASS = "on_outpass";
    public static final String ATT_MANUAL_PRESENT = "manual_present";
}

