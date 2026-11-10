package com.hostel.repository;

import com.hostel.entity.CircularTarget;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface CircularTargetRepository extends JpaRepository<CircularTarget, Long> {
    List<CircularTarget> findByCircularId(Long circularId);
    void deleteByCircularId(Long circularId);
}

