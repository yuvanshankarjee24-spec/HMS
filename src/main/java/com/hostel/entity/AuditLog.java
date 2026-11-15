package com.hostel.entity;

import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "audit_logs")
public class AuditLog {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "actor_user_id")
    private User actorUser;

    @Column(nullable = false, length = 100)
    private String action;

    @Column(length = 255)
    private String target;

    @Column(nullable = false, length = 50)
    private String result;

    @Column(length = 50)
    private String module;

    @Column(name = "reference_id")
    private Long referenceId;

    @Column(name = "old_value", length = 500)
    private String oldValue;

    @Column(name = "new_value", length = 500)
    private String newValue;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt = LocalDateTime.now();

    public AuditLog() {}

    public AuditLog(User actorUser, String action, String target, String result) {
        this.actorUser = actorUser;
        this.action = action;
        this.target = target;
        this.result = result;
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public User getActorUser() { return actorUser; }
    public void setActorUser(User actorUser) { this.actorUser = actorUser; }

    public String getAction() { return action; }
    public void setAction(String action) { this.action = action; }

    public String getTarget() { return target; }
    public void setTarget(String target) { this.target = target; }

    public String getResult() { return result; }
    public void setResult(String result) { this.result = result; }

    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }

    public String getModule() { return module; }
    public void setModule(String module) { this.module = module; }

    public Long getReferenceId() { return referenceId; }
    public void setReferenceId(Long referenceId) { this.referenceId = referenceId; }

    public String getOldValue() { return oldValue; }
    public void setOldValue(String oldValue) { this.oldValue = oldValue; }

    public String getNewValue() { return newValue; }
    public void setNewValue(String newValue) { this.newValue = newValue; }
}
