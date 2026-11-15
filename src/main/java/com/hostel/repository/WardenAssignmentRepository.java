package com.hostel.repository;

import com.hostel.entity.WardenAssignment;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface WardenAssignmentRepository extends JpaRepository<WardenAssignment, Long> {
    List<WardenAssignment> findByWardenUserIdAndActiveTrue(Long wardenUserId);
    List<WardenAssignment> findByHostelIdAndActiveTrue(Long hostelId);
}

