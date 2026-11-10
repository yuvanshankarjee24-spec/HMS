package com.hostel.repository;

import com.hostel.entity.MessPeriodConfig;
import com.hostel.entity.MessToken;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

@Repository
public interface MessTokenRepository extends JpaRepository<MessToken, Long> {

    Optional<MessToken> findByTokenCode(String tokenCode);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT t FROM MessToken t WHERE t.id = :id")
    Optional<MessToken> findByIdWithLock(@Param("id") Long id);

    @Query("SELECT COUNT(t) FROM MessToken t WHERE t.tokenCode LIKE CONCAT(:prefix, '%')")
    long countByTokenCodePrefix(@Param("prefix") String prefix);

    @Query("SELECT t FROM MessToken t WHERE t.student.id = :studentId AND t.status IN :statuses " +
           "ORDER BY t.menuDate DESC, t.createdAt DESC")
    Page<MessToken> findByStudentAndStatuses(
            @Param("studentId") Long studentId,
            @Param("statuses") List<MessToken.Status> statuses,
            Pageable pageable);

    List<MessToken> findByStatusInAndMenuDate(List<MessToken.Status> statuses, LocalDate menuDate);

    List<MessToken> findByStatusInAndMenuDateLessThanEqual(List<MessToken.Status> statuses, LocalDate menuDate);

    List<MessToken> findByMenuDateAndPeriod(LocalDate menuDate, MessPeriodConfig.Period period);

    List<MessToken> findByMenuDateBetween(LocalDate start, LocalDate end);

    @Query("SELECT t FROM MessToken t JOIN t.student s WHERE " +
           "(:date IS NULL OR t.menuDate = :date) AND " +
           "(:period IS NULL OR t.period = :period) AND " +
           "(:status IS NULL OR t.status = :status) AND " +
           "(:query IS NULL OR LOWER(t.tokenCode) LIKE LOWER(CONCAT('%', :query, '%')) " +
           "   OR LOWER(s.fullName) LIKE LOWER(CONCAT('%', :query, '%')) " +
           "   OR LOWER(s.registerNumber) LIKE LOWER(CONCAT('%', :query, '%'))) " +
           "ORDER BY t.menuDate DESC, t.createdAt DESC")
    Page<MessToken> searchFiltered(
            @Param("date") LocalDate date,
            @Param("period") MessPeriodConfig.Period period,
            @Param("status") MessToken.Status status,
            @Param("query") String query,
            Pageable pageable);
}

