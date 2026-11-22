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

    Optional<Bed> findByRoomIdAndBedLabel(Long roomId, String bedLabel);

    long countByRoomIdAndStudentIsNotNull(Long roomId);

    long countByStudentIsNotNull();

    @Query("SELECT COUNT(b) FROM Bed b WHERE b.student IS NULL AND (b.status = 'available' OR b.status IS NULL) AND (b.status IS NULL OR LOWER(b.status) != 'maintenance') AND b.room.status = com.hostel.entity.Room.Status.ACTIVE")
    long countAvailableBeds();

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT COUNT(b) FROM Bed b WHERE b.student IS NULL AND (b.status = 'available' OR b.status IS NULL) AND (b.status IS NULL OR LOWER(b.status) != 'maintenance') AND b.room.status = com.hostel.entity.Room.Status.ACTIVE")
    long countAvailableBedsWithLock();

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT b FROM Bed b WHERE b.id = :id")
    Optional<Bed> findByIdWithLock(@Param("id") Long id);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT b FROM Bed b WHERE b.room.id = :roomId AND b.bedLabel = :bedLabel")
    Optional<Bed> findByRoomIdAndBedLabelWithLock(@Param("roomId") Long roomId, @Param("bedLabel") String bedLabel);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT b FROM Bed b WHERE b.room.id = :roomId AND b.student IS NULL AND (b.status = 'available' OR b.status IS NULL) AND (b.status IS NULL OR LOWER(b.status) != 'maintenance') ORDER BY b.bedNumber ASC")
    List<Bed> findAvailableBedsInRoomWithLock(@Param("roomId") Long roomId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT b FROM Bed b JOIN b.room r WHERE r.status = com.hostel.entity.Room.Status.ACTIVE AND b.student IS NULL AND (b.status = 'available' OR b.status IS NULL) AND (b.status IS NULL OR LOWER(b.status) != 'maintenance') ORDER BY r.roomNumber ASC, b.bedNumber ASC")
    List<Bed> findAllAvailableBedsWithLock();

    @Query("SELECT b FROM Bed b WHERE b.room.id = :roomId AND b.student IS NULL AND (b.status = 'available' OR b.status IS NULL) AND (b.status IS NULL OR LOWER(b.status) != 'maintenance') ORDER BY b.bedNumber ASC")
    List<Bed> findAvailableBedsInRoom(@Param("roomId") Long roomId);

    @Query("SELECT b FROM Bed b JOIN FETCH b.room WHERE b.student IS NULL AND (b.status = 'available' OR b.status IS NULL) AND (b.status IS NULL OR LOWER(b.status) != 'maintenance') AND b.room.status = com.hostel.entity.Room.Status.ACTIVE ORDER BY b.room.roomNumber ASC, b.bedNumber ASC")
    List<Bed> findAllAvailableBeds();
}
