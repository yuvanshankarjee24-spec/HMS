package com.hostel;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.hostel.dto.AuthDtos.LoginRequest;
import com.hostel.dto.DigitalAttendanceDtos.UnlockOutpassRequest;
import com.hostel.entity.*;
import com.hostel.repository.*;
import com.hostel.security.JwtService;
import com.hostel.service.CircularService;
import com.hostel.service.DigitalAttendanceService;
import com.hostel.service.ParcelService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockCookie;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;
import java.util.UUID;

import static org.hamcrest.Matchers.is;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
public class DatabaseConsolidationTests {

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;
    @Autowired private JdbcTemplate jdbcTemplate;
    @Autowired private PlatformTransactionManager txManager;
    @Autowired private JwtService jwtService;
    @Autowired private PasswordEncoder passwordEncoder;

    @Autowired private UserRepository userRepository;
    @Autowired private HostelRepository hostelRepository;
    @Autowired private StudentRepository studentRepository;
    @Autowired private OutpassRepository outpassRepository;
    @Autowired private OutpassLockRepository outpassLockRepository;
    @Autowired private OutpassScanLogRepository outpassScanLogRepository;
    @Autowired private AttendanceSessionRepository sessionRepository;
    @Autowired private DigitalAttendanceRecordRepository recordRepository;
    @Autowired private DigitalAttendanceService digitalAttendanceService;
    @Autowired private ParcelService parcelService;
    @Autowired private CircularService circularService;
    @jakarta.persistence.PersistenceContext private jakarta.persistence.EntityManager entityManager;

    private User adminUser, wardenUser, securityUser;
    private Hostel mainHostel;
    private MockCookie adminCookie, wardenCookie, securityCookie;

    @BeforeEach
    void setUp() {
        mainHostel = hostelRepository.findByCodeIgnoreCase("MAIN")
                .orElseGet(() -> {
                    Hostel h = new Hostel();
                    h.setCode("MAIN");
                    h.setName("Main Block");
                    h.setAddress("Test Main Campus");
                    return hostelRepository.save(h);
                });

        wardenUser = userRepository.findByEmail("warden@hostel.edu").orElseThrow();
        securityUser = userRepository.findByEmail("security@hostel.edu").orElseThrow();
        adminUser = userRepository.findByRoleAndStatus("ADMIN", User.Status.ACTIVE)
                .stream().findFirst().orElseGet(() -> {
                    User a = new User();
                    a.setEmail("cons.admin@hostel.edu");
                    a.setName("Consolidation Admin");
                    a.setPasswordHash(passwordEncoder.encode("Admin@123"));
                    a.setRole("ADMIN");
                    a.setStatus(User.Status.ACTIVE);
                    a.setActive(true);
                    return userRepository.save(a);
                });

        adminCookie = new MockCookie("hostel_jwt", jwtService.generateToken(adminUser.getId(), adminUser.getEmail(), "ADMIN"));
        wardenCookie = new MockCookie("hostel_jwt", jwtService.generateToken(wardenUser.getId(), wardenUser.getEmail(), "WARDEN"));
        securityCookie = new MockCookie("hostel_jwt", jwtService.generateToken(securityUser.getId(), securityUser.getEmail(), "SECURITY"));
    }

    private Student createTestStudent(String prefix) {
        String uid = UUID.randomUUID().toString().substring(0, 6).toUpperCase();
        String email = (prefix + "_" + uid + "@college.edu").toLowerCase();
        String regNo = ("DC" + uid).toUpperCase();

        User u = new User();
        u.setEmail(email);
        u.setRegisterNumber(regNo);
        u.setName("Consolidated " + uid);
        u.setPhone("9876543210");
        u.setPasswordHash(passwordEncoder.encode("Pass@1234"));
        u.setRole("STUDENT");
        u.setStatus(User.Status.ACTIVE);
        u.setActive(true);
        User savedUser = userRepository.save(u);

        Student s = new Student();
        s.setUser(savedUser);
        s.setFullName("Consolidated " + uid);
        s.setRegisterNumber(regNo);
        s.setCollegeEmail(email);
        s.setPhone("9876543210");
        s.setDepartment("CSE");
        s.setYear(2);
        s.setHostel(mainHostel);
        s.setActive(true);
        return studentRepository.save(s);
    }

