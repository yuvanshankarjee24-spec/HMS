package com.hostel.repository;

import com.hostel.entity.ParcelQrToken;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface ParcelQrTokenRepository extends JpaRepository<ParcelQrToken, Long> {
    Optional<ParcelQrToken> findByParcelId(Long parcelId);
    Optional<ParcelQrToken> findByQrToken(String qrToken);
    Optional<ParcelQrToken> findByPickupCode(String pickupCode);
    boolean existsByPickupCode(String pickupCode);
}

