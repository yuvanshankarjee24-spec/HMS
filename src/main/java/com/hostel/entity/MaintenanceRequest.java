package com.hostel.entity;

import jakarta.persistence.*;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Entity
@Table(name = "maintenance_requests")
public class MaintenanceRequest {

    public enum Category {
        ROOM_CLEANING,
        ELECTRICAL,
        PLUMBING,
        FURNITURE_CARPENTRY,
        INTERNET_WIFI,
        BATHROOM_WASHROOM,
        OTHER
    }

    public enum Priority {
        LOW,
        MEDIUM,
        HIGH,
        URGENT
    }

    public enum Status {
        PENDING,
        ASSIGNED,
        IN_PROGRESS,
        COMPLETED,
        RESOLVED,
        REJECTED,
        CANCELLED,
        REOPENED,
        ON_HOLD
    }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "request_code", nullable = false, unique = true, length = 32)
    private String requestCode;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "student_id", nullable = false)
    private Student student;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 50)
    private Category category;

    @Column(nullable = false, length = 150)
    private String title;

    @Column(columnDefinition = "TEXT", nullable = false)
    private String description;

    @Column(name = "hostel_block", nullable = false, length = 100)
    private String hostelBlock;

    @Column(name = "room_number", nullable = false, length = 50)
    private String roomNumber;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private Priority priority = Priority.MEDIUM;

    @Column(name = "preferred_time_slot", length = 100)
    private String preferredTimeSlot;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private Status status = Status.PENDING;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "assigned_worker_id")
    private WorkerProfile assignedWorker;

    @Column(name = "warden_instruction", length = 500)
    private String wardenInstruction;

    @Column(name = "expected_completion_date")
    private LocalDate expectedCompletionDate;

    @Column(name = "rejection_reason", length = 500)
    private String rejectionReason;

    @Column(name = "on_hold_reason", length = 500)
    private String onHoldReason;

    @Column(name = "worker_remarks", length = 500)
    private String workerRemarks;

    @Column(name = "reopen_reason", length = 500)
    private String reopenReason;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt = LocalDateTime.now();

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt = LocalDateTime.now();

    @Column(name = "completed_at")
    private LocalDateTime completedAt;

    @Column(name = "resolved_at")
    private LocalDateTime resolvedAt;

    public MaintenanceRequest() {}

    @PreUpdate
    public void preUpdate() {
        this.updatedAt = LocalDateTime.now();
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public String getRequestCode() { return requestCode; }
    public void setRequestCode(String requestCode) { this.requestCode = requestCode; }

    public Student getStudent() { return student; }
    public void setStudent(Student student) { this.student = student; }

    public Category getCategory() { return category; }
    public void setCategory(Category category) { this.category = category; }

    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }

    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }

    public String getHostelBlock() { return hostelBlock; }
    public void setHostelBlock(String hostelBlock) { this.hostelBlock = hostelBlock; }

    public String getRoomNumber() { return roomNumber; }
    public void setRoomNumber(String roomNumber) { this.roomNumber = roomNumber; }

    public Priority getPriority() { return priority; }
    public void setPriority(Priority priority) { this.priority = priority; }

    public String getPreferredTimeSlot() { return preferredTimeSlot; }
    public void setPreferredTimeSlot(String preferredTimeSlot) { this.preferredTimeSlot = preferredTimeSlot; }

    public Status getStatus() { return status; }
    public void setStatus(Status status) { this.status = status; }

    public WorkerProfile getAssignedWorker() { return assignedWorker; }
    public void setAssignedWorker(WorkerProfile assignedWorker) { this.assignedWorker = assignedWorker; }

    public String getWardenInstruction() { return wardenInstruction; }
    public void setWardenInstruction(String wardenInstruction) { this.wardenInstruction = wardenInstruction; }

    public LocalDate getExpectedCompletionDate() { return expectedCompletionDate; }
    public void setExpectedCompletionDate(LocalDate expectedCompletionDate) { this.expectedCompletionDate = expectedCompletionDate; }

    public String getRejectionReason() { return rejectionReason; }
    public void setRejectionReason(String rejectionReason) { this.rejectionReason = rejectionReason; }

    public String getOnHoldReason() { return onHoldReason; }
    public void setOnHoldReason(String onHoldReason) { this.onHoldReason = onHoldReason; }

    public String getWorkerRemarks() { return workerRemarks; }
    public void setWorkerRemarks(String workerRemarks) { this.workerRemarks = workerRemarks; }

    public String getReopenReason() { return reopenReason; }
    public void setReopenReason(String reopenReason) { this.reopenReason = reopenReason; }

    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }

    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; }

    public LocalDateTime getCompletedAt() { return completedAt; }
    public void setCompletedAt(LocalDateTime completedAt) { this.completedAt = completedAt; }

    public LocalDateTime getResolvedAt() { return resolvedAt; }
    public void setResolvedAt(LocalDateTime resolvedAt) { this.resolvedAt = resolvedAt; }
}

