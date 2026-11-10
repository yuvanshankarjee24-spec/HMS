package com.hostel.repository;

import com.hostel.entity.User;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface UserRepository extends JpaRepository<User, Long> {
    Optional<User> findByEmail(String email);
    Optional<User> findByRegisterNumber(String registerNumber);

    @Query("SELECT u FROM User u WHERE u.email = :identifier OR (u.registerNumber IS NOT NULL AND u.registerNumber = :identifier)")
    Optional<User> findByIdentifier(@Param("identifier") String identifier);

    @Query("SELECT u FROM User u WHERE (:role IS NULL OR u.role = :role) AND " +
           "(:query IS NULL OR LOWER(u.email) LIKE LOWER(CONCAT('%', :query, '%')) OR " +
           "(u.registerNumber IS NOT NULL AND LOWER(u.registerNumber) LIKE LOWER(CONCAT('%', :query, '%'))))")
    Page<User> searchUsers(@Param("role") String role, @Param("query") String query, Pageable pageable);

    boolean existsByEmail(String email);
    boolean existsByRegisterNumber(String registerNumber);
    java.util.List<User> findByRoleAndStatus(String role, User.Status status);
}
