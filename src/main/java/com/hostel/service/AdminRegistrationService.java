package com.hostel.service;

import com.hostel.dto.RegistrationDtos.*;
import com.hostel.entity.*;
import com.hostel.exception.CustomExceptions.BadRequestException;
import com.hostel.exception.CustomExceptions.ConflictException;
import com.hostel.exception.CustomExceptions.ResourceNotFoundException;
import com.hostel.repository.*;
import com.hostel.util.RollNumberValidator;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.*;

@Service
public class AdminRegistrationService {

    private final UserRepository userRepository;
    private final StudentRepository studentRepository;
    private final StaffProfileRepository staffProfileRepository;
    private final HostelRepository hostelRepository;
    private final RoomRepository roomRepository;
    private final BedRepository bedRepository;
    private final PasswordEncoder passwordEncoder;
    private final AttachmentStorageService attachmentStorageService;
    private final AuditService auditService;

    @Value("${app.security.force-password-change-on-first-login:${FORCE_PASSWORD_CHANGE_ON_FIRST_LOGIN:true}}")
    private boolean forcePasswordChangeOnFirstLogin;

    public AdminRegistrationService(UserRepository userRepository,
                                    StudentRepository studentRepository,
                                    StaffProfileRepository staffProfileRepository,
                                    HostelRepository hostelRepository,
                                    RoomRepository roomRepository,
                                    BedRepository bedRepository,
                                    PasswordEncoder passwordEncoder,
                                    AttachmentStorageService attachmentStorageService,
                                    AuditService auditService) {
        this.userRepository = userRepository;
        this.studentRepository = studentRepository;
        this.staffProfileRepository = staffProfileRepository;
        this.hostelRepository = hostelRepository;
        this.roomRepository = roomRepository;
        this.bedRepository = bedRepository;
        this.passwordEncoder = passwordEncoder;
        this.attachmentStorageService = attachmentStorageService;
        this.auditService = auditService;
    }

    public List<Hostel> getHostels() {
        return hostelRepository.findAll();
    }

    @Transactional
    public RegistrationSuccessResponse registerStudent(StudentRegisterRequest req,
                                                        MultipartFile photo,
                                                        MultipartFile idProof,
                                                        User actor) {
        String rollNumber = RollNumberValidator.validateAndNormalize(req.rollNumber());
        String email = req.email().trim().toLowerCase();

        // 1. Uniqueness checks
        if (userRepository.existsByRegisterNumber(rollNumber) || studentRepository.findByRegisterNumber(rollNumber).isPresent()) {
            throw new ConflictException("Roll number " + rollNumber + " is already registered.");
        }
        if (userRepository.existsByEmail(email)) {
            throw new ConflictException("Email address " + email + " is already in use.");
        }

        // 2. Validate pincode if provided
        if (req.pincode() != null && !req.pincode().isBlank()) {
            if (!req.pincode().trim().matches("^[0-9]{6}$")) {
                throw new BadRequestException("Pincode must be a 6-digit number");
            }
        }

        // 3. Process optional photo upload
        String photoPath = null;
        if (photo != null && !photo.isEmpty()) {
            attachmentStorageService.validateFiles(new MultipartFile[]{photo}, 1, AttachmentStorageService.IMAGE_TYPES, 2 * 1024 * 1024);
            photoPath = attachmentStorageService.storeFile(photo, "students", rollNumber);
        }

        // 4. Process optional guardian ID proof upload
        String idProofPath = null;
        if (idProof != null && !idProof.isEmpty()) {
            attachmentStorageService.validateFiles(new MultipartFile[]{idProof}, 1, AttachmentStorageService.DOC_AND_IMAGE_TYPES, 4 * 1024 * 1024);
            idProofPath = attachmentStorageService.storeFile(idProof, "documents", rollNumber + "_idproof");
        }

        // 5. Create User (Temporary password = Roll number, must_change_password = true)
        User user = new User();
        user.setEmail(email);
        user.setRegisterNumber(rollNumber);
        user.setName(req.fullName().trim());
        user.setPhone(req.phone().trim());
        user.setRole("STUDENT");
        user.setStatus(User.Status.ACTIVE);
        user.setActive(true);
        user.setPasswordHash(passwordEncoder.encode(rollNumber));
        user.setMustChangePassword(forcePasswordChangeOnFirstLogin);
        user.setTempPasswordCreatedAt(LocalDateTime.now());
        user.setFailedAttempts(0);
        user = userRepository.save(user);

        // 6. Create Student Profile
        Student student = new Student();
        student.setUser(user);
        student.setFullName(req.fullName().trim());
        student.setRegisterNumber(rollNumber);
        student.setCollegeEmail(email);
        student.setPhone(req.phone().trim());
        student.setDepartment(req.department().trim());
        student.setYear(req.year() > 0 ? req.year() : 1);
        student.setPhotoPath(photoPath);
        student.setGender(req.gender());
        student.setBloodGroup(req.bloodGroup());
        student.setProgramCourse(req.programCourse());
        student.setSection(req.section());
        student.setAdmissionYear(req.admissionYear());

        if (req.dateOfBirth() != null && !req.dateOfBirth().isBlank()) {
            try { student.setDateOfBirth(LocalDate.parse(req.dateOfBirth().trim())); } catch (Exception ignored) {}
        }

        // Optional Hostel & Room allocation
        if (req.roomId() != null) {
            long freeBeds = bedRepository.countAvailableBeds();
            if (freeBeds <= 0) {
                throw new ConflictException("No beds available");
            }
            Room room = roomRepository.findById(req.roomId())
                    .orElseThrow(() -> new ResourceNotFoundException("Room not found: " + req.roomId()));
            student.setRoom(room);
            if (room.getHostel() != null) {
                student.setHostel(room.getHostel());
            }
        } else if (req.hostelId() != null) {
            hostelRepository.findById(req.hostelId()).ifPresent(student::setHostel);
        }

        student.setAddressLine1(req.addressLine1());
        student.setAddressLine2(req.addressLine2());
        student.setCity(req.city());
        student.setDistrict(req.district());
        student.setState(req.state());
        student.setPincode(req.pincode());
        student.setNationality(req.nationality() != null ? req.nationality() : "Indian");
        student.setReligion(req.religion());
        student.setCommunity(req.community());

        // Mask/encrypt Aadhaar
        if (req.aadhaarNumber() != null && !req.aadhaarNumber().isBlank()) {
            student.setAadhaarEncrypted(RollNumberValidator.maskAadhaar(req.aadhaarNumber()));
        }

        student.setFatherName(req.fatherName());
        student.setFatherPhone(req.fatherPhone());
        student.setMotherName(req.motherName());
        student.setMotherPhone(req.motherPhone());
        student.setGuardianName(req.guardianName());
        student.setGuardianPhone(req.guardianPhone());
        student.setGuardianRelation(req.guardianRelation());
        student.setEmergencyContact(req.emergencyContact());
        student.setMedicalConditions(req.medicalConditions());
        student.setPreviousInstitution(req.previousInstitution());
        student.setGuardianIdProofPath(idProofPath);
        student.setActive(true);

        studentRepository.save(student);

        auditService.log(actor, "REGISTER_STUDENT", rollNumber, "SUCCESS");

        return new RegistrationSuccessResponse(
            user.getId(),
            rollNumber,
            user.getName(),
            email,
            "STUDENT",
            "Student registered successfully. Temporary password is set to the roll number."
        );
    }

