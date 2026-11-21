package com.hostel.util;

import com.hostel.exception.CustomExceptions.BadRequestException;
import java.util.regex.Pattern;

/**
 * Utility to enforce 12-digit roll numbers starting with 7155 for students and staff.
 * Pattern: ^7155\d{8}$
 */
public final class RollNumberValidator {

    public static final String ROLL_NUMBER_REGEX = "^7155\\d{8}$";
    private static final Pattern PATTERN = Pattern.compile(ROLL_NUMBER_REGEX);

    private RollNumberValidator() {}

    public static boolean isValid(String rollNumber) {
        if (rollNumber == null) return false;
        return PATTERN.matcher(rollNumber).matches();
    }

    public static String validateAndNormalize(String rollNumber) {
        if (rollNumber == null || !isValid(rollNumber.trim())) {
            throw new BadRequestException("Enter a 12-digit roll number starting with 7155 (e.g. 715521104001).");
        }
        return rollNumber.trim();
    }

    public static String maskAadhaar(String rawOrEncrypted) {
        if (rawOrEncrypted == null || rawOrEncrypted.isBlank()) return "";
        String clean = rawOrEncrypted.replaceAll("[^0-9]", "");
        if (clean.length() >= 4) {
            String last4 = clean.substring(clean.length() - 4);
            return "XXXX-XXXX-" + last4;
        }
        return "XXXX-XXXX-XXXX";
    }
}
