package com.hostel.entity;

import jakarta.persistence.*;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Entity
@Table(name = "attendance_notifications_log")
public class AttendanceNotificationLog {

    public enum ReminderType {
        FIRST_REMINDER,
        SECOND_REMINDER,
        MISSED_LOCK
    }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "student_id", nullable = false)
    private Student student;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "session_id", nullable = false)
    private AttendanceSession session;

    @Column(name = "attendance_date", nullable = false)
    private LocalDate attendanceDate;

    @Enumerated(EnumType.STRING)
    @Column(name = "reminder_type", nullable = false, length = 40)
    private ReminderType reminderType;

    @Column(name = "sent_at", nullable = false, updatable = false)
    private LocalDateTime sentAt = LocalDateTime.now();

    public AttendanceNotificationLog() {}

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public Student getStudent() { return student; }
    public void setStudent(Student student) { this.student = student; }

    public AttendanceSession getSession() { return session; }
    public void setSession(AttendanceSession session) { this.session = session; }

    public LocalDate getAttendanceDate() { return attendanceDate; }
    public void setAttendanceDate(LocalDate attendanceDate) { this.attendanceDate = attendanceDate; }

    public ReminderType getReminderType() { return reminderType; }
    public void setReminderType(ReminderType reminderType) { this.reminderType = reminderType; }

    public LocalDateTime getSentAt() { return sentAt; }
    public void setSentAt(LocalDateTime sentAt) { this.sentAt = sentAt; }
}

