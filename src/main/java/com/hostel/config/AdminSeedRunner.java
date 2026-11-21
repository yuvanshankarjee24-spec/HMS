package com.hostel.config;

import com.hostel.entity.User;
import com.hostel.repository.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.core.annotation.Order;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * Ensures that after cleanup or on initial boot, exactly ONE administrator account
 * is seeded into the system reading ADMIN_USERNAME and ADMIN_PASSWORD from .env.
 * The administrator is flagged with must_change_password = true on first login.
 */
@Component
@Order(10)
public class AdminSeedRunner implements CommandLineRunner {

    private static final Logger log = LoggerFactory.getLogger(AdminSeedRunner.class);

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    @Value("${app.admin.seed.username:1234567890}")
    private String adminUsername;

    @Value("${app.admin.seed.password:1234567890}")
    private String adminPassword;

    public AdminSeedRunner(UserRepository userRepository, PasswordEncoder passwordEncoder) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
    }

    @Override
    @Transactional
    public void run(String... args) {
        String username = (adminUsername != null && !adminUsername.isBlank()) ? adminUsername.trim() : "1234567890";
        String email = username.contains("@") ? username : (username + "@hostel.edu");

        List<User> activeAdmins = userRepository.findByRoleAndStatus("ADMIN", User.Status.ACTIVE);
        User admin;

        if (!activeAdmins.isEmpty()) {
            admin = activeAdmins.get(0);
            log.info("AdminSeedRunner: Found existing administrator ID {}. Updating credentials from .env...", admin.getId());
        } else {
            admin = new User();
            admin.setRole("ADMIN");
            admin.setName("Hostel Administrator");
            log.info("AdminSeedRunner: No administrator found. Creating single administrator from .env...");
        }

        admin.setEmail(email);
        admin.setRegisterNumber(username);
        admin.setStatus(User.Status.ACTIVE);
        admin.setActive(true);
        admin.setPasswordHash(passwordEncoder.encode(adminPassword));
        admin.setMustChangePassword(true);
        admin.setFailedAttempts(0);
        admin.setLockedUntil(null);
        admin.setTempPasswordCreatedAt(null);

        userRepository.save(admin);
        log.info("AdminSeedRunner: Administrator '{}' successfully configured with forced password change on first login.", username);
    }
}
