package com.hostel.scheduler;

import com.hostel.service.DigitalAttendanceService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.time.ZoneId;

@Component
public class DigitalAttendanceScheduler {

    private static final Logger log = LoggerFactory.getLogger(DigitalAttendanceScheduler.class);
    private static final ZoneId IST = ZoneId.of("Asia/Kolkata");

    private final DigitalAttendanceService digitalAttendanceService;

    public DigitalAttendanceScheduler(DigitalAttendanceService digitalAttendanceService) {
        this.digitalAttendanceService = digitalAttendanceService;
    }

    @Scheduled(cron = "0 * * * * *", zone = "Asia/Kolkata")
    public void runDigitalAttendanceJobs() {
        try {
            LocalDateTime now = LocalDateTime.now(IST);
            digitalAttendanceService.processAttendanceCycle(now);
        } catch (Exception e) {
            log.error("Error running digital attendance scheduler cycle: {}", e.getMessage(), e);
        }
    }
}

