package com.hostel.repository;

import com.hostel.entity.Student;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface StudentRepository extends JpaRepository<Student, Long> {
    Optional<Student> findByUserId(Long userId);
    Optional<Student> findByRegisterNumber(String registerNumber);
    Optional<Student> findByCollegeEmail(String collegeEmail);

    @Query("SELECT s FROM Student s WHERE " +
           "(:query IS NULL OR LOWER(s.fullName) LIKE LOWER(CONCAT('%', :query, '%')) OR " +
           "LOWER(s.registerNumber) LIKE LOWER(CONCAT('%', :query, '%')) OR " +
           "LOWER(s.department) LIKE LOWER(CONCAT('%', :query, '%')))")
    Page<Student> searchStudents(@Param("query") String query, Pageable pageable);

    boolean existsByRegisterNumber(String registerNumber);
    boolean existsByCollegeEmail(String collegeEmail);
}
