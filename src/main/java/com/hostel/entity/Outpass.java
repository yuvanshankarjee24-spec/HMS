package com.hostel.entity;

import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "outpasses")
public class Outpass {

    public enum PassType {
        DAY_PASS,
        WEEKEND_PASS,
        EMERGENCY_PASS,
        VACATION_PASS
    }

    public enum Status {
        PENDING,
        APPROVED,
        OUT,
        RETURNED,
        REJECTED,
        EXPIRED
    }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "student_id", nullable = false)
    private Student student;

    @Enumerated(EnumType.STRING)
    @Column(name = "pass_type", nullable = false, length = 50)
    private PassType passType;

    @Column(name = "departure_date_time", nullable = false)
    private LocalDateTime departureDateTime;

    @Column(name = "return_date_time", nullable = false)
    private LocalDateTime returnDateTime;

    @Column(columnDefinition = "TEXT", nullable = false)
    private String reason;

    @Column(name = "destination")
    private String destination;

    @Column(name = "emergency_phone", length = 50)
    private String emergencyPhone;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 50)
    private Status status = Status.PENDING;

    @Column(name = "warden_remarks")
    private String wardenRemarks;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "decided_by_user_id")
    private User decidedBy;

    @Column(name = "decided_at")
    private LocalDateTime decidedAt;

    @Column(name = "actual_exit_time")
    private LocalDateTime actualExitTime;

    @Column(name = "created_at", insertable = false, updatable = false)
    private LocalDateTime createdAt;

    public Outpass() {}

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public Student getStudent() { return student; }
    public void setStudent(Student student) { this.student = student; }

    public PassType getPassType() { return passType; }
    public void setPassType(PassType passType) { this.passType = passType; }

    public LocalDateTime getDepartureDateTime() { return departureDateTime; }
    public void setDepartureDateTime(LocalDateTime departureDateTime) { this.departureDateTime = departureDateTime; }

    public LocalDateTime getReturnDateTime() { return returnDateTime; }
    public void setReturnDateTime(LocalDateTime returnDateTime) { this.returnDateTime = returnDateTime; }

    public String getReason() { return reason; }
    public void setReason(String reason) { this.reason = reason; }

    public String getDestination() { return destination; }
    public void setDestination(String destination) { this.destination = destination; }

    public String getEmergencyPhone() { return emergencyPhone; }
    public void setEmergencyPhone(String emergencyPhone) { this.emergencyPhone = emergencyPhone; }

    public Status getStatus() { return status; }
    public void setStatus(Status status) { this.status = status; }

    public String getWardenRemarks() { return wardenRemarks; }
    public void setWardenRemarks(String wardenRemarks) { this.wardenRemarks = wardenRemarks; }

    public User getDecidedBy() { return decidedBy; }
    public void setDecidedBy(User decidedBy) { this.decidedBy = decidedBy; }

    public LocalDateTime getDecidedAt() { return decidedAt; }
    public void setDecidedAt(LocalDateTime decidedAt) { this.decidedAt = decidedAt; }

    public LocalDateTime getActualExitTime() { return actualExitTime; }
    public void setActualExitTime(LocalDateTime actualExitTime) { this.actualExitTime = actualExitTime; }

    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
}
