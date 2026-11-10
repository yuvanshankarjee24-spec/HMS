package com.hostel.entity;

import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "circulars")
public class Circular {

    public enum Type { EVENT, CIRCULAR, REGULATION, EMERGENCY }
    public enum Priority { NORMAL, IMPORTANT, URGENT }
    public enum Status { DRAFT, SCHEDULED, PUBLISHED, ARCHIVED }
    public enum TargetScope { ALL, BLOCK, YEAR_DEPT, STUDENTS }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "circular_code", nullable = false, unique = true, length = 32)
    private String circularCode;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 40)
    private Type type = Type.CIRCULAR;

    @Column(nullable = false, length = 200)
    private String title;

    @Column(columnDefinition = "TEXT", nullable = false)
    private String description;

    @Column(name = "rules_and_regulations", columnDefinition = "TEXT")
    private String rulesAndRegulations;

    @Column(name = "event_date_time")
    private LocalDateTime eventDateTime;

    @Column(length = 200)
    private String venue;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private Priority priority = Priority.NORMAL;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private Status status = Status.DRAFT;

    @Column(name = "require_acknowledgement", nullable = false)
    private boolean requireAcknowledgement = false;

    @Enumerated(EnumType.STRING)
    @Column(name = "target_scope", nullable = false, length = 40)
    private TargetScope targetScope = TargetScope.ALL;

    @Column(name = "hostel_block", length = 100)
    private String hostelBlock;

    @Column(name = "scheduled_publish_at")
    private LocalDateTime scheduledPublishAt;

    @Column(name = "expires_at")
    private LocalDateTime expiresAt;

    @Column(name = "published_at")
    private LocalDateTime publishedAt;

    @Column(nullable = false)
    private int version = 1;

    @Column(name = "updated_flag", nullable = false)
    private boolean updatedFlag = false;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "created_by_user_id")
    private User createdBy;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt = LocalDateTime.now();

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt = LocalDateTime.now();

    public Circular() {}

    @PreUpdate
    public void preUpdate() { this.updatedAt = LocalDateTime.now(); }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getCircularCode() { return circularCode; }
    public void setCircularCode(String circularCode) { this.circularCode = circularCode; }
    public Type getType() { return type; }
    public void setType(Type type) { this.type = type; }
    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }
    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }
    public String getRulesAndRegulations() { return rulesAndRegulations; }
    public void setRulesAndRegulations(String rulesAndRegulations) { this.rulesAndRegulations = rulesAndRegulations; }
    public LocalDateTime getEventDateTime() { return eventDateTime; }
    public void setEventDateTime(LocalDateTime eventDateTime) { this.eventDateTime = eventDateTime; }
    public String getVenue() { return venue; }
    public void setVenue(String venue) { this.venue = venue; }
    public Priority getPriority() { return priority; }
    public void setPriority(Priority priority) { this.priority = priority; }
    public Status getStatus() { return status; }
    public void setStatus(Status status) { this.status = status; }
    public boolean isRequireAcknowledgement() { return requireAcknowledgement; }
    public void setRequireAcknowledgement(boolean requireAcknowledgement) { this.requireAcknowledgement = requireAcknowledgement; }
    public TargetScope getTargetScope() { return targetScope; }
    public void setTargetScope(TargetScope targetScope) { this.targetScope = targetScope; }
    public String getHostelBlock() { return hostelBlock; }
    public void setHostelBlock(String hostelBlock) { this.hostelBlock = hostelBlock; }
    public LocalDateTime getScheduledPublishAt() { return scheduledPublishAt; }
    public void setScheduledPublishAt(LocalDateTime scheduledPublishAt) { this.scheduledPublishAt = scheduledPublishAt; }
    public LocalDateTime getExpiresAt() { return expiresAt; }
    public void setExpiresAt(LocalDateTime expiresAt) { this.expiresAt = expiresAt; }
    public LocalDateTime getPublishedAt() { return publishedAt; }
    public void setPublishedAt(LocalDateTime publishedAt) { this.publishedAt = publishedAt; }
    public int getVersion() { return version; }
    public void setVersion(int version) { this.version = version; }
    public boolean isUpdatedFlag() { return updatedFlag; }
    public void setUpdatedFlag(boolean updatedFlag) { this.updatedFlag = updatedFlag; }
    public User getCreatedBy() { return createdBy; }
    public void setCreatedBy(User createdBy) { this.createdBy = createdBy; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; }
}

