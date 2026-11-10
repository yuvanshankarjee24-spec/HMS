package com.hostel.repository;

import com.hostel.entity.CircularRead;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface CircularReadRepository extends JpaRepository<CircularRead, Long> {
    Optional<CircularRead> findByCircularIdAndStudentId(Long circularId, Long studentId);
    List<CircularRead> findByCircularId(Long circularId);
    long countByCircularId(Long circularId);
    long countByCircularIdAndAcknowledgedAtIsNotNull(Long circularId);
}

