package com.hostel;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.hostel.dto.AuthDtos.LoginRequest;
import com.hostel.entity.User;
import com.hostel.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.transaction.annotation.Transactional;

import static org.hamcrest.Matchers.containsString;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
public class AuthTests {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private ObjectMapper objectMapper;

    @BeforeEach
    void setUp() {
        // Ensure test user exists
        if (userRepository.findByEmail("test_student@college.edu").isEmpty()) {
            User user = new User();
            user.setEmail("test_student@college.edu");
            user.setRegisterNumber("TESTREG01");
            user.setPasswordHash(passwordEncoder.encode("Test@1234"));
            user.setRole("STUDENT");
            user.setStatus(User.Status.ACTIVE);
            user.setFailedAttempts(0);
            userRepository.save(user);
        }
    }

    @Test
    @DisplayName("Login Success with valid credentials and matching role")
    void testLoginSuccess() throws Exception {
        LoginRequest req = new LoginRequest("test_student@college.edu", "Test@1234", "STUDENT");

        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isOk())
                .andExpect(cookie().exists("hostel_jwt"))
                .andExpect(cookie().httpOnly("hostel_jwt", true))
                .andExpect(jsonBody -> {
                    String json = jsonBody.getResponse().getContentAsString();
                    assertTrue(json.contains("Login successful"));
                });
    }

    @Test
    @DisplayName("Login Failure with incorrect password returns 401 Invalid credentials")
    void testLoginFailureWrongPassword() throws Exception {
        LoginRequest req = new LoginRequest("test_student@college.edu", "WrongPassword123", "STUDENT");

        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.message").value("Invalid credentials"));
    }

    @Test
    @DisplayName("Role mismatch login rejected with generic Invalid credentials")
    void testRoleMismatchRejected() throws Exception {
        // Student user attempting to log in on the WARDEN tab
        LoginRequest req = new LoginRequest("test_student@college.edu", "Test@1234", "WARDEN");

        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.message").value("Invalid credentials"));
    }

    @Test
    @DisplayName("Lockout after 5 failed login attempts")
    void testLockoutAfter5Failures() throws Exception {
        String testEmail = "lockout_user@college.edu";
        User user = new User();
        user.setEmail(testEmail);
        user.setRegisterNumber("LOCK001");
        user.setPasswordHash(passwordEncoder.encode("Correct@123"));
        user.setRole("STUDENT");
        user.setStatus(User.Status.ACTIVE);
        user.setFailedAttempts(0);
        userRepository.save(user);

        LoginRequest wrongReq = new LoginRequest(testEmail, "WrongPass1!", "STUDENT");

        // 4 failed attempts -> 401
        for (int i = 1; i <= 4; i++) {
            mockMvc.perform(post("/api/auth/login")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(wrongReq)))
                    .andExpect(status().isUnauthorized());
        }

        // 5th failed attempt -> account locked
        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(wrongReq)))
                .andExpect(status().isLocked())
                .andExpect(jsonPath("$.message", containsString("locked for 15 minutes")));

        User lockedUser = userRepository.findByEmail(testEmail).orElseThrow();
        assertEquals(User.Status.LOCKED, lockedUser.getStatus());
        assertNotNull(lockedUser.getLockedUntil());
    }
}
