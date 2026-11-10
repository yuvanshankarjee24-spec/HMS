package com.hostel.entity;

import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "circular_versions")
public class CircularVersion {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "circular_id", nullable = false)
    private Circular circular;

    @Column(name = "version_number", nullable = false)
    private int versionNumber;

    @Column(nullable = false, length = 200)
    private String title;

    @Column(columnDefinition = "TEXT", nullable = false)
    private String description;

    @Column(name = "rules_and_regulations", columnDefinition = "TEXT")
    private String rulesAndRegulations;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "edited_by_user_id")
    private User editedBy;

    @Column(name = "edit_note", length = 500)
    private String editNote;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt = LocalDateTime.now();

    public CircularVersion() {}

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public Circular getCircular() { return circular; }
    public void setCircular(Circular circular) { this.circular = circular; }
    public int getVersionNumber() { return versionNumber; }
    public void setVersionNumber(int versionNumber) { this.versionNumber = versionNumber; }
    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }
    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }
    public String getRulesAndRegulations() { return rulesAndRegulations; }
    public void setRulesAndRegulations(String rulesAndRegulations) { this.rulesAndRegulations = rulesAndRegulations; }
    public User getEditedBy() { return editedBy; }
    public void setEditedBy(User editedBy) { this.editedBy = editedBy; }
    public String getEditNote() { return editNote; }
    public void setEditNote(String editNote) { this.editNote = editNote; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
}

