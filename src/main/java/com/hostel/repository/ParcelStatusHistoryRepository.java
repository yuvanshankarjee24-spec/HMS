package com.hostel.repository;

import com.hostel.entity.ParcelStatusHistory;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface ParcelStatusHistoryRepository extends JpaRepository<ParcelStatusHistory, Long> {
    List<ParcelStatusHistory> findByParcelIdOrderByCreatedAtAsc(Long parcelId);
}

