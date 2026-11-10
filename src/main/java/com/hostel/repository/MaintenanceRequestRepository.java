package com.hostel.repository;

import com.hostel.entity.MaintenanceRequest;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public interface MaintenanceRequestRepository extends JpaRepository<MaintenanceRequest, Long> {

    Optional<MaintenanceRequest> findByRequestCode(String requestCode);

    @Query("SELECT m FROM MaintenanceRequest m WHERE m.student.id = :studentId " +
           "AND (:status IS NULL OR m.status = :status) " +
           "AND (:category IS NULL OR m.category = :category) " +
           "AND (:fromDate IS NULL OR m.createdAt >= :fromDate) " +
           "AND (:toDate IS NULL OR m.createdAt <= :toDate)")
    Page<MaintenanceRequest> findByStudentFiltered(
            @Param("studentId") Long studentId,
            @Param("status") MaintenanceRequest.Status status,
            @Param("category") MaintenanceRequest.Category category,
            @Param("fromDate") LocalDateTime fromDate,
            @Param("toDate") LocalDateTime toDate,
            Pageable pageable);

    @Query("SELECT m FROM MaintenanceRequest m " +
           "LEFT JOIN m.student s " +
           "WHERE (:status IS NULL OR m.status = :status) " +
           "AND (:category IS NULL OR m.category = :category) " +
           "AND (:priority IS NULL OR m.priority = :priority) " +
           "AND (:block IS NULL OR LOWER(m.hostelBlock) LIKE LOWER(CONCAT('%', :block, '%'))) " +
           "AND (:room IS NULL OR LOWER(m.roomNumber) LIKE LOWER(CONCAT('%', :room, '%'))) " +
           "AND (:fromDate IS NULL OR m.createdAt >= :fromDate) " +
           "AND (:toDate IS NULL OR m.createdAt <= :toDate) " +
           "AND (:query IS NULL OR LOWER(m.requestCode) LIKE LOWER(CONCAT('%', :query, '%')) " +
           "     OR LOWER(m.title) LIKE LOWER(CONCAT('%', :query, '%')) " +
           "     OR LOWER(s.fullName) LIKE LOWER(CONCAT('%', :query, '%')) " +
           "     OR LOWER(s.registerNumber) LIKE LOWER(CONCAT('%', :query, '%')))")
    Page<MaintenanceRequest> searchAllFiltered(
            @Param("status") MaintenanceRequest.Status status,
            @Param("category") MaintenanceRequest.Category category,
            @Param("priority") MaintenanceRequest.Priority priority,
            @Param("block") String block,
            @Param("room") String room,
            @Param("fromDate") LocalDateTime fromDate,
            @Param("toDate") LocalDateTime toDate,
            @Param("query") String query,
            Pageable pageable);

    @Query("SELECT m FROM MaintenanceRequest m WHERE m.assignedWorker.id = :workerId " +
           "AND (:status IS NULL OR m.status = :status)")
    Page<MaintenanceRequest> findByAssignedWorkerFiltered(
            @Param("workerId") Long workerId,
            @Param("status") MaintenanceRequest.Status status,
            Pageable pageable);

    long countByStatus(MaintenanceRequest.Status status);

    long countByAssignedWorkerIdAndStatus(Long workerId, MaintenanceRequest.Status status);

    long countByAssignedWorkerIdAndCompletedAtBetween(Long workerId, LocalDateTime start, LocalDateTime end);

    @Query("SELECT COUNT(m) FROM MaintenanceRequest m WHERE m.requestCode LIKE CONCAT(:prefix, '%')")
    long countByRequestCodePrefix(@Param("prefix") String prefix);

    List<MaintenanceRequest> findByStatusInAndCreatedAtBeforeOrderByCreatedAtAsc(
            List<MaintenanceRequest.Status> statuses, LocalDateTime before);
}

