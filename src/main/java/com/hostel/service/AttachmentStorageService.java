package com.hostel.service;

import com.hostel.entity.FileAttachment;
import com.hostel.entity.User;
import com.hostel.exception.CustomExceptions.BadRequestException;
import com.hostel.exception.CustomExceptions.ResourceNotFoundException;
import com.hostel.repository.FileAttachmentRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.core.io.UrlResource;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.net.MalformedURLException;
import java.nio.file.*;
import java.util.*;

/**
 * Shared file validation, disk storage, Resource loading, and unified file_attachments registry.
 */
@Service
public class AttachmentStorageService {

    public static final Set<String> IMAGE_TYPES = Set.of("image/jpeg", "image/jpg", "image/png", "image/webp");
    public static final Set<String> DOC_AND_IMAGE_TYPES = Set.of("application/pdf", "image/jpeg", "image/jpg", "image/png", "image/webp");

    private final FileAttachmentRepository fileAttachmentRepository;

    @Value("${app.upload.dir:./storage/photos}")
    private String uploadDir;

    public AttachmentStorageService(FileAttachmentRepository fileAttachmentRepository) {
        this.fileAttachmentRepository = fileAttachmentRepository;
    }

    public List<MultipartFile> validateFiles(MultipartFile[] files, int maxFiles, Set<String> allowedTypes, long maxBytes) {
        if (files == null || files.length == 0) return List.of();
        List<MultipartFile> valid = new ArrayList<>();
        for (MultipartFile f : files) {
            if (f != null && !f.isEmpty()) valid.add(f);
        }
        if (valid.size() > maxFiles) {
            throw new BadRequestException("Maximum " + maxFiles + " attachment(s) allowed");
        }
        for (MultipartFile f : valid) {
            String ct = f.getContentType();
            if (ct == null || !allowedTypes.contains(ct.toLowerCase())) {
                throw new BadRequestException("Unsupported file type: " + ct);
            }
            if (f.getSize() > maxBytes) {
                throw new BadRequestException("File size exceeds limit (" + (maxBytes / (1024 * 1024)) + " MB): " + f.getOriginalFilename());
            }
        }
        return valid;
    }

    public String storeFile(MultipartFile file, String subDir, String prefixName) {
        try {
            Path dir = Paths.get(uploadDir, subDir).toAbsolutePath().normalize();
            Files.createDirectories(dir);
            String ct = file.getContentType() != null ? file.getContentType().toLowerCase() : "application/octet-stream";
            String ext = ct.contains("pdf") ? ".pdf" : ct.contains("png") ? ".png" : ct.contains("webp") ? ".webp" : ".jpg";
            String safeName = prefixName + "_" + System.currentTimeMillis() + "_" + UUID.randomUUID().toString().substring(0, 6) + ext;
            Path target = dir.resolve(safeName);
            Files.copy(file.getInputStream(), target, StandardCopyOption.REPLACE_EXISTING);
            return target.toString();
        } catch (IOException e) {
            throw new RuntimeException("Failed to store file: " + e.getMessage(), e);
        }
    }

    @Transactional
    public FileAttachment registerAttachment(String module, Long entityId, String storedPath, MultipartFile file, String attachmentType, User uploadedBy) {
        FileAttachment fa = new FileAttachment();
        fa.setModule(module);
        fa.setEntityId(entityId);
        fa.setFilePath(storedPath);
        fa.setOriginalFilename(file.getOriginalFilename() != null ? file.getOriginalFilename() : "attachment");
        fa.setContentType(file.getContentType() != null ? file.getContentType().toLowerCase() : "application/octet-stream");
        fa.setFileSize(file.getSize());
        fa.setAttachmentType(attachmentType);
        fa.setUploadedBy(uploadedBy);
        return fileAttachmentRepository.save(fa);
    }

    public Resource loadResource(String filePath) {
        try {
            Path p = Paths.get(filePath).normalize();
            Resource r = new UrlResource(p.toUri());
            if (!r.exists() || !r.isReadable()) {
                throw new ResourceNotFoundException("Attachment file not found");
            }
            return r;
        } catch (MalformedURLException e) {
            throw new ResourceNotFoundException("Invalid attachment path");
        }
    }
}

