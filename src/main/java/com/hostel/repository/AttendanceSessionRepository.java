package com.hostel.repository;

import com.hostel.entity.AttendanceSession;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface AttendanceSessionRepository extends JpaRepository<AttendanceSession, Long> {
    List<AttendanceSession> findByEnabledTrueOrderByStartTimeAsc();
    List<AttendanceSession> findAllByOrderByStartTimeAsc();
}