    @Transactional
    public RegistrationSuccessResponse registerStaff(StaffRegisterRequest req,
                                                      MultipartFile photo,
                                                      User actor) {
        String rollNumber = RollNumberValidator.validateAndNormalize(req.rollNumber());
        String email = req.email().trim().toLowerCase();

        if (userRepository.existsByRegisterNumber(rollNumber) || staffProfileRepository.findByRollNumber(rollNumber).isPresent()) {
            throw new ConflictException("Staff ID / Roll number " + rollNumber + " is already registered.");
        }
        if (userRepository.existsByEmail(email)) {
            throw new ConflictException("Email address " + email + " is already in use.");
        }

        String photoPath = null;
        if (photo != null && !photo.isEmpty()) {
            attachmentStorageService.validateFiles(new MultipartFile[]{photo}, 1, AttachmentStorageService.IMAGE_TYPES, 2 * 1024 * 1024);
            photoPath = attachmentStorageService.storeFile(photo, "staff", rollNumber);
        }

        String targetRole = req.role().trim().toUpperCase();

        User user = new User();
        user.setEmail(email);
        user.setRegisterNumber(rollNumber);
        user.setName(req.fullName().trim());
        user.setPhone(req.phone().trim());
        user.setRole(targetRole);
        user.setStatus(User.Status.ACTIVE);
        user.setActive(true);
        user.setPasswordHash(passwordEncoder.encode(rollNumber));
        user.setMustChangePassword(forcePasswordChangeOnFirstLogin);
        user.setTempPasswordCreatedAt(LocalDateTime.now());
        user.setFailedAttempts(0);
        user = userRepository.save(user);

        StaffProfile staff = new StaffProfile();
        staff.setUser(user);
        staff.setRollNumber(rollNumber);
        staff.setFullName(req.fullName().trim());
        staff.setPhone(req.phone().trim());
        staff.setEmail(email);
        staff.setDepartment(req.department());
        staff.setDesignation(req.designation().trim());
        staff.setGender(req.gender());
        staff.setAddress(req.address());
        staff.setEmergencyContact(req.emergencyContact());
        staff.setPhotoPath(photoPath);

        if (req.dateOfBirth() != null && !req.dateOfBirth().isBlank()) {
            try { staff.setDateOfBirth(LocalDate.parse(req.dateOfBirth().trim())); } catch (Exception ignored) {}
        }
        if (req.joiningDate() != null && !req.joiningDate().isBlank()) {
            try { staff.setJoiningDate(LocalDate.parse(req.joiningDate().trim())); } catch (Exception ignored) {}
        }

        staffProfileRepository.save(staff);

        auditService.log(actor, "REGISTER_STAFF", rollNumber, "SUCCESS");

        return new RegistrationSuccessResponse(
            user.getId(),
            rollNumber,
            user.getName(),
            email,
            targetRole,
            "Staff registered successfully. Temporary password is set to the roll number."
        );
    }

