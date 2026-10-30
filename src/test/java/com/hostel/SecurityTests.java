package com.hostel;

import com.hostel.security.JwtService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.mock.web.MockCookie;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
public class SecurityTests {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JwtService jwtService;

    @Test
    @DisplayName("Unauthenticated request to protected endpoint returns 401 Unauthorized")
    void testUnauthenticatedAccess() throws Exception {
        mockMvc.perform(get("/api/auth/me"))
                .andExpect(status().isUnauthorized());

        mockMvc.perform(get("/api/warden/summary"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("Student cannot call warden endpoints (Returns 403 Forbidden)")
    void testStudentCannotCallWardenEndpoints() throws Exception {
        // Generate JWT for STUDENT role
        String studentToken = jwtService.generateToken(999L, "student1@college.edu", "STUDENT");
        MockCookie jwtCookie = new MockCookie("hostel_jwt", studentToken);

        mockMvc.perform(get("/api/warden/summary")
                        .cookie(jwtCookie))
                .andExpect(status().isForbidden());

        mockMvc.perform(get("/api/warden/rooms")
                        .cookie(jwtCookie))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("Student cannot call admin endpoints (Returns 403 Forbidden)")
    void testStudentCannotCallAdminEndpoints() throws Exception {
        String studentToken = jwtService.generateToken(999L, "student1@college.edu", "STUDENT");
        MockCookie jwtCookie = new MockCookie("hostel_jwt", studentToken);

        mockMvc.perform(get("/api/admin/users")
                        .cookie(jwtCookie))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("Warden can call warden endpoints but cannot call admin endpoints")
    void testWardenRbacRestrictions() throws Exception {
        String wardenToken = jwtService.generateToken(888L, "warden@hostel.edu", "WARDEN");
        MockCookie jwtCookie = new MockCookie("hostel_jwt", wardenToken);

        // Can access warden summary
        mockMvc.perform(get("/api/warden/summary")
                        .cookie(jwtCookie))
                .andExpect(status().isOk());

        // Forbidden on admin users
        mockMvc.perform(get("/api/admin/users")
                        .cookie(jwtCookie))
                .andExpect(status().isForbidden());
    }
}
