package com.hostel;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.hostel.dto.MaintenanceDtos.*;
import com.hostel.entity.Student;
import com.hostel.entity.User;
import com.hostel.entity.WorkerProfile;
import com.hostel.repository.*;
import com.hostel.security.JwtService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockCookie;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;

import static org.hamcrest.Matchers.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
public class MaintenanceTests {

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;
    @Autowired private JwtService jwtService;
    @Autowired private PasswordEncoder passwordEncoder;
    @Autowired private UserRepository userRepository;
    @Autowired private StudentRepository studentRepository;
    @Autowired private WorkerProfileRepository workerProfileRepository;
    @Autowired private NotificationRepository notificationRepository;

    private User studentUserA, studentUserB, wardenUser, adminUser, securityUser, workerUser1, workerUser2;
    private WorkerProfile workerProfile1, workerProfile2;
    private MockCookie cookieStudentA, cookieStudentB, cookieWarden, cookieAdmin, cookieSecurity, cookieWorker1, cookieWorker2;

    @BeforeEach
    void setUp() {
        studentUserA = createStudentUser("maint_studa@college.edu", "MAINTA", "Maint Student A");
        studentUserB = createStudentUser("maint_studb@college.edu", "MAINTB", "Maint Student B");
        wardenUser = userRepository.findByEmail("warden@hostel.edu").orElseThrow();
        adminUser = userRepository.findByEmail("admin@hostel.edu").orElseThrow();
        securityUser = userRepository.findByEmail("security@hostel.edu").orElseThrow();

        workerProfile1 = createWorker("maint_wrk1@hostel.edu", "MWRK01", "Electrician One", WorkerProfile.WorkerType.ELECTRICAL);
        workerUser1 = workerProfile1.getUser();
        workerProfile2 = createWorker("maint_wrk2@hostel.edu", "MWRK02", "Plumber Two", WorkerProfile.WorkerType.PLUMBING);
        workerUser2 = workerProfile2.getUser();

        cookieStudentA = cookieFor(studentUserA);
        cookieStudentB = cookieFor(studentUserB);
        cookieWarden = cookieFor(wardenUser);
        cookieAdmin = cookieFor(adminUser);
        cookieSecurity = cookieFor(securityUser);
        cookieWorker1 = cookieFor(workerUser1);
        cookieWorker2 = cookieFor(workerUser2);
    }

    private MockCookie cookieFor(User u) {
        return new MockCookie("hostel_jwt", jwtService.generateToken(u.getId(), u.getEmail(), u.getRole()));
    }

    private User createStudentUser(String email, String regNo, String name) {
        User u = new User();
        u.setEmail(email);
        u.setRegisterNumber(regNo);
        u.setPasswordHash(passwordEncoder.encode("Test@1234"));
        u.setRole("STUDENT");
        u.setStatus(User.Status.ACTIVE);
        u = userRepository.save(u);

        Student s = new Student();
        s.setUser(u);
        s.setFullName(name);
        s.setRegisterNumber(regNo);
        s.setCollegeEmail(email);
        s.setPhone("+91 9000000001");
        s.setDepartment("CSE");
        s.setYear(2);
        studentRepository.save(s);
        return u;
    }

    private WorkerProfile createWorker(String email, String empId, String name, WorkerProfile.WorkerType type) {
        User u = new User();
        u.setEmail(email);
        u.setPasswordHash(passwordEncoder.encode("Test@1234"));
        u.setRole("WORKER");
        u.setStatus(User.Status.ACTIVE);
        u = userRepository.save(u);

        WorkerProfile wp = new WorkerProfile();
        wp.setUser(u);
        wp.setEmployeeId(empId);
        wp.setFullName(name);
        wp.setPhone("+91 9888877777");
        wp.setWorkerType(type);
        wp.setActive(true);
        return workerProfileRepository.save(wp);
    }

