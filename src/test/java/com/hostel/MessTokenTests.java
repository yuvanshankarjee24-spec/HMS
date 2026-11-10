package com.hostel;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.hostel.dto.MessDtos.*;
import com.hostel.entity.*;
import com.hostel.exception.CustomExceptions.*;
import com.hostel.repository.*;
import com.hostel.security.JwtService;
import com.hostel.service.MessTokenService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockCookie;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;

import static org.hamcrest.Matchers.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
public class MessTokenTests {

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;
    @Autowired private JwtService jwtService;
    @Autowired private PasswordEncoder passwordEncoder;
    @Autowired private MessTokenService messService;

    @Autowired private UserRepository userRepository;
    @Autowired private StudentRepository studentRepository;
    @Autowired private FoodItemRepository foodItemRepository;
    @Autowired private MenuScheduleRepository menuScheduleRepository;
    @Autowired private MessTokenRepository messTokenRepository;
    @Autowired private MessPeriodConfigRepository periodConfigRepository;

    private User studentUserA, studentUserB, wardenUser, adminUser, securityUser, workerUser;
    private Student studentA, studentB;
    private MockCookie cookieStudA, cookieWarden, cookieAdmin, cookieSecurity, cookieWorker;
    private FoodItem chickenBiryani, boiledEgg;

    @BeforeEach
    void setUp() {
        studentA = createStudent("mess_studa@college.edu", "MESSA01", "Mess Student A");
        studentUserA = studentA.getUser();
        studentB = createStudent("mess_studb@college.edu", "MESSB02", "Mess Student B");
        studentUserB = studentB.getUser();

        wardenUser = userRepository.findByEmail("warden@hostel.edu").orElseThrow();
        adminUser = userRepository.findByEmail("admin@hostel.edu").orElseThrow();
        securityUser = userRepository.findByEmail("security@hostel.edu").orElseThrow();
        workerUser = userRepository.findByEmail("electrician.worker@hostel.edu").orElseThrow();

        cookieStudA = cookieFor(studentUserA);
        cookieWarden = cookieFor(wardenUser);
        cookieAdmin = cookieFor(adminUser);
        cookieSecurity = cookieFor(securityUser);
        cookieWorker = cookieFor(workerUser);

        chickenBiryani = foodItemRepository.findAll().stream()
                .filter(f -> "Chicken Biryani".equalsIgnoreCase(f.getName())).findFirst().orElseThrow();
        boiledEgg = foodItemRepository.findAll().stream()
                .filter(f -> "Boiled Egg".equalsIgnoreCase(f.getName())).findFirst().orElseThrow();
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
        s.setPhone("+91 9000044444");
        s.setDepartment("MECH");
        s.setYear(2);
        return studentRepository.save(s);
    }

    @Test
    @DisplayName("Seeded food items exist and RBAC blocks unauthorized roles across mess endpoints")
    void testSeededItemsAndRbac() throws Exception {
        mockMvc.perform(get("/api/warden/mess/items").cookie(cookieWarden))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[*].name", hasItems("Boiled Egg", "Omelette", "Chicken Gravy", "Chicken Biryani", "Gobi Manchurian")));

        mockMvc.perform(get("/api/warden/mess/summary").cookie(cookieStudA)).andExpect(status().isForbidden());
        mockMvc.perform(get("/api/warden/mess/summary").cookie(cookieSecurity)).andExpect(status().isForbidden());
        mockMvc.perform(get("/api/warden/mess/summary").cookie(cookieWorker)).andExpect(status().isForbidden());
        mockMvc.perform(get("/api/admin/mess/reports").cookie(cookieWarden)).andExpect(status().isForbidden());
        mockMvc.perform(get("/api/admin/mess/reports").cookie(cookieAdmin)).andExpect(status().isOk());
    }

