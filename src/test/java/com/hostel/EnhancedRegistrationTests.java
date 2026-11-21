package com.hostel;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.hostel.dto.AuthDtos.ChangePasswordRequest;
import com.hostel.dto.AuthDtos.LoginRequest;
import com.hostel.dto.RegistrationDtos.*;
import com.hostel.entity.User;
import com.hostel.repository.StudentRepository;
import com.hostel.repository.UserRepository;
import com.hostel.service.AdminRegistrationService;
import com.hostel.service.AuthService;
import com.hostel.util.RollNumberValidator;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
public class EnhancedRegistrationTests {

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private StudentRepository studentRepository;

    @Autowired
    private AdminRegistrationService registrationService;

    @Autowired
    private AuthService authService;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    @DisplayName("Roll Number Validation strictly enforces 12 digits starting with 7155")
    void testRollNumberRegex() {
        // Valid roll numbers
        assertTrue(RollNumberValidator.isValid("715521104001"));
        assertTrue(RollNumberValidator.isValid("715522106099"));

        // Invalid roll numbers
        assertFalse(RollNumberValidator.isValid("71552110400"));   // 11 digits
        assertFalse(RollNumberValidator.isValid("7155211040011")); // 13 digits
        assertFalse(RollNumberValidator.isValid("123456789012"));  // Not starting with 7155
        assertFalse(RollNumberValidator.isValid("71552110400A"));  // Contains letter
        assertFalse(RollNumberValidator.isValid(" 715521104001 ")); // Spaces un-normalized
        assertFalse(RollNumberValidator.isValid(null));
        assertFalse(RollNumberValidator.isValid(""));
    }

    @Test
    @DisplayName("Admin user is seeded with mustChangePassword = true and can login with 10-digit credentials")
    void testAdminSeeded() {
        var admins = userRepository.findByRoleAndStatus("ADMIN", User.Status.ACTIVE);
        assertFalse(admins.isEmpty(), "At least one administrator should be seeded");
        User admin = admins.get(0);
        assertTrue(admin.isMustChangePassword(), "Admin must be flagged to change password on first login");
        assertEquals("1234567890", admin.getRegisterNumber(), "Admin username should be 1234567890");

        // Verify admin can login with 1234567890 / 1234567890
        MockHttpServletResponse httpResp = new MockHttpServletResponse();
        var loginResp = authService.login(new LoginRequest("1234567890", "1234567890", "ADMIN"), httpResp);
        assertNotNull(loginResp);
        assertTrue(loginResp.mustChangePassword());
    }

    @Test
    @Transactional
    @DisplayName("Student registration sets temporary password to roll number and enforces password change")
    void testStudentRegistrationFlow() {
        String roll = "715521104055";
        String email = "test.student55@college.edu";

        User adminActor = userRepository.findByRoleAndStatus("ADMIN", User.Status.ACTIVE).get(0);

        StudentRegisterRequest req = new StudentRegisterRequest(
                roll, "Naveen Kumar", email, "9876543210", "Computer Science", 2,
                "Male", "2004-05-15", "O+", "B.E. CSE", "A", 2022, null, null,
                "123 Main St", "Campus Road", "Coimbatore", "Coimbatore", "Tamil Nadu", "641062",
                "Indian", "Hindu", "BC", "123456789012", "Father Name", "9876543211",
                "Mother Name", "9876543212", "Guardian Name", "9876543213", "Uncle",
                "9876543214", "None", "School of Excellence"
        );

        RegistrationSuccessResponse resp = registrationService.registerStudent(req, null, null, adminActor);
        assertNotNull(resp);
        assertEquals(roll, resp.rollNumber());

        // Verify user in DB
        User user = userRepository.findByRegisterNumber(roll).orElseThrow();
        assertEquals("STUDENT", user.getRole());
        assertTrue(user.isMustChangePassword());
        assertTrue(passwordEncoder.matches(roll, user.getPasswordHash()), "Temporary password must equal roll number");

        // Verify login with temporary password
        MockHttpServletResponse httpResp = new MockHttpServletResponse();
        var loginResp = authService.login(new LoginRequest(roll, roll, "STUDENT"), httpResp);
        assertNotNull(loginResp);
        assertTrue(loginResp.mustChangePassword(), "Login response must flag mustChangePassword");

        // Attempting to change password to the same roll number must fail
        assertThrows(Exception.class, () -> {
            authService.changePassword(user, new ChangePasswordRequest(roll, roll));
        });

        // Changing to a strong password succeeds
        String newPassword = "SecurePassword@2026";
        authService.changePassword(user, new ChangePasswordRequest(roll, newPassword));

        User updatedUser = userRepository.findById(user.getId()).orElseThrow();
        assertFalse(updatedUser.isMustChangePassword());
        assertTrue(passwordEncoder.matches(newPassword, updatedUser.getPasswordHash()));

        // Old temporary password should no longer work
        MockHttpServletResponse httpRespFail = new MockHttpServletResponse();
        assertThrows(Exception.class, () -> {
            authService.login(new LoginRequest(roll, roll, "STUDENT"), httpRespFail);
        });

        // New password works
        MockHttpServletResponse httpRespSuccess = new MockHttpServletResponse();
        var newLoginResp = authService.login(new LoginRequest(roll, newPassword, "STUDENT"), httpRespSuccess);
        assertNotNull(newLoginResp);
        assertFalse(newLoginResp.mustChangePassword());
    }

