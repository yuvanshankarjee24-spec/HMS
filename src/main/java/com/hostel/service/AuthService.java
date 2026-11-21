package com.hostel.service;

import com.hostel.dto.AuthDtos.*;
import com.hostel.entity.StaffProfile;
import com.hostel.entity.Student;
import com.hostel.entity.User;
import com.hostel.entity.WorkerProfile;
import com.hostel.exception.CustomExceptions.*;
import com.hostel.repository.StaffProfileRepository;
import com.hostel.repository.StudentRepository;
import com.hostel.repository.UserRepository;
import com.hostel.repository.WorkerProfileRepository;
import com.hostel.security.JwtService;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;

@Service
public class AuthService {

    private final UserRepository userRepository;
    private final StudentRepository studentRepository;
    private final StaffProfileRepository staffProfileRepository;
    private final WorkerProfileRepository workerProfileRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;
    private final AuditService auditService;

    @Value("${app.security.force-password-change-on-first-login:${FORCE_PASSWORD_CHANGE_ON_FIRST_LOGIN:true}}")
    private boolean forcePasswordChangeOnFirstLogin;

    private static final List<String> STAFF_ROLES = Arrays.asList(
            "SECURITY", "WORKER", "CLEANING_WORKER", "MAINTENANCE_STAFF", "MESS_STAFF", "VENDING_MANAGER"
    );

    public AuthService(UserRepository userRepository,
                       StudentRepository studentRepository,
                       StaffProfileRepository staffProfileRepository,
                       WorkerProfileRepository workerProfileRepository,
                       PasswordEncoder passwordEncoder,
                       JwtService jwtService,
                       AuditService auditService) {
        this.userRepository = userRepository;
        this.studentRepository = studentRepository;
        this.staffProfileRepository = staffProfileRepository;
        this.workerProfileRepository = workerProfileRepository;
        this.passwordEncoder = passwordEncoder;
        this.jwtService = jwtService;
        this.auditService = auditService;
    }

    @Transactional(noRollbackFor = {UnauthorizedException.class, AccountLockedException.class})
    public LoginResponse login(LoginRequest request, HttpServletResponse response) {
        String identifier = request.identifier().trim();

        // Enforce 12-digit roll number rule starting with 7155 for STUDENT and STAFF roles
        String tab = request.selectedRole() != null ? request.selectedRole().trim().toUpperCase() : "";
        if (("STUDENT".equals(tab) || "STAFF".equals(tab)) && !com.hostel.util.RollNumberValidator.isValid(identifier)) {
            auditService.log(null, "LOGIN", identifier, "FAILURE: Invalid roll number format for student/staff login");
            throw new UnauthorizedException("Invalid username or password");
        }

        Optional<User> userOpt = userRepository.findByIdentifier(identifier);

        if (userOpt.isEmpty()) {
            auditService.log(null, "LOGIN", identifier, "FAILURE: User not found");
            throw new UnauthorizedException("Invalid username or password");
        }

        User user = userOpt.get();

        // Check if account is disabled or soft-deleted
        if (user.getStatus() == User.Status.DISABLED || !user.isActive() || user.getDeletedAt() != null) {
            auditService.log(user, "LOGIN", user.getEmail(), "FAILURE: Account disabled or deactivated");
            throw new ForbiddenException("Account has been disabled. Please contact administrator.");
        }

        // Check lock status
        if (user.getStatus() == User.Status.LOCKED) {
            if (user.getLockedUntil() != null && user.getLockedUntil().isAfter(LocalDateTime.now())) {
                auditService.log(user, "LOGIN", user.getEmail(), "FAILURE: Account locked");
                throw new AccountLockedException("Account is locked due to too many failed attempts. Try again after " + user.getLockedUntil());
            } else {
                // Lock expired - unlock
                user.setStatus(User.Status.ACTIVE);
                user.setFailedAttempts(0);
                user.setLockedUntil(null);
                userRepository.save(user);
            }
        }

        boolean shouldForcePasswordChange = "ADMIN".equals(user.getRole())
                ? user.isMustChangePassword()
                : (forcePasswordChangeOnFirstLogin && user.isMustChangePassword());

        // Check 7-day expiration on temporary password (roll number password)
        if (shouldForcePasswordChange && user.getTempPasswordCreatedAt() != null) {
            if (user.getTempPasswordCreatedAt().isBefore(LocalDateTime.now().minusDays(7))) {
                auditService.log(user, "LOGIN", user.getEmail(), "FAILURE: Temporary password expired (7 days)");
                throw new UnauthorizedException("Temporary password has expired. Please contact administrator to reset it.");
            }
        }

        // Verify password
        boolean passwordMatches = passwordEncoder.matches(request.password(), user.getPasswordHash());

        // Verify role matches selected tab
        boolean roleMatches = verifyRoleTab(user.getRole(), request.selectedRole());

        if (!passwordMatches || !roleMatches) {
            int attempts = user.getFailedAttempts() + 1;
            user.setFailedAttempts(attempts);

            if (attempts >= 5) {
                user.setStatus(User.Status.LOCKED);
                user.setLockedUntil(LocalDateTime.now().plusMinutes(15));
                userRepository.save(user);
                auditService.log(user, "LOGIN", user.getEmail(), "LOCKED: 5 failed attempts reached");
                throw new AccountLockedException("Account is locked for 15 minutes due to 5 consecutive failed attempts.");
            }

            userRepository.save(user);
            auditService.log(user, "LOGIN", user.getEmail(), "FAILURE: Invalid credentials or role mismatch");
            throw new UnauthorizedException("Invalid username or password");
        }

        // Successful login
        user.setFailedAttempts(0);
        user.setLockedUntil(null);
        userRepository.save(user);

        // Generate JWT and set httpOnly cookie
        String token = jwtService.generateToken(user.getId(), user.getEmail(), user.getRole());
        jwtService.setJwtCookie(response, token);

        String displayName = resolveDisplayName(user);
        auditService.log(user, "LOGIN", user.getEmail(), "SUCCESS");

        return new LoginResponse(
                user.getId(),
                displayName,
                user.getRole(),
                shouldForcePasswordChange,
                "Login successful"
        );
    }

