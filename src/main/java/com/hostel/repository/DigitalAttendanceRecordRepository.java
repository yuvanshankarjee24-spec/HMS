package com.hostel.repository;

import com.hostel.entity.DigitalAttendanceRecord;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

@Repository
public interface DigitalAttendanceRecordRepository extends JpaRepository<DigitalAttendanceRecord, Long> {

    Optional<DigitalAttendanceRecord> findByStudentIdAndSessionIdAndAttendanceDate(
            Long studentId, Long sessionId, LocalDate attendanceDate);

    List<DigitalAttendanceRecord> findByAttendanceDate(LocalDate attendanceDate);

    List<DigitalAttendanceRecord> findBySessionIdAndAttendanceDate(Long sessionId, LocalDate attendanceDate);

    List<DigitalAttendanceRecord> findBySessionIdAndAttendanceDateAndStatus(
            Long sessionId, LocalDate attendanceDate, DigitalAttendanceRecord.Status status);

    @Query("SELECT r FROM DigitalAttendanceRecord r WHERE r.student.id = :studentId " +
           "AND (:fromDate IS NULL OR r.attendanceDate >= :fromDate) " +
           "AND (:toDate IS NULL OR r.attendanceDate <= :toDate) " +
           "ORDER BY r.attendanceDate DESC, r.session.startTime DESC")
    Page<DigitalAttendanceRecord> findStudentHistory(
            @Param("studentId") Long studentId,
            @Param("fromDate") LocalDate fromDate,
            @Param("toDate") LocalDate toDate,
            Pageable pageable);

    @Query("SELECT r FROM DigitalAttendanceRecord r WHERE r.student.id = :studentId " +
           "AND (:fromDate IS NULL OR r.attendanceDate >= :fromDate) " +
           "AND (:toDate IS NULL OR r.attendanceDate <= :toDate)")
    List<DigitalAttendanceRecord> findStudentHistoryAll(
            @Param("studentId") Long studentId,
            @Param("fromDate") LocalDate fromDate,
            @Param("toDate") LocalDate toDate);

    @Query("SELECT COUNT(r) FROM DigitalAttendanceRecord r WHERE r.student.id = :studentId " +
           "AND r.status = :status AND r.attendanceDate >= :sinceDate")
    long countStudentByStatusSince(
            @Param("studentId") Long studentId,
            @Param("status") DigitalAttendanceRecord.Status status,
            @Param("sinceDate") LocalDate sinceDate);

    List<DigitalAttendanceRecord> findByStatusAndAttendanceDateBetweenOrderByAttendanceDateDesc(
            DigitalAttendanceRecord.Status status, LocalDate start, LocalDate end);

    List<DigitalAttendanceRecord> findByAttendanceDateBetween(LocalDate start, LocalDate end);

    @Query("SELECT r FROM DigitalAttendanceRecord r " +
           "JOIN r.student s " +
           "WHERE r.status = :status " +
           "AND (:date IS NULL OR r.attendanceDate = :date) " +
           "AND (:sessionId IS NULL OR r.session.id = :sessionId) " +
           "AND (:query IS NULL OR LOWER(s.fullName) LIKE LOWER(CONCAT('%', :query, '%')) " +
           "     OR LOWER(s.registerNumber) LIKE LOWER(CONCAT('%', :query, '%'))) " +
           "ORDER BY r.attendanceDate DESC, r.createdAt DESC")
    Page<DigitalAttendanceRecord> findByStatusFiltered(
            @Param("status") DigitalAttendanceRecord.Status status,
            @Param("date") LocalDate date,
            @Param("sessionId") Long sessionId,
            @Param("query") String query,
            Pageable pageable);

    boolean existsByStudentIdAndStatus(Long studentId, DigitalAttendanceRecord.Status status);
}

