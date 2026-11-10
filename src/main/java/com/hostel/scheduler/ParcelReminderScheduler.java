package com.hostel.scheduler;

import com.hostel.service.ParcelService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.time.ZoneId;

@Component
public class ParcelReminderScheduler {

    private static final Logger log = LoggerFactory.getLogger(ParcelReminderScheduler.class);
    private static final ZoneId IST = ZoneId.of("Asia/Kolkata");

    private final ParcelService parcelService;

    public ParcelReminderScheduler(ParcelService parcelService) {
        this.parcelService = parcelService;
    }

    @Scheduled(cron = "0 0 * * * *", zone = "Asia/Kolkata")
    public void sendParcelReminders() {
        try {
            parcelService.sendPendingParcelReminders(LocalDateTime.now(IST));
        } catch (Exception e) {
            log.error("Error running parcel reminder scheduler: {}", e.getMessage(), e);
        }
    }
}

