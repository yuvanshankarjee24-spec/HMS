package com.hostel.repository;

import com.hostel.entity.Room;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface RoomRepository extends JpaRepository<Room, Long> {
    Optional<Room> findByRoomNumber(String roomNumber);
    List<Room> findByStatus(Room.Status status);
    List<Room> findByFloor(int floor);
    List<Room> findAllByOrderByRoomNumberAsc();
    List<Room> findByHostelIdOrderByRoomNumberAsc(Long hostelId);
    long countByHostelId(Long hostelId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT r FROM Room r WHERE r.id = :id")
    Optional<Room> findByIdWithLock(@Param("id") Long id);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT r FROM Room r WHERE r.roomNumber = :roomNumber")
    Optional<Room> findByRoomNumberWithLock(@Param("roomNumber") String roomNumber);
}
