package com.hostel.repository;

import com.hostel.entity.Parcel;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public interface ParcelRepository extends JpaRepository<Parcel, Long> {

    Optional<Parcel> findByParcelCode(String parcelCode);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT p FROM Parcel p WHERE p.id = :id")
    Optional<Parcel> findByIdWithLock(@Param("id") Long id);

    @Query("SELECT COUNT(p) FROM Parcel p WHERE p.parcelCode LIKE CONCAT(:prefix, '%')")
    long countByParcelCodePrefix(@Param("prefix") String prefix);

    @Query("SELECT p FROM Parcel p WHERE p.student.id = :studentId AND p.status IN :statuses ORDER BY p.receivedAt DESC")
    Page<Parcel> findByStudentAndStatuses(
            @Param("studentId") Long studentId,
            @Param("statuses") List<Parcel.Status> statuses,
            Pageable pageable);

    long countByStudentIdAndStatusIn(Long studentId, List<Parcel.Status> statuses);

    @Query("SELECT p FROM Parcel p JOIN p.student s WHERE " +
           "(:status IS NULL OR p.status = :status) AND " +
           "(:courier IS NULL OR LOWER(p.courierName) LIKE LOWER(CONCAT('%', :courier, '%'))) AND " +
           "(:fromDate IS NULL OR p.receivedAt >= :fromDate) AND " +
           "(:toDate IS NULL OR p.receivedAt <= :toDate) AND " +
           "(:query IS NULL OR LOWER(p.parcelCode) LIKE LOWER(CONCAT('%', :query, '%')) " +
           "   OR LOWER(s.fullName) LIKE LOWER(CONCAT('%', :query, '%')) " +
           "   OR LOWER(s.registerNumber) LIKE LOWER(CONCAT('%', :query, '%')) " +
           "   OR LOWER(p.courierName) LIKE LOWER(CONCAT('%', :query, '%')) " +
           "   OR LOWER(p.hostelBlock) LIKE LOWER(CONCAT('%', :query, '%'))) " +
           "ORDER BY p.receivedAt DESC")
    Page<Parcel> searchFiltered(
            @Param("status") Parcel.Status status,
            @Param("courier") String courier,
            @Param("fromDate") LocalDateTime fromDate,
            @Param("toDate") LocalDateTime toDate,
            @Param("query") String query,
            Pageable pageable);

    long countByStatusIn(List<Parcel.Status> statuses);

    long countByStatus(Parcel.Status status);

    long countByReceivedAtBetween(LocalDateTime start, LocalDateTime end);

    long countByDeliveredAtBetween(LocalDateTime start, LocalDateTime end);

    long countByStatusInAndCodPaymentPendingTrueAndCodCollectedFalse(List<Parcel.Status> statuses);

    List<Parcel> findByStatusInAndReceivedAtBefore(List<Parcel.Status> statuses, LocalDateTime before);

    List<Parcel> findByReceivedAtBetweenOrderByReceivedAtDesc(LocalDateTime start, LocalDateTime end);
}

