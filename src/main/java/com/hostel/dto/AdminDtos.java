package com.hostel.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import java.time.LocalDateTime;

public class AdminDtos {

    public record CreateStudentRequest(
        @NotBlank(message = "Email is required")
        @Email(message = "Invalid email format")
        String email,

        @NotBlank(message = "Register number is required")
        String registerNumber,

        @NotBlank(message = "Full name is required")
        String fullName,

        @NotBlank(message = "Phone number is required")
        String phone,

        @NotBlank(message = "Department is required")
        String department,

        @Min(value = 1, message = "Year must be 1 to 5")
        @Max(value = 5, message = "Year must be 1 to 5")
        int year
    ) {}

    public record CreateStaffRequest(
        @NotBlank(message = "Email is required")
        @Email(message = "Invalid email format")
        String email,

        @NotBlank(message = "Full name is required")
        String fullName,

        @NotBlank(message = "Phone number is required")
        String phone,

        @NotBlank(message = "Designation is required")
        String designation,

        @NotBlank(message = "Role is required")
        String role // WARDEN, SECURITY, CLEANING_WORKER, MAINTENANCE_STAFF, MESS_STAFF, VENDING_MANAGER
    ) {}

    public record UpdateUserStatusRequest(
        @NotBlank(message = "Status is required")
        String status // ACTIVE, LOCKED, DISABLED
    ) {}

    public record AdminUserItem(
        Long id,
        String email,
        String registerNumber,
        String name,
        String role,
        String status,
        boolean mustChangePassword,
        int failedAttempts,
        LocalDateTime lockedUntil,
        LocalDateTime createdAt
    ) {}

    public record UserCreationResponse(
        Long id,
        String email,
        String temporaryPassword,
        String role,
        String message
    ) {}
}
