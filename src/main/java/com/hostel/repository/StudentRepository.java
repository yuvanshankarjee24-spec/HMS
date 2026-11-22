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

    @Query("SELECT COUNT(s) FROM Student s WHERE s.active = true AND s.deletedAt IS NULL AND s.room IS NULL")
    long countUnallocatedStudents();

    @Query("SELECT s FROM Student s WHERE s.active = true AND s.deletedAt IS NULL AND (" +
           ":query IS NULL OR LOWER(s.fullName) LIKE LOWER(CONCAT('%', :query, '%')) OR " +
           "LOWER(s.registerNumber) LIKE LOWER(CONCAT('%', :query, '%')) OR " +
           "LOWER(s.department) LIKE LOWER(CONCAT('%', :query, '%')))")
    Page<Student> searchStudents(@Param("query") String query, Pageable pageable);

    @Query("SELECT s FROM Student s WHERE s.active = true AND s.deletedAt IS NULL " +
           "AND (:unallocatedOnly = false OR s.room IS NULL) " +
           "AND (:query IS NULL OR :query = '' " +
           "     OR LOWER(s.fullName) LIKE LOWER(CONCAT('%', :query, '%')) " +
           "     OR LOWER(s.registerNumber) LIKE LOWER(CONCAT('%', :query, '%')) " +
           "     OR LOWER(s.department) LIKE LOWER(CONCAT('%', :query, '%')))")
    Page<Student> searchStudentsForAllocation(@Param("query") String query,
                                              @Param("unallocatedOnly") boolean unallocatedOnly,
                                              Pageable pageable);

    boolean existsByRegisterNumber(String registerNumber);
    boolean existsByCollegeEmail(String collegeEmail);
}
