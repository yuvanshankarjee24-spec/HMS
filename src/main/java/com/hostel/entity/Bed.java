package com.hostel.entity;

import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "beds", uniqueConstraints = {
    @UniqueConstraint(name = "uq_room_bed", columnNames = {"room_id", "bed_number"}),
    @UniqueConstraint(name = "uq_bed_student", columnNames = {"student_id"})
})
public class Bed {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "room_id", nullable = false)
    private Room room;

    @Column(name = "bed_number", nullable = false)
    private int bedNumber;

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "student_id", unique = true)
    private Student student;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt = LocalDateTime.now();

    public Bed() {}

    public Bed(Room room, int bedNumber) {
        this.room = room;
        this.bedNumber = bedNumber;
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public Room getRoom() { return room; }
    public void setRoom(Room room) { this.room = room; }

    public int getBedNumber() { return bedNumber; }
    public void setBedNumber(int bedNumber) { this.bedNumber = bedNumber; }

    public Student getStudent() { return student; }
    public void setStudent(Student student) { this.student = student; }

    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
}
