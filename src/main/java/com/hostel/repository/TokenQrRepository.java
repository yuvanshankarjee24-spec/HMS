package com.hostel.repository;

import com.hostel.entity.TokenQr;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface TokenQrRepository extends JpaRepository<TokenQr, Long> {
    Optional<TokenQr> findByTokenId(Long tokenId);
    Optional<TokenQr> findByQrToken(String qrToken);
    Optional<TokenQr> findByPickupCode(String pickupCode);
    boolean existsByPickupCode(String pickupCode);
}

