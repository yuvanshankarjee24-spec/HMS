package com.hostel;

import com.hostel.dto.WardenDtos.ApproveRequest;
import com.hostel.entity.*;
import com.hostel.exception.CustomExceptions.ConflictException;
import com.hostel.repository.*;
import com.hostel.service.WardenService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
public class AllocationConcurrencyTests {

    @Autowired
    private WardenService wardenService;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private StudentRepository studentRepository;

    @Autowired
    private RoomRepository roomRepository;

    @Autowired
    private BedRepository bedRepository;

    @Autowired
    private RoomRequestRepository roomRequestRepository;

    @Test
    @DisplayName("Two concurrent approvals for the last bed result in exactly one success")
    void testConcurrentApprovalsForLastBed() throws InterruptedException, ExecutionException {
        // Create Warden actor user
        User wardenUser = new User();
        wardenUser.setEmail("warden_concurrency@hostel.edu");
        wardenUser.setPasswordHash("dummy");
        wardenUser.setRole("WARDEN");
        wardenUser = userRepository.save(wardenUser);

        // Create Room with exactly 1 Bed
        Room room = new Room("CONCURRENCY_ROOM_" + System.currentTimeMillis(), 1, 1, Room.Status.ACTIVE);
        room = roomRepository.save(room);

        Bed bed = new Bed(room, 1);
        bed = bedRepository.save(bed);
        final Long bedId = bed.getId();

        // Create Student 1 & Request 1
        User u1 = new User();
        u1.setEmail("concurrent_stud1_" + System.currentTimeMillis() + "@college.edu");
        u1.setRegisterNumber("CONC01_" + System.currentTimeMillis());
        u1.setPasswordHash("dummy");
        u1.setRole("STUDENT");
        u1 = userRepository.save(u1);

        Student s1 = new Student();
        s1.setUser(u1);
        s1.setFullName("Concurrent Student 1");
        s1.setRegisterNumber(u1.getRegisterNumber());
        s1.setCollegeEmail(u1.getEmail());
        s1.setPhone("+91 9999900001");
        s1.setDepartment("CSE");
        s1.setYear(1);
        s1 = studentRepository.save(s1);

        RoomRequest r1 = new RoomRequest();
        r1.setStudent(s1);
        r1.setPreferredRoom(room);
        r1.setStatus(RoomRequest.Status.PENDING);
        r1 = roomRequestRepository.save(r1);
        final Long reqId1 = r1.getId();

        // Create Student 2 & Request 2
        User u2 = new User();
        u2.setEmail("concurrent_stud2_" + System.currentTimeMillis() + "@college.edu");
        u2.setRegisterNumber("CONC02_" + System.currentTimeMillis());
        u2.setPasswordHash("dummy");
        u2.setRole("STUDENT");
        u2 = userRepository.save(u2);

        Student s2 = new Student();
        s2.setUser(u2);
        s2.setFullName("Concurrent Student 2");
        s2.setRegisterNumber(u2.getRegisterNumber());
        s2.setCollegeEmail(u2.getEmail());
        s2.setPhone("+91 9999900002");
        s2.setDepartment("ECE");
        s2.setYear(1);
        s2 = studentRepository.save(s2);

        RoomRequest r2 = new RoomRequest();
        r2.setStudent(s2);
        r2.setPreferredRoom(room);
        r2.setStatus(RoomRequest.Status.PENDING);
        r2 = roomRequestRepository.save(r2);
        final Long reqId2 = r2.getId();

        // Concurrency Execution with 2 threads and CountDownLatch
        ExecutorService executor = Executors.newFixedThreadPool(2);
        CountDownLatch startLatch = new CountDownLatch(1);
        AtomicInteger successCount = new AtomicInteger(0);
        AtomicInteger conflictCount = new AtomicInteger(0);

        final User finalWarden = wardenUser;

        Callable<Void> task1 = () -> {
            startLatch.await();
            try {
                wardenService.approveRoomRequest(reqId1, new ApproveRequest(bedId), finalWarden);
                successCount.incrementAndGet();
            } catch (ConflictException e) {
                conflictCount.incrementAndGet();
            }
            return null;
        };

        Callable<Void> task2 = () -> {
            startLatch.await();
            try {
                wardenService.approveRoomRequest(reqId2, new ApproveRequest(bedId), finalWarden);
                successCount.incrementAndGet();
            } catch (ConflictException e) {
                conflictCount.incrementAndGet();
            }
            return null;
        };

        Future<Void> f1 = executor.submit(task1);
        Future<Void> f2 = executor.submit(task2);

        // Fire both simultaneously
        startLatch.countDown();

        f1.get();
        f2.get();
        executor.shutdown();

        // Assertions
        assertEquals(1, successCount.get(), "Exactly one approval should succeed for the last bed");
        assertEquals(1, conflictCount.get(), "The competing approval should fail with ConflictException");

        Bed finalBed = bedRepository.findById(bedId).orElseThrow();
        assertNotNull(finalBed.getStudent(), "The bed must have an allocated student");
    }
}
