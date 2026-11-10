package com.hostel.repository;

import com.hostel.entity.TokenStatusHistory;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface TokenStatusHistoryRepository extends JpaRepository<TokenStatusHistory, Long> {
    List<TokenStatusHistory> findByTokenIdOrderByCreatedAtAsc(Long tokenId);
}

