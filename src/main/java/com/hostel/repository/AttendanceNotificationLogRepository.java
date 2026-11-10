package com.hostel.repository;

import com.hostel.entity.AttendanceNotificationLog;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;

@Repository
public interface AttendanceNotificationLogRepository extends JpaRepository<AttendanceNotificationLog, Long> {
    boolean existsByStudentIdAndSessionIdAndAttendanceDateAndReminderType(
            Long studentId,
            Long sessionId,
            LocalDate attendanceDate,
            AttendanceNotificationLog.ReminderType reminderType);
}

