package com.hostel.entity;

import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "outpass_scan_logs")
public class OutpassScanLog {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "outpass_id", nullable = false)
    private Outpass outpass;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "scanned_by_user_id", nullable = false)
    private User scannedBy;

    @Column(name = "scan_action", nullable = false, length = 30)
    private String scanAction; // exit, entry, denied

    @Column(name = "scan_result", nullable = false, length = 50)
    private String scanResult;

    @Column(length = 500)
    private String notes;

    @Column(name = "scanned_at", nullable = false, updatable = false)
    private LocalDateTime scannedAt = LocalDateTime.now();

    public OutpassScanLog() {}

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public Outpass getOutpass() { return outpass; }
    public void setOutpass(Outpass outpass) { this.outpass = outpass; }

    public User getScannedBy() { return scannedBy; }
    public void setScannedBy(User scannedBy) { this.scannedBy = scannedBy; }

    public String getScanAction() { return scanAction; }
    public void setScanAction(String scanAction) { this.scanAction = scanAction; }

    public String getScanResult() { return scanResult; }
    public void setScanResult(String scanResult) { this.scanResult = scanResult; }

    public String getNotes() { return notes; }
    public void setNotes(String notes) { this.notes = notes; }

    public LocalDateTime getScannedAt() { return scannedAt; }
    public void setScannedAt(LocalDateTime scannedAt) { this.scannedAt = scannedAt; }
}

