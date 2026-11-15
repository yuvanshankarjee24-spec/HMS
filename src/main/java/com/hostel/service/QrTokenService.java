package com.hostel.service;

import com.hostel.entity.QrToken;
import com.hostel.entity.Student;
import com.hostel.repository.QrTokenRepository;
import com.hostel.util.StatusConstants;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.util.Base64;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Predicate;

/**
 * Unified QR Token signing, verification, pickup-code generation, and shared qr_tokens persistence.
 */
@Service
public class QrTokenService {

    private static final SecureRandom SECURE_RANDOM = new SecureRandom();
    private final QrTokenRepository qrTokenRepository;

    @Value("${app.qr.secret:${app.jwt.secret}}")
    private String hmacSecret;

    public QrTokenService(QrTokenRepository qrTokenRepository) {
        this.qrTokenRepository = qrTokenRepository;
    }

    public String generateSignedToken(String prefix, Long entityId, Long studentId) {
        String nonce = UUID.randomUUID().toString().replace("-", "").substring(0, 12);
        String payload = entityId + ":" + studentId + ":" + nonce;
        String encoded = Base64.getUrlEncoder().withoutPadding()
                .encodeToString(payload.getBytes(StandardCharsets.UTF_8));
        String sig = hmacSha256(encoded);
        return prefix + "." + encoded + "." + sig;
    }

    public boolean verifySignature(String token, String expectedPrefix) {
        if (token == null || !token.startsWith(expectedPrefix + ".")) return false;
        String[] parts = token.split("\\.");
        if (parts.length != 3) return false;
        String expectedSig = hmacSha256(parts[1]);
        return MessageDigest.isEqual(
                parts[2].getBytes(StandardCharsets.UTF_8),
                expectedSig.getBytes(StandardCharsets.UTF_8));
    }

    public String generateUniquePickupCode(Predicate<String> extraExistsChecker) {
        for (int i = 0; i < 30; i++) {
            String code = String.format("%06d", 100000 + SECURE_RANDOM.nextInt(900000));
            boolean existsUnified = qrTokenRepository.findAll().stream()
                    .anyMatch(q -> code.equals(q.getPickupCode()));
            if (!existsUnified && (extraExistsChecker == null || !extraExistsChecker.test(code))) {
                return code;
            }
        }
        return String.valueOf(System.currentTimeMillis() % 900000 + 100000);
    }

    @Transactional
    public QrToken saveUnifiedToken(String module, Long entityId, Student student, String qrToken, String pickupCode, LocalDateTime expiresAt) {
        Optional<QrToken> existing = qrTokenRepository.findByModuleAndEntityId(module, entityId);
        QrToken qt = existing.orElseGet(QrToken::new);
        qt.setModule(module);
        qt.setEntityId(entityId);
        qt.setStudent(student);
        qt.setQrToken(qrToken);
        qt.setPickupCode(pickupCode);
        qt.setExpiresAt(expiresAt);
        return qrTokenRepository.save(qt);
    }

    @Transactional
    public void markUsed(String module, Long entityId, LocalDateTime usedAt) {
        qrTokenRepository.findByModuleAndEntityId(module, entityId).ifPresent(qt -> {
            qt.setUsed(true);
            qt.setClosed(true);
            qt.setUsedAt(usedAt != null ? usedAt : StatusConstants.nowUtc());
            qrTokenRepository.save(qt);
        });
    }

    @Transactional
    public void markClosed(String module, Long entityId) {
        qrTokenRepository.findByModuleAndEntityId(module, entityId).ifPresent(qt -> {
            qt.setClosed(true);
            qrTokenRepository.save(qt);
        });
    }

    private String hmacSha256(String data) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(hmacSecret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            byte[] raw = mac.doFinal(data.getBytes(StandardCharsets.UTF_8));
            return Base64.getUrlEncoder().withoutPadding().encodeToString(raw);
        } catch (Exception e) {
            throw new RuntimeException("Failed to sign QR token", e);
        }
    }
}

