package com.hostel.entity;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "parcels")
public class Parcel {

    public enum ParcelType {
        SMALL,
        MEDIUM,
        LARGE,
        FRAGILE,
        COD
    }

    public enum Status {
        RECEIVED,
        NOTIFIED,
        DELIVERED,
        RETURNED_TO_SENDER,
        CANCELLED,
        UNCLAIMED
    }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "parcel_code", nullable = false, unique = true, length = 32)
    private String parcelCode;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "student_id", nullable = false)
    private Student student;

    @Column(name = "hostel_block", nullable = false, length = 100)
    private String hostelBlock = "Main Block";

    @Column(name = "courier_name", nullable = false, length = 100)
    private String courierName;

    @Column(name = "tracking_number", length = 100)
    private String trackingNumber;

    @Enumerated(EnumType.STRING)
    @Column(name = "parcel_type", nullable = false, length = 40)
    private ParcelType parcelType = ParcelType.MEDIUM;

    @Column(length = 500)
    private String description;

    @Column(name = "is_cod", nullable = false)
    private boolean cod = false;

    @Column(name = "cod_amount", precision = 10, scale = 2)
    private BigDecimal codAmount;

    @Column(name = "cod_payment_pending", nullable = false)
    private boolean codPaymentPending = false;

    @Column(name = "cod_collected", nullable = false)
    private boolean codCollected = false;

    @Column(name = "storage_location", length = 100)
    private String storageLocation;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 40)
    private Status status = Status.RECEIVED;

    @Column(name = "received_at", nullable = false)
    private LocalDateTime receivedAt = LocalDateTime.now();

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "received_by_user_id")
    private User receivedBy;

    @Column(name = "delivered_at")
    private LocalDateTime deliveredAt;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "delivered_by_user_id")
    private User deliveredBy;

    @Column(name = "delivery_remarks", length = 500)
    private String deliveryRemarks;

    @Column(name = "handover_to_name", length = 150)
    private String handoverToName;

    @Column(name = "handover_reason", length = 500)
    private String handoverReason;

    @Column(name = "return_or_unclaimed_reason", length = 500)
    private String returnOrUnclaimedReason;

    @Column(name = "reminder_24h_sent", nullable = false)
    private boolean reminder24hSent = false;

    @Column(name = "reminder_3d_sent", nullable = false)
    private boolean reminder3dSent = false;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt = LocalDateTime.now();

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt = LocalDateTime.now();

    public Parcel() {}

    @PreUpdate
    public void preUpdate() {
        this.updatedAt = LocalDateTime.now();
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public String getParcelCode() { return parcelCode; }
    public void setParcelCode(String parcelCode) { this.parcelCode = parcelCode; }

    public Student getStudent() { return student; }
    public void setStudent(Student student) { this.student = student; }

    public String getHostelBlock() { return hostelBlock; }
    public void setHostelBlock(String hostelBlock) { this.hostelBlock = hostelBlock; }

    public String getCourierName() { return courierName; }
    public void setCourierName(String courierName) { this.courierName = courierName; }

    public String getTrackingNumber() { return trackingNumber; }
    public void setTrackingNumber(String trackingNumber) { this.trackingNumber = trackingNumber; }

    public ParcelType getParcelType() { return parcelType; }
    public void setParcelType(ParcelType parcelType) { this.parcelType = parcelType; }

    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }

    public boolean isCod() { return cod; }
    public void setCod(boolean cod) { this.cod = cod; }

    public BigDecimal getCodAmount() { return codAmount; }
    public void setCodAmount(BigDecimal codAmount) { this.codAmount = codAmount; }

    public boolean isCodPaymentPending() { return codPaymentPending; }
    public void setCodPaymentPending(boolean codPaymentPending) { this.codPaymentPending = codPaymentPending; }

    public boolean isCodCollected() { return codCollected; }
    public void setCodCollected(boolean codCollected) { this.codCollected = codCollected; }

    public String getStorageLocation() { return storageLocation; }
    public void setStorageLocation(String storageLocation) { this.storageLocation = storageLocation; }

    public Status getStatus() { return status; }
    public void setStatus(Status status) { this.status = status; }

    public LocalDateTime getReceivedAt() { return receivedAt; }
    public void setReceivedAt(LocalDateTime receivedAt) { this.receivedAt = receivedAt; }

    public User getReceivedBy() { return receivedBy; }
    public void setReceivedBy(User receivedBy) { this.receivedBy = receivedBy; }

    public LocalDateTime getDeliveredAt() { return deliveredAt; }
    public void setDeliveredAt(LocalDateTime deliveredAt) { this.deliveredAt = deliveredAt; }

    public User getDeliveredBy() { return deliveredBy; }
    public void setDeliveredBy(User deliveredBy) { this.deliveredBy = deliveredBy; }

    public String getDeliveryRemarks() { return deliveryRemarks; }
    public void setDeliveryRemarks(String deliveryRemarks) { this.deliveryRemarks = deliveryRemarks; }

    public String getHandoverToName() { return handoverToName; }
    public void setHandoverToName(String handoverToName) { this.handoverToName = handoverToName; }

    public String getHandoverReason() { return handoverReason; }
    public void setHandoverReason(String handoverReason) { this.handoverReason = handoverReason; }

    public String getReturnOrUnclaimedReason() { return returnOrUnclaimedReason; }
    public void setReturnOrUnclaimedReason(String returnOrUnclaimedReason) { this.returnOrUnclaimedReason = returnOrUnclaimedReason; }

    public boolean isReminder24hSent() { return reminder24hSent; }
    public void setReminder24hSent(boolean reminder24hSent) { this.reminder24hSent = reminder24hSent; }

    public boolean isReminder3dSent() { return reminder3dSent; }
    public void setReminder3dSent(boolean reminder3dSent) { this.reminder3dSent = reminder3dSent; }

    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }

    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; }
}

