package com.hostel.entity;

import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "allocation_whitelist")
public class AllocationWhitelist {

    public enum Status {
        ELIGIBLE,
        ALLOCATED,
        REVOKED
    }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "leader_register_number", nullable = false, unique = true, length = 50)
    private String leaderRegisterNumber;

    @Column(name = "leader_name", length = 100)
    private String leaderName;

    @Column(name = "target_room_number", length = 20)
    private String targetRoomNumber;

    @Column(name = "group_size", nullable = false)
    private Integer groupSize = 1;

    @Column(name = "friend_register_numbers", columnDefinition = "TEXT")
    private String friendRegisterNumbers;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 50)
    private Status status = Status.ELIGIBLE;

    @Column(name = "allocated_room_number", length = 20)
    private String allocatedRoomNumber;

    @Column(name = "allocated_at")
    private LocalDateTime allocatedAt;

    @Column(length = 255)
    private String notes;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt = LocalDateTime.now();

    @Column(name = "updated_at")
    private LocalDateTime updatedAt = LocalDateTime.now();

    public AllocationWhitelist() {}

    public AllocationWhitelist(String leaderRegisterNumber, String leaderName, String targetRoomNumber,
                               Integer groupSize, String friendRegisterNumbers, String notes) {
        this.leaderRegisterNumber = leaderRegisterNumber;
        this.leaderName = leaderName;
        this.targetRoomNumber = targetRoomNumber;
        this.groupSize = groupSize != null ? groupSize : 1;
        this.friendRegisterNumbers = friendRegisterNumbers;
        this.notes = notes;
        this.status = Status.ELIGIBLE;
    }

    @PreUpdate
    public void preUpdate() {
        this.updatedAt = LocalDateTime.now();
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public String getLeaderRegisterNumber() { return leaderRegisterNumber; }
    public void setLeaderRegisterNumber(String leaderRegisterNumber) { this.leaderRegisterNumber = leaderRegisterNumber; }

    public String getLeaderName() { return leaderName; }
    public void setLeaderName(String leaderName) { this.leaderName = leaderName; }

    public String getTargetRoomNumber() { return targetRoomNumber; }
    public void setTargetRoomNumber(String targetRoomNumber) { this.targetRoomNumber = targetRoomNumber; }

    public Integer getGroupSize() { return groupSize; }
    public void setGroupSize(Integer groupSize) { this.groupSize = groupSize; }

    public String getFriendRegisterNumbers() { return friendRegisterNumbers; }
    public void setFriendRegisterNumbers(String friendRegisterNumbers) { this.friendRegisterNumbers = friendRegisterNumbers; }

    public Status getStatus() { return status; }
    public void setStatus(Status status) { this.status = status; }

    public String getAllocatedRoomNumber() { return allocatedRoomNumber; }
    public void setAllocatedRoomNumber(String allocatedRoomNumber) { this.allocatedRoomNumber = allocatedRoomNumber; }

    public LocalDateTime getAllocatedAt() { return allocatedAt; }
    public void setAllocatedAt(LocalDateTime allocatedAt) { this.allocatedAt = allocatedAt; }

    public String getNotes() { return notes; }
    public void setNotes(String notes) { this.notes = notes; }

    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }

    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; }
}
