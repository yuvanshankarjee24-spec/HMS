package com.hostel.entity;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Entity
@Table(name = "mess_tokens")
public class MessToken {

    public enum Status { BOOKED, QR_ACTIVE, DELIVERED, PARTIALLY_DELIVERED, CANCELLED, EXPIRED }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "token_code", nullable = false, unique = true, length = 32)
    private String tokenCode;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "student_id", nullable = false)
    private Student student;

    @Column(name = "hostel_block", nullable = false, length = 100)
    private String hostelBlock = "Main Block";

    @Column(name = "menu_date", nullable = false)
    private LocalDate menuDate;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private MessPeriodConfig.Period period;

    @Column(name = "total_amount", nullable = false, precision = 10, scale = 2)
    private BigDecimal totalAmount = BigDecimal.ZERO;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private Status status = Status.BOOKED;

    @Column(name = "delivered_at")
    private LocalDateTime deliveredAt;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "delivered_by_user_id")
    private User deliveredBy;

    @Column(name = "delivery_remarks", length = 500)
    private String deliveryRemarks;

    @Column(name = "qr_activation_notified", nullable = false)
    private boolean qrActivationNotified = false;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt = LocalDateTime.now();

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt = LocalDateTime.now();

    public MessToken() {}

    @PreUpdate
    public void preUpdate() { this.updatedAt = LocalDateTime.now(); }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getTokenCode() { return tokenCode; }
    public void setTokenCode(String tokenCode) { this.tokenCode = tokenCode; }
    public Student getStudent() { return student; }
    public void setStudent(Student student) { this.student = student; }
    public String getHostelBlock() { return hostelBlock; }
    public void setHostelBlock(String hostelBlock) { this.hostelBlock = hostelBlock; }
    public LocalDate getMenuDate() { return menuDate; }
    public void setMenuDate(LocalDate menuDate) { this.menuDate = menuDate; }
    public MessPeriodConfig.Period getPeriod() { return period; }
    public void setPeriod(MessPeriodConfig.Period period) { this.period = period; }
    public BigDecimal getTotalAmount() { return totalAmount; }
    public void setTotalAmount(BigDecimal totalAmount) { this.totalAmount = totalAmount; }
    public Status getStatus() { return status; }
    public void setStatus(Status status) { this.status = status; }
    public LocalDateTime getDeliveredAt() { return deliveredAt; }
    public void setDeliveredAt(LocalDateTime deliveredAt) { this.deliveredAt = deliveredAt; }
    public User getDeliveredBy() { return deliveredBy; }
    public void setDeliveredBy(User deliveredBy) { this.deliveredBy = deliveredBy; }
    public String getDeliveryRemarks() { return deliveryRemarks; }
    public void setDeliveryRemarks(String deliveryRemarks) { this.deliveryRemarks = deliveryRemarks; }
    public boolean isQrActivationNotified() { return qrActivationNotified; }
    public void setQrActivationNotified(boolean qrActivationNotified) { this.qrActivationNotified = qrActivationNotified; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; }
}

