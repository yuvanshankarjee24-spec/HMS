package com.hostel.repository;

import com.hostel.entity.FileAttachment;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface FileAttachmentRepository extends JpaRepository<FileAttachment, Long> {
    List<FileAttachment> findByModuleAndEntityIdOrderByCreatedAtAsc(String module, Long entityId);
}

