package com.hostel.entity;

import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "students")
public class Student {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false, unique = true)
    private User user;

    @Column(name = "full_name", nullable = false, length = 150)
    private String fullName;

    @Column(name = "register_number", nullable = false, unique = true, length = 100)
    private String registerNumber;

    @Column(name = "college_email", nullable = false, unique = true, length = 191)
    private String collegeEmail;

    @Column(nullable = false, length = 20)
    private String phone;

    @Column(nullable = false, length = 100)
    private String department;

    @Column(name = "`year`", nullable = false)
    private int year;

    @Column(name = "photo_path", length = 255)
    private String photoPath;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "hostel_id")
    private Hostel hostel;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "room_id")
    private Room room;

    @Column(name = "guardian_name", length = 150)
    private String guardianName;

    @Column(name = "guardian_phone", length = 20)
    private String guardianPhone;

    @Column(name = "guardian_relation", length = 50)
    private String guardianRelation;

    @Column(name = "is_active", nullable = false)
    private boolean active = true;

    @Column(name = "deleted_at")
    private LocalDateTime deletedAt;

    @Column(name = "is_locked", nullable = false)
    private boolean locked = false;

    @Column(name = "lock_reason", length = 500)
    private String lockReason;

    @Column(name = "locked_at")
    private LocalDateTime lockedAt;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "unlocked_by_user_id")
    private User unlockedBy;

    @Column(name = "unlocked_at")
    private LocalDateTime unlockedAt;

    @Column(name = "unlock_remarks", length = 500)
    private String unlockRemarks;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt = LocalDateTime.now();

    public Student() {}

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public User getUser() { return user; }
    public void setUser(User user) { this.user = user; }

    public String getFullName() { return fullName; }
    public void setFullName(String fullName) { this.fullName = fullName; }

    public String getRegisterNumber() { return registerNumber; }
    public void setRegisterNumber(String registerNumber) { this.registerNumber = registerNumber; }

    public String getCollegeEmail() { return collegeEmail; }
    public void setCollegeEmail(String collegeEmail) { this.collegeEmail = collegeEmail; }

    public String getPhone() { return phone; }
    public void setPhone(String phone) { this.phone = phone; }

    public String getDepartment() { return department; }
    public void setDepartment(String department) { this.department = department; }

    public int getYear() { return year; }
    public void setYear(int year) { this.year = year; }

    public String getPhotoPath() { return photoPath; }
    public void setPhotoPath(String photoPath) { this.photoPath = photoPath; }

    public boolean isLocked() { return locked; }
    public void setLocked(boolean locked) { this.locked = locked; }

    public String getLockReason() { return lockReason; }
    public void setLockReason(String lockReason) { this.lockReason = lockReason; }

    public LocalDateTime getLockedAt() { return lockedAt; }
    public void setLockedAt(LocalDateTime lockedAt) { this.lockedAt = lockedAt; }

    public User getUnlockedBy() { return unlockedBy; }
    public void setUnlockedBy(User unlockedBy) { this.unlockedBy = unlockedBy; }

    public LocalDateTime getUnlockedAt() { return unlockedAt; }
    public void setUnlockedAt(LocalDateTime unlockedAt) { this.unlockedAt = unlockedAt; }

    public String getUnlockRemarks() { return unlockRemarks; }
    public void setUnlockRemarks(String unlockRemarks) { this.unlockRemarks = unlockRemarks; }

    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }

    public Hostel getHostel() { return hostel; }
    public void setHostel(Hostel hostel) { this.hostel = hostel; }

    public Room getRoom() { return room; }
    public void setRoom(Room room) { this.room = room; }

    public String getGuardianName() { return guardianName; }
    public void setGuardianName(String guardianName) { this.guardianName = guardianName; }

    public String getGuardianPhone() { return guardianPhone; }
    public void setGuardianPhone(String guardianPhone) { this.guardianPhone = guardianPhone; }

    public String getGuardianRelation() { return guardianRelation; }
    public void setGuardianRelation(String guardianRelation) { this.guardianRelation = guardianRelation; }

    public boolean isActive() { return active; }
    public void setActive(boolean active) { this.active = active; }

    public LocalDateTime getDeletedAt() { return deletedAt; }
    public void setDeletedAt(LocalDateTime deletedAt) { this.deletedAt = deletedAt; }
}
