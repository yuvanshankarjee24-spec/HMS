package com.hostel.repository;

import com.hostel.entity.RequestAttachment;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface RequestAttachmentRepository extends JpaRepository<RequestAttachment, Long> {
    List<RequestAttachment> findByRequestIdOrderByCreatedAtAsc(Long requestId);
}