    @Test
    @DisplayName("Health endpoint returns HTTP 200 UP with UTC storage & Asia/Kolkata display timezone")
    void testHealthEndpoint() throws Exception {
        mockMvc.perform(get("/health"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status", is("UP")))
                .andExpect(jsonPath("$.database", is("UP")))
                .andExpect(jsonPath("$.dbTimezone", is("UTC")))
                .andExpect(jsonPath("$.displayTimezone", is("Asia/Kolkata")));
    }

    @Test
    @DisplayName("End-to-end cross-module flow: missed attendance -> outpass lock -> security deny & log -> warden unlock -> security exit & on_outpass")
    void testCrossModuleAttendanceLockAndSecurityGateFlow() throws Exception {
        // Isolate deterministic attendance sessions
        sessionRepository.findAll().forEach(ses -> {
            ses.setEnabled(false);
            sessionRepository.save(ses);
        });

        AttendanceSession nightSession = new AttendanceSession();
        nightSession.setName("DC Night Roll");
        nightSession.setHostelBlock("ALL");
        nightSession.setHostel(mainHostel);
        nightSession.setStartTime(LocalTime.of(20, 0));
        nightSession.setEndTime(LocalTime.of(21, 0));
        nightSession.setActiveDays("MON,TUE,WED,THU,FRI,SAT,SUN");
        nightSession.setEnabled(true);
        nightSession.setGeofenceEnabled(false);
        nightSession = sessionRepository.save(nightSession);

        Student student = createTestStudent("flow");

        // Pre-existing approved outpass starting tomorrow (so tonight's attendance still applies)
        Outpass pass = new Outpass();
        pass.setStudent(student);
        pass.setPassType(Outpass.PassType.WEEKEND_PASS);
        pass.setDepartureDateTime(LocalDateTime.now().plusDays(1));
        pass.setReturnDateTime(LocalDateTime.now().plusDays(3));
        pass.setReason("Family function");
        pass.setDestination("Coimbatore");
        pass.setStatus(Outpass.Status.APPROVED);
        pass.setDecidedBy(wardenUser);
        pass.setDecidedAt(LocalDateTime.now());
        pass = outpassRepository.save(pass);

        LocalDate today = LocalDate.now();
        // 1. Initialize pending record during open window, then trigger deadline miss after 21:00
        digitalAttendanceService.initializeSessionsForDate(LocalDateTime.of(today, LocalTime.of(20, 15)));
        int missedCount = digitalAttendanceService.processDeadlinesAt(LocalDateTime.of(today, LocalTime.of(21, 10)));
        assertTrue(missedCount >= 1);

        Student refreshed = studentRepository.findById(student.getId()).orElseThrow();
        assertTrue(refreshed.isLocked(), "Student should be locked after missing attendance");
        assertTrue(outpassLockRepository.findFirstByStudentIdAndActiveTrueOrderByLockedAtDesc(student.getId()).isPresent(),
                "Dedicated outpass_locks row must be created and active");

        // 2. Security gate scan sees locked state
        String verifyBody = mockMvc.perform(get("/api/security/outpasses/verify")
                        .cookie(securityCookie)
                        .param("query", String.valueOf(pass.getId())))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        JsonNode verifyJson = objectMapper.readTree(verifyBody);
        assertFalse(verifyJson.get("exitAllowed").asBoolean());

        // 3. Security gate exit is blocked (403) and logs a 'denied' entry in outpass_scan_logs
        mockMvc.perform(post("/api/security/outpasses/" + pass.getId() + "/exit")
                        .cookie(securityCookie))
                .andExpect(status().isForbidden());

        List<OutpassScanLog> logsAfterDeny = outpassScanLogRepository.findByOutpassIdOrderByScannedAtDesc(pass.getId());
        assertEquals(1, logsAfterDeny.size());
        assertEquals("denied", logsAfterDeny.get(0).getScanAction());
        assertEquals("LOCKED_MISSED_ATTENDANCE", logsAfterDeny.get(0).getScanResult());

        // 4. Warden unlocks outpass -> deactivates outpass_locks
        mockMvc.perform(post("/api/warden/digital-attendance/unlock/" + student.getId())
                        .cookie(wardenCookie)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new UnlockOutpassRequest("Approved after parent confirmation"))))
                .andExpect(status().isOk());

        assertTrue(outpassLockRepository.findFirstByStudentIdAndActiveTrueOrderByLockedAtDesc(student.getId()).isEmpty(),
                "Active outpass lock must be deactivated after warden unlock");
        assertFalse(studentRepository.findById(student.getId()).orElseThrow().isLocked());

        // 5. Security gate exit now succeeds (200) & records 'exit' ALLOWED scan log
        mockMvc.perform(post("/api/security/outpasses/" + pass.getId() + "/exit")
                        .cookie(securityCookie))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.outpassStatus", is("OUT")));

        List<OutpassScanLog> logsAfterExit = outpassScanLogRepository.findByOutpassIdOrderByScannedAtDesc(pass.getId());
        assertEquals(2, logsAfterExit.size());
        assertTrue(logsAfterExit.stream().anyMatch(l -> "exit".equals(l.getScanAction()) && "ALLOWED".equals(l.getScanResult())));

        // Verify normalized lowercase DB status
        entityManager.flush();
        String dbStatus = jdbcTemplate.queryForObject(
                "SELECT status FROM outpasses WHERE id = ?", String.class, pass.getId());
        assertEquals("out", dbStatus);

        // 6. Active OUT student gets marked 'on_outpass' on a subsequent session
        AttendanceSession morningSession = new AttendanceSession();
        morningSession.setName("DC Morning Check");
        morningSession.setHostelBlock("ALL");
        morningSession.setHostel(mainHostel);
        morningSession.setStartTime(LocalTime.of(7, 0));
        morningSession.setEndTime(LocalTime.of(8, 0));
        morningSession.setActiveDays("MON,TUE,WED,THU,FRI,SAT,SUN");
        morningSession.setEnabled(true);
        morningSession.setGeofenceEnabled(false);
        morningSession = sessionRepository.save(morningSession);

        digitalAttendanceService.initializeSessionsForDate(LocalDateTime.of(today.plusDays(1), LocalTime.of(7, 15)));
        entityManager.flush();
        DigitalAttendanceRecord nextRec = recordRepository
                .findByStudentIdAndSessionIdAndAttendanceDate(student.getId(), morningSession.getId(), today.plusDays(1))
                .orElseThrow();
        assertEquals(DigitalAttendanceRecord.Status.ON_OUTPASS, nextRec.getStatus());
        String recDbStatus = jdbcTemplate.queryForObject(
                "SELECT status FROM attendance_records WHERE id = ?", String.class, nextRec.getId());
        assertEquals("on_outpass", recDbStatus);
    }

