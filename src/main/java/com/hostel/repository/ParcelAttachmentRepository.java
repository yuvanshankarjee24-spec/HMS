package com.hostel.repository;

import com.hostel.entity.ParcelAttachment;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface ParcelAttachmentRepository extends JpaRepository<ParcelAttachment, Long> {
    List<ParcelAttachment> findByParcelIdOrderByCreatedAtAsc(Long parcelId);
}

