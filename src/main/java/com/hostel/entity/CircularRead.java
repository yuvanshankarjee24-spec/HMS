package com.hostel.entity;

import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "circular_reads")
public class CircularRead {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "circular_id", nullable = false)
    private Circular circular;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "student_id", nullable = false)
    private Student student;

    @Column(name = "read_at", nullable = false)
    private LocalDateTime readAt = LocalDateTime.now();

    @Column(name = "acknowledged_at")
    private LocalDateTime acknowledgedAt;

    @Column(name = "reminder_24h_sent", nullable = false)
    private boolean reminder24hSent = false;

    public CircularRead() {}

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public Circular getCircular() { return circular; }
    public void setCircular(Circular circular) { this.circular = circular; }
    public Student getStudent() { return student; }
    public void setStudent(Student student) { this.student = student; }
    public LocalDateTime getReadAt() { return readAt; }
    public void setReadAt(LocalDateTime readAt) { this.readAt = readAt; }
    public LocalDateTime getAcknowledgedAt() { return acknowledgedAt; }
    public void setAcknowledgedAt(LocalDateTime acknowledgedAt) { this.acknowledgedAt = acknowledgedAt; }
    public boolean isReminder24hSent() { return reminder24hSent; }
    public void setReminder24hSent(boolean reminder24hSent) { this.reminder24hSent = reminder24hSent; }
}

