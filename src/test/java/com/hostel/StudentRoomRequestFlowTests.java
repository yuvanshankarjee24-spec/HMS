package com.hostel;

import com.hostel.dto.StudentDtos.RoomRequestCreate;
import com.hostel.dto.StudentDtos.RoomRequestResponse;
import com.hostel.dto.WardenDtos.RejectRequest;
import com.hostel.entity.*;
import com.hostel.exception.CustomExceptions.BadRequestException;
import com.hostel.exception.CustomExceptions.ConflictException;
import com.hostel.exception.CustomExceptions.ForbiddenException;
import com.hostel.repository.*;
import com.hostel.service.RoomAllocationService;
import com.hostel.service.StudentService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
public class StudentRoomRequestFlowTests {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private StudentService studentService;

    @Autowired
    private RoomAllocationService roomAllocationService;

    @Autowired
    private RoomRequestRepository roomRequestRepository;

    @Autowired
    private RoomRepository roomRepository;

    @Autowired
    private BedRepository bedRepository;

    @Autowired
    private StudentRepository studentRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private HostelRepository hostelRepository;

    @Autowired
    private com.hostel.controller.AdminRoomAllotmentController adminRoomAllotmentController;

    @Autowired
    private NotificationRepository notificationRepository;

    private User adminUser;
    private Hostel testHostel;
    private Room testRoom;

    @BeforeEach
    public void setup() {
        testHostel = hostelRepository.findAll().stream().findFirst().orElseGet(() -> {
            Hostel h = new Hostel();
            h.setName("PSG iTech Hostel");
            h.setCode("PSG_ITECH");
            return hostelRepository.save(h);
        });

        adminUser = userRepository.findAll().stream()
                .filter(u -> "ADMIN".equals(u.getRole()))
                .findFirst()
                .orElseGet(() -> {
                    User u = new User();
                    u.setEmail("admin_rrq_" + UUID.randomUUID() + "@hostel.edu");
                    u.setRegisterNumber("1234567890");
                    u.setRole("ADMIN");
                    u.setStatus(User.Status.ACTIVE);
                    u.setActive(true);
                    u.setPasswordHash("hashed");
                    return userRepository.save(u);
                });

        String uniqueRoomNum = "RRQ_R_" + System.currentTimeMillis() % 10000;
        Room r = new Room();
        r.setRoomNumber(uniqueRoomNum);
        r.setFloor(1);
        r.setCapacity(4);
        r.setStatus(Room.Status.ACTIVE);
        r.setActive(true);
        r.setHostel(testHostel);
        testRoom = roomRepository.save(r);

        for (int i = 1; i <= 4; i++) {
            Bed b = new Bed(testRoom, i);
            b.setBedLabel(String.valueOf((char) ('A' + (i - 1))));
            b.setStatus("available");
            bedRepository.save(b);
        }
    }

    private Student createStudent(String roll, String name, String gender) {
        User u = new User();
        u.setEmail(roll + "@student.hostel.edu");
        u.setRegisterNumber(roll);
        u.setRole("STUDENT");
        u.setStatus(User.Status.ACTIVE);
        u.setActive(true);
        u.setPasswordHash("hash");
        userRepository.save(u);

        Student s = new Student();
        s.setUser(u);
        s.setFullName(name);
        s.setRegisterNumber(roll);
        s.setCollegeEmail(roll + "@college.edu");
        s.setPhone("9876543210");
        s.setDepartment("Information Technology");
        s.setYear(2);
        s.setGender(gender != null ? gender : "Male");
        return studentRepository.save(s);
    }

    @Test
    @DisplayName("1. Student Submit Room Request: Generates formatted code and sets Pending status")
    @Transactional
    public void testStudentSubmitRoomRequest() {
        Student s = createStudent("715521001101", "Request Student 1", "Male");
        RoomRequestCreate reqDto = new RoomRequestCreate(testRoom.getId(), 1, "Standard 4-Bed", "715521001102, 715521001103", "Quiet room please");

        RoomRequestResponse resp = studentService.createRoomRequest(s.getUser(), reqDto);

        assertNotNull(resp.id());
        assertNotNull(resp.requestCode());
        assertTrue(resp.requestCode().startsWith("RRQ-"));
        assertEquals("PENDING", resp.status());
        assertEquals("Quiet room please", resp.note());
        assertEquals("715521001102, 715521001103", resp.preferredRoommates());

        // Verify in getMyRequests
        List<RoomRequestResponse> myRequests = studentService.getMyRequests(s.getUser());
        assertEquals(1, myRequests.size());
        assertEquals(resp.requestCode(), myRequests.get(0).requestCode());
    }

