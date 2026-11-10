package com.hostel.entity;

import jakarta.persistence.*;
import java.time.LocalDateTime;
import java.time.LocalTime;

@Entity
@Table(name = "attendance_sessions")
public class AttendanceSession {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 120)
    private String name;

    @Column(name = "hostel_block", nullable = false, length = 100)
    private String hostelBlock = "ALL";

    @Column(name = "start_time", nullable = false)
    private LocalTime startTime;

    @Column(name = "end_time", nullable = false)
    private LocalTime endTime;

    @Column(name = "active_days", nullable = false, length = 64)
    private String activeDays = "MON,TUE,WED,THU,FRI,SAT,SUN";

    @Column(nullable = false)
    private boolean enabled = true;

    @Column(name = "reminder_lead_minutes", nullable = false)
    private int reminderLeadMinutes = 30;

    @Column(name = "second_reminder_minutes")
    private Integer secondReminderMinutes = 5;

    @Column(name = "geofence_enabled", nullable = false)
    private boolean geofenceEnabled = false;

    @Column(name = "hostel_latitude")
    private Double hostelLatitude;

    @Column(name = "hostel_longitude")
    private Double hostelLongitude;

    @Column(name = "geofence_radius_meters")
    private Integer geofenceRadiusMeters = 200;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "created_by_user_id")
    private User createdBy;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt = LocalDateTime.now();

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt = LocalDateTime.now();

    public AttendanceSession() {}

    @PreUpdate
    public void preUpdate() {
        this.updatedAt = LocalDateTime.now();
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public String getHostelBlock() { return hostelBlock; }
    public void setHostelBlock(String hostelBlock) { this.hostelBlock = hostelBlock; }

    public LocalTime getStartTime() { return startTime; }
    public void setStartTime(LocalTime startTime) { this.startTime = startTime; }

    public LocalTime getEndTime() { return endTime; }
    public void setEndTime(LocalTime endTime) { this.endTime = endTime; }

    public String getActiveDays() { return activeDays; }
    public void setActiveDays(String activeDays) { this.activeDays = activeDays; }

    public boolean isEnabled() { return enabled; }
    public void setEnabled(boolean enabled) { this.enabled = enabled; }

    public int getReminderLeadMinutes() { return reminderLeadMinutes; }
    public void setReminderLeadMinutes(int reminderLeadMinutes) { this.reminderLeadMinutes = reminderLeadMinutes; }

    public Integer getSecondReminderMinutes() { return secondReminderMinutes; }
    public void setSecondReminderMinutes(Integer secondReminderMinutes) { this.secondReminderMinutes = secondReminderMinutes; }

    public boolean isGeofenceEnabled() { return geofenceEnabled; }
    public void setGeofenceEnabled(boolean geofenceEnabled) { this.geofenceEnabled = geofenceEnabled; }

    public Double getHostelLatitude() { return hostelLatitude; }
    public void setHostelLatitude(Double hostelLatitude) { this.hostelLatitude = hostelLatitude; }

    public Double getHostelLongitude() { return hostelLongitude; }
    public void setHostelLongitude(Double hostelLongitude) { this.hostelLongitude = hostelLongitude; }

    public Integer getGeofenceRadiusMeters() { return geofenceRadiusMeters; }
    public void setGeofenceRadiusMeters(Integer geofenceRadiusMeters) { this.geofenceRadiusMeters = geofenceRadiusMeters; }

    public User getCreatedBy() { return createdBy; }
    public void setCreatedBy(User createdBy) { this.createdBy = createdBy; }

    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }

    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; }
}

