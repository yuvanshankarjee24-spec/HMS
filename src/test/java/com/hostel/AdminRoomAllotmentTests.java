package com.hostel;

import com.hostel.dto.RoomAllocationDtos.BulkValidationResponse;
import com.hostel.entity.*;
import com.hostel.exception.CustomExceptions.BadRequestException;
import com.hostel.exception.CustomExceptions.ConflictException;
import com.hostel.repository.*;
import com.hostel.service.RoomAllocationService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
public class AdminRoomAllotmentTests {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private RoomAllocationService roomAllocationService;

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
    private RoomAllocationRepository roomAllocationRepository;

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
                    u.setEmail("admin_test_" + UUID.randomUUID() + "@hostel.edu");
                    u.setRegisterNumber("1234567890");
                    u.setRole("ADMIN");
                    u.setStatus(User.Status.ACTIVE);
                    u.setActive(true);
                    u.setPasswordHash("hashed");
                    return userRepository.save(u);
                });

        // Ensure we have a fresh test room
        String uniqueRoomNum = "TEST_" + System.currentTimeMillis() % 10000;
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

    private Student createTestStudent(String roll, String name, String gender) {
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
        s.setDepartment("Computer Science");
        s.setYear(2);
        s.setGender(gender != null ? gender : "Male");
        return studentRepository.save(s);
    }

    @Test
    @DisplayName("1. Capacity Enforcement: Exactly 4 students succeed; 5th student rejected")
    @Transactional
    public void testRoomCapacityFourRejection() {
        for (int i = 1; i <= 4; i++) {
            String roll = "715521" + String.format("%06d", 1000 + i);
            Student s = createTestStudent(roll, "Student " + i, "Male");
            assertNotNull(roomAllocationService.allocate(s.getId(), testRoom.getId(), null, "Test", adminUser));
        }

        assertEquals(4, bedRepository.countByRoomIdAndStudentIsNotNull(testRoom.getId()));

        Student fifthStudent = createTestStudent("715521005999", "Fifth Student", "Male");
        assertThrows(ConflictException.class, () -> {
            roomAllocationService.allocate(fifthStudent.getId(), testRoom.getId(), null, "Overfill", adminUser);
        });
    }

    @Test
    @DisplayName("2. Concurrent Allocation Race: Multi-threaded allocation with row locking")
    public void testConcurrentAllocationRace() throws InterruptedException {
        String raceRoomNum = "RACE_" + System.currentTimeMillis() % 10000;
        Room raceRoom = new Room();
        raceRoom.setRoomNumber(raceRoomNum);
        raceRoom.setFloor(2);
        raceRoom.setCapacity(4);
        raceRoom.setStatus(Room.Status.ACTIVE);
        raceRoom.setActive(true);
        raceRoom.setHostel(testHostel);
        raceRoom = roomRepository.save(raceRoom);

        for (int i = 1; i <= 4; i++) {
            Bed b = new Bed(raceRoom, i);
            b.setBedLabel(String.valueOf((char) ('A' + (i - 1))));
            b.setStatus("available");
            bedRepository.save(b);
        }

        Long roomId = raceRoom.getId();
        int attempts = 6;
        ExecutorService executor = Executors.newFixedThreadPool(attempts);
        CountDownLatch latch = new CountDownLatch(1);
        AtomicInteger successCount = new AtomicInteger(0);
        AtomicInteger failureCount = new AtomicInteger(0);

        for (int i = 1; i <= attempts; i++) {
            final int idx = i;
            executor.submit(() -> {
                try {
                    latch.await();
                    String roll = "715522" + String.format("%06d", 5000 + idx);
                    Student s = createTestStudent(roll, "Race Student " + idx, "Male");
                    roomAllocationService.allocate(s.getId(), roomId, null, "Concurrent test", adminUser);
                    successCount.incrementAndGet();
                } catch (Exception e) {
                    failureCount.incrementAndGet();
                }
            });
        }

        latch.countDown();
        executor.shutdown();
        assertTrue(executor.awaitTermination(10, TimeUnit.SECONDS));

        assertEquals(4, successCount.get(), "Exactly 4 allocations must succeed");
        assertEquals(2, failureCount.get(), "Excess allocations must fail");
        assertEquals(4, bedRepository.countByRoomIdAndStudentIsNotNull(roomId));
    }

    @Test
    @DisplayName("3. One Active Allocation Rule: Student cannot have two active beds")
    @Transactional
    public void testOneActiveAllocationRule() {
        Student s = createTestStudent("715521009876", "Single Alloc Student", "Male");
        assertNotNull(roomAllocationService.allocate(s.getId(), testRoom.getId(), "A", "First", adminUser));

        assertThrows(ConflictException.class, () -> {
            roomAllocationService.allocate(s.getId(), testRoom.getId(), "B", "Duplicate", adminUser);
        });
    }

    @Test
    @DisplayName("4. Maintenance Block: Room and Bed under maintenance cannot be allotted")
    @Transactional
    public void testMaintenanceBlock() {
        Student s1 = createTestStudent("715521007701", "Maint Student 1", "Male");

        // Mark Bed A as maintenance
        Bed bedA = bedRepository.findByRoomIdAndBedLabel(testRoom.getId(), "A").orElseThrow();
        bedA.setStatus("maintenance");
        bedRepository.save(bedA);

        // Attempting to allot Bed A must fail
        assertThrows(BadRequestException.class, () -> {
            roomAllocationService.allocate(s1.getId(), testRoom.getId(), "A", "Maint Bed", adminUser);
        });

        // Mark entire room as maintenance
        testRoom.setStatus(Room.Status.MAINTENANCE);
        roomRepository.save(testRoom);

        Student s2 = createTestStudent("715521007702", "Maint Student 2", "Male");
        assertThrows(BadRequestException.class, () -> {
            roomAllocationService.allocate(s2.getId(), testRoom.getId(), "B", "Maint Room", adminUser);
        });
    }

    @Test
    @DisplayName("5. Gender Rule: Blocking student of different gender from mixed room")
    @Transactional
    public void testGenderCompatibilityRule() {
        Student maleStudent = createTestStudent("715521008801", "Male Resident", "Male");
        roomAllocationService.allocate(maleStudent.getId(), testRoom.getId(), "A", "Male Allotment", adminUser);

        Student femaleStudent = createTestStudent("715521008802", "Female Resident", "Female");
        assertThrows(ConflictException.class, () -> {
            roomAllocationService.allocate(femaleStudent.getId(), testRoom.getId(), "B", "Female Allotment", adminUser);
        });
    }

    @Test
    @DisplayName("6. Bulk Upload Validation: Previews valid and invalid rows with reasons")
    @Transactional
    public void testBulkValidationPreview() {
        Student validStudent = createTestStudent("715521003301", "Valid CSV Student", "Male");

        String csvContent = "RollNumber,RoomNumber,BedLabel\n" +
                validStudent.getRegisterNumber() + "," + testRoom.getRoomNumber() + ",A\n" +
                "999999999999," + testRoom.getRoomNumber() + ",B\n" + // Bad roll format
                "715599999999," + testRoom.getRoomNumber() + ",C\n";  // Non-existent student

        MockMultipartFile file = new MockMultipartFile("file", "test.csv", "text/csv", csvContent.getBytes());
        BulkValidationResponse resp = roomAllocationService.bulkValidate(file);

        assertEquals(3, resp.totalRows());
        assertEquals(1, resp.validCount());
        assertEquals(2, resp.errorCount());
    }

    @Test
    @DisplayName("7. Admin-Only Permissions: Non-admin users cannot access /api/admin/rooms endpoints")
    @WithMockUser(username = "715521104001", roles = {"STUDENT"})
    public void testStudentForbiddenFromAdminApis() throws Exception {
        mockMvc.perform(get("/api/admin/rooms/summary"))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("8. Admin-Only Permissions: Wardens cannot access /api/admin/rooms endpoints")
    @WithMockUser(username = "warden_test@hostel.edu", roles = {"WARDEN"})
    public void testWardenForbiddenFromAdminApis() throws Exception {
        mockMvc.perform(get("/api/admin/rooms/summary"))
                .andExpect(status().isForbidden());
    }
}
