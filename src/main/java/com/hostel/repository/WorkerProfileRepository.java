package com.hostel.repository;

import com.hostel.entity.WorkerProfile;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface WorkerProfileRepository extends JpaRepository<WorkerProfile, Long> {
    Optional<WorkerProfile> findByUserId(Long userId);
    Optional<WorkerProfile> findByEmployeeId(String employeeId);
    boolean existsByEmployeeId(String employeeId);
    List<WorkerProfile> findByActiveTrueOrderByFullNameAsc();
    List<WorkerProfile> findByActiveTrueAndWorkerTypeOrderByFullNameAsc(WorkerProfile.WorkerType workerType);
    List<WorkerProfile> findAllByOrderByCreatedAtDesc();
    long countByActiveTrue();

    @Query("SELECT w FROM WorkerProfile w WHERE " +
           "(:workerType IS NULL OR w.workerType = :workerType) AND " +
           "(:active IS NULL OR w.active = :active) AND " +
           "(:query IS NULL OR LOWER(w.fullName) LIKE LOWER(CONCAT('%', :query, '%')) OR " +
           "LOWER(w.employeeId) LIKE LOWER(CONCAT('%', :query, '%')) OR " +
           "LOWER(w.phone) LIKE LOWER(CONCAT('%', :query, '%'))) " +
           "ORDER BY w.createdAt DESC")
    List<WorkerProfile> searchWorkers(
            @Param("workerType") WorkerProfile.WorkerType workerType,
            @Param("active") Boolean active,
            @Param("query") String query);
}

