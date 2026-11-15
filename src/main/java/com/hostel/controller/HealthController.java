package com.hostel.controller;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.Statement;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;

@RestController
public class HealthController {

    private final DataSource dataSource;

    @Value("${app.db-timezone:UTC}")
    private String dbTimezone;

    @Value("${app.timezone:Asia/Kolkata}")
    private String displayTimezone;

    public HealthController(DataSource dataSource) {
        this.dataSource = dataSource;
    }

    @GetMapping("/health")
    public ResponseEntity<Map<String, Object>> health() {
        Map<String, Object> body = new LinkedHashMap<>();
        try (Connection conn = dataSource.getConnection();
             Statement st = conn.createStatement();
             ResultSet rs = st.executeQuery("SELECT 1")) {
            boolean ok = rs.next();
            body.put("status", ok ? "UP" : "DOWN");
            body.put("database", ok ? "UP" : "DOWN");
            body.put("dbTimezone", dbTimezone);
            body.put("displayTimezone", displayTimezone);
            body.put("timestampUtc", Instant.now().toString());
            return ResponseEntity.status(ok ? HttpStatus.OK : HttpStatus.SERVICE_UNAVAILABLE).body(body);
        } catch (Exception e) {
            body.put("status", "DOWN");
            body.put("database", "UNREACHABLE");
            body.put("error", e.getMessage());
            return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).body(body);
        }
    }
}

