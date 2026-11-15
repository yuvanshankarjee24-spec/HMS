package com.hostel.repository;

import com.hostel.entity.QrToken;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface QrTokenRepository extends JpaRepository<QrToken, Long> {
    Optional<QrToken> findByModuleAndEntityId(String module, Long entityId);
    Optional<QrToken> findByQrToken(String qrToken);
    Optional<QrToken> findByModuleAndPickupCode(String module, String pickupCode);
}

