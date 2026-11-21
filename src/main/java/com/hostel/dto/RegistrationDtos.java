package com.hostel.dto;

import com.hostel.util.RollNumberValidator;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import java.util.List;

public class RegistrationDtos {

    public record StudentRegisterRequest(
        @NotBlank(message = "Roll number is required")
        @Pattern(regexp = RollNumberValidator.ROLL_NUMBER_REGEX, message = "Enter a 12-digit roll number starting with 7155")
        String rollNumber,

        @NotBlank(message = "Full name is required")
        String fullName,

        @NotBlank(message = "Email is required")
        @Email(message = "Valid email is required")
        String email,

        @NotBlank(message = "Phone number is required")
        @Pattern(regexp = "^[0-9]{10}$", message = "Phone must be a valid 10-digit number")
        String phone,

        @NotBlank(message = "Department is required")
        String department,

        int year,

        String gender,
        String dateOfBirth,
        String bloodGroup,
        String programCourse,
        String section,
        Integer admissionYear,
        Long hostelId,
        Long roomId,
        String addressLine1,
        String addressLine2,
        String city,
        String district,
        String state,
        String pincode,
        String nationality,
        String religion,
        String community,
        String aadhaarNumber,
        String fatherName,
        String fatherPhone,
        String motherName,
        String motherPhone,
        String guardianName,
        String guardianPhone,
        String guardianRelation,
        String emergencyContact,
        String medicalConditions,
        String previousInstitution
    ) {}

    public record StaffRegisterRequest(
        @NotBlank(message = "Roll number is required")
        @Pattern(regexp = RollNumberValidator.ROLL_NUMBER_REGEX, message = "Enter a 12-digit roll number starting with 7155")
        String rollNumber,

        @NotBlank(message = "Full name is required")
        String fullName,

        @NotBlank(message = "Email is required")
        @Email(message = "Valid email is required")
        String email,

        @NotBlank(message = "Phone number is required")
        @Pattern(regexp = "^[0-9]{10}$", message = "Phone must be a valid 10-digit number")
        String phone,

        String department,

        @NotBlank(message = "Designation is required")
        String designation,

        String gender,
        String dateOfBirth,
        String joiningDate,
        String address,
        String emergencyContact,

        @NotBlank(message = "Role is required")
        String role // WARDEN, SECURITY, CLEANING_WORKER, MAINTENANCE_STAFF, MESS_STAFF, VENDING_MANAGER, etc.
    ) {}

    public record RegistrationSuccessResponse(
        Long userId,
        String rollNumber,
        String fullName,
        String email,
        String role,
        String message
    ) {}

    public record BulkRowError(
        int rowNumber,
        String rollNumber,
        String reason
    ) {}

    public record BulkRegistrationResponse(
        int totalRows,
        int successCount,
        int failedCount,
        List<BulkRowError> errors,
        String message
    ) {}
}
