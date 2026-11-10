package com.hostel.entity;

import jakarta.persistence.*;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Entity
@Table(name = "outpass_lock_history")
public class OutpassLockHistory {

    public enum Action {
        LOCKED,
        UNLOCKED,
        RELOCKED
    }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "student_id", nullable = false)
    private Student student;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private Action action;

    @Column(nullable = false, length = 500)
    private String reason;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "actor_user_id")
    private User actor;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "session_id")
    private AttendanceSession session;

    @Column(name = "attendance_date")
    private LocalDate attendanceDate;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt = LocalDateTime.now();

    public OutpassLockHistory() {}

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public Student getStudent() { return student; }
    public void setStudent(Student student) { this.student = student; }

    public Action getAction() { return action; }
    public void setAction(Action action) { this.action = action; }

    public String getReason() { return reason; }
    public void setReason(String reason) { this.reason = reason; }

    public User getActor() { return actor; }
    public void setActor(User actor) { this.actor = actor; }

    public AttendanceSession getSession() { return session; }
    public void setSession(AttendanceSession session) { this.session = session; }

    public LocalDate getAttendanceDate() { return attendanceDate; }
    public void setAttendanceDate(LocalDate attendanceDate) { this.attendanceDate = attendanceDate; }

    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
}

