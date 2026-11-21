package com.hostel.config;

import com.hostel.entity.Bed;
import com.hostel.entity.Hostel;
import com.hostel.entity.Room;
import com.hostel.repository.BedRepository;
import com.hostel.repository.HostelRepository;
import com.hostel.repository.RoomRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.CommandLineRunner;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

@Component
@Order(2) // Runs right after AdminSeedRunner
public class RoomBedInitializer implements CommandLineRunner {

    private static final Logger log = LoggerFactory.getLogger(RoomBedInitializer.class);

    private final HostelRepository hostelRepository;
    private final RoomRepository roomRepository;
    private final BedRepository bedRepository;
    private final RoomSeedProperties props;

    public RoomBedInitializer(HostelRepository hostelRepository,
                              RoomRepository roomRepository,
                              BedRepository bedRepository,
                              RoomSeedProperties props) {
        this.hostelRepository = hostelRepository;
        this.roomRepository = roomRepository;
        this.bedRepository = bedRepository;
        this.props = props;
    }

    @Override
    @Transactional
    public void run(String... args) {
        // 1. Ensure primary Hostel
        Hostel hostel = hostelRepository.findByCodeIgnoreCase(props.getHostelCode())
                .or(() -> hostelRepository.findByCodeIgnoreCase("MAIN"))
                .orElseGet(() -> {
                    Hostel h = new Hostel();
                    h.setCode(props.getHostelCode());
                    h.setName(props.getHostelName());
                    h.setAddress(props.getHostelAddress());
                    h.setActive(true);
                    return hostelRepository.save(h);
                });

        if (!props.getHostelName().equalsIgnoreCase(hostel.getName())) {
            hostel.setName(props.getHostelName());
            hostel = hostelRepository.save(hostel);
        }

        // 2. Ensure exactly 100 rooms (numbers 101 to 200) with capacity 4
        int roomsCreated = 0;
        int bedsCreated = 0;

        for (int rNum = props.getStartRoomNumber(); rNum <= props.getEndRoomNumber(); rNum++) {
            String roomStr = String.valueOf(rNum);
            int floorIndex = (rNum - props.getStartRoomNumber()) / props.getRoomsPerFloor() + 1;

            Optional<Room> existingRoomOpt = roomRepository.findByRoomNumber(roomStr);
            Room room;
            if (existingRoomOpt.isEmpty()) {
                room = new Room();
                room.setRoomNumber(roomStr);
                room.setFloor(floorIndex);
                room.setCapacity(props.getCapacityPerRoom());
                room.setStatus(Room.Status.ACTIVE);
                room.setActive(true);
                room.setHostel(hostel);
                room = roomRepository.save(room);
                roomsCreated++;
            } else {
                room = existingRoomOpt.get();
                boolean changed = false;
                if (room.getCapacity() != props.getCapacityPerRoom()) {
                    room.setCapacity(props.getCapacityPerRoom());
                    changed = true;
                }
                if (room.getHostel() == null) {
                    room.setHostel(hostel);
                    changed = true;
                }
                if (room.getFloor() != floorIndex) {
                    room.setFloor(floorIndex);
                    changed = true;
                }
                if (changed) {
                    room = roomRepository.save(room);
                }
            }

            // 3. Ensure 4 beds (A, B, C, D)
            List<Bed> existingBeds = bedRepository.findByRoomIdOrderByBedNumberAsc(room.getId());
            List<String> requiredLabels = props.getBedLabels();

            for (int bIndex = 0; bIndex < requiredLabels.size(); bIndex++) {
                int bedNum = bIndex + 1;
                String bedLabel = requiredLabels.get(bIndex);

                boolean exists = existingBeds.stream().anyMatch(b ->
                        bedLabel.equalsIgnoreCase(b.getBedLabel()) || b.getBedNumber() == bedNum
                );

                if (!exists) {
                    Bed newBed = new Bed();
                    newBed.setRoom(room);
                    newBed.setBedNumber(bedNum);
                    newBed.setBedLabel(bedLabel);
                    newBed.setStatus("available");
                    bedRepository.save(newBed);
                    bedsCreated++;
                } else {
                    // Make sure label is set
                    existingBeds.stream()
                            .filter(b -> b.getBedNumber() == bedNum && (b.getBedLabel() == null || b.getBedLabel().isBlank()))
                            .findFirst()
                            .ifPresent(b -> {
                                b.setBedLabel(bedLabel);
                                if (b.getStatus() == null || b.getStatus().isBlank()) {
                                    b.setStatus(b.getStudent() != null ? "occupied" : "available");
                                }
                                bedRepository.save(b);
                            });
                }
            }
        }

        long totalRooms = roomRepository.count();
        long totalBeds = bedRepository.count();
        long occupiedBeds = bedRepository.countByStudentIsNotNull();
        long availableBeds = bedRepository.countAvailableBeds();

        log.info("RoomBedInitializer: Verified capacity -> Total Rooms: {}, Total Beds: {}, Occupied: {}, Available: {}",
                totalRooms, totalBeds, occupiedBeds, availableBeds);
    }
}
