package com.hostel;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.hostel.dto.ParcelDtos.*;
import com.hostel.entity.Parcel;
import com.hostel.entity.Student;
import com.hostel.entity.User;
import com.hostel.repository.*;
import com.hostel.security.JwtService;
import com.hostel.service.ParcelService;
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

import java.time.LocalDateTime;

import static org.hamcrest.Matchers.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
public class ParcelTests {

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;
    @Autowired private JwtService jwtService;
    @Autowired private PasswordEncoder passwordEncoder;
    @Autowired private ParcelService parcelService;
    @Autowired private UserRepository userRepository;
    @Autowired private StudentRepository studentRepository;
    @Autowired private ParcelRepository parcelRepository;
    @Autowired private NotificationRepository notificationRepository;

    private User studentUserA, studentUserB, wardenUser, adminUser, securityUser, workerUser;
    private Student studentA, studentB;
    private MockCookie cookieStudA, cookieStudB, cookieWarden, cookieAdmin, cookieSecurity, cookieWorker;

    @BeforeEach
    void setUp() {
        studentA = createStudent("prc_studa@college.edu", "PRCA01", "Parcel Student A");
        studentUserA = studentA.getUser();
        studentB = createStudent("prc_studb@college.edu", "PRCB02", "Parcel Student B");
        studentUserB = studentB.getUser();

        wardenUser = userRepository.findByEmail("warden@hostel.edu").orElseThrow();
        adminUser = userRepository.findByEmail("admin@hostel.edu").orElseThrow();
        securityUser = userRepository.findByEmail("security@hostel.edu").orElseThrow();
        workerUser = userRepository.findByEmail("electrician.worker@hostel.edu").orElseThrow();

        cookieStudA = cookieFor(studentUserA);
        cookieStudB = cookieFor(studentUserB);
        cookieWarden = cookieFor(wardenUser);
        cookieAdmin = cookieFor(adminUser);
        cookieSecurity = cookieFor(securityUser);
        cookieWorker = cookieFor(workerUser);
    }

    private MockCookie cookieFor(User u) {
        return new MockCookie("hostel_jwt", jwtService.generateToken(u.getId(), u.getEmail(), u.getRole()));
    }

    private Student createStudent(String email, String regNo, String name) {
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
        s.setPhone("+91 9000022222");
        s.setDepartment("CSE");
        s.setYear(2);
        return studentRepository.save(s);
    }

