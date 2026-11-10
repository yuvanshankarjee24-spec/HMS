package com.hostel.entity;

import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "token_qr")
public class TokenQr {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "token_id", nullable = false, unique = true)
    private MessToken token;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "student_id", nullable = false)
    private Student student;

    @Column(name = "qr_token", nullable = false, unique = true, length = 255)
    private String qrToken;

    @Column(name = "pickup_code", nullable = false, unique = true, length = 12)
    private String pickupCode;

    @Column(nullable = false)
    private boolean used = false;

    @Column(nullable = false)
    private boolean closed = false;

    @Column(name = "used_at")
    private LocalDateTime usedAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt = LocalDateTime.now();

    public TokenQr() {}

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public MessToken getToken() { return token; }
    public void setToken(MessToken token) { this.token = token; }
    public Student getStudent() { return student; }
    public void setStudent(Student student) { this.student = student; }
    public String getQrToken() { return qrToken; }
    public void setQrToken(String qrToken) { this.qrToken = qrToken; }
    public String getPickupCode() { return pickupCode; }
    public void setPickupCode(String pickupCode) { this.pickupCode = pickupCode; }
    public boolean isUsed() { return used; }
    public void setUsed(boolean used) { this.used = used; }
    public boolean isClosed() { return closed; }
    public void setClosed(boolean closed) { this.closed = closed; }
    public LocalDateTime getUsedAt() { return usedAt; }
    public void setUsedAt(LocalDateTime usedAt) { this.usedAt = usedAt; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
}