    @Test
    @DisplayName("RBAC: Roles are strictly isolated across Student, Warden, Worker, Admin, and Security maintenance endpoints")
    void testRolePermissions() throws Exception {
        mockMvc.perform(get("/api/warden/maintenance").cookie(cookieStudentA)).andExpect(status().isForbidden());
        mockMvc.perform(get("/api/worker/maintenance").cookie(cookieStudentA)).andExpect(status().isForbidden());
        mockMvc.perform(get("/api/admin/maintenance/reports").cookie(cookieStudentA)).andExpect(status().isForbidden());

        mockMvc.perform(get("/api/student/maintenance").cookie(cookieWorker1)).andExpect(status().isForbidden());
        mockMvc.perform(get("/api/warden/maintenance").cookie(cookieWorker1)).andExpect(status().isForbidden());
        mockMvc.perform(get("/api/admin/maintenance/reports").cookie(cookieWorker1)).andExpect(status().isForbidden());

        mockMvc.perform(get("/api/student/maintenance").cookie(cookieSecurity)).andExpect(status().isForbidden());
        mockMvc.perform(get("/api/warden/maintenance").cookie(cookieSecurity)).andExpect(status().isForbidden());
        mockMvc.perform(get("/api/worker/maintenance").cookie(cookieSecurity)).andExpect(status().isForbidden());

        mockMvc.perform(get("/api/warden/maintenance").cookie(cookieWarden)).andExpect(status().isOk());
        mockMvc.perform(get("/api/admin/maintenance/reports").cookie(cookieWarden)).andExpect(status().isForbidden());
        mockMvc.perform(get("/api/admin/maintenance/reports").cookie(cookieAdmin)).andExpect(status().isOk());
    }