    @Test
    @DisplayName("Cutoff enforcement, max-per-student, mess-closed, stock checks, and pre-cutoff cancellation")
    void testBookingCutoffsAndCancellation() {
        LocalDate menuDate = LocalDate.now().plusDays(2);
        // LUNCH config default: 12:30 - 14:30, cutoff = 120m -> cutoff at 10:30, QR opens at 12:15
        MenuScheduleResponse biryaniSched = messService.scheduleMenuItem(wardenUser,
                new MenuScheduleItemRequest(menuDate, "LUNCH", chickenBiryani.getId(), 5, 2, true, false));
        MenuScheduleResponse eggSched = messService.scheduleMenuItem(wardenUser,
                new MenuScheduleItemRequest(menuDate, "LUNCH", boiledEgg.getId(), 4, 3, false, false));

        // 1. Booking AFTER cutoff (10:35) must fail
        assertThrows(BadRequestException.class, () -> messService.bookTokenAt(
                studentUserA,
                new BookTokenRequest(menuDate, "LUNCH", List.of(new BookItemQty(biryaniSched.id(), 1))),
                LocalDateTime.of(menuDate, LocalTime.of(10, 35))
        ));

        // 2. Exceeding maxPerStudent (max=2, ask=3) must fail
        assertThrows(BadRequestException.class, () -> messService.bookTokenAt(
                studentUserA,
                new BookTokenRequest(menuDate, "LUNCH", List.of(new BookItemQty(biryaniSched.id(), 3))),
                LocalDateTime.of(menuDate, LocalTime.of(9, 0))
        ));

        // 3. Booking BEFORE cutoff (09:00) succeeds & decrements remainingStock cleanly
        MessTokenResponse booked = messService.bookTokenAt(
                studentUserA,
                new BookTokenRequest(menuDate, "LUNCH", List.of(
                        new BookItemQty(biryaniSched.id(), 2),
                        new BookItemQty(eggSched.id(), 2))),
                LocalDateTime.of(menuDate, LocalTime.of(9, 0))
        );
        assertEquals("BOOKED", booked.status());
        assertTrue(booked.tokenCode().startsWith("TKN-"));
        // Student view before QR window (09:00 < 12:15) MUST redact QR token!
        assertTrue(booked.qrLockedBeforeWindow());
        assertFalse(booked.qrCurrentlyActive());
        assertNull(booked.qrToken());

        MenuSchedule afterBook = menuScheduleRepository.findById(biryaniSched.id()).orElseThrow();
        assertEquals(3, afterBook.getRemainingStock());

        // 4. Attempting to cancel AFTER cutoff (11:00) must fail
        assertThrows(BadRequestException.class, () -> messService.cancelTokenAt(
                studentUserA, booked.id(), LocalDateTime.of(menuDate, LocalTime.of(11, 0))));

        // 5. Cancelling BEFORE cutoff (09:30) succeeds and restores stock!
        MessTokenResponse cancelled = messService.cancelTokenAt(
                studentUserA, booked.id(), LocalDateTime.of(menuDate, LocalTime.of(9, 30)));
        assertEquals("CANCELLED", cancelled.status());
        assertEquals(5, menuScheduleRepository.findById(biryaniSched.id()).orElseThrow().getRemainingStock());
        assertEquals(4, menuScheduleRepository.findById(eggSched.id()).orElseThrow().getRemainingStock());

        // 6. Marking mess closed rejects new bookings
        messService.scheduleMenuItem(wardenUser,
                new MenuScheduleItemRequest(menuDate, "LUNCH", chickenBiryani.getId(), 5, 2, true, true));
        assertThrows(BadRequestException.class, () -> messService.bookTokenAt(
                studentUserB,
                new BookTokenRequest(menuDate, "LUNCH", List.of(new BookItemQty(biryaniSched.id(), 1))),
                LocalDateTime.of(menuDate, LocalTime.of(9, 0))
        ));
    }

