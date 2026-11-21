package com.hostel;

import com.hostel.dto.RoomAllocationDtos.BulkAllocationResponse;
import com.hostel.entity.*;
import com.hostel.exception.CustomExceptions.ConflictException;
import com.hostel.exception.CustomExceptions.ForbiddenException;
import com.hostel.repository.*;
import com.hostel.service.RoomAllocationService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
public class RoomBedAllocationTests {

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
    private WardenAssignmentRepository wardenAssignmentRepository;

    @Autowired
    private RoomAllocationRepository roomAllocationRepository;

    private User adminUser;
    private Hostel mainHostel;

    @BeforeEach
    void setUp() {
        mainHostel = hostelRepository.findAll().stream().findFirst().orElseGet(() -> {
            Hostel h = new Hostel();
            h.setCode("TEST_HOSTEL");
            h.setName("PSG iTech Hostel");
            h.setActive(true);
            return hostelRepository.save(h);
        });

        adminUser = userRepository.findByRoleAndStatus("ADMIN", User.Status.ACTIVE).stream().findFirst().orElseGet(() -> {
            User u = new User();
            u.setEmail("admin_test_" + UUID.randomUUID().toString().substring(0, 8) + "@hostel.edu");
            u.setRole("ADMIN");
            u.setPasswordHash("hashed");
            u.setStatus(User.Status.ACTIVE);
            return userRepository.save(u);
        });
    }

    private Student createTestStudent(String rollSuffix) {
        String roll = "7155" + rollSuffix;
        User user = new User();
        user.setEmail("stud_" + roll + "@student.edu");
        user.setRegisterNumber(roll);
        user.setName("Student " + rollSuffix);
        user.setRole("STUDENT");
        user.setStatus(User.Status.ACTIVE);
        user.setPasswordHash("hashed");
        user = userRepository.save(user);

        Student student = new Student();
        student.setUser(user);
        student.setFullName("Student " + rollSuffix);
        student.setRegisterNumber(roll);
        student.setCollegeEmail(user.getEmail());
        student.setPhone("9876543" + rollSuffix.substring(Math.max(0, rollSuffix.length() - 3)));
        student.setDepartment("CSE");
        student.setYear(1);
        student.setActive(true);
        return studentRepository.save(student);
    }

    @Test
    @Transactional
    @DisplayName("1. Room capacity of 4: exactly 4 students can be allocated, 5th student is rejected")
    void testRoomCapacityOfFour() {
        Room room = roomRepository.findByRoomNumber("110").orElseThrow();

        // Vacate any pre-existing allocations in room 110 for isolation
        List<Bed> existingBeds = bedRepository.findByRoomIdOrderByBedNumberAsc(room.getId());
        for (Bed b : existingBeds) {
            if (b.getStudent() != null) {
                roomAllocationService.vacate(b.getStudent().getId(), "Setup clean", adminUser);
            }
        }

        // Create 5 students
        Student s1 = createTestStudent("21104901");
        Student s2 = createTestStudent("21104902");
        Student s3 = createTestStudent("21104903");
        Student s4 = createTestStudent("21104904");
        Student s5 = createTestStudent("21104905");

        // Allocate first 4 students
        assertNotNull(roomAllocationService.allocate(s1.getId(), room.getId(), "A", "Alloc 1", adminUser));
        assertNotNull(roomAllocationService.allocate(s2.getId(), room.getId(), "B", "Alloc 2", adminUser));
        assertNotNull(roomAllocationService.allocate(s3.getId(), room.getId(), "C", "Alloc 3", adminUser));
        assertNotNull(roomAllocationService.allocate(s4.getId(), room.getId(), "D", "Alloc 4", adminUser));

        // 5th student allocation must fail with capacity / conflict exception
        ConflictException ex = assertThrows(ConflictException.class, () -> {
            roomAllocationService.allocate(s5.getId(), room.getId(), null, "Alloc 5", adminUser);
        });
        assertTrue(ex.getMessage().toLowerCase().contains("occupied") || ex.getMessage().toLowerCase().contains("capacity"));

        // Verify room still has exactly 4 occupied beds
        assertEquals(4, bedRepository.countByRoomIdAndStudentIsNotNull(room.getId()));
    }