    @Test
    @DisplayName("2. Admin Approve Request: Allots bed, updates room allocation, updates student dashboard")
    @Transactional
    @WithMockUser(username = "admin@hostel.edu", roles = {"ADMIN"})
    public void testAdminApproveRoomRequest() {
        Student s = createStudent("715521001102", "Request Student 2", "Male");
        RoomRequestCreate reqDto = new RoomRequestCreate(testRoom.getId(), 1, "Standard 4-Bed", null, "Allotment test");
        RoomRequestResponse created = studentService.createRoomRequest(s.getUser(), reqDto);

        var userDetails = org.springframework.security.core.userdetails.User
                .withUsername(adminUser.getEmail())
                .password("pass")
                .roles("ADMIN")
                .build();

        var approveResult = adminRoomAllotmentController.approveRoomRequest(
                userDetails,
                created.id(),
                Map.of("roomId", testRoom.getId().toString(), "bedLabel", "A", "remarks", "Approved by Admin")
        );

        assertNotNull(approveResult.getBody());
        assertEquals(testRoom.getRoomNumber(), approveResult.getBody().get("roomNumber"));
        assertEquals("A", approveResult.getBody().get("bedLabel"));

        // Verify Request is now APPROVED
        RoomRequest updatedReq = roomRequestRepository.findById(created.id()).orElseThrow();
        assertEquals(RoomRequest.Status.APPROVED, updatedReq.getStatus());
        assertNotNull(updatedReq.getAllottedRoom());
        assertNotNull(updatedReq.getAllottedBed());
        assertEquals("A", updatedReq.getAllottedBed().getBedLabel());

        // Verify Student now has Room and Bed
        Student updatedStudent = studentRepository.findById(s.getId()).orElseThrow();
        assertNotNull(updatedStudent.getRoom());
        assertEquals(testRoom.getId(), updatedStudent.getRoom().getId());

        Bed bed = bedRepository.findByStudentId(s.getId()).orElseThrow();
        assertEquals("occupied", bed.getStatus());
        assertEquals("A", bed.getBedLabel());
    }

    @Test
    @DisplayName("3. Double Approval Guard: Already finalized request cannot be approved twice")
    @Transactional
    @WithMockUser(username = "admin@hostel.edu", roles = {"ADMIN"})
    public void testDoubleApprovalGuard() {
        Student s = createStudent("715521001103", "Request Student 3", "Male");
        RoomRequestResponse created = studentService.createRoomRequest(s.getUser(), new RoomRequestCreate(testRoom.getId(), 1, null, null, null));

        var userDetails = org.springframework.security.core.userdetails.User
                .withUsername(adminUser.getEmail())
                .password("pass")
                .roles("ADMIN")
                .build();

        // First approval succeeds
        adminRoomAllotmentController.approveRoomRequest(userDetails, created.id(), Map.of());

        // Second approval attempt fails
        assertThrows(BadRequestException.class, () -> {
            adminRoomAllotmentController.approveRoomRequest(userDetails, created.id(), Map.of());
        });
    }

    @Test
    @DisplayName("4. Reject Request: Mandatory reason saved and request finalized")
    @Transactional
    @WithMockUser(username = "admin@hostel.edu", roles = {"ADMIN"})
    public void testRejectRequestWithReason() {
        Student s = createStudent("715521001104", "Request Student 4", "Male");
        RoomRequestResponse created = studentService.createRoomRequest(s.getUser(), new RoomRequestCreate(testRoom.getId(), 1, null, null, null));

        var userDetails = org.springframework.security.core.userdetails.User
                .withUsername(adminUser.getEmail())
                .password("pass")
                .roles("ADMIN")
                .build();

        var rejectResult = adminRoomAllotmentController.rejectRoomRequest(
                userDetails,
                created.id(),
                new RejectRequest("Hostel full for this department batch.")
        );

        assertNotNull(rejectResult.getBody());

        RoomRequest req = roomRequestRepository.findById(created.id()).orElseThrow();
        assertEquals(RoomRequest.Status.REJECTED, req.getStatus());
        assertEquals("Hostel full for this department batch.", req.getDecisionNote());
    }