    @Test
    @Transactional
    @DisplayName("Temporary password older than 7 days is rejected with expiration notice")
    void testTemporaryPasswordExpiration() {
        String roll = "715521104077";
        String email = "expired.student77@college.edu";
        User adminActor = userRepository.findByRoleAndStatus("ADMIN", User.Status.ACTIVE).get(0);

        StudentRegisterRequest req = new StudentRegisterRequest(
                roll, "Expired Student", email, "9876543215", "Information Tech", 1,
                "Female", "2005-08-20", "A+", "B.Tech IT", "B", 2023, null, null,
                null, null, null, null, null, null, "Indian", null, null, null,
                null, null, null, null, null, null, null, null, null, null
        );

        registrationService.registerStudent(req, null, null, adminActor);

        User user = userRepository.findByRegisterNumber(roll).orElseThrow();
        // Set temp password created 8 days ago
        user.setTempPasswordCreatedAt(LocalDateTime.now().minusDays(8));
        userRepository.save(user);

        MockHttpServletResponse httpResp = new MockHttpServletResponse();
        Exception ex = assertThrows(Exception.class, () -> {
            authService.login(new LoginRequest(roll, roll, "STUDENT"), httpResp);
        });
        assertTrue(ex.getMessage().toLowerCase().contains("expired"), "Message should mention expired temporary password");
    }

    @Test
    @Transactional
    @DisplayName("Bulk registration parses CSV, validates rows, and reports errors")
    void testBulkRegistration() {
        User adminActor = userRepository.findByRoleAndStatus("ADMIN", User.Status.ACTIVE).get(0);

        String csvData = "RollNumber,FullName,Email,Phone,Department,Year\n" +
                "715521104081,Valid Student 1,bulk1@student.edu,9876543221,CSE,1\n" +
                "12345,Invalid Roll,bulk2@student.edu,9876543222,ECE,1\n" + // Invalid roll (non-7155, 5 digits)
                "715521104082,Valid Student 2,bulk3@student.edu,9876543223,MECH,2\n";

        MockMultipartFile file = new MockMultipartFile("file", "students.csv", "text/csv", csvData.getBytes());

        BulkRegistrationResponse resp = registrationService.bulkRegister(file, "STUDENT", adminActor);
        assertNotNull(resp);
        assertEquals(3, resp.totalRows());
        assertEquals(2, resp.successCount());
        assertEquals(1, resp.failedCount());
        assertEquals(1, resp.errors().size());
        assertEquals("12345", resp.errors().get(0).rollNumber());
    }

    @Test
    @Transactional
    @DisplayName("Requirement 8: Register 715512345678 -> login with roll number succeeds, bulk upload login succeeds, wrong password fails, admin reset returns to roll number")
    void testRequirement8ExplicitFlow() {
        User adminActor = userRepository.findByRoleAndStatus("ADMIN", User.Status.ACTIVE).get(0);
        String roll = "715512345678";
        String email = "student.req8@college.edu";

        // 1. Single registration
        StudentRegisterRequest req = new StudentRegisterRequest(
                roll, "Requirement Eight Student", email, "9876543299", "CSE", 2,
                "Male", "2004-01-01", "B+", "B.E. CSE", "A", 2022, null, null,
                null, null, null, null, null, null, "Indian", null, null, null,
                null, null, null, null, null, null, null, null, null, null
        );
        RegistrationSuccessResponse singleResp = registrationService.registerStudent(req, null, null, adminActor);
        assertNotNull(singleResp);
        assertEquals(roll, singleResp.rollNumber());

        // 2. Log in with 715512345678 / 715512345678 -> succeeds
        MockHttpServletResponse httpRespSuccess = new MockHttpServletResponse();
        var loginResp = authService.login(new LoginRequest(roll, roll, "STUDENT"), httpRespSuccess);
        assertNotNull(loginResp);
        assertEquals("STUDENT", loginResp.role());

        // 3. Log in with wrong password -> fails
        MockHttpServletResponse httpRespFail = new MockHttpServletResponse();
        Exception wrongPassEx = assertThrows(Exception.class, () -> {
            authService.login(new LoginRequest(roll, "WrongPassword@99", "STUDENT"), httpRespFail);
        });
        assertEquals("Invalid username or password", wrongPassEx.getMessage());

        // 4. Bulk registration -> login with roll number succeeds
        String bulkRoll = "715599887766";
        String bulkEmail = "bulk.req8@college.edu";
        String csvData = "RollNumber,FullName,Email,Phone,Department,Year\n" +
                bulkRoll + ",Bulk Student Req8," + bulkEmail + ",9876543298,ECE,1\n";
        MockMultipartFile bulkFile = new MockMultipartFile("file", "bulk_req8.csv", "text/csv", csvData.getBytes());
        BulkRegistrationResponse bulkResp = registrationService.bulkRegister(bulkFile, "STUDENT", adminActor);
        assertEquals(1, bulkResp.successCount());

        MockHttpServletResponse httpRespBulk = new MockHttpServletResponse();
        var bulkLoginResp = authService.login(new LoginRequest(bulkRoll, bulkRoll, "STUDENT"), httpRespBulk);
        assertNotNull(bulkLoginResp);
        assertEquals("STUDENT", bulkLoginResp.role());

        // 5. Change password to new password
        User user = userRepository.findByRegisterNumber(roll).orElseThrow();
        String newPassword = "NewSecurePassword@2026";
        authService.changePassword(user, new ChangePasswordRequest(roll, newPassword));

        // 6. Admin resets password -> returns to roll number
        registrationService.resetPasswordToRollNumber(user.getId(), adminActor);
        MockHttpServletResponse httpRespAfterReset = new MockHttpServletResponse();
        var afterResetLoginResp = authService.login(new LoginRequest(roll, roll, "STUDENT"), httpRespAfterReset);
        assertNotNull(afterResetLoginResp);
        assertEquals("STUDENT", afterResetLoginResp.role());
    }
}
