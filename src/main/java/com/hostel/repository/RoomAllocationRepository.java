package com.hostel.repository;

import com.hostel.entity.RoomAllocation;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface RoomAllocationRepository extends JpaRepository<RoomAllocation, Long> {

    Optional<RoomAllocation> findByStudentIdAndStatus(Long studentId, String status);

    Optional<RoomAllocation> findByBedIdAndStatus(Long bedId, String status);

    long countByRoomIdAndStatus(Long roomId, String status);

    long countByStatus(String status);

    List<RoomAllocation> findByRoomIdAndStatus(Long roomId, String status);

    List<RoomAllocation> findByStudentIdOrderByAllocatedAtDesc(Long studentId);

    @Query("SELECT ra FROM RoomAllocation ra JOIN FETCH ra.student s JOIN FETCH ra.room r JOIN FETCH ra.bed b WHERE ra.status = 'active' ORDER BY r.roomNumber ASC, b.bedLabel ASC")
    List<RoomAllocation> findAllActiveAllocationsWithDetails();

    @Query("SELECT ra FROM RoomAllocation ra JOIN FETCH ra.room r JOIN FETCH ra.bed b WHERE ra.student.id = :studentId AND ra.status = 'active'")
    Optional<RoomAllocation> findActiveAllocationForStudent(@Param("studentId") Long studentId);
}
