package com.hostel.entity;

import jakarta.persistence.*;

@Entity
@Table(name = "circular_targets")
public class CircularTarget {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "circular_id", nullable = false)
    private Circular circular;

    @Column(name = "hostel_block", length = 100)
    private String hostelBlock;

    @Column(length = 100)
    private String department;

    @Column(name = "study_year")
    private Integer studyYear;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "student_id")
    private Student student;

    public CircularTarget() {}

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public Circular getCircular() { return circular; }
    public void setCircular(Circular circular) { this.circular = circular; }
    public String getHostelBlock() { return hostelBlock; }
    public void setHostelBlock(String hostelBlock) { this.hostelBlock = hostelBlock; }
    public String getDepartment() { return department; }
    public void setDepartment(String department) { this.department = department; }
    public Integer getStudyYear() { return studyYear; }
    public void setStudyYear(Integer studyYear) { this.studyYear = studyYear; }
    public Student getStudent() { return student; }
    public void setStudent(Student student) { this.student = student; }
}

