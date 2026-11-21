package com.hostel.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
@ConfigurationProperties(prefix = "app.room-seed")
public class RoomSeedProperties {

    private String hostelCode = "PSG_ITECH";
    private String hostelName = "PSG iTech Hostel";
    private String hostelAddress = "PSG iTech Campus, Neelambur, Coimbatore";
    private int startRoomNumber = 101;
    private int endRoomNumber = 200;
    private int capacityPerRoom = 4;
    private int roomsPerFloor = 25;
    private int totalBedCapacity = 400;
    private List<String> bedLabels = List.of("A", "B", "C", "D");

    public String getHostelCode() { return hostelCode; }
    public void setHostelCode(String hostelCode) { this.hostelCode = hostelCode; }

    public String getHostelName() { return hostelName; }
    public void setHostelName(String hostelName) { this.hostelName = hostelName; }

    public String getHostelAddress() { return hostelAddress; }
    public void setHostelAddress(String hostelAddress) { this.hostelAddress = hostelAddress; }

    public int getStartRoomNumber() { return startRoomNumber; }
    public void setStartRoomNumber(int startRoomNumber) { this.startRoomNumber = startRoomNumber; }

    public int getEndRoomNumber() { return endRoomNumber; }
    public void setEndRoomNumber(int endRoomNumber) { this.endRoomNumber = endRoomNumber; }

    public int getCapacityPerRoom() { return capacityPerRoom; }
    public void setCapacityPerRoom(int capacityPerRoom) { this.capacityPerRoom = capacityPerRoom; }

    public int getRoomsPerFloor() { return roomsPerFloor; }
    public void setRoomsPerFloor(int roomsPerFloor) { this.roomsPerFloor = roomsPerFloor; }

    public int getTotalBedCapacity() { return totalBedCapacity; }
    public void setTotalBedCapacity(int totalBedCapacity) { this.totalBedCapacity = totalBedCapacity; }

    public List<String> getBedLabels() { return bedLabels; }
    public void setBedLabels(List<String> bedLabels) { this.bedLabels = bedLabels; }
}