    @Test
    @DisplayName("2. Concurrent allocation race: two threads competing for the last bed with row lock")
    void testConcurrentAllocationRace() throws InterruptedException, ExecutionException {
        // Create an isolated room with 1 bed to test race condition
        Room raceRoom = new Room("RACE_" + System.currentTimeMillis(), 1, 1, Room.Status.ACTIVE);
        raceRoom.setHostel(mainHostel);
        raceRoom = roomRepository.save(raceRoom);

        Bed bed = new Bed(raceRoom, 1);
        bed.setBedLabel("A");
        bed.setStatus("available");
        bed = bedRepository.save(bed);

        Student s1 = createTestStudent("31104911");
        Student s2 = createTestStudent("31104912");

        final Long roomId = raceRoom.getId();
        final Long s1Id = s1.getId();
        final Long s2Id = s2.getId();

        ExecutorService executor = Executors.newFixedThreadPool(2);
        CountDownLatch latch = new CountDownLatch(1);
        AtomicInteger successCount = new AtomicInteger(0);
        AtomicInteger conflictCount = new AtomicInteger(0);

        Callable<Void> task1 = () -> {
            latch.await();
            try {
                roomAllocationService.allocate(s1Id, roomId, "A", "Race 1", adminUser);
                successCount.incrementAndGet();
            } catch (ConflictException e) {
                conflictCount.incrementAndGet();
            }
            return null;
        };

        Callable<Void> task2 = () -> {
            latch.await();
            try {
                roomAllocationService.allocate(s2Id, roomId, "A", "Race 2", adminUser);
                successCount.incrementAndGet();
            } catch (ConflictException e) {
                conflictCount.incrementAndGet();
            }
            return null;
        };

        Future<Void> f1 = executor.submit(task1);
        Future<Void> f2 = executor.submit(task2);
        latch.countDown();

        f1.get();
        f2.get();
        executor.shutdown();

        assertEquals(1, successCount.get(), "Exactly one allocation should succeed");
        assertEquals(1, conflictCount.get(), "One allocation should fail with conflict");
        assertEquals(1, bedRepository.countByRoomIdAndStudentIsNotNull(roomId), "Room capacity must never exceed 1");
    }

    @Test
    @Transactional
    @DisplayName("3. One active allocation per student rule")
    void testOneActiveAllocationPerStudentRule() {
        Room room1 = roomRepository.findByRoomNumber("120").orElseThrow();
        Room room2 = roomRepository.findByRoomNumber("121").orElseThrow();
        Student student = createTestStudent("41104921");

        // First allocation
        RoomAllocation alloc1 = roomAllocationService.allocate(student.getId(), room1.getId(), "A", "First", adminUser);
        assertNotNull(alloc1);

        // Attempting a second active allocation without transferring/vacating must fail
        ConflictException ex = assertThrows(ConflictException.class, () -> {
            roomAllocationService.allocate(student.getId(), room2.getId(), "A", "Second", adminUser);
        });
        assertTrue(ex.getMessage().toLowerCase().contains("already allocated"));
    }

    @Test
    @Transactional
    @DisplayName("4. Vacate and move history: closing old allocation, recording history, freeing bed")
    void testVacateAndMoveHistory() {
        Room r1 = roomRepository.findByRoomNumber("130").orElseThrow();
        Room r2 = roomRepository.findByRoomNumber("131").orElseThrow();
        Student student = createTestStudent("51104931");

        // 1. Initial allocation
        RoomAllocation alloc1 = roomAllocationService.allocate(student.getId(), r1.getId(), "A", "Initial", adminUser);
        assertEquals("active", alloc1.getStatus());
        assertNull(alloc1.getVacatedAt());

        // 2. Change room (Move)
        RoomAllocation alloc2 = roomAllocationService.changeRoom(student.getId(), r2.getId(), "B", "Moved for study", adminUser);
        assertEquals("active", alloc2.getStatus());
        assertEquals("131", alloc2.getRoom().getRoomNumber());
        assertEquals("B", alloc2.getBed().getBedLabel());

        // Verify old allocation is marked transferred with vacatedAt
        RoomAllocation oldAlloc = roomAllocationRepository.findById(alloc1.getId()).orElseThrow();
        assertEquals("transferred", oldAlloc.getStatus());
        assertNotNull(oldAlloc.getVacatedAt());

        // Verify old bed is free
        Bed oldBed = bedRepository.findByRoomIdAndBedLabel(r1.getId(), "A").orElseThrow();
        assertNull(oldBed.getStudent());
        assertEquals("available", oldBed.getStatus());

        // 3. Vacate
        roomAllocationService.vacate(student.getId(), "Graduated", adminUser);

        // Verify current allocation is marked vacated
        RoomAllocation vacatedAlloc = roomAllocationRepository.findById(alloc2.getId()).orElseThrow();
        assertEquals("vacated", vacatedAlloc.getStatus());
        assertNotNull(vacatedAlloc.getVacatedAt());

        // Verify new bed is also free
        Bed newBed = bedRepository.findByRoomIdAndBedLabel(r2.getId(), "B").orElseThrow();
        assertNull(newBed.getStudent());
        assertEquals("available", newBed.getStatus());

        // Verify student has 2 history records
        List<RoomAllocation> history = roomAllocationRepository.findByStudentIdOrderByAllocatedAtDesc(student.getId());
        assertEquals(2, history.size());
    }

