package com.hostel.entity;

import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "event_rsvps")
public class EventRsvp {

    public enum RsvpStatus { ATTENDING, NOT_ATTENDING }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "circular_id", nullable = false)
    private Circular circular;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "student_id", nullable = false)
    private Student student;

    @Enumerated(EnumType.STRING)
    @Column(name = "rsvp_status", nullable = false, length = 30)
    private RsvpStatus rsvpStatus;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt = LocalDateTime.now();

    public EventRsvp() {}

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public Circular getCircular() { return circular; }
    public void setCircular(Circular circular) { this.circular = circular; }
    public Student getStudent() { return student; }
    public void setStudent(Student student) { this.student = student; }
    public RsvpStatus getRsvpStatus() { return rsvpStatus; }
    public void setRsvpStatus(RsvpStatus rsvpStatus) { this.rsvpStatus = rsvpStatus; }
    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; }
}

