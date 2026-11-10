package com.hostel.service;

import com.hostel.dto.MaintenanceDtos.*;
import com.hostel.entity.MaintenanceRequest;
import com.hostel.entity.User;
import com.hostel.entity.WorkerProfile;
import com.hostel.exception.CustomExceptions.BadRequestException;
import com.hostel.exception.CustomExceptions.ResourceNotFoundException;
import com.hostel.repository.*;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.stream.Collectors;

@Service
public class WorkerManagementService {

    private final UserRepository userRepository;
    private final WorkerProfileRepository workerProfileRepository;
    private final MaintenanceRequestRepository maintenanceRequestRepository;
    private final RequestRatingRepository requestRatingRepository;
    private final PasswordEncoder passwordEncoder;
    private final AuditService auditService;

    public WorkerManagementService(UserRepository userRepository,
                                   WorkerProfileRepository workerProfileRepository,
                                   MaintenanceRequestRepository maintenanceRequestRepository,
                                   RequestRatingRepository requestRatingRepository,
                                   PasswordEncoder passwordEncoder,
                                   AuditService auditService) {
        this.userRepository = userRepository;
        this.workerProfileRepository = workerProfileRepository;
        this.maintenanceRequestRepository = maintenanceRequestRepository;
        this.requestRatingRepository = requestRatingRepository;
        this.passwordEncoder = passwordEncoder;
        this.auditService = auditService;
    }

    @Transactional
    public WorkerResponse createWorker(CreateWorkerRequest req, User actor) {
        String email = req.email().trim().toLowerCase();
        String empId = req.employeeId().trim().toUpperCase();

        if (userRepository.existsByEmail(email)) {
            throw new BadRequestException("User with email " + email + " already exists");
        }
        if (workerProfileRepository.existsByEmployeeId(empId)) {
            throw new BadRequestException("Worker with employee ID " + empId + " already exists");
        }

        WorkerProfile.WorkerType workerType = parseWorkerType(req.workerType());

        User user = new User();
        user.setEmail(email);
        user.setPasswordHash(passwordEncoder.encode(req.password()));
        user.setRole("WORKER");
        user.setStatus(User.Status.ACTIVE);
        user.setMustChangePassword(false);
        User savedUser = userRepository.save(user);

        WorkerProfile profile = new WorkerProfile();
        profile.setUser(savedUser);
        profile.setEmployeeId(empId);
        profile.setFullName(req.fullName().trim());
        profile.setPhone(req.phone().trim());
        profile.setWorkerType(workerType);
        profile.setActive(true);
        WorkerProfile savedProfile = workerProfileRepository.save(profile);

        auditService.log(actor, "CREATE_WORKER", "EmpId=" + empId + ", Type=" + workerType, "SUCCESS");
        return toWorkerResponse(savedProfile);
    }

    @Transactional
    public WorkerResponse updateWorker(Long workerId, UpdateWorkerRequest req, User actor) {
        WorkerProfile profile = workerProfileRepository.findById(workerId)
                .orElseThrow(() -> new ResourceNotFoundException("Worker not found"));

        String empId = req.employeeId().trim().toUpperCase();
        if (!profile.getEmployeeId().equals(empId) && workerProfileRepository.existsByEmployeeId(empId)) {
            throw new BadRequestException("Another worker already uses employee ID " + empId);
        }

        profile.setFullName(req.fullName().trim());
        profile.setPhone(req.phone().trim());
        profile.setEmployeeId(empId);
        profile.setWorkerType(parseWorkerType(req.workerType()));

        User user = profile.getUser();
        if (req.active() != null) {
            profile.setActive(req.active());
            user.setStatus(req.active() ? User.Status.ACTIVE : User.Status.DISABLED);
        }
        if (req.newPassword() != null && !req.newPassword().isBlank()) {
            if (req.newPassword().length() < 6) {
                throw new BadRequestException("Password must be at least 6 characters");
            }
            user.setPasswordHash(passwordEncoder.encode(req.newPassword()));
        }

        userRepository.save(user);
        WorkerProfile saved = workerProfileRepository.save(profile);

        auditService.log(actor, "UPDATE_WORKER", "WorkerId=" + workerId + ", Active=" + saved.isActive(), "SUCCESS");
        return toWorkerResponse(saved);
    }