    @Test
    @DisplayName("Ownership & Transition Flow: Student raises -> Warden assigns -> Worker holds/completes -> Student reopens -> Completes -> Confirms & Rates")
    void testEndToEndMaintenanceFlowAndValidation() throws Exception {
        MockMultipartFile photo = new MockMultipartFile(
                "files", "fan.png", "image/png", new byte[]{1, 2, 3, 4});

        // 1. Student A raises request
        MvcResult createRes = mockMvc.perform(multipart("/api/student/maintenance")
                        .file(photo)
                        .param("category", "ELECTRICAL")
                        .param("title", "Fan regulator broken")
                        .param("description", "Ceiling fan stuck at speed 1")
                        .param("hostelBlock", "Block A")
                        .param("roomNumber", "101")
                        .param("priority", "HIGH")
                        .cookie(cookieStudentA))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.requestCode", startsWith("REQ-")))
                .andExpect(jsonPath("$.status").value("PENDING"))
                .andExpect(jsonPath("$.attachments", hasSize(1)))
                .andReturn();

        JsonNode createdJson = objectMapper.readTree(createRes.getResponse().getContentAsString());
        long reqId = createdJson.get("id").asLong();

        // Warden receives notification
        assertTrue(notificationRepository.countByUserIdAndReadFalse(wardenUser.getId()) >= 1);

        // 2. IDOR: Student B cannot view or cancel Student A's request; Worker 1 cannot view unassigned task
        mockMvc.perform(get("/api/student/maintenance/" + reqId).cookie(cookieStudentB))
                .andExpect(status().isForbidden());
        mockMvc.perform(post("/api/student/maintenance/" + reqId + "/cancel").cookie(cookieStudentB))
                .andExpect(status().isForbidden());
        mockMvc.perform(get("/api/worker/maintenance/" + reqId).cookie(cookieWorker1))
                .andExpect(status().isForbidden());

        // Invalid transition: Student A cannot confirm resolved while PENDING
        mockMvc.perform(post("/api/student/maintenance/" + reqId + "/confirm-resolved")
                        .cookie(cookieStudentA)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new ConfirmResolvedRequest(5, "Good"))))
                .andExpect(status().isBadRequest());

        // 3. Warden assigns Worker 1
        mockMvc.perform(post("/api/warden/maintenance/" + reqId + "/assign")
                        .cookie(cookieWarden)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                new AssignWorkerRequest(workerProfile1.getId(), "Replace 2.5uF capacitor", LocalDate.now().plusDays(1)))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("ASSIGNED"))
                .andExpect(jsonPath("$.assignedWorkerId").value(workerProfile1.getId()));

        // Student cannot cancel after ASSIGNED
        mockMvc.perform(post("/api/student/maintenance/" + reqId + "/cancel").cookie(cookieStudentA))
                .andExpect(status().isBadRequest());

        // Worker 2 cannot access Worker 1's assigned task
        mockMvc.perform(get("/api/worker/maintenance/" + reqId).cookie(cookieWorker2))
                .andExpect(status().isForbidden());
        mockMvc.perform(post("/api/worker/maintenance/" + reqId + "/start").cookie(cookieWorker2))
                .andExpect(status().isForbidden());

        // Worker 1 cannot complete before starting (must be IN_PROGRESS first)
        mockMvc.perform(multipart("/api/worker/maintenance/" + reqId + "/complete")
                        .param("remarks", "Done")
                        .cookie(cookieWorker1))
                .andExpect(status().isBadRequest());

        // 4. Worker 1 starts work -> marks Need Materials -> Warden resumes/assigns -> Worker starts -> Worker completes with photo
        mockMvc.perform(post("/api/worker/maintenance/" + reqId + "/start").cookie(cookieWorker1))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("IN_PROGRESS"));

        mockMvc.perform(post("/api/worker/maintenance/" + reqId + "/need-materials")
                        .cookie(cookieWorker1)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new NeedMaterialsRequest("Need new regulator switch"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("ON_HOLD"));

        mockMvc.perform(post("/api/warden/maintenance/" + reqId + "/assign")
                        .cookie(cookieWarden)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                new AssignWorkerRequest(workerProfile1.getId(), "Regulator issued from store", LocalDate.now()))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("ASSIGNED"));

        mockMvc.perform(post("/api/worker/maintenance/" + reqId + "/start").cookie(cookieWorker1))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("IN_PROGRESS"));

        MockMultipartFile afterPhoto = new MockMultipartFile(
                "afterPhoto", "fixed.jpg", "image/jpeg", new byte[]{5, 6, 7, 8});
        mockMvc.perform(multipart("/api/worker/maintenance/" + reqId + "/complete")
                        .file(afterPhoto)
                        .param("remarks", "Replaced regulator and tested")
                        .cookie(cookieWorker1))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("COMPLETED"))
                .andExpect(jsonPath("$.attachments", hasSize(2)));

        // 5. Student reopens -> Worker completes again -> Student confirms resolved with 5-star rating
        mockMvc.perform(post("/api/student/maintenance/" + reqId + "/reopen")
                        .cookie(cookieStudentA)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new ReopenMaintenanceRequest("Still making humming noise"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("REOPENED"));

        mockMvc.perform(post("/api/worker/maintenance/" + reqId + "/start").cookie(cookieWorker1))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("IN_PROGRESS"));

        mockMvc.perform(multipart("/api/worker/maintenance/" + reqId + "/complete")
                        .param("remarks", "Tightened mounting screw")
                        .cookie(cookieWorker1))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("COMPLETED"));

        mockMvc.perform(post("/api/student/maintenance/" + reqId + "/confirm-resolved")
                        .cookie(cookieStudentA)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new ConfirmResolvedRequest(5, "Great service!"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("RESOLVED"))
                .andExpect(jsonPath("$.rating.rating").value(5));

        // 6. Admin reports reflect completed work & rating
        mockMvc.perform(get("/api/admin/maintenance/reports").cookie(cookieAdmin))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalRequests", greaterThanOrEqualTo(1)))
                .andExpect(jsonPath("$.categoryCounts.ELECTRICAL", greaterThanOrEqualTo(1)));
    }

    @Test
    @DisplayName("Student can cancel PENDING request and Warden cannot assign deactivated worker")
    void testCancelPendingAndDeactivatedWorkerGuard() throws Exception {
        MvcResult createRes = mockMvc.perform(multipart("/api/student/maintenance")
                        .param("category", "PLUMBING")
                        .param("title", "Tap dripping")
                        .param("description", "Washbasin tap leaking")
                        .param("hostelBlock", "Block B")
                        .param("roomNumber", "202")
                        .param("priority", "LOW")
                        .cookie(cookieStudentA))
                .andExpect(status().isOk())
                .andReturn();
        long reqId = objectMapper.readTree(createRes.getResponse().getContentAsString()).get("id").asLong();

        // Deactivate workerProfile2 via Warden
        mockMvc.perform(patch("/api/warden/workers/" + workerProfile2.getId() + "/active?active=false")
                        .cookie(cookieWarden))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.active").value(false));

        // Assigning deactivated worker is rejected
        mockMvc.perform(post("/api/warden/maintenance/" + reqId + "/assign")
                        .cookie(cookieWarden)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new AssignWorkerRequest(workerProfile2.getId(), null, null))))
                .andExpect(status().isBadRequest());

        // Student cancels PENDING request
        mockMvc.perform(post("/api/student/maintenance/" + reqId + "/cancel").cookie(cookieStudentA))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CANCELLED"));
    }
}

