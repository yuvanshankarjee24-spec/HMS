package com.hostel.repository;

import com.hostel.entity.RoomRequest;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface RoomRequestRepository extends JpaRepository<RoomRequest, Long> {

    Optional<RoomRequest> findByStudentIdAndStatus(Long studentId, RoomRequest.Status status);

    boolean existsByStudentIdAndStatus(Long studentId, RoomRequest.Status status);

    List<RoomRequest> findByStudentIdOrderByCreatedAtDesc(Long studentId);

    @Query("SELECT r FROM RoomRequest r JOIN FETCH r.student s LEFT JOIN FETCH r.preferredRoom pr WHERE r.status = :status ORDER BY r.createdAt ASC")
    List<RoomRequest> findByStatusWithDetails(@Param("status") RoomRequest.Status status);

    List<RoomRequest> findAllByOrderByCreatedAtDesc();

    long countByStatus(RoomRequest.Status status);
}
