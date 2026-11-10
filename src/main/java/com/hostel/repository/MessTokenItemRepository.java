package com.hostel.repository;

import com.hostel.entity.MessTokenItem;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;

@Repository
public interface MessTokenItemRepository extends JpaRepository<MessTokenItem, Long> {
    List<MessTokenItem> findByTokenId(Long tokenId);

    @Query("SELECT COALESCE(SUM(i.quantity), 0) FROM MessTokenItem i " +
           "WHERE i.token.student.id = :studentId " +
           "AND i.menuSchedule.id = :scheduleId " +
           "AND i.token.status NOT IN ('CANCELLED', 'EXPIRED')")
    int sumActiveBookedQuantityForStudentAndSchedule(
            @Param("studentId") Long studentId,
            @Param("scheduleId") Long scheduleId);

    @Query("SELECT i FROM MessTokenItem i WHERE i.token.menuDate = :menuDate " +
           "AND i.token.period = :period " +
           "AND i.token.status IN ('BOOKED', 'QR_ACTIVE', 'DELIVERED', 'PARTIALLY_DELIVERED')")
    List<MessTokenItem> findActiveItemsForDateAndPeriod(
            @Param("menuDate") LocalDate menuDate,
            @Param("period") com.hostel.entity.MessPeriodConfig.Period period);
}

