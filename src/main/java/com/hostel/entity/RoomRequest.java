package com.hostel.entity;

import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "room_requests", indexes = {
    @Index(name = "idx_requests_student_status", columnList = "student_id, status"),
    @Index(name = "idx_requests_status", columnList = "status"),
    @Index(name = "idx_rr_request_code", columnList = "request_code")
})
public class RoomRequest {

    public enum Status {
        PENDING, APPROVED, REJECTED, CANCELLED
    }

    @Converter
    public static class StatusConverter extends com.hostel.entity.converter.LowercaseEnumConverter<Status> {
        public StatusConverter() { super(Status.class); }
    }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "request_code", length = 50)
    private String requestCode;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "student_id", nullable = false)
    private Student student;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "preferred_room_id")
    private Room preferredRoom;

    @Column(name = "preferred_floor")
    private Integer preferredFloor;

    @Column(name = "preferred_room_type", length = 50)
    private String preferredRoomType;

    @Column(name = "preferred_roommates", length = 255)
    private String preferredRoommates;

    @Column(columnDefinition = "TEXT")
    private String note;

    @Convert(converter = StatusConverter.class)
    @Column(nullable = false)
    private Status status = Status.PENDING;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "allotted_room_id")
    private Room allottedRoom;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "allotted_bed_id")
    private Bed allottedBed;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "decided_by")
    private User decidedBy;

    @Column(name = "decision_note", columnDefinition = "TEXT")
    private String decisionNote;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt = LocalDateTime.now();

    @Column(name = "decided_at")
    private LocalDateTime decidedAt;

    public RoomRequest() {}

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public String getRequestCode() {
        if (requestCode != null && !requestCode.isBlank()) {
            return requestCode;
        }
        int yr = createdAt != null ? createdAt.getYear() : 2026;
        long num = id != null ? id : 1L;
        return String.format("RRQ-%d-%04d", yr, num);
    }
    public void setRequestCode(String requestCode) { this.requestCode = requestCode; }

    public Student getStudent() { return student; }
    public void setStudent(Student student) { this.student = student; }

    public Room getPreferredRoom() { return preferredRoom; }
    public void setPreferredRoom(Room preferredRoom) { this.preferredRoom = preferredRoom; }

    public Integer getPreferredFloor() { return preferredFloor; }
    public void setPreferredFloor(Integer preferredFloor) { this.preferredFloor = preferredFloor; }

    public String getPreferredRoomType() { return preferredRoomType; }
    public void setPreferredRoomType(String preferredRoomType) { this.preferredRoomType = preferredRoomType; }

    public String getPreferredRoommates() { return preferredRoommates; }
    public void setPreferredRoommates(String preferredRoommates) { this.preferredRoommates = preferredRoommates; }

    public String getNote() { return note; }
    public void setNote(String note) { this.note = note; }

    public Status getStatus() { return status; }
    public void setStatus(Status status) { this.status = status; }

    public Room getAllottedRoom() { return allottedRoom; }
    public void setAllottedRoom(Room allottedRoom) { this.allottedRoom = allottedRoom; }

    public Bed getAllottedBed() { return allottedBed; }
    public void setAllottedBed(Bed allottedBed) { this.allottedBed = allottedBed; }

    public User getDecidedBy() { return decidedBy; }
    public void setDecidedBy(User decidedBy) { this.decidedBy = decidedBy; }

    public String getDecisionNote() { return decisionNote; }
    public void setDecisionNote(String decisionNote) { this.decisionNote = decisionNote; }

    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }

    public LocalDateTime getDecidedAt() { return decidedAt; }
    public void setDecidedAt(LocalDateTime decidedAt) { this.decidedAt = decidedAt; }
}
