package com.hostel.repository;

import com.hostel.entity.MenuSchedule;
import com.hostel.entity.MessPeriodConfig;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

@Repository
public interface MenuScheduleRepository extends JpaRepository<MenuSchedule, Long> {

    List<MenuSchedule> findByMenuDateAndPeriodOrderByFoodItemNameAsc(LocalDate menuDate, MessPeriodConfig.Period period);

    List<MenuSchedule> findByMenuDateOrderByPeriodAscFoodItemNameAsc(LocalDate menuDate);

    Optional<MenuSchedule> findByMenuDateAndPeriodAndFoodItemId(LocalDate menuDate, MessPeriodConfig.Period period, Long foodItemId);

    boolean existsByMenuDate(LocalDate menuDate);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT ms FROM MenuSchedule ms WHERE ms.id = :id")
    Optional<MenuSchedule> findByIdWithLock(@Param("id") Long id);
}

