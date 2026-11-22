package com.hostel.repository;

import com.hostel.entity.RoomRequestStatusHistory;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface RoomRequestStatusHistoryRepository extends JpaRepository<RoomRequestStatusHistory, Long> {
    List<RoomRequestStatusHistory> findByRequestIdOrderByChangedAtAsc(Long requestId);
}
