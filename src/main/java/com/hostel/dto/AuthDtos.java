package com.hostel.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public class AuthDtos {

    public record LoginRequest(
        @NotBlank(message = "Identifier is required")
        String identifier,

        @NotBlank(message = "Password is required")
        String password,

        @NotBlank(message = "Role tab selection is required")
        String selectedRole // STUDENT, WARDEN, ADMIN, STAFF
    ) {}

    public record LoginResponse(
        Long id,
        String name,
        String role,
        boolean mustChangePassword,
        String message
    ) {}

    public record MeResponse(
        Long id,
        String name,
        String email,
        String registerNumber,
        String role,
        boolean mustChangePassword
    ) {}

    public record ChangePasswordRequest(
        @NotBlank(message = "Current password is required")
        String oldPassword,

        @NotBlank(message = "New password is required")
        @Size(min = 8, message = "Password must be at least 8 characters long")
        @Pattern(regexp = "^(?=.*[a-zA-Z])(?=.*\\d).+$", message = "Password must contain at least one letter and one number")
        String newPassword
    ) {}
}
