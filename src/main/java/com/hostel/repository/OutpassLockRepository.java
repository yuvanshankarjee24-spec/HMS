package com.hostel.repository;

import com.hostel.entity.OutpassLock;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface OutpassLockRepository extends JpaRepository<OutpassLock, Long> {
    Optional<OutpassLock> findFirstByStudentIdAndActiveTrueOrderByLockedAtDesc(Long studentId);
    List<OutpassLock> findByStudentIdAndActiveTrue(Long studentId);
    List<OutpassLock> findByOutpassIdAndActiveTrue(Long outpassId);
    List<OutpassLock> findByActiveTrueOrderByLockedAtDesc();
}