    @Test
    @DisplayName("5. Student Cancel Pending Request: Status transitions to CANCELLED")
    @Transactional
    public void testStudentCancelPendingRequest() {
        Student s = createStudent("715521001105", "Request Student 5", "Male");
        RoomRequestResponse created = studentService.createRoomRequest(s.getUser(), new RoomRequestCreate(testRoom.getId(), 1, null, null, null));

        studentService.cancelRoomRequest(s.getUser(), created.id());

        RoomRequest req = roomRequestRepository.findById(created.id()).orElseThrow();
        assertEquals(RoomRequest.Status.CANCELLED, req.getStatus());
        assertEquals("Cancelled by student", req.getDecisionNote());
    }

    @Test
    @DisplayName("6. Active Room Guard: Student with allocated room cannot submit new request")
    @Transactional
    public void testStudentWithActiveRoomBlockedFromRequest() {
        Student s = createStudent("715521001106", "Allocated Student", "Male");
        roomAllocationService.allocate(s.getId(), testRoom.getId(), "A", "Active", adminUser);

        assertThrows(BadRequestException.class, () -> {
            studentService.createRoomRequest(s.getUser(), new RoomRequestCreate(testRoom.getId(), 1, null, null, null));
        });
    }

    @Test
    @DisplayName("7. Pending Request Guard: Student cannot submit second request while first is pending")
    @Transactional
    public void testStudentCannotSubmitDuplicatePendingRequest() {
        Student s = createStudent("715521001107", "Pending Student", "Male");
        studentService.createRoomRequest(s.getUser(), new RoomRequestCreate(testRoom.getId(), 1, null, null, null));

        assertThrows(BadRequestException.class, () -> {
            studentService.createRoomRequest(s.getUser(), new RoomRequestCreate(testRoom.getId(), 1, null, null, null));
        });
    }

    @Test
    @DisplayName("8. IDOR Protection: Student cannot cancel another student's request")
    @Transactional
    public void testStudentCannotCancelAnotherStudentRequest() {
        Student studentA = createStudent("715521001108", "Student A", "Male");
        Student studentB = createStudent("715521001109", "Student B", "Male");

        RoomRequestResponse reqA = studentService.createRoomRequest(studentA.getUser(), new RoomRequestCreate(testRoom.getId(), 1, null, null, null));

        assertThrows(ForbiddenException.class, () -> {
            studentService.cancelRoomRequest(studentB.getUser(), reqA.id());
        });
    }