    public void logout(HttpServletResponse response, User user) {
        jwtService.clearJwtCookie(response);
        if (user != null) {
            auditService.log(user, "LOGOUT", user.getEmail(), "SUCCESS");
        }
    }

    public MeResponse getMe(User user) {
        String displayName = resolveDisplayName(user);
        boolean shouldForcePasswordChange = "ADMIN".equals(user.getRole())
                ? user.isMustChangePassword()
                : (forcePasswordChangeOnFirstLogin && user.isMustChangePassword());
        return new MeResponse(
                user.getId(),
                displayName,
                user.getEmail(),
                user.getRegisterNumber(),
                user.getRole(),
                shouldForcePasswordChange
        );
    }

    @Transactional
    public void changePassword(User user, ChangePasswordRequest request) {
        if (!passwordEncoder.matches(request.oldPassword(), user.getPasswordHash())) {
            auditService.log(user, "CHANGE_PASSWORD", user.getEmail(), "FAILURE: Incorrect current password");
            throw new BadRequestException("Current password does not match");
        }

        if (passwordEncoder.matches(request.newPassword(), user.getPasswordHash())) {
            throw new BadRequestException("New password cannot be the same as the old password");
        }

        if (user.getRegisterNumber() != null && request.newPassword().trim().equalsIgnoreCase(user.getRegisterNumber().trim())) {
            throw new BadRequestException("New password cannot be the same as your roll number");
        }

        user.setPasswordHash(passwordEncoder.encode(request.newPassword()));
        user.setMustChangePassword(false);
        user.setTempPasswordCreatedAt(null);
        userRepository.save(user);

        auditService.log(user, "CHANGE_PASSWORD", user.getEmail(), "SUCCESS");
    }

    private boolean verifyRoleTab(String actualRole, String selectedRole) {
        if (selectedRole == null) return false;
        String tab = selectedRole.trim().toUpperCase();

        if ("STUDENT".equals(tab)) {
            return "STUDENT".equals(actualRole);
        } else if ("WARDEN".equals(tab)) {
            return "WARDEN".equals(actualRole);
        } else if ("ADMIN".equals(tab)) {
            return "ADMIN".equals(actualRole);
        } else if ("SECURITY".equals(tab)) {
            return "SECURITY".equals(actualRole);
        } else if ("WORKER".equals(tab)) {
            return "WORKER".equals(actualRole);
        } else if ("STAFF".equals(tab)) {
            return STAFF_ROLES.contains(actualRole);
        }
        return false;
    }

    private String resolveDisplayName(User user) {
        if ("STUDENT".equals(user.getRole())) {
            return studentRepository.findByUserId(user.getId())
                    .map(Student::getFullName)
                    .orElse(user.getEmail());
        } else if ("WORKER".equals(user.getRole())) {
            return workerProfileRepository.findByUserId(user.getId())
                    .map(WorkerProfile::getFullName)
                    .orElse(user.getEmail());
        } else {
            return staffProfileRepository.findByUserId(user.getId())
                    .map(StaffProfile::getFullName)
                    .orElse(user.getEmail());
        }
    }
}
