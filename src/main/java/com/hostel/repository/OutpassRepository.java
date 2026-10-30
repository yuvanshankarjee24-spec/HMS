package com.hostel.repository;

import com.hostel.entity.Outpass;
import com.hostel.entity.Student;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface OutpassRepository extends JpaRepository<Outpass, Long> {
    List<Outpass> findAllByOrderByCreatedAtDesc();
    List<Outpass> findByStatusOrderByCreatedAtDesc(Outpass.Status status);
    List<Outpass> findByStudentOrderByCreatedAtDesc(Student student);
    long countByStatus(Outpass.Status status);
}
