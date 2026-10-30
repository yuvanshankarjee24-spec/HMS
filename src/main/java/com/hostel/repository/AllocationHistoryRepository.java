package com.hostel.repository;

import com.hostel.entity.AllocationHistory;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface AllocationHistoryRepository extends JpaRepository<AllocationHistory, Long> {
    List<AllocationHistory> findByStudentIdOrderByCreatedAtDesc(Long studentId);

    @Query("SELECT ah FROM AllocationHistory ah JOIN FETCH ah.student s JOIN FETCH ah.bed b JOIN FETCH b.room r ORDER BY ah.createdAt DESC")
    List<AllocationHistory> findRecentHistory();
}
