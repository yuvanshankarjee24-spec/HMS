package com.hostel.repository;

import com.hostel.entity.Attendance;
import com.hostel.entity.Student;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

@Repository
public interface AttendanceRepository extends JpaRepository<Attendance, Long> {
    List<Attendance> findByAttendanceDate(LocalDate date);
    Optional<Attendance> findByStudentAndAttendanceDate(Student student, LocalDate date);
    List<Attendance> findByStudentOrderByAttendanceDateDesc(Student student);
    List<Attendance> findByStudentAndStatus(Student student, Attendance.Status status);
    List<Attendance> findByStudentAndAttendanceDateGreaterThanEqual(Student student, LocalDate fromDate);
    Optional<Attendance> findTopByStudentOrderByAttendanceDateDesc(Student student);
    long countByAttendanceDateAndStatus(LocalDate date, Attendance.Status status);
}