    @Test
    @DisplayName("Schema integrity: CHECK constraints enforce lowercase statuses and FKs block orphans")
    void testDatabaseConstraintsAndNormalizedStatuses() {
        // Verify zero uppercase statuses remain in consolidated DB
        Integer badUsers = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM users WHERE status <> LOWER(status)", Integer.class);
        assertEquals(0, badUsers);

        // Run negative constraint checks inside REQUIRES_NEW sub-transactions so outer test stays healthy
        TransactionTemplate isolatedTx = new TransactionTemplate(txManager);
        isolatedTx.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);

        // 1. CHECK constraint rejects non-lowercase/invalid user status
        assertThrows(DataIntegrityViolationException.class, () ->
                isolatedTx.executeWithoutResult(status ->
                        jdbcTemplate.update(
                                "INSERT INTO users (email, password_hash, role, status, is_active, created_at) " +
                                        "VALUES ('bad_status@test.edu', 'hash', 'STUDENT', 'ACTIVE_BAD', TRUE, CURRENT_TIMESTAMP)")
                )
        );

        // 2. Foreign key constraint rejects orphan outpass_scan_logs pointing to non-existent outpass_id
        assertThrows(DataIntegrityViolationException.class, () ->
                isolatedTx.executeWithoutResult(status ->
                        jdbcTemplate.update(
                                "INSERT INTO outpass_scan_logs (outpass_id, scanned_by_user_id, scan_action, scan_result, scanned_at) " +
                                        "VALUES (9999999, ?, 'exit', 'ALLOWED', CURRENT_TIMESTAMP)", securityUser.getId())
                )
        );
    }

    @Test
    @DisplayName("Soft-delete disables login/JWT/targeting while preserving historical records and enforcing Admin RBAC")
    void testSoftDeleteAndRbac() throws Exception {
        Student student = createTestStudent("softdel");
        User stuUser = student.getUser();
        MockCookie studentCookie = new MockCookie("hostel_jwt",
                jwtService.generateToken(stuUser.getId(), stuUser.getEmail(), "STUDENT"));

        // Create historical outpass owned by student
        Outpass histPass = new Outpass();
        histPass.setStudent(student);
        histPass.setPassType(Outpass.PassType.DAY_PASS);
        histPass.setDepartureDateTime(LocalDateTime.now().plusHours(2));
        histPass.setReturnDateTime(LocalDateTime.now().plusHours(6));
        histPass.setReason("Historical record check");
        histPass.setDestination("Library");
        histPass.setStatus(Outpass.Status.PENDING);
        histPass = outpassRepository.save(histPass);

        // Student cannot soft-delete users (403 Forbidden)
        mockMvc.perform(delete("/api/admin/users/" + stuUser.getId())
                        .cookie(studentCookie))
                .andExpect(status().isForbidden());

        // Admin soft-deletes student
        mockMvc.perform(delete("/api/admin/users/" + stuUser.getId())
                        .cookie(adminCookie))
                .andExpect(status().isOk());

        User updatedUser = userRepository.findById(stuUser.getId()).orElseThrow();
        Student updatedStudent = studentRepository.findById(student.getId()).orElseThrow();
        assertFalse(updatedUser.isActive());
        assertEquals(User.Status.DISABLED, updatedUser.getStatus());
        assertNotNull(updatedUser.getDeletedAt());
        assertFalse(updatedStudent.isActive());
        assertNotNull(updatedStudent.getDeletedAt());

        // Historical outpass still intact
        assertTrue(outpassRepository.findById(histPass.getId()).isPresent());

        // Deleted user cannot log in
        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new LoginRequest(stuUser.getEmail(), "Pass@1234", "STUDENT"))))
                .andExpect(status().isForbidden());

        // Deleted user excluded from parcel search, student directory search & circular targeting
        assertTrue(parcelService.searchStudents(student.getRegisterNumber()).isEmpty());
        assertTrue(studentRepository.searchStudents(student.getRegisterNumber(), PageRequest.of(0, 10)).isEmpty());

        Circular allCirc = new Circular();
        allCirc.setTargetScope(Circular.TargetScope.ALL);
        assertTrue(circularService.resolveTargetedStudents(allCirc).stream()
                .noneMatch(s -> s.getId().equals(student.getId())));
    }
}
