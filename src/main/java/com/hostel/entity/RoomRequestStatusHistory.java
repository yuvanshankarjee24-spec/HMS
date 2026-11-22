package com.hostel.entity;

import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "room_request_status_history", indexes = {
    @Index(name = "idx_rr_history_req", columnList = "request_id")
})
public class RoomRequestStatusHistory {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "request_id", nullable = false)
    private RoomRequest request;

    @Column(name = "old_status", length = 30)
    private String oldStatus;

    @Column(name = "new_status", nullable = false, length = 30)
    private String newStatus;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "changed_by_user_id")
    private User changedBy;

    @Column(name = "changed_at", nullable = false, updatable = false)
    private LocalDateTime changedAt = LocalDateTime.now();

    @Column(columnDefinition = "TEXT")
    private String remarks;

    public RoomRequestStatusHistory() {}

    public RoomRequestStatusHistory(RoomRequest request, String oldStatus, String newStatus, User changedBy, String remarks) {
        this.request = request;
        this.oldStatus = oldStatus;
        this.newStatus = newStatus;
        this.changedBy = changedBy;
        this.remarks = remarks;
        this.changedAt = LocalDateTime.now();
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public RoomRequest getRequest() { return request; }
    public void setRequest(RoomRequest request) { this.request = request; }

    public String getOldStatus() { return oldStatus; }
    public void setOldStatus(String oldStatus) { this.oldStatus = oldStatus; }

    public String getNewStatus() { return newStatus; }
    public void setNewStatus(String newStatus) { this.newStatus = newStatus; }

    public User getChangedBy() { return changedBy; }
    public void setChangedBy(User changedBy) { this.changedBy = changedBy; }

    public LocalDateTime getChangedAt() { return changedAt; }
    public void setChangedAt(LocalDateTime changedAt) { this.changedAt = changedAt; }

    public String getRemarks() { return remarks; }
    public void setRemarks(String remarks) { this.remarks = remarks; }
}
