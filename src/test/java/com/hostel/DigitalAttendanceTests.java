package com.hostel;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.hostel.dto.DigitalAttendanceDtos.*;
import com.hostel.dto.OutpassDtos.CreateOutpassRequest;
import com.hostel.entity.*;
import com.hostel.repository.*;
import com.hostel.security.JwtService;
import com.hostel.service.DigitalAttendanceService;
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

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;

import static org.hamcrest.Matchers.containsString;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
public class DigitalAttendanceTests {

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;
    @Autowired private JwtService jwtService;
    @Autowired private PasswordEncoder passwordEncoder;
    @Autowired private DigitalAttendanceService digitalAttendanceService;

    @Autowired private UserRepository userRepository;
    @Autowired private StudentRepository studentRepository;
    @Autowired private AttendanceSessionRepository sessionRepository;
    @Autowired private DigitalAttendanceRecordRepository recordRepository;
    @Autowired private OutpassRepository outpassRepository;
    @Autowired private OutpassLockHistoryRepository lockHistoryRepository;
    @Autowired private NotificationRepository notificationRepository;

    private User studentUser1, studentUser2, wardenUser, securityUser, workerUser;
    private Student student1, student2;
    private MockCookie cookieStud1, cookieStud2, cookieWarden, cookieSecurity, cookieWorker;
    private AttendanceSession testSession;

    @BeforeEach
    void setUp() {
        // Disable seeded session so test windows are strictly deterministic
        sessionRepository.findAll().forEach(s -> {
            s.setEnabled(false);
            sessionRepository.save(s);
        });

        student1 = createStudent("da_stud1@college.edu", "DASTD01", "DA Student One");
        studentUser1 = student1.getUser();
        student2 = createStudent("da_stud2@college.edu", "DASTD02", "DA Student Two");
        studentUser2 = student2.getUser();

        wardenUser = userRepository.findByEmail("warden@hostel.edu").orElseThrow();
        securityUser = userRepository.findByEmail("security@hostel.edu").orElseThrow();
        workerUser = userRepository.findByEmail("electrician.worker@hostel.edu").orElseThrow();

        cookieStud1 = cookieFor(studentUser1);
        cookieStud2 = cookieFor(studentUser2);
        cookieWarden = cookieFor(wardenUser);
        cookieSecurity = cookieFor(securityUser);
        cookieWorker = cookieFor(workerUser);

        testSession = new AttendanceSession();
        testSession.setName("Test Night Roll");
        testSession.setHostelBlock("ALL");
        testSession.setStartTime(LocalTime.of(20, 0));
        testSession.setEndTime(LocalTime.of(22, 0));
        testSession.setActiveDays("MON,TUE,WED,THU,FRI,SAT,SUN");
        testSession.setEnabled(true);
        testSession.setReminderLeadMinutes(30);
        testSession.setSecondReminderMinutes(5);
        testSession.setGeofenceEnabled(false);
        testSession = sessionRepository.save(testSession);
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
        s.setPhone("+91 9000011111");
        s.setDepartment("CSE");
        s.setYear(2);
        return studentRepository.save(s);
    }

    @Test
    @DisplayName("Server-time marking window validation, duplicate marking rejection, and geofence enforcement")
    void testMarkingWindowDuplicateAndGeofence() {
        LocalDate d = LocalDate.of(2026, 11, 10);

        // Before startTime -> rejected
        assertThrows(Exception.class, () -> digitalAttendanceService.markAttendanceAt(
                studentUser1, new MarkDigitalAttendanceRequest(testSession.getId(), null, null),
                "TestBrowser", "127.0.0.1", LocalDateTime.of(d, LocalTime.of(19, 30))));

        // After endTime -> rejected
        assertThrows(Exception.class, () -> digitalAttendanceService.markAttendanceAt(
                studentUser1, new MarkDigitalAttendanceRequest(testSession.getId(), null, null),
                "TestBrowser", "127.0.0.1", LocalDateTime.of(d, LocalTime.of(22, 0))));

        // Inside window -> succeeds
        AttendanceHistoryItem marked = digitalAttendanceService.markAttendanceAt(
                studentUser1, new MarkDigitalAttendanceRequest(testSession.getId(), null, null),
                "TestBrowser", "127.0.0.1", LocalDateTime.of(d, LocalTime.of(21, 0)));
        assertEquals("MARKED", marked.status());
        assertNotNull(marked.markedAt());

        // Duplicate marking -> rejected
        assertThrows(Exception.class, () -> digitalAttendanceService.markAttendanceAt(
                studentUser1, new MarkDigitalAttendanceRequest(testSession.getId(), null, null),
                "TestBrowser", "127.0.0.1", LocalDateTime.of(d, LocalTime.of(21, 5))));

        // Enable geofence on session for Student 2
        testSession.setGeofenceEnabled(true);
        testSession.setHostelLatitude(11.0698);
        testSession.setHostelLongitude(77.0928);
        testSession.setGeofenceRadiusMeters(150);
        sessionRepository.save(testSession);

        // Missing location -> rejected
        assertThrows(Exception.class, () -> digitalAttendanceService.markAttendanceAt(
                studentUser2, new MarkDigitalAttendanceRequest(testSession.getId(), null, null),
                "TestBrowser", "127.0.0.1", LocalDateTime.of(d, LocalTime.of(21, 10))));

        // Far outside radius (~5 km away) -> rejected
        assertThrows(Exception.class, () -> digitalAttendanceService.markAttendanceAt(
                studentUser2, new MarkDigitalAttendanceRequest(testSession.getId(), 11.1200, 77.1200),
                "TestBrowser", "127.0.0.1", LocalDateTime.of(d, LocalTime.of(21, 10))));

        // Inside radius (~10m away) -> accepted
        AttendanceHistoryItem geoMarked = digitalAttendanceService.markAttendanceAt(
                studentUser2, new MarkDigitalAttendanceRequest(testSession.getId(), 11.06985, 77.09285),
                "TestPhone", "127.0.0.1", LocalDateTime.of(d, LocalTime.of(21, 12)));
        assertEquals("MARKED", geoMarked.status());
        assertEquals(11.06985, geoMarked.latitude());
    }

