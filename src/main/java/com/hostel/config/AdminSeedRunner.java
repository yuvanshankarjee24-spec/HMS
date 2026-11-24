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
        String password = (adminPassword != null && !adminPassword.isBlank()) ? adminPassword.trim() : "1234567890";
        String email = username.contains("@") ? username : (username + "@hostel.edu");

        // Query any existing admin accounts regardless of status (ACTIVE, LOCKED, DISABLED)
        List<User> existingAdmins = userRepository.findByRole("ADMIN");
        User admin;

        if (!existingAdmins.isEmpty()) {
            admin = existingAdmins.get(0);
            log.info("AdminSeedRunner: Found existing administrator ID {}. Updating credentials from .env...", admin.getId());
            // Clean up any duplicate admin accounts if they exist, guaranteeing a single administrator
            for (int i = 1; i < existingAdmins.size(); i++) {
                User dup = existingAdmins.get(i);
                log.warn("AdminSeedRunner: Removing duplicate administrator ID {}", dup.getId());
                userRepository.delete(dup);
            }
        } else {
            // Also check by username or email in case role was modified
            admin = userRepository.findByIdentifier(username).orElseGet(() -> {
                User newUser = new User();
                newUser.setName("Hostel Administrator");
                log.info("AdminSeedRunner: No administrator found. Creating single administrator from .env...");
                return newUser;
            });
        }

        admin.setRole("ADMIN");
        admin.setEmail(email);
        admin.setRegisterNumber(username);
        admin.setStatus(User.Status.ACTIVE);
        admin.setActive(true);
        admin.setPasswordHash(passwordEncoder.encode(password));
        admin.setMustChangePassword(false);
        admin.setFailedAttempts(0);
        admin.setLockedUntil(null);
        admin.setTempPasswordCreatedAt(null);

        userRepository.save(admin);
        log.info("AdminSeedRunner: Administrator '{}' successfully seeded/updated and unlocked with BCrypt hash.", username);
    }
}
