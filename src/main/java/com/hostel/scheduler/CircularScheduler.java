package com.hostel.scheduler;

import com.hostel.service.CircularService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.time.ZoneId;

@Component
public class CircularScheduler {

    private static final Logger log = LoggerFactory.getLogger(CircularScheduler.class);
    private static final ZoneId IST = ZoneId.of("Asia/Kolkata");

    private final CircularService circularService;

    public CircularScheduler(CircularService circularService) {
        this.circularService = circularService;
    }

    @Scheduled(cron = "0 * * * * *", zone = "Asia/Kolkata")
    public void runCircularJobs() {
        try {
            circularService.processCircularJobs(LocalDateTime.now(IST));
        } catch (Exception e) {
            log.error("Error running circular scheduler: {}", e.getMessage(), e);
        }
    }
}