    @Test
    @DisplayName("Idempotent reminders, On-Outpass exclusion, Missed auto-lock, Outpass creation block, Security block, and Warden unlock")
    void testSchedulerRemindersMissedLockSecurityAndUnlock() throws Exception {
        LocalDate d = LocalDate.of(2026, 11, 10);

        // Give student2 an active APPROVED outpass covering d (must be excluded & never locked)
        Outpass pass2 = new Outpass();
        pass2.setStudent(student2);
        pass2.setPassType(Outpass.PassType.WEEKEND_PASS);
        pass2.setDepartureDateTime(LocalDateTime.of(d.minusDays(1), LocalTime.of(10, 0)));
        pass2.setReturnDateTime(LocalDateTime.of(d.plusDays(1), LocalTime.of(18, 0)));
        pass2.setReason("Family event");
        pass2.setStatus(Outpass.Status.APPROVED);
        outpassRepository.save(pass2);

        // Give student1 an existing APPROVED outpass to verify Security Gate blocks Exit once locked
        Outpass pass1 = new Outpass();
        pass1.setStudent(student1);
        pass1.setPassType(Outpass.PassType.DAY_PASS);
        pass1.setDepartureDateTime(LocalDateTime.of(d, LocalTime.of(8, 0)));
        pass1.setReturnDateTime(LocalDateTime.of(d, LocalTime.of(19, 0)));
        pass1.setReason("City errand");
        pass1.setStatus(Outpass.Status.APPROVED);
        pass1 = outpassRepository.save(pass1);

        // 1. Session starts at 20:00 -> Student 1 initialized PENDING, Student 2 initialized ON_OUTPASS
        digitalAttendanceService.initializeSessionsForDate(LocalDateTime.of(d, LocalTime.of(20, 0)));
        DigitalAttendanceRecord rec1 = recordRepository.findByStudentIdAndSessionIdAndAttendanceDate(
                student1.getId(), testSession.getId(), d).orElseThrow();
        DigitalAttendanceRecord rec2 = recordRepository.findByStudentIdAndSessionIdAndAttendanceDate(
                student2.getId(), testSession.getId(), d).orElseThrow();
        assertEquals(DigitalAttendanceRecord.Status.PENDING, rec1.getStatus());
        assertEquals(DigitalAttendanceRecord.Status.ON_OUTPASS, rec2.getStatus());

        // 2. 30 minutes before deadline (21:30): reminder sent ONLY to student1, idempotent if run twice
        long beforeNotifs1 = notificationRepository.countByUserIdAndReadFalse(studentUser1.getId());
        long beforeNotifs2 = notificationRepository.countByUserIdAndReadFalse(studentUser2.getId());
        digitalAttendanceService.sendRemindersAt(LocalDateTime.of(d, LocalTime.of(21, 30)));
        digitalAttendanceService.sendRemindersAt(LocalDateTime.of(d, LocalTime.of(21, 31)));

        assertEquals(beforeNotifs1 + 1, notificationRepository.countByUserIdAndReadFalse(studentUser1.getId()));
        assertEquals(beforeNotifs2, notificationRepository.countByUserIdAndReadFalse(studentUser2.getId()));

        // 3. Deadline passes (22:00): student1 marked MISSED and outpass locked; student2 remains ON_OUTPASS & unlocked
        digitalAttendanceService.processDeadlinesAt(LocalDateTime.of(d, LocalTime.of(22, 0)));
        DigitalAttendanceRecord afterRec1 = recordRepository.findById(rec1.getId()).orElseThrow();
        assertEquals(DigitalAttendanceRecord.Status.MISSED, afterRec1.getStatus());

        Student refreshed1 = studentRepository.findById(student1.getId()).orElseThrow();
        Student refreshed2 = studentRepository.findById(student2.getId()).orElseThrow();
        assertTrue(refreshed1.isLocked());
        assertFalse(refreshed2.isLocked());
        assertFalse(lockHistoryRepository.findByStudentIdOrderByCreatedAtDesc(student1.getId()).isEmpty());

        // 4. Locked student1 cannot create a new outpass
        CreateOutpassRequest newPassReq = new CreateOutpassRequest(
                "DAY_PASS", LocalDateTime.now().plusDays(1), LocalDateTime.now().plusDays(1).plusHours(5),
                "Shopping", "Mall", "9999999999");
        mockMvc.perform(post("/api/student/outpasses")
                        .cookie(cookieStud1)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(newPassReq)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message", containsString("Outpass locked due to missed attendance")));

        // 5. Security scan shows OUTPASS LOCKED - MISSED ATTENDANCE and blocks Exit (403)
        mockMvc.perform(get("/api/security/outpasses/verify?query=" + pass1.getId())
                        .cookie(cookieSecurity))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.outpassLocked").value(true))
                .andExpect(jsonPath("$.exitAllowed").value(false))
                .andExpect(jsonPath("$.gateMessage", containsString("OUTPASS LOCKED - MISSED ATTENDANCE")));

        mockMvc.perform(post("/api/security/outpasses/" + pass1.getId() + "/exit")
                        .cookie(cookieSecurity))
                .andExpect(status().isForbidden());

        // 6. Unlock RBAC: Student, Security, Worker cannot unlock (403)
        UnlockOutpassRequest unlockReq = new UnlockOutpassRequest("Approved after parent confirmation");
        mockMvc.perform(post("/api/warden/digital-attendance/unlock/" + student1.getId())
                        .cookie(cookieStud1)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(unlockReq)))
                .andExpect(status().isForbidden());
        mockMvc.perform(post("/api/warden/digital-attendance/unlock/" + student1.getId())
                        .cookie(cookieSecurity)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(unlockReq)))
                .andExpect(status().isForbidden());
        mockMvc.perform(post("/api/warden/digital-attendance/unlock/" + student1.getId())
                        .cookie(cookieWorker)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(unlockReq)))
                .andExpect(status().isForbidden());

        // 7. Warden unlocks student1 -> Security can now mark Exit
        mockMvc.perform(post("/api/warden/digital-attendance/unlock/" + student1.getId())
                        .cookie(cookieWarden)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(unlockReq)))
                .andExpect(status().isOk());

        assertFalse(studentRepository.findById(student1.getId()).orElseThrow().isLocked());

        mockMvc.perform(post("/api/security/outpasses/" + pass1.getId() + "/exit")
                        .cookie(cookieSecurity))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.outpassStatus").value("OUT"));
    }

    @Test
    @DisplayName("Warden manual mark present automatically unlocks if missed attendance was the only lock reason, plus bulk unlock & CSV export")
    void testManualMarkAutoUnlockAndReports() throws Exception {
        LocalDate d = LocalDate.of(2026, 11, 10);
        digitalAttendanceService.initializeSessionsForDate(LocalDateTime.of(d, LocalTime.of(20, 0)));
        digitalAttendanceService.processDeadlinesAt(LocalDateTime.of(d, LocalTime.of(22, 0)));

        assertTrue(studentRepository.findById(student1.getId()).orElseThrow().isLocked());
        assertTrue(studentRepository.findById(student2.getId()).orElseThrow().isLocked());

        // Manual mark student1 -> auto-unlocks student1
        ManualMarkAttendanceRequest manualReq = new ManualMarkAttendanceRequest(
                student1.getId(), testSession.getId(), d, "Network outage on floor 1");
        mockMvc.perform(post("/api/warden/digital-attendance/manual-mark")
                        .cookie(cookieWarden)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(manualReq)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.attendanceStatus").value("MANUAL_PRESENT"))
                .andExpect(jsonPath("$.outpassLocked").value(false));

        // Bulk unlock student2
        mockMvc.perform(post("/api/warden/digital-attendance/unlock-bulk")
                        .cookie(cookieWarden)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new BulkUnlockRequest(List.of(student2.getId()), "Cleared in bulk"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.unlockedCount").value(1));

        // CSV Export works
        mockMvc.perform(get("/api/warden/digital-attendance/report/export")
                        .cookie(cookieWarden))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("RegisterNumber,FullName")));
    }
}

