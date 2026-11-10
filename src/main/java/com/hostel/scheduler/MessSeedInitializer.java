package com.hostel.scheduler;

import com.hostel.service.MessTokenService;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.ZoneId;

@Component
public class MessSeedInitializer implements ApplicationRunner {

    private final MessTokenService messTokenService;

    public MessSeedInitializer(MessTokenService messTokenService) {
        this.messTokenService = messTokenService;
    }

    @Override
    public void run(ApplicationArguments args) {
        messTokenService.ensureSampleWeeklyMenu(LocalDate.now(ZoneId.of("Asia/Kolkata")));
    }
}

