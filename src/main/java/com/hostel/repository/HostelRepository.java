package com.hostel.repository;

import com.hostel.entity.Hostel;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface HostelRepository extends JpaRepository<Hostel, Long> {
    Optional<Hostel> findByNameIgnoreCase(String name);
    Optional<Hostel> findByCodeIgnoreCase(String code);
}

