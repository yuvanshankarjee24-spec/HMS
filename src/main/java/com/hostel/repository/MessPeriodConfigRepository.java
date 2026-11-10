package com.hostel.repository;

import com.hostel.entity.MessPeriodConfig;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface MessPeriodConfigRepository extends JpaRepository<MessPeriodConfig, MessPeriodConfig.Period> {
}

