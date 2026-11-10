package com.hostel.repository;

import com.hostel.entity.OutpassLockHistory;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface OutpassLockHistoryRepository extends JpaRepository<OutpassLockHistory, Long> {
    List<OutpassLockHistory> findByStudentIdOrderByCreatedAtDesc(Long studentId);
}

