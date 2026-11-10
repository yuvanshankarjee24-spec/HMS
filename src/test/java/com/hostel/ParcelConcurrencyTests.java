package com.hostel;

import com.hostel.dto.ParcelDtos.ConfirmDeliveryRequest;
import com.hostel.dto.ParcelDtos.ParcelResponse;
import com.hostel.entity.Student;
import com.hostel.entity.User;
import com.hostel.repository.StudentRepository;
import com.hostel.repository.UserRepository;
import com.hostel.service.ParcelService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
public class ParcelConcurrencyTests {

    @Autowired private ParcelService parcelService;
    @Autowired private UserRepository userRepository;
    @Autowired private StudentRepository studentRepository;

    @Test
    @DisplayName("Two concurrent QR delivery confirmations for the same parcel result in exactly one success")
    void testConcurrentDoubleScanDelivery() throws InterruptedException, ExecutionException {
        User warden = userRepository.findByEmail("warden@hostel.edu").orElseThrow();

        User u = new User();
        u.setEmail("prc_conc_" + System.currentTimeMillis() + "@college.edu");
        u.setRegisterNumber("PRCC_" + System.currentTimeMillis());
        u.setPasswordHash("dummy");
        u.setRole("STUDENT");
        u.setStatus(User.Status.ACTIVE);
        u = userRepository.save(u);

        Student s = new Student();
        s.setUser(u);
        s.setFullName("Concurrent Parcel Student");
        s.setRegisterNumber(u.getRegisterNumber());
        s.setCollegeEmail(u.getEmail());
        s.setPhone("+91 9876543210");
        s.setDepartment("CSE");
        s.setYear(2);
        s = studentRepository.save(s);

        ParcelResponse created = parcelService.createParcel(
                warden, s.getId(), "Amazon", "TRK-CONC-1", "SMALL",
                "Test concurrency box", false, null, "Rack 1", null);
        final Long parcelId = created.id();

        ExecutorService pool = Executors.newFixedThreadPool(2);
        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch go = new CountDownLatch(1);
        AtomicInteger successes = new AtomicInteger(0);
        AtomicInteger failures = new AtomicInteger(0);

        Callable<Void> task = () -> {
            ready.countDown();
            go.await(5, TimeUnit.SECONDS);
            try {
                parcelService.confirmDelivery(warden, new ConfirmDeliveryRequest(null, parcelId, true, "Scan", null, null));
                successes.incrementAndGet();
            } catch (Exception e) {
                failures.incrementAndGet();
            }
            return null;
        };

        Future<Void> f1 = pool.submit(task);
        Future<Void> f2 = pool.submit(task);
        ready.await(5, TimeUnit.SECONDS);
        go.countDown();
        f1.get();
        f2.get();
        pool.shutdown();

        assertEquals(1, successes.get(), "Exactly one scan must succeed in delivering the parcel");
        assertEquals(1, failures.get(), "Second simultaneous scan must be rejected");
    }
}

