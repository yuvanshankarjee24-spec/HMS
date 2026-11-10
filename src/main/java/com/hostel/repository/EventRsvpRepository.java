package com.hostel.repository;

import com.hostel.entity.EventRsvp;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface EventRsvpRepository extends JpaRepository<EventRsvp, Long> {
    Optional<EventRsvp> findByCircularIdAndStudentId(Long circularId, Long studentId);
    long countByCircularIdAndRsvpStatus(Long circularId, EventRsvp.RsvpStatus status);
    List<EventRsvp> findByCircularId(Long circularId);
}

