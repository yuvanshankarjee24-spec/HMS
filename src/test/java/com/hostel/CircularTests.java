package com.hostel;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.hostel.dto.CircularDtos.*;
import com.hostel.entity.*;
import com.hostel.repository.*;
import com.hostel.security.JwtService;
import com.hostel.service.CircularService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.domain.Pageable;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockCookie;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

import static org.hamcrest.Matchers.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
public class CircularTests {

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;
    @Autowired private JwtService jwtService;
    @Autowired private PasswordEncoder passwordEncoder;
    @Autowired private CircularService circularService;

    @Autowired private UserRepository userRepository;
    @Autowired private StudentRepository studentRepository;
    @Autowired private RoomRepository roomRepository;
    @Autowired private BedRepository bedRepository;
    @Autowired private CircularRepository circularRepository;
    @Autowired private NotificationRepository notificationRepository;
    @Autowired private CircularEventReminderRepository reminderRepository;

    private User studentUserA, studentUserB, wardenUser, adminUser, securityUser, workerUser;
    private Student studentA, studentB;
    private MockCookie cookieStudA, cookieStudB, cookieWarden, cookieAdmin, cookieSecurity, cookieWorker;

    @BeforeEach
    void setUp() {
        studentA = createStudentWithRoom("circ_studa@college.edu", "CIRCA01", "Circular Student A", "CSE", 2, 1, "C-101");
        studentUserA = studentA.getUser();
        studentB = createStudentWithRoom("circ_studb@college.edu", "CIRCB02", "Circular Student B", "ECE", 3, 2, "C-201");
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

    private Student createStudentWithRoom(String email, String regNo, String name, String dept, int year, int floor, String roomNo) {
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
        s.setPhone("+91 9000033333");
        s.setDepartment(dept);
        s.setYear(year);
        s = studentRepository.save(s);

        Room room = new Room(roomNo, floor, 2, Room.Status.ACTIVE);
        room = roomRepository.save(room);
        Bed bed = new Bed(room, 1);
        bed.setStudent(s);
        bedRepository.save(bed);
        return s;
    }

    @Test
    @DisplayName("RBAC: Student/Security/Worker forbidden on warden/admin circular APIs; Warden cannot publish ALL-hostels")
    void testCircularRbacAndWardenScope() throws Exception {
        mockMvc.perform(get("/api/warden/circulars").cookie(cookieStudA))
                .andExpect(status().isForbidden());
        mockMvc.perform(get("/api/warden/circulars").cookie(cookieSecurity))
                .andExpect(status().isForbidden());
        mockMvc.perform(get("/api/warden/circulars").cookie(cookieWorker))
                .andExpect(status().isForbidden());
        mockMvc.perform(get("/api/admin/circulars").cookie(cookieWarden))
                .andExpect(status().isForbidden());

        // Warden attempting targetScope=ALL must be 403 Forbidden
        mockMvc.perform(multipart("/api/warden/circulars")
                        .cookie(cookieWarden)
                        .param("type", "CIRCULAR")
                        .param("title", "All Hostels Notice")
                        .param("description", "Attempted global notice by warden")
                        .param("targetScope", "ALL")
                        .param("publishAction", "PUBLISH"))
                .andExpect(status().isForbidden());

        // Admin CAN publish targetScope=ALL
        mockMvc.perform(multipart("/api/warden/circulars")
                        .cookie(cookieAdmin)
                        .param("type", "CIRCULAR")
                        .param("title", "Admin Global Notice")
                        .param("description", "Official campus notice")
                        .param("targetScope", "ALL")
                        .param("priority", "IMPORTANT")
                        .param("publishAction", "PUBLISH"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status", is("PUBLISHED")))
                .andExpect(jsonPath("$.circularCode", startsWith("CIR-")));
    }

    @Test
    @DisplayName("Block and Year/Dept targeting isolation + attachment validation")
    void testTargetingIsolationAndAttachments() throws Exception {
        // Reject unsupported file type
        MockMultipartFile exeFile = new MockMultipartFile("files", "bad.exe", "application/octet-stream", "MZ".getBytes());
        mockMvc.perform(multipart("/api/warden/circulars")
                        .file(exeFile)
                        .cookie(cookieWarden)
                        .param("type", "REGULATION")
                        .param("title", "Bad File Circular")
                        .param("description", "Should fail")
                        .param("targetScope", "BLOCK")
                        .param("hostelBlock", "Floor 1")
                        .param("publishAction", "PUBLISH"))
                .andExpect(status().isBadRequest());

        // Valid Floor 1 circular with PDF attachment targeting only Floor 1 (studentA is on Floor 1, studentB is on Floor 2)
        MockMultipartFile pdfFile = new MockMultipartFile("files", "rules.pdf", "application/pdf", "%PDF-1.4\n%test".getBytes());
        MvcResult res = mockMvc.perform(multipart("/api/warden/circulars")
                        .file(pdfFile)
                        .cookie(cookieWarden)
                        .param("type", "REGULATION")
                        .param("title", "Floor 1 Quiet Hours")
                        .param("description", "Strict quiet hours after 10 PM.")
                        .param("rulesAndRegulations", "1. No loud music.\n2. Study lamps after 11 PM.")
                        .param("priority", "URGENT")
                        .param("targetScope", "BLOCK")
                        .param("hostelBlock", "Floor 1")
                        .param("requireAcknowledgement", "true")
                        .param("publishAction", "PUBLISH"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.attachments", hasSize(1)))
                .andReturn();

        Long circId = objectMapper.readTree(res.getResponse().getContentAsString()).get("id").asLong();

        // Student A (Floor 1) can open detail and sees attachment + urgent banner
        mockMvc.perform(get("/api/student/circulars/" + circId).cookie(cookieStudA))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.read", is(true)))
                .andExpect(jsonPath("$.attachments", hasSize(1)));

        // Student B (Floor 2) is NOT targeted -> 403 on detail & does not see Floor 1 Quiet Hours in list
        mockMvc.perform(get("/api/student/circulars/" + circId).cookie(cookieStudB))
                .andExpect(status().isForbidden());
        mockMvc.perform(get("/api/student/circulars").cookie(cookieStudB))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[*].title", not(hasItem("Floor 1 Quiet Hours"))));
    }

    @Test
    @DisplayName("Read, mandatory acknowledgement, edit versioning, event RSVP, ICS download, and tracking CSV")
    void testReadAckEditRsvpIcsAndTracking() throws Exception {
        LocalDateTime eventAt = LocalDateTime.now().plusDays(2);
        CircularResponse created = circularService.createCircular(
                adminUser,
                "EVENT",
                "Annual Hostel Meet",
                "Mandatory orientation and cultural briefing.",
                "Wear formal attire. Arrive 10 minutes early.",
                eventAt,
                "Main Auditorium",
                "IMPORTANT",
                "STUDENTS",
                null, null, null,
                List.of(studentA.getId(), studentB.getId()),
                true,
                "PUBLISH",
                null,
                null,
                null
        );

        assertEquals(1, created.version());
        assertFalse(created.updatedFlag());

        // Student A opens -> marks read, not yet acknowledged
        mockMvc.perform(get("/api/student/circulars/" + created.id()).cookie(cookieStudA))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.read", is(true)))
                .andExpect(jsonPath("$.acknowledged", is(false)));

        // Student A acknowledges + RSVPs ATTENDING
        mockMvc.perform(post("/api/student/circulars/" + created.id() + "/acknowledge").cookie(cookieStudA))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.acknowledged", is(true)))
                .andExpect(jsonPath("$.acknowledgedAt", notNullValue()));

        mockMvc.perform(post("/api/student/circulars/" + created.id() + "/rsvp")
                        .cookie(cookieStudA)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new RsvpRequest("ATTENDING"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.myRsvpStatus", is("ATTENDING")))
                .andExpect(jsonPath("$.attendingCount", is(1)));

        // Student A downloads .ics calendar
        mockMvc.perform(get("/api/student/circulars/" + created.id() + "/calendar.ics").cookie(cookieStudA))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Type", containsString("text/calendar")))
                .andExpect(content().string(containsString("BEGIN:VEVENT")))
                .andExpect(content().string(containsString("Annual Hostel Meet")));

        // Edit circular -> version 2 + updatedFlag=true + preserves version history
        mockMvc.perform(put("/api/warden/circulars/" + created.id())
                        .cookie(cookieAdmin)
                        .param("title", "Annual Hostel Meet (Venue Changed)")
                        .param("venue", "Seminar Hall B")
                        .param("editNote", "Shifted venue"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.version", is(2)))
                .andExpect(jsonPath("$.updatedFlag", is(true)))
                .andExpect(jsonPath("$.versions", hasSize(2)));

        // Tracking shows 1 acknowledged (studentA) and 1 pending (studentB) + CSV export works
        mockMvc.perform(get("/api/warden/circulars/" + created.id() + "/tracking").cookie(cookieAdmin))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalTargeted", is(2)))
                .andExpect(jsonPath("$.readCount", is(1)))
                .andExpect(jsonPath("$.acknowledgedCount", is(1)))
                .andExpect(jsonPath("$.attendingCount", is(1)));

        mockMvc.perform(get("/api/warden/circulars/" + created.id() + "/tracking/export").cookie(cookieAdmin))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Type", containsString("text/csv")))
                .andExpect(content().string(containsString(studentA.getRegisterNumber())));
    }

    @Test
    @DisplayName("Scheduled circulars auto-publish, 24h unacknowledged & 1h event reminders are idempotent")
    void testScheduledJobsAndIdempotentReminders() {
        LocalDateTime baseNow = LocalDateTime.now();
        // Scheduled circular whose publish time is already reached
        CircularResponse sched = circularService.createCircular(
                adminUser,
                "CIRCULAR",
                "Scheduled Water Shutdown",
                "Tank cleaning tomorrow.",
                null, null, null, "NORMAL",
                "STUDENTS", null, null, null, List.of(studentA.getId()),
                false, "SCHEDULE", baseNow.minusMinutes(5), null, null
        );
        assertEquals("SCHEDULED", sched.status());

        // Event needing 24h unack reminder + 1h event reminder
        CircularResponse event = circularService.createCircular(
                adminUser,
                "EVENT",
                "Fire Safety Drill",
                "Mandatory assembly at courtyard.",
                "Use stairs only.",
                baseNow.plusHours(25).plusMinutes(30),
                "Courtyard",
                "URGENT",
                "STUDENTS", null, null, null, List.of(studentA.getId(), studentB.getId()),
                true, "PUBLISH", null, null, null
        );

        // Student A acknowledges immediately -> should NOT get UNACK_24H reminder
        circularService.acknowledgeCircular(studentUserA, event.id());

        // Backdate publishedAt by 25 hours cleanly in DB
        Circular entity = circularRepository.findById(event.id()).orElseThrow();
        entity.setPublishedAt(baseNow.minusHours(25));
        circularRepository.save(entity);

        // Run jobs at baseNow (scheduled circular publishes) and then at eventAt - 30m (24h unack + 1_HOUR reminder)
        LocalDateTime eventMinus30m = entity.getEventDateTime().minusMinutes(30);
        long beforeNotifsB = notificationRepository.findByUserIdOrderByCreatedAtDesc(studentUserB.getId(), Pageable.unpaged()).getTotalElements();
        circularService.processCircularJobs(eventMinus30m);
        circularService.processCircularJobs(eventMinus30m); // Run twice to assert idempotency!

        Circular refreshedSched = circularRepository.findById(sched.id()).orElseThrow();
        assertEquals(Circular.Status.PUBLISHED, refreshedSched.getStatus());

        // Student A did NOT get UNACK_24H, got 1_HOUR once
        assertFalse(reminderRepository.existsByCircularIdAndStudentIdAndReminderType(event.id(), studentA.getId(), "UNACK_24H"));
        assertTrue(reminderRepository.existsByCircularIdAndStudentIdAndReminderType(event.id(), studentA.getId(), "1_HOUR"));

        // Student B got both UNACK_24H and 1_HOUR, and duplicate run sent zero extras
        assertTrue(reminderRepository.existsByCircularIdAndStudentIdAndReminderType(event.id(), studentB.getId(), "UNACK_24H"));
        assertTrue(reminderRepository.existsByCircularIdAndStudentIdAndReminderType(event.id(), studentB.getId(), "1_HOUR"));

        long afterNotifsB = notificationRepository.findByUserIdOrderByCreatedAtDesc(studentUserB.getId(), Pageable.unpaged()).getTotalElements();
        // +1 from scheduled circular publish + 1 from UNACK_24H + 1 from 1_HOUR = 3 new notifications across TWO job runs
        assertEquals(beforeNotifsB + 3, afterNotifsB);
    }
}
