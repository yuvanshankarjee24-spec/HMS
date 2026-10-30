package com.hostel.repository;

import com.hostel.entity.Bed;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface BedRepository extends JpaRepository<Bed, Long> {

    List<Bed> findByRoomIdOrderByBedNumberAsc(Long roomId);

    Optional<Bed> findByStudentId(Long studentId);

    long countByRoomIdAndStudentIsNotNull(Long roomId);

    long countByStudentIsNotNull();

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT b FROM Bed b WHERE b.id = :id")
    Optional<Bed> findByIdWithLock(@Param("id") Long id);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT b FROM Bed b WHERE b.room.id = :roomId AND b.student IS NULL ORDER BY b.bedNumber ASC")
    List<Bed> findAvailableBedsInRoomWithLock(@Param("roomId") Long roomId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT b FROM Bed b JOIN b.room r WHERE r.status = 'ACTIVE' AND b.student IS NULL ORDER BY r.roomNumber ASC, b.bedNumber ASC")
    List<Bed> findAllAvailableBedsWithLock();

    @Query("SELECT b FROM Bed b WHERE b.room.id = :roomId AND b.student IS NULL ORDER BY b.bedNumber ASC")
    List<Bed> findAvailableBedsInRoom(@Param("roomId") Long roomId);

    @Query("SELECT b FROM Bed b JOIN FETCH b.room WHERE b.student IS NULL AND b.room.status = 'ACTIVE' ORDER BY b.room.roomNumber ASC, b.bedNumber ASC")
    List<Bed> findAllAvailableBeds();
}