    @Transactional
    public WorkerResponse setWorkerActive(Long workerId, boolean active, User actor) {
        WorkerProfile profile = workerProfileRepository.findById(workerId)
                .orElseThrow(() -> new ResourceNotFoundException("Worker not found"));

        profile.setActive(active);
        User user = profile.getUser();
        user.setStatus(active ? User.Status.ACTIVE : User.Status.DISABLED);
        userRepository.save(user);
        WorkerProfile saved = workerProfileRepository.save(profile);

        auditService.log(actor, active ? "ACTIVATE_WORKER" : "DEACTIVATE_WORKER", "WorkerId=" + workerId, "SUCCESS");
        return toWorkerResponse(saved);
    }

    public List<WorkerResponse> listWorkers(String workerTypeStr, Boolean active, String query) {
        WorkerProfile.WorkerType workerType = (workerTypeStr != null && !workerTypeStr.isBlank())
                ? parseWorkerType(workerTypeStr)
                : null;
        String q = (query != null && !query.isBlank()) ? query.trim() : null;
        return workerProfileRepository.searchWorkers(workerType, active, q).stream()
                .map(this::toWorkerResponse)
                .collect(Collectors.toList());
    }

    public List<WorkerResponse> listActiveWorkersForCategory(String categoryStr) {
        if (categoryStr == null || categoryStr.isBlank()) {
            return workerProfileRepository.findByActiveTrueOrderByFullNameAsc().stream()
                    .map(this::toWorkerResponse)
                    .collect(Collectors.toList());
        }
        MaintenanceRequest.Category category;
        try {
            category = MaintenanceRequest.Category.valueOf(categoryStr.trim().toUpperCase());
        } catch (IllegalArgumentException e) {
            return workerProfileRepository.findByActiveTrueOrderByFullNameAsc().stream()
                    .map(this::toWorkerResponse)
                    .collect(Collectors.toList());
        }

        WorkerProfile.WorkerType matched = mapCategoryToWorkerType(category);
        List<WorkerProfile> activeWorkers = workerProfileRepository.findByActiveTrueOrderByFullNameAsc();

        // Matching skill workers first, or filtered directly if any match exists
        List<WorkerProfile> matchedOnly = activeWorkers.stream()
                .filter(w -> w.getWorkerType() == matched ||
                        (category == MaintenanceRequest.Category.BATHROOM_WASHROOM && w.getWorkerType() == WorkerProfile.WorkerType.CLEANING))
                .collect(Collectors.toList());

        if (!matchedOnly.isEmpty()) {
            return matchedOnly.stream().map(this::toWorkerResponse).collect(Collectors.toList());
        }
        return activeWorkers.stream().map(this::toWorkerResponse).collect(Collectors.toList());
    }

    public static WorkerProfile.WorkerType mapCategoryToWorkerType(MaintenanceRequest.Category category) {
        return switch (category) {
            case ROOM_CLEANING -> WorkerProfile.WorkerType.CLEANING;
            case ELECTRICAL -> WorkerProfile.WorkerType.ELECTRICAL;
            case PLUMBING, BATHROOM_WASHROOM -> WorkerProfile.WorkerType.PLUMBING;
            case FURNITURE_CARPENTRY -> WorkerProfile.WorkerType.CARPENTRY;
            case INTERNET_WIFI -> WorkerProfile.WorkerType.IT_NETWORK;
            case OTHER -> WorkerProfile.WorkerType.GENERAL;
        };
    }

    private WorkerProfile.WorkerType parseWorkerType(String typeStr) {
        try {
            return WorkerProfile.WorkerType.valueOf(typeStr.trim().toUpperCase());
        } catch (Exception e) {
            throw new BadRequestException("Invalid worker skill/type: " + typeStr +
                    ". Allowed: CLEANING, ELECTRICAL, PLUMBING, CARPENTRY, IT_NETWORK, GENERAL");
        }
    }

    private WorkerResponse toWorkerResponse(WorkerProfile w) {
        long completed = maintenanceRequestRepository.countByAssignedWorkerIdAndStatus(w.getId(), MaintenanceRequest.Status.COMPLETED)
                + maintenanceRequestRepository.countByAssignedWorkerIdAndStatus(w.getId(), MaintenanceRequest.Status.RESOLVED);
        Double avg = requestRatingRepository.findAverageRatingByWorkerId(w.getId());
        Double roundedAvg = avg != null ? Math.round(avg * 100.0) / 100.0 : null;
        return new WorkerResponse(
                w.getId(),
                w.getUser().getId(),
                w.getEmployeeId(),
                w.getUser().getEmail(),
                w.getFullName(),
                w.getPhone(),
                w.getWorkerType().name(),
                w.isActive(),
                completed,
                roundedAvg,
                w.getCreatedAt()
        );
    }
}

