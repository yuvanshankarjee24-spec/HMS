package com.hostel.repository;

import com.hostel.entity.OutpassScanLog;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface OutpassScanLogRepository extends JpaRepository<OutpassScanLog, Long> {
    List<OutpassScanLog> findByOutpassIdOrderByScannedAtDesc(Long outpassId);
}