    @Test
    @DisplayName("RBAC: Non-warden roles blocked from Warden/Admin parcel endpoints; Student IDOR blocked")
    void testRolePermissionsAndStudentIsolation() throws Exception {
        mockMvc.perform(get("/api/warden/parcels").cookie(cookieStudA)).andExpect(status().isForbidden());
        mockMvc.perform(get("/api/warden/parcels").cookie(cookieSecurity)).andExpect(status().isForbidden());
        mockMvc.perform(get("/api/warden/parcels").cookie(cookieWorker)).andExpect(status().isForbidden());
        mockMvc.perform(get("/api/admin/parcels/reports").cookie(cookieWarden)).andExpect(status().isForbidden());
        mockMvc.perform(get("/api/admin/parcels/reports").cookie(cookieAdmin)).andExpect(status().isOk());

        // Warden registers parcel for Student A
        MvcResult createRes = mockMvc.perform(multipart("/api/warden/parcels")
                        .param("studentId", String.valueOf(studentA.getId()))
                        .param("courierName", "Amazon")
                        .param("parcelType", "SMALL")
                        .cookie(cookieWarden))
                .andExpect(status().isOk())
                .andReturn();
        long parcelId = objectMapper.readTree(createRes.getResponse().getContentAsString()).get("id").asLong();

        // Student A can view own parcel
        mockMvc.perform(get("/api/student/parcels/" + parcelId).cookie(cookieStudA))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.qrToken", startsWith("PRC1.")));

        // Student B cannot view Student A's parcel
        mockMvc.perform(get("/api/student/parcels/" + parcelId).cookie(cookieStudB))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("QR verification, forged token rejection, single-use enforcement, COD check, and status transitions")
    void testQrValidationCodAndStatusTransitions() throws Exception {
        MockMultipartFile photo = new MockMultipartFile("photo", "box.jpg", "image/jpeg", new byte[]{1, 2, 3, 4});

        // 1. Register COD parcel for Student A
        MvcResult createRes = mockMvc.perform(multipart("/api/warden/parcels")
                        .file(photo)
                        .param("studentId", String.valueOf(studentA.getId()))
                        .param("courierName", "Flipkart")
                        .param("parcelType", "COD")
                        .param("cod", "true")
                        .param("codAmount", "550.00")
                        .param("description", "Headphones")
                        .param("storageLocation", "Shelf B-2")
                        .cookie(cookieWarden))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("NOTIFIED"))
                .andExpect(jsonPath("$.codPaymentPending").value(true))
                .andExpect(jsonPath("$.qrToken", startsWith("PRC1.")))
                .andReturn();

        JsonNode created = objectMapper.readTree(createRes.getResponse().getContentAsString());
        long parcelId = created.get("id").asLong();
        String qrToken = created.get("qrToken").asText();
        String pickupCode = created.get("pickupCode").asText();

        // Student notified on registration
        assertTrue(notificationRepository.countByUserIdAndReadFalse(studentUserA.getId()) >= 1);

        // 2. Forged token rejected
        mockMvc.perform(get("/api/warden/parcels/verify-qr?tokenOrCode=PRC1.999.999.deadbeef.badsignature")
                        .cookie(cookieWarden))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.valid").value(false));

        // Valid QR token and 6-digit pickup code both verify
        mockMvc.perform(get("/api/warden/parcels/verify-qr?tokenOrCode=" + qrToken).cookie(cookieWarden))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.valid").value(true));
        mockMvc.perform(get("/api/warden/parcels/verify-qr?tokenOrCode=" + pickupCode).cookie(cookieWarden))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.valid").value(true));

        // 3. COD check: attempting delivery without confirming paymentCollected fails
        ConfirmDeliveryRequest unpaidReq = new ConfirmDeliveryRequest(qrToken, parcelId, false, "Handed", null, null);
        mockMvc.perform(post("/api/warden/parcels/deliver")
                        .cookie(cookieWarden)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(unpaidReq)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message", containsString("COD payment")));

        // 4. Confirm delivery with paymentCollected = true
        ConfirmDeliveryRequest paidReq = new ConfirmDeliveryRequest(qrToken, parcelId, true, "Collected COD", null, null);
        mockMvc.perform(post("/api/warden/parcels/deliver")
                        .cookie(cookieWarden)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(paidReq)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("DELIVERED"))
                .andExpect(jsonPath("$.codCollected").value(true))
                .andExpect(jsonPath("$.tokenUsed").value(true));

        // 5. Re-scanning same QR returns Already delivered error
        mockMvc.perform(get("/api/warden/parcels/verify-qr?tokenOrCode=" + qrToken).cookie(cookieWarden))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.valid").value(false))
                .andExpect(jsonPath("$.errorMessage", containsString("Already delivered on")));

        // 6. Second parcel: Return to Sender transition & 24h reminder idempotency
        MvcResult createRes2 = mockMvc.perform(multipart("/api/warden/parcels")
                        .param("studentId", String.valueOf(studentB.getId()))
                        .param("courierName", "India Post")
                        .param("parcelType", "SMALL")
                        .cookie(cookieWarden))
                .andExpect(status().isOk())
                .andReturn();
        long parcelId2 = objectMapper.readTree(createRes2.getResponse().getContentAsString()).get("id").asLong();

        Parcel p2 = parcelRepository.findById(parcelId2).orElseThrow();
        p2.setReceivedAt(LocalDateTime.now().minusHours(26));
        parcelRepository.save(p2);
        long beforeNotif = notificationRepository.countByUserIdAndReadFalse(studentUserB.getId());
        parcelService.sendPendingParcelReminders(LocalDateTime.now());
        parcelService.sendPendingParcelReminders(LocalDateTime.now()); // idempotent second call
        assertEquals(beforeNotif + 1, notificationRepository.countByUserIdAndReadFalse(studentUserB.getId()));

        mockMvc.perform(post("/api/warden/parcels/" + parcelId2 + "/return-sender")
                        .cookie(cookieWarden)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new ParcelReasonRequest("Wrong addressee"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("RETURNED_TO_SENDER"));

        // Returned parcel fails QR verification
        mockMvc.perform(get("/api/warden/parcels/verify-qr?tokenOrCode=" + p2.getParcelCode()).cookie(cookieWarden))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.valid").value(false));
    }
}

