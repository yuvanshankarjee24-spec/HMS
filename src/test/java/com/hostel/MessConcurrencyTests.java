package com.hostel;

import com.hostel.dto.MessDtos.*;
import com.hostel.entity.FoodItem;
import com.hostel.entity.Student;
import com.hostel.entity.User;
import com.hostel.repository.FoodItemRepository;
import com.hostel.repository.MenuScheduleRepository;
import com.hostel.repository.StudentRepository;
import com.hostel.repository.UserRepository;
import com.hostel.service.MessTokenService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
public class MessConcurrencyTests {

    @Autowired private MessTokenService messService;
    @Autowired private UserRepository userRepository;
    @Autowired private StudentRepository studentRepository;
    @Autowired private FoodItemRepository foodItemRepository;
    @Autowired private MenuScheduleRepository menuScheduleRepository;

    private Student createCommittedStudent(String prefix) {
        String uniq = prefix + "_" + System.nanoTime();
        User u = new User();
        u.setEmail(uniq + "@college.edu");
        u.setRegisterNumber("MS_" + System.nanoTime() % 1_000_000_000L);
        u.setPasswordHash("dummy");
        u.setRole("STUDENT");
        u.setStatus(User.Status.ACTIVE);
        u = userRepository.save(u);

        Student s = new Student();
        s.setUser(u);
        s.setFullName("Conc Mess " + prefix);
        s.setRegisterNumber(u.getRegisterNumber());
        s.setCollegeEmail(u.getEmail());
        s.setPhone("+91 9876501234");
        s.setDepartment("CSE");
        s.setYear(2);
        return studentRepository.save(s);
    }

    @Test
    @DisplayName("Two students racing to book the last 1 remaining stock item result in exactly 1 success and zero overselling")
    void testConcurrentLastStockBooking() throws InterruptedException, ExecutionException {
        User warden = userRepository.findByEmail("warden@hostel.edu").orElseThrow();
        Student s1 = createCommittedStudent("s1");
        Student s2 = createCommittedStudent("s2");

        FoodItem biryani = foodItemRepository.findAll().stream()
                .filter(f -> "Chicken Biryani".equalsIgnoreCase(f.getName())).findFirst().orElseThrow();
        LocalDate menuDate = LocalDate.now().plusDays(5);

        // Set dailyStock = 1 for DINNER on menuDate
        MenuScheduleResponse sched = messService.scheduleMenuItem(warden,
                new MenuScheduleItemRequest(menuDate, "DINNER", biryani.getId(), 1, 1, true, false));
        LocalDateTime bookAt = LocalDateTime.of(menuDate, LocalTime.of(16, 0));

        ExecutorService pool = Executors.newFixedThreadPool(2);
        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch go = new CountDownLatch(1);
        AtomicInteger successes = new AtomicInteger(0);
        AtomicInteger failures = new AtomicInteger(0);
        AtomicReference<Long> winningTokenId = new AtomicReference<>();

        Callable<Void> r1 = () -> {
            ready.countDown();
            go.await(5, TimeUnit.SECONDS);
            try {
                MessTokenResponse res = messService.bookTokenAt(
                        s1.getUser(), new BookTokenRequest(menuDate, "DINNER", List.of(new BookItemQty(sched.id(), 1))), bookAt);
                winningTokenId.set(res.id());
                successes.incrementAndGet();
            } catch (Exception e) {
                failures.incrementAndGet();
            }
            return null;
        };

        Callable<Void> r2 = () -> {
            ready.countDown();
            go.await(5, TimeUnit.SECONDS);
            try {
                MessTokenResponse res = messService.bookTokenAt(
                        s2.getUser(), new BookTokenRequest(menuDate, "DINNER", List.of(new BookItemQty(sched.id(), 1))), bookAt);
                winningTokenId.set(res.id());
                successes.incrementAndGet();
            } catch (Exception e) {
                failures.incrementAndGet();
            }
            return null;
        };

        Future<Void> f1 = pool.submit(r1);
        Future<Void> f2 = pool.submit(r2);
        ready.await(5, TimeUnit.SECONDS);
        go.countDown();
        f1.get();
        f2.get();
        pool.shutdown();

        assertEquals(1, successes.get(), "Exactly one student must win the last stock item");
        assertEquals(1, failures.get(), "Second racing student must be rejected without overselling");
        assertEquals(0, menuScheduleRepository.findById(sched.id()).orElseThrow().getRemainingStock());

        // Now test double-scan race on that winning token during the DINNER QR window (19:50)
        ExecutorService scanPool = Executors.newFixedThreadPool(2);
        CountDownLatch scanReady = new CountDownLatch(2);
        CountDownLatch scanGo = new CountDownLatch(1);
        AtomicInteger scanSuccess = new AtomicInteger(0);
        AtomicInteger scanFail = new AtomicInteger(0);
        LocalDateTime scanAt = LocalDateTime.of(menuDate, LocalTime.of(19, 50));

        Callable<Void> scanTask = () -> {
            scanReady.countDown();
            scanGo.await(5, TimeUnit.SECONDS);
            try {
                messService.confirmDeliveryAt(
                        warden,
                        new ConfirmMessDeliveryRequest(null, winningTokenId.get(), "Race scan", null),
                        scanAt);
                scanSuccess.incrementAndGet();
            } catch (Exception e) {
                scanFail.incrementAndGet();
            }
            return null;
        };

        Future<Void> sf1 = scanPool.submit(scanTask);
        Future<Void> sf2 = scanPool.submit(scanTask);
        scanReady.await(5, TimeUnit.SECONDS);
        scanGo.countDown();
        sf1.get();
        sf2.get();
        scanPool.shutdown();

        assertEquals(1, scanSuccess.get(), "Exactly one QR delivery scan must succeed");
        assertEquals(1, scanFail.get(), "Simultaneous double-scan must be blocked");
    }
}
