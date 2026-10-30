package com.hostel.repository;

import com.hostel.entity.Room;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface RoomRepository extends JpaRepository<Room, Long> {
    Optional<Room> findByRoomNumber(String roomNumber);
    List<Room> findByStatus(Room.Status status);
    List<Room> findByFloor(int floor);
    List<Room> findAllByOrderByRoomNumberAsc();
}