    @Test
    @DisplayName("QR activation 15m before meal, tampered token rejection, single-use delivery, partial delivery & auto-expiry")
    void testQrLifecycleDeliveryAndExpiry() throws Exception {
        LocalDate menuDate = LocalDate.now().plusDays(3);
        // DINNER: 20:00 - 22:00, cutoff 120m (18:00), QR opens 15m before (19:45)
        MenuScheduleResponse biryani = messService.scheduleMenuItem(wardenUser,
                new MenuScheduleItemRequest(menuDate, "DINNER", chickenBiryani.getId(), 10, 3, false, false));
        MenuScheduleResponse egg = messService.scheduleMenuItem(wardenUser,
                new MenuScheduleItemRequest(menuDate, "DINNER", boiledEgg.getId(), 10, 4, false, false));

        // Student A & Student B book at 16:00
        MessTokenResponse tokA = messService.bookTokenAt(
                studentUserA,
                new BookTokenRequest(menuDate, "DINNER", List.of(
                        new BookItemQty(biryani.id(), 2),
                        new BookItemQty(egg.id(), 2))),
                LocalDateTime.of(menuDate, LocalTime.of(16, 0))
        );
        MessTokenResponse tokB = messService.bookTokenAt(
                studentUserB,
                new BookTokenRequest(menuDate, "DINNER", List.of(new BookItemQty(biryani.id(), 1))),
                LocalDateTime.of(menuDate, LocalTime.of(16, 5))
        );

        // 1. Before QR open window (19:00 < 19:45): pickupCode & qrToken are hidden from student and tokenCode lookup is invalid ("not active yet")
        assertNull(tokA.pickupCode());
        MessScanVerifyResponse tooEarly = messService.verifyQrOrCodeAt(
                tokA.tokenCode(), null, LocalDateTime.of(menuDate, LocalTime.of(19, 0)));
        assertFalse(tooEarly.valid());
        assertTrue(tooEarly.errorMessage().contains("not active yet"));

        // 2. Tampered QR signature rejected
        MessScanVerifyResponse tampered = messService.verifyQrOrCodeAt(
                "TKN1.fakePayload.badSig", null, LocalDateTime.of(menuDate, LocalTime.of(19, 50)));
        assertFalse(tampered.valid());
        assertTrue(tampered.errorMessage().contains("Invalid or tampered"));

        // 3. Scheduler runs at 19:46 -> activates tokens & student view reveals real QR token
        messService.processMessTokenJobs(LocalDateTime.of(menuDate, LocalTime.of(19, 46)));
        MessToken activatedA = messTokenRepository.findById(tokA.id()).orElseThrow();
        assertEquals(MessToken.Status.QR_ACTIVE, activatedA.getStatus());

        MessTokenResponse studentActiveView = messService.toTokenResponse(
                activatedA, true, LocalDateTime.of(menuDate, LocalTime.of(19, 50)));
        assertTrue(studentActiveView.qrCurrentlyActive());
        assertNotNull(studentActiveView.qrToken());
        assertTrue(studentActiveView.qrToken().startsWith("TKN1."));

        // 4. Warden verifies QR inside meal window -> valid = true
        MessScanVerifyResponse scanValid = messService.verifyQrOrCodeAt(
                studentActiveView.qrToken(), null, LocalDateTime.of(menuDate, LocalTime.of(20, 10)));
        assertTrue(scanValid.valid());

        // 5. Partial delivery: 2/2 Biryani, 1/2 Boiled Egg ("Ran out of eggs") -> PARTIALLY_DELIVERED
        Long biryaniItemId = scanValid.token().items().stream()
                .filter(i -> i.foodName().equals("Chicken Biryani")).findFirst().orElseThrow().id();
        Long eggItemId = scanValid.token().items().stream()
                .filter(i -> i.foodName().equals("Boiled Egg")).findFirst().orElseThrow().id();

        MessTokenResponse delivered = messService.confirmDeliveryAt(
                wardenUser,
                new ConfirmMessDeliveryRequest(
                        studentActiveView.qrToken(), tokA.id(), "Partial counter serve",
                        List.of(
                                new DeliveredItemLine(biryaniItemId, 2, null),
                                new DeliveredItemLine(eggItemId, 1, "Only 1 egg left in batch")
                        )
                ),
                LocalDateTime.of(menuDate, LocalTime.of(20, 12))
        );
        assertEquals("PARTIALLY_DELIVERED", delivered.status());
        assertTrue(delivered.tokenUsed());
        assertTrue(delivered.tokenClosed());

        // 6. Re-scanning same QR returns "Already delivered on ..." and confirmDelivery throws ConflictException
        MessScanVerifyResponse rescan = messService.verifyQrOrCodeAt(
                studentActiveView.qrToken(), null, LocalDateTime.of(menuDate, LocalTime.of(20, 15)));
        assertFalse(rescan.valid());
        assertTrue(rescan.errorMessage().startsWith("Already delivered on "));

        assertThrows(ConflictException.class, () -> messService.confirmDeliveryAt(
                wardenUser,
                new ConfirmMessDeliveryRequest(studentActiveView.qrToken(), tokA.id(), "Retry", null),
                LocalDateTime.of(menuDate, LocalTime.of(20, 16))
        ));

        // 7. After DINNER endTime (22:05), scheduler expires uncollected tokB and restores its 1 Biryani stock
        int stockBeforeExpiry = menuScheduleRepository.findById(biryani.id()).orElseThrow().getRemainingStock();
        assertEquals(7, stockBeforeExpiry); // 10 - 2 (A) - 1 (B)

        messService.processMessTokenJobs(LocalDateTime.of(menuDate, LocalTime.of(22, 5)));

        MessToken expiredB = messTokenRepository.findById(tokB.id()).orElseThrow();
        assertEquals(MessToken.Status.EXPIRED, expiredB.getStatus());
        assertEquals(8, menuScheduleRepository.findById(biryani.id()).orElseThrow().getRemainingStock());

        // 8. Warden summary & CSV export endpoints respond cleanly
        mockMvc.perform(get("/api/warden/mess/summary")
                        .cookie(cookieWarden)
                        .param("date", menuDate.toString())
                        .param("period", "DINNER"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalTokens", is(2)))
                .andExpect(jsonPath("$.deliveredTokens", is(1)))
                .andExpect(jsonPath("$.expiredTokens", is(1)));

        mockMvc.perform(get("/api/warden/mess/export")
                        .cookie(cookieWarden)
                        .param("fromDate", menuDate.toString())
                        .param("toDate", menuDate.toString()))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Type", containsString("text/csv")))
                .andExpect(content().string(containsString(tokA.tokenCode())));
    }
}
