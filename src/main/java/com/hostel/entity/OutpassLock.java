package com.hostel.entity;

import jakarta.persistence.*;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Entity
@Table(name = "outpass_locks")
public class OutpassLock {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "student_id", nullable = false)
    private Student student;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "outpass_id")
    private Outpass outpass;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "attendance_session_id")
    private AttendanceSession attendanceSession;

    @Column(name = "attendance_date")
    private LocalDate attendanceDate;

    @Column(nullable = false, length = 500)
    private String reason;

    @Column(name = "is_active", nullable = false)
    private boolean active = true;

    @Column(name = "locked_at", nullable = false)
    private LocalDateTime lockedAt = LocalDateTime.now();

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "unlocked_by_user_id")
    private User unlockedBy;

    @Column(name = "unlocked_at")
    private LocalDateTime unlockedAt;

    @Column(name = "unlock_remarks", length = 500)
    private String unlockRemarks;

    public OutpassLock() {}

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public Student getStudent() { return student; }
    public void setStudent(Student student) { this.student = student; }

    public Outpass getOutpass() { return outpass; }
    public void setOutpass(Outpass outpass) { this.outpass = outpass; }

    public AttendanceSession getAttendanceSession() { return attendanceSession; }
    public void setAttendanceSession(AttendanceSession attendanceSession) { this.attendanceSession = attendanceSession; }

    public LocalDate getAttendanceDate() { return attendanceDate; }
    public void setAttendanceDate(LocalDate attendanceDate) { this.attendanceDate = attendanceDate; }

    public String getReason() { return reason; }
    public void setReason(String reason) { this.reason = reason; }

    public boolean isActive() { return active; }
    public void setActive(boolean active) { this.active = active; }

    public LocalDateTime getLockedAt() { return lockedAt; }
    public void setLockedAt(LocalDateTime lockedAt) { this.lockedAt = lockedAt; }

    public User getUnlockedBy() { return unlockedBy; }
    public void setUnlockedBy(User unlockedBy) { this.unlockedBy = unlockedBy; }

    public LocalDateTime getUnlockedAt() { return unlockedAt; }
    public void setUnlockedAt(LocalDateTime unlockedAt) { this.unlockedAt = unlockedAt; }

    public String getUnlockRemarks() { return unlockRemarks; }
    public void setUnlockRemarks(String unlockRemarks) { this.unlockRemarks = unlockRemarks; }
}

