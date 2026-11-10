package com.hostel.entity;

import jakarta.persistence.*;
import java.time.LocalDateTime;
import java.time.LocalTime;

@Entity
@Table(name = "mess_period_config")
public class MessPeriodConfig {

    public enum Period { BREAKFAST, LUNCH, SNACKS, DINNER }

    @Id
    @Enumerated(EnumType.STRING)
    @Column(length = 30)
    private Period period;

    @Column(name = "start_time", nullable = false)
    private LocalTime startTime;

    @Column(name = "end_time", nullable = false)
    private LocalTime endTime;

    @Column(name = "cutoff_minutes_before", nullable = false)
    private int cutoffMinutesBefore = 120;

    @Column(name = "qr_open_minutes_before", nullable = false)
    private int qrOpenMinutesBefore = 15;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt = LocalDateTime.now();

    public MessPeriodConfig() {}

    @PreUpdate
    public void preUpdate() { this.updatedAt = LocalDateTime.now(); }

    public Period getPeriod() { return period; }
    public void setPeriod(Period period) { this.period = period; }
    public LocalTime getStartTime() { return startTime; }
    public void setStartTime(LocalTime startTime) { this.startTime = startTime; }
    public LocalTime getEndTime() { return endTime; }
    public void setEndTime(LocalTime endTime) { this.endTime = endTime; }
    public int getCutoffMinutesBefore() { return cutoffMinutesBefore; }
    public void setCutoffMinutesBefore(int cutoffMinutesBefore) { this.cutoffMinutesBefore = cutoffMinutesBefore; }
    public int getQrOpenMinutesBefore() { return qrOpenMinutesBefore; }
    public void setQrOpenMinutesBefore(int qrOpenMinutesBefore) { this.qrOpenMinutesBefore = qrOpenMinutesBefore; }
    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; }
}

