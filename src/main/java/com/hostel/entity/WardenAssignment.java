package com.hostel.entity;

import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "warden_assignments", uniqueConstraints = {
    @UniqueConstraint(name = "uk_warden_hostel", columnNames = {"warden_user_id", "hostel_id"})
})
public class WardenAssignment {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "warden_user_id", nullable = false)
    private User wardenUser;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "hostel_id", nullable = false)
    private Hostel hostel;

    @Column(name = "is_primary", nullable = false)
    private boolean primaryAssignment = true;

    @Column(nullable = false)
    private boolean active = true;

    @Column(name = "assigned_at", nullable = false, updatable = false)
    private LocalDateTime assignedAt = LocalDateTime.now();

    public WardenAssignment() {}

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public User getWardenUser() { return wardenUser; }
    public void setWardenUser(User wardenUser) { this.wardenUser = wardenUser; }

    public Hostel getHostel() { return hostel; }
    public void setHostel(Hostel hostel) { this.hostel = hostel; }

    public boolean isPrimaryAssignment() { return primaryAssignment; }
    public void setPrimaryAssignment(boolean primaryAssignment) { this.primaryAssignment = primaryAssignment; }

    public boolean isActive() { return active; }
    public void setActive(boolean active) { this.active = active; }

    public LocalDateTime getAssignedAt() { return assignedAt; }
    public void setAssignedAt(LocalDateTime assignedAt) { this.assignedAt = assignedAt; }
}