    @Test
    @Transactional
    @DisplayName("5. Bulk CSV allocation: processes rows, validates capacity, reports errors")
    void testBulkCsvAllocation() {
        Room room = roomRepository.findByRoomNumber("140").orElseThrow();
        Student s1 = createTestStudent("61104941");
        Student s2 = createTestStudent("61104942");

        String csv = "RollNumber,RoomNumber,BedLabel\n" +
                s1.getRegisterNumber() + ",140,A\n" +
                s2.getRegisterNumber() + ",140,B\n" +
                "12345,140,C\n" +               // Invalid roll
                "715599999999,140,D\n" +        // Non-existent student
                s1.getRegisterNumber() + ",999,A\n"; // Non-existent room

        MockMultipartFile file = new MockMultipartFile("file", "bulk_alloc.csv", "text/csv", csv.getBytes());
        BulkAllocationResponse resp = roomAllocationService.bulkAllocate(file, adminUser);

        assertEquals(5, resp.totalRows());
        assertEquals(2, resp.successCount());
        assertEquals(3, resp.failedCount());
        assertEquals(3, resp.errors().size());

        // Verify s1 and s2 are in room 140
        Bed bA = bedRepository.findByRoomIdAndBedLabel(room.getId(), "A").orElseThrow();
        assertNotNull(bA.getStudent());
        assertEquals(s1.getId(), bA.getStudent().getId());

        Bed bB = bedRepository.findByRoomIdAndBedLabel(room.getId(), "B").orElseThrow();
        assertNotNull(bB.getStudent());
        assertEquals(s2.getId(), bB.getStudent().getId());
    }

    @Test
    @Transactional
    @DisplayName("6. Permissions: Warden can only allocate within own hostel, Student can only see own room")
    void testPermissions() {
        // Create second hostel
        Hostel otherHostel = new Hostel();
        otherHostel.setCode("OTHER_HOSTEL_" + System.currentTimeMillis());
        otherHostel.setName("Other Campus Hostel");
        otherHostel.setActive(true);
        otherHostel = hostelRepository.save(otherHostel);

        // Create room in second hostel
        Room otherRoom = new Room("OTHER_201", 1, 4, Room.Status.ACTIVE);
        otherRoom.setHostel(otherHostel);
        otherRoom = roomRepository.save(otherRoom);

        Bed otherBed = new Bed(otherRoom, 1);
        otherBed.setBedLabel("A");
        otherBed.setStatus("available");
        bedRepository.save(otherBed);

        // Create Warden user assigned ONLY to mainHostel
        User wardenUser = new User();
        wardenUser.setEmail("warden_perm_" + System.currentTimeMillis() + "@hostel.edu");
        wardenUser.setRole("WARDEN");
        wardenUser.setPasswordHash("hashed");
        wardenUser.setStatus(User.Status.ACTIVE);
        wardenUser = userRepository.save(wardenUser);

        WardenAssignment wa = new WardenAssignment();
        wa.setWardenUser(wardenUser);
        wa.setHostel(mainHostel);
        wa.setActive(true);
        wardenAssignmentRepository.save(wa);

        Student student = createTestStudent("71104951");

        // 1. Warden allocating to other hostel must fail with ForbiddenException
        final Long otherRoomId = otherRoom.getId();
        final User wardenActor = wardenUser;
        assertThrows(ForbiddenException.class, () -> {
            roomAllocationService.allocate(student.getId(), otherRoomId, "A", "Warden cross hostel", wardenActor);
        });

        // 2. Admin allocating to other hostel succeeds
        RoomAllocation adminAlloc = roomAllocationService.allocate(student.getId(), otherRoom.getId(), "A", "Admin cross hostel", adminUser);
        assertNotNull(adminAlloc);

        // 3. Student querying another student's allocation must fail with ForbiddenException
        Student studentOther = createTestStudent("71104952");
        assertThrows(ForbiddenException.class, () -> {
            roomAllocationService.getStudentAllocation(student.getId(), studentOther.getUser());
        });

        // 4. Student querying own allocation succeeds
        var selfAlloc = roomAllocationService.getStudentAllocation(student.getId(), student.getUser());
        assertNotNull(selfAlloc);
        assertEquals(student.getRegisterNumber(), selfAlloc.rollNumber());
    }
}
