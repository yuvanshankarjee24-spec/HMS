package com.hostel.scheduler;

import com.hostel.service.MessTokenService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.time.ZoneId;

@Component
public class MessTokenScheduler {

    private static final Logger log = LoggerFactory.getLogger(MessTokenScheduler.class);
    private static final ZoneId IST = ZoneId.of("Asia/Kolkata");

    private final MessTokenService messTokenService;

    public MessTokenScheduler(MessTokenService messTokenService) {
        this.messTokenService = messTokenService;
    }

    @Scheduled(cron = "0 * * * * *", zone = "Asia/Kolkata")
    public void runMessTokenJobs() {
        try {
            messTokenService.processMessTokenJobs(LocalDateTime.now(IST));
        } catch (Exception e) {
            log.error("Error running mess token scheduler: {}", e.getMessage(), e);
        }
    }
}