    @Transactional
    public BulkRegistrationResponse bulkRegister(MultipartFile file, String registrationType, User actor) {
        if (file == null || file.isEmpty()) {
            throw new BadRequestException("Please select a valid CSV or Excel file to upload.");
        }

        List<BulkRowError> errors = new ArrayList<>();
        int totalRows = 0;
        int successCount = 0;

        try (BufferedReader reader = new BufferedReader(new InputStreamReader(file.getInputStream(), StandardCharsets.UTF_8))) {
            String line;
            int lineNumber = 0;
            while ((line = reader.readLine()) != null) {
                lineNumber++;
                if (lineNumber == 1 && line.toLowerCase().contains("roll")) {
                    // Skip header row
                    continue;
                }
                if (line.trim().isEmpty()) continue;

                totalRows++;
                String[] cols = line.split(",(?=(?:[^\"]*\"[^\"]*\")*[^\"]*$)"); // Split CSV by comma outside quotes
                for (int i = 0; i < cols.length; i++) {
                    cols[i] = cols[i].trim().replaceAll("^\"|\"$", "");
                }

                if (cols.length < 5) {
                    errors.add(new BulkRowError(lineNumber, cols.length > 0 ? cols[0] : "N/A", "Insufficient columns in row."));
                    continue;
                }

                String roll = cols[0];
                if (!RollNumberValidator.isValid(roll)) {
                    errors.add(new BulkRowError(lineNumber, roll, "Roll number must be 12 digits starting with 7155."));
                    continue;
                }

                String name = cols[1];
                String email = cols[2];
                String phone = cols[3];
                String dept = cols[4];

                if (userRepository.existsByRegisterNumber(roll)) {
                    errors.add(new BulkRowError(lineNumber, roll, "Roll number already registered."));
                    continue;
                }
                if (userRepository.existsByEmail(email)) {
                    errors.add(new BulkRowError(lineNumber, roll, "Email already registered: " + email));
                    continue;
                }

                try {
                    if ("STAFF".equalsIgnoreCase(registrationType)) {
                        String designation = cols.length > 5 ? cols[5] : "Staff Member";
                        String role = cols.length > 6 ? cols[6] : "WARDEN";
                        StaffRegisterRequest sreq = new StaffRegisterRequest(
                            roll, name, email, phone, dept, designation, "Other", null, null, null, null, role
                        );
                        registerStaff(sreq, null, actor);
                    } else {
                        int year = 1;
                        if (cols.length > 5) {
                            try { year = Integer.parseInt(cols[5]); } catch (Exception ignored) {}
                        }
                        StudentRegisterRequest req = new StudentRegisterRequest(
                            roll, name, email, phone, dept, year, "Other", null, null, null, null, null,
                            null, null, null, null, null, null, null, null, "Indian", null, null, null,
                            null, null, null, null, null, null, null, null, null, null
                        );
                        registerStudent(req, null, null, actor);
                    }
                    successCount++;
                } catch (Exception e) {
                    errors.add(new BulkRowError(lineNumber, roll, e.getMessage()));
                }
            }
        } catch (Exception ex) {
            throw new BadRequestException("Failed to read CSV file: " + ex.getMessage());
        }

        return new BulkRegistrationResponse(
            totalRows,
            successCount,
            errors.size(),
            errors,
            "Bulk registration processed: " + successCount + " successful, " + errors.size() + " failed."
        );
    }

    @Transactional
    public void resetPasswordToRollNumber(Long userId, User actor) {
        User user = userRepository.findById(userId)
            .orElseThrow(() -> new ResourceNotFoundException("User not found with id: " + userId));

        if (user.getRegisterNumber() == null || user.getRegisterNumber().isBlank()) {
            throw new BadRequestException("User does not have a registered roll number.");
        }

        String rollNumber = user.getRegisterNumber();
        user.setPasswordHash(passwordEncoder.encode(rollNumber));
        user.setMustChangePassword(forcePasswordChangeOnFirstLogin);
        user.setTempPasswordCreatedAt(LocalDateTime.now());
        user.setFailedAttempts(0);
        user.setStatus(User.Status.ACTIVE);
        userRepository.save(user);

        auditService.log(actor, "RESET_PASSWORD", user.getEmail(), "SUCCESS");
    }

    public String generateSampleCsv(String type) {
        if ("STAFF".equalsIgnoreCase(type)) {
            return "RollNumber,FullName,Email,Phone,Department,Designation,Role\n" +
                   "715521104101,Ramesh K,ramesh@hostel.edu,9840112233,Administration,Deputy Warden,WARDEN\n" +
                   "715521104102,Murugan S,murugan@hostel.edu,9840223344,Security,Main Gate Guard,SECURITY\n";
        }
        return "RollNumber,FullName,Email,Phone,Department,Year\n" +
               "715521104001,Aarav Sharma,aarav@student.edu,9876543210,Computer Science,2\n" +
               "715521104002,Pooja Patel,pooja@student.edu,9876543211,Information Tech,1\n";
    }
}
