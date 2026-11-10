package com.hostel.repository;

import com.hostel.entity.CircularVersion;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface CircularVersionRepository extends JpaRepository<CircularVersion, Long> {
    List<CircularVersion> findByCircularIdOrderByVersionNumberDesc(Long circularId);
}

