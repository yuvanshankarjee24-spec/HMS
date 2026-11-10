package com.hostel.service;

import com.hostel.dto.AdminDtos.*;
import com.hostel.entity.*;
import com.hostel.exception.CustomExceptions.*;
import com.hostel.repository.*;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Arrays;
import java.util.List;
import java.util.UUID;

@Service
public class AdminService {

    private final UserRepository userRepository;
    private final StudentRepository studentRepository;
    private final StaffProfileRepository staffProfileRepository;
    private final WorkerProfileRepository workerProfileRepository;
    private final PasswordEncoder passwordEncoder;
    private final AuditService auditService;

    private static final List<String> VALID_STAFF_ROLES = Arrays.asList(
            "WARDEN", "SECURITY", "WORKER", "CLEANING_WORKER", "MAINTENANCE_STAFF", "MESS_STAFF", "VENDING_MANAGER"
    );

    public AdminService(UserRepository userRepository,
                        StudentRepository studentRepository,
                        StaffProfileRepository staffProfileRepository,
                        WorkerProfileRepository workerProfileRepository,
                        PasswordEncoder passwordEncoder,
                        AuditService auditService) {
        this.userRepository = userRepository;
        this.studentRepository = studentRepository;
        this.staffProfileRepository = staffProfileRepository;
        this.workerProfileRepository = workerProfileRepository;
        this.passwordEncoder = passwordEncoder;
        this.auditService = auditService;
    }

    @Transactional
    public UserCreationResponse createStudent(CreateStudentRequest req, User actorUser) {
        String email = req.email().trim().toLowerCase();
        String regNo = req.registerNumber().trim().toUpperCase();

        if (userRepository.existsByEmail(email)) {
            throw new BadRequestException("User with email " + email + " already exists");
        }
        if (userRepository.existsByRegisterNumber(regNo) || studentRepository.existsByRegisterNumber(regNo)) {
            throw new BadRequestException("Student with register number " + regNo + " already exists");
        }

        String tempPassword = "Temp@" + UUID.randomUUID().toString().substring(0, 6) + "1";

        User user = new User();
        user.setEmail(email);
        user.setRegisterNumber(regNo);
        user.setPasswordHash(passwordEncoder.encode(tempPassword));
        user.setRole("STUDENT");
        user.setStatus(User.Status.ACTIVE);
        user.setMustChangePassword(true);
        User savedUser = userRepository.save(user);

        Student student = new Student();
        student.setUser(savedUser);
        student.setFullName(req.fullName().trim());
        student.setRegisterNumber(regNo);
        student.setCollegeEmail(email);
        student.setPhone(req.phone().trim());
        student.setDepartment(req.department().trim());
        student.setYear(req.year());
        studentRepository.save(student);

        auditService.log(actorUser, "CREATE_STUDENT", "Email=" + email + ", RegNo=" + regNo, "SUCCESS");

        return new UserCreationResponse(
                savedUser.getId(),
                savedUser.getEmail(),
                tempPassword,
                "STUDENT",
                "Student account created successfully with temporary password"
        );
    }

    @Transactional
    public UserCreationResponse createStaff(CreateStaffRequest req, User actorUser) {
        String email = req.email().trim().toLowerCase();
        String role = req.role().trim().toUpperCase();

        if (!VALID_STAFF_ROLES.contains(role)) {
            throw new BadRequestException("Invalid staff role: " + role + ". Allowed roles: " + VALID_STAFF_ROLES);
        }

        if (userRepository.existsByEmail(email)) {
            throw new BadRequestException("User with email " + email + " already exists");
        }

        String tempPassword = "Temp@" + UUID.randomUUID().toString().substring(0, 6) + "1";

        User user = new User();
        user.setEmail(email);
        user.setPasswordHash(passwordEncoder.encode(tempPassword));
        user.setRole(role);
        user.setStatus(User.Status.ACTIVE);
        user.setMustChangePassword(true);
        User savedUser = userRepository.save(user);

        StaffProfile profile = new StaffProfile();
        profile.setUser(savedUser);
        profile.setFullName(req.fullName().trim());
        profile.setPhone(req.phone().trim());
        profile.setDesignation(req.designation().trim());
        staffProfileRepository.save(profile);

        auditService.log(actorUser, "CREATE_STAFF", "Email=" + email + ", Role=" + role, "SUCCESS");

        return new UserCreationResponse(
                savedUser.getId(),
                savedUser.getEmail(),
                tempPassword,
                role,
                "Staff account created successfully with temporary password"
        );
    }

    public Page<AdminUserItem> searchUsers(String role, String query, Pageable pageable) {
        String roleParam = (role != null && !role.isBlank()) ? role.trim().toUpperCase() : null;
        String queryParam = (query != null && !query.isBlank()) ? query.trim() : null;

        Page<User> users = userRepository.searchUsers(roleParam, queryParam, pageable);

        return users.map(u -> {
            String name = u.getEmail();
            if ("STUDENT".equals(u.getRole())) {
                name = studentRepository.findByUserId(u.getId()).map(Student::getFullName).orElse(u.getEmail());
            } else if ("WORKER".equals(u.getRole())) {
                name = workerProfileRepository.findByUserId(u.getId()).map(WorkerProfile::getFullName).orElse(u.getEmail());
            } else {
                name = staffProfileRepository.findByUserId(u.getId()).map(StaffProfile::getFullName).orElse(u.getEmail());
            }
            return new AdminUserItem(
                    u.getId(),
                    u.getEmail(),
                    u.getRegisterNumber(),
                    name,
                    u.getRole(),
                    u.getStatus().name(),
                    u.isMustChangePassword(),
                    u.getFailedAttempts(),
                    u.getLockedUntil(),
                    u.getCreatedAt()
            );
        });
    }

    @Transactional
    public void updateUserStatus(Long userId, UpdateUserStatusRequest req, User actorUser) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));

        if (actorUser != null && user.getId().equals(actorUser.getId())) {
            throw new BadRequestException("You cannot disable or modify the status of your own account.");
        }

        User.Status newStatus;
        try {
            newStatus = User.Status.valueOf(req.status().trim().toUpperCase());
        } catch (IllegalArgumentException e) {
            throw new BadRequestException("Invalid status: " + req.status() + ". Allowed: ACTIVE, LOCKED, DISABLED");
        }

        user.setStatus(newStatus);
        if (newStatus == User.Status.ACTIVE) {
            user.setFailedAttempts(0);
            user.setLockedUntil(null);
        }

        userRepository.save(user);
        auditService.log(actorUser, "UPDATE_USER_STATUS", "UserId=" + userId + ", Status=" + newStatus, "SUCCESS");
    }

    @Transactional
    public String resetPassword(Long userId, User actorUser) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));

        String tempPassword = "Reset@" + UUID.randomUUID().toString().substring(0, 6) + "1";

        user.setPasswordHash(passwordEncoder.encode(tempPassword));
        user.setMustChangePassword(true);
        user.setFailedAttempts(0);
        user.setLockedUntil(null);
        if (user.getStatus() == User.Status.LOCKED) {
            user.setStatus(User.Status.ACTIVE);
        }
        userRepository.save(user);

        auditService.log(actorUser, "RESET_PASSWORD", "UserId=" + userId, "SUCCESS");
        return tempPassword;
    }
}
