package com.hostel.repository;

import com.hostel.entity.CircularAttachment;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface CircularAttachmentRepository extends JpaRepository<CircularAttachment, Long> {
    List<CircularAttachment> findByCircularIdOrderByCreatedAtAsc(Long circularId);
}