    @Test
    @DisplayName("9. Admin-Only Permission: Student or Warden forbidden from approving requests")
    @WithMockUser(username = "715521104001", roles = {"STUDENT"})
    public void testStudentForbiddenFromApprovingRequests() throws Exception {
        mockMvc.perform(post("/api/admin/rooms/requests/1/approve"))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("10. Admin-Only Permission: Warden forbidden from approving requests")
    @WithMockUser(username = "warden@hostel.edu", roles = {"WARDEN"})
    public void testWardenForbiddenFromApprovingRequests() throws Exception {
        mockMvc.perform(post("/api/admin/rooms/requests/1/approve"))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("11. Auto-assign Approve: Verifies notification, profile update, and room occupancy")
    @Transactional
    @WithMockUser(username = "admin@hostel.edu", roles = {"ADMIN"})
    public void testApproveWithAutoAssignAndCheckNotificationAndDashboard() {
        Student s = createStudent("715521001111", "Auto Assign Student", "Male");
        RoomRequestCreate reqDto = new RoomRequestCreate(null, 1, "Standard 4-Bed", null, "Auto-assign test");
        RoomRequestResponse created = studentService.createRoomRequest(s.getUser(), reqDto);

        var userDetails = org.springframework.security.core.userdetails.User
                .withUsername(adminUser.getEmail())
                .password("pass")
                .roles("ADMIN")
                .build();

        // Approve with empty body -> auto-assign
        var approveResult = adminRoomAllotmentController.approveRoomRequest(userDetails, created.id(), Map.of());
        assertNotNull(approveResult.getBody());

        RoomRequest approvedReq = roomRequestRepository.findById(created.id()).orElseThrow();
        assertEquals("APPROVED", approvedReq.getStatus().name());
        assertNotNull(approvedReq.getAllottedRoom());
        assertNotNull(approvedReq.getAllottedBed());

        // Verify room occupancy of the allotted room has at least 1 resident
        long occupiedInAllotted = bedRepository.countByRoomIdAndStudentIsNotNull(approvedReq.getAllottedRoom().getId());
        assertTrue(occupiedInAllotted >= 1);

        // Verify Student Profile shows ALLOCATED and room/bed
        var profile = studentService.getProfile(s.getUser());
        assertEquals("ALLOCATED", profile.allocationStatus());
        assertEquals(approvedReq.getAllottedRoom().getRoomNumber(), profile.roomNumber());
        assertEquals(approvedReq.getAllottedBed().getBedNumber(), profile.bedNumber());

        // Verify Notification sent to Student
        var notifs = notificationRepository.findByUserIdOrderByCreatedAtDesc(s.getUser().getId(), org.springframework.data.domain.PageRequest.of(0, 10));
        assertTrue(notifs.getContent().stream().anyMatch(n -> n.getTitle().contains("Room") && n.getMessage().contains("approved")));
    }

    @Test
    @DisplayName("12. Full Room Rejection: Clear message when chosen room has no free beds")
    @Transactional
    @WithMockUser(username = "admin@hostel.edu", roles = {"ADMIN"})
    public void testApproveFullRoomRejectedWithClearMessage() {
        // Fill all 4 beds in testRoom
        for (int i = 1; i <= 4; i++) {
            Student resident = createStudent("71552100112" + i, "Resident " + i, "Male");
            Bed b = bedRepository.findByRoomIdAndBedLabel(testRoom.getId(), String.valueOf((char) ('A' + (i - 1)))).orElseThrow();
            b.setStudent(resident);
            b.setStatus("occupied");
            bedRepository.save(b);
        }

        Student s = createStudent("715521001129", "Overfill Student", "Male");
        RoomRequestResponse created = studentService.createRoomRequest(s.getUser(), new RoomRequestCreate(testRoom.getId(), 1, null));

        var userDetails = org.springframework.security.core.userdetails.User
                .withUsername(adminUser.getEmail())
                .password("pass")
                .roles("ADMIN")
                .build();

        ConflictException ex = assertThrows(ConflictException.class, () -> {
            adminRoomAllotmentController.approveRoomRequest(
                    userDetails,
                    created.id(),
                    Map.of("roomId", testRoom.getId().toString())
            );
        });

        assertTrue(ex.getMessage().contains("fully occupied"), "Expected 'fully occupied' in: " + ex.getMessage());
    }

    @Test
    @DisplayName("13. Student Already Has Room: Rejected with clear message")
    @Transactional
    @WithMockUser(username = "admin@hostel.edu", roles = {"ADMIN"})
    public void testApproveStudentAlreadyHasRoomRejectedWithClearMessage() {
        Student s = createStudent("715521001130", "Already Housed Student", "Male");
        RoomRequestResponse created = studentService.createRoomRequest(s.getUser(), new RoomRequestCreate(null, 1, null));

        // Allocate student directly
        roomAllocationService.allocate(s.getId(), testRoom.getId(), "A", "Pre-allocated", adminUser);

        var userDetails = org.springframework.security.core.userdetails.User
                .withUsername(adminUser.getEmail())
                .password("pass")
                .roles("ADMIN")
                .build();

        ConflictException ex = assertThrows(ConflictException.class, () -> {
            adminRoomAllotmentController.approveRoomRequest(userDetails, created.id(), Map.of());
        });

        assertTrue(ex.getMessage().contains("already has an active room allocation"), "Expected active room message in: " + ex.getMessage());
    }
}
