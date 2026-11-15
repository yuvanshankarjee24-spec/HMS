package com.hostel;

import jakarta.annotation.PostConstruct;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.TimeZone;

@SpringBootApplication
@EnableScheduling
public class HostelApplication {

    @PostConstruct
    public void init() {
        TimeZone.setDefault(TimeZone.getTimeZone("UTC"));
    }

    public static void main(String[] args) {
        loadDotEnvIfPresent();
        TimeZone.setDefault(TimeZone.getTimeZone("UTC"));
        SpringApplication.run(HostelApplication.class, args);
    }

    private static void loadDotEnvIfPresent() {
        try {
            Path envPath = Paths.get(".env");
            if (!Files.exists(envPath)) return;
            for (String line : Files.readAllLines(envPath)) {
                String trimmed = line.trim();
                if (trimmed.isEmpty() || trimmed.startsWith("#")) continue;
                int idx = trimmed.indexOf('=');
                if (idx <= 0) continue;
                String key = trimmed.substring(0, idx).trim();
                String val = trimmed.substring(idx + 1).trim();
                if (System.getenv(key) == null && System.getProperty(key) == null) {
                    System.setProperty(key, val);
                }
            }
        } catch (Exception ignored) {}
    }
}
