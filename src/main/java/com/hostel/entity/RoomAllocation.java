package com.hostel.entity;

import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "room_allocations", indexes = {
    @Index(name = "idx_ra_student_status", columnList = "student_id, status"),
    @Index(name = "idx_ra_room_status", columnList = "room_id, status"),
    @Index(name = "idx_ra_bed_status", columnList = "bed_id, status")
})
public class RoomAllocation {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "student_id", nullable = false)
    private Student student;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "room_id", nullable = false)
    private Room room;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "bed_id", nullable = false)
    private Bed bed;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "allocated_by", nullable = false)
    private User allocatedBy;

    @Column(name = "allocated_at", nullable = false, updatable = false)
    private LocalDateTime allocatedAt = LocalDateTime.now();

    @Column(name = "vacated_at")
    private LocalDateTime vacatedAt;

    @Column(nullable = false, length = 30)
    private String status = "active"; // active, vacated, transferred

    @Column(length = 255)
    private String remarks;

    public RoomAllocation() {}

    public RoomAllocation(Student student, Room room, Bed bed, User allocatedBy, String status, String remarks) {
        this.student = student;
        this.room = room;
        this.bed = bed;
        this.allocatedBy = allocatedBy;
        this.status = status;
        this.remarks = remarks;
        this.allocatedAt = LocalDateTime.now();
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public Student getStudent() { return student; }
    public void setStudent(Student student) { this.student = student; }

    public Room getRoom() { return room; }
    public void setRoom(Room room) { this.room = room; }

    public Bed getBed() { return bed; }
    public void setBed(Bed bed) { this.bed = bed; }

    public User getAllocatedBy() { return allocatedBy; }
    public void setAllocatedBy(User allocatedBy) { this.allocatedBy = allocatedBy; }

    public LocalDateTime getAllocatedAt() { return allocatedAt; }
    public void setAllocatedAt(LocalDateTime allocatedAt) { this.allocatedAt = allocatedAt; }

    public LocalDateTime getVacatedAt() { return vacatedAt; }
    public void setVacatedAt(LocalDateTime vacatedAt) { this.vacatedAt = vacatedAt; }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }

    public String getRemarks() { return remarks; }
    public void setRemarks(String remarks) { this.remarks = remarks; }
}
