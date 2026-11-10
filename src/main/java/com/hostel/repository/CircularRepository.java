package com.hostel.repository;

import com.hostel.entity.Circular;
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
public interface CircularRepository extends JpaRepository<Circular, Long> {
    Optional<Circular> findByCircularCode(String circularCode);

    @Query("SELECT COUNT(c) FROM Circular c WHERE c.circularCode LIKE CONCAT(:prefix, '%')")
    long countByCodePrefix(@Param("prefix") String prefix);

    List<Circular> findByStatusOrderByPublishedAtDescCreatedAtDesc(Circular.Status status);

    List<Circular> findByStatusAndScheduledPublishAtLessThanEqual(Circular.Status status, LocalDateTime now);

    List<Circular> findByStatusAndTypeAndEventDateTimeBetween(
            Circular.Status status, Circular.Type type, LocalDateTime start, LocalDateTime end);

    long countByStatus(Circular.Status status);
    long countByStatusAndTypeAndEventDateTimeAfter(Circular.Status status, Circular.Type type, LocalDateTime after);

    @Query("SELECT c FROM Circular c WHERE " +
           "(:status IS NULL OR c.status = :status) AND " +
           "(:type IS NULL OR c.type = :type) AND " +
           "(:query IS NULL OR LOWER(c.title) LIKE LOWER(CONCAT('%', :query, '%')) " +
           "   OR LOWER(c.circularCode) LIKE LOWER(CONCAT('%', :query, '%'))) " +
           "ORDER BY c.createdAt DESC")
    Page<Circular> searchAdminOrWarden(
            @Param("status") Circular.Status status,
            @Param("type") Circular.Type type,
            @Param("query") String query,
            Pageable pageable);
}

