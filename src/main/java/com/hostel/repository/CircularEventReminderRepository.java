package com.hostel.repository;

import com.hostel.entity.CircularEventReminder;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface CircularEventReminderRepository extends JpaRepository<CircularEventReminder, Long> {
    boolean existsByCircularIdAndStudentIdAndReminderType(Long circularId, Long studentId, String reminderType);
}

