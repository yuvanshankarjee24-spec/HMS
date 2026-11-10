package com.hostel.entity;

import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "circular_event_reminders")
public class CircularEventReminder {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "circular_id", nullable = false)
    private Circular circular;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "student_id", nullable = false)
    private Student student;

    @Column(name = "reminder_type", nullable = false, length = 30)
    private String reminderType;

    @Column(name = "sent_at", nullable = false)
    private LocalDateTime sentAt = LocalDateTime.now();

    public CircularEventReminder() {}

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public Circular getCircular() { return circular; }
    public void setCircular(Circular circular) { this.circular = circular; }
    public Student getStudent() { return student; }
    public void setStudent(Student student) { this.student = student; }
    public String getReminderType() { return reminderType; }
    public void setReminderType(String reminderType) { this.reminderType = reminderType; }
    public LocalDateTime getSentAt() { return sentAt; }
    public void setSentAt(LocalDateTime sentAt) { this.sentAt = sentAt; }
}

