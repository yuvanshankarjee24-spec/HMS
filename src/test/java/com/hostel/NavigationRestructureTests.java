package com.hostel;

import com.hostel.entity.User;
import com.hostel.repository.UserRepository;
import com.hostel.security.JwtService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.mock.web.MockCookie;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import static org.hamcrest.Matchers.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
public class NavigationRestructureTests {

    @Autowired private MockMvc mockMvc;
    @Autowired private JwtService jwtService;
    @Autowired private UserRepository userRepository;

    private MockCookie cookieWarden, cookieStudent;

    @BeforeEach
    void setUp() {
        User warden = userRepository.findByEmail("warden@hostel.edu").orElseThrow();
        User student = userRepository.findAll().stream()
                .filter(u -> "STUDENT".equals(u.getRole())).findFirst().orElseThrow();
        cookieWarden = new MockCookie("hostel_jwt", jwtService.generateToken(warden.getId(), warden.getEmail(), warden.getRole()));
        cookieStudent = new MockCookie("hostel_jwt", jwtService.generateToken(student.getId(), student.getEmail(), student.getRole()));
    }

    @Test
    @DisplayName("All deprecated student & warden HTML URLs redirect cleanly with 302 to merged/dashboard routes")
    void testAllRouteRedirects() throws Exception {
        String[][] mappings = new String[][]{
            {"/student/mess-booking.html", "/student/mess-tokens.html?tab=book"},
            {"/student/my-tokens.html", "/student/mess-tokens.html?tab=my-tokens"},
            {"/student/raise-request.html", "/student/maintenance.html?tab=raise"},
            {"/student/my-requests.html", "/student/maintenance.html?tab=list"},
            {"/student/booking.html", "/student/dashboard.html"},
            {"/warden/mess-scan.html", "/warden/mess.html?tab=scan"},
            {"/warden/mess-menu.html", "/warden/mess.html?tab=menu"},
            {"/warden/parcel-scan.html", "/warden/parcels.html?tab=scan"},
            {"/warden/workers.html", "/warden/maintenance.html?tab=workers"},
            {"/warden/rooms.html", "/warden/dashboard.html"},
            {"/warden/whitelist.html", "/warden/dashboard.html"}
        };
        for (String[] pair : mappings) {
            mockMvc.perform(get(pair[0]))
                    .andExpect(status().is3xxRedirection())
                    .andExpect(redirectedUrl(pair[1]));
        }
    }

    @Test
    @DisplayName("Warden summary aggregate endpoints return expected metrics and enforce RBAC against students")
    void testWardenSummaryEndpointsAndRbac() throws Exception {
        mockMvc.perform(get("/api/warden/outpasses/summary").cookie(cookieWarden))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.pending", greaterThanOrEqualTo(0)))
                .andExpect(jsonPath("$.currentlyOut", greaterThanOrEqualTo(0)))
                .andExpect(jsonPath("$.lateReturns", greaterThanOrEqualTo(0)));

        mockMvc.perform(get("/api/warden/circulars/summary").cookie(cookieWarden))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.publishedCount", greaterThanOrEqualTo(0)))
                .andExpect(jsonPath("$.unreadOrUnackCount", greaterThanOrEqualTo(0)))
                .andExpect(jsonPath("$.upcomingEventsCount", greaterThanOrEqualTo(0)));

        mockMvc.perform(get("/api/warden/students/summary").cookie(cookieWarden))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalStudents", greaterThanOrEqualTo(0)))
                .andExpect(jsonPath("$.allocatedStudents", greaterThanOrEqualTo(0)));

        mockMvc.perform(get("/api/warden/maintenance/stats").cookie(cookieWarden))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.activeWorkers", greaterThanOrEqualTo(0)));

        mockMvc.perform(get("/api/warden/outpasses").param("status", "LATE").cookie(cookieWarden))
                .andExpect(status().isOk());

        // Student must remain forbidden on all warden summary endpoints
        mockMvc.perform(get("/api/warden/outpasses/summary").cookie(cookieStudent)).andExpect(status().isForbidden());
        mockMvc.perform(get("/api/warden/circulars/summary").cookie(cookieStudent)).andExpect(status().isForbidden());
        mockMvc.perform(get("/api/warden/students/summary").cookie(cookieStudent)).andExpect(status().isForbidden());
    }
}

