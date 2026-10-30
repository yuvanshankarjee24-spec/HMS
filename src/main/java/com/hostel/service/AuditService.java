package com.hostel.service;

import com.hostel.entity.AuditLog;
import com.hostel.entity.User;
import com.hostel.repository.AuditLogRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AuditService {

    private final AuditLogRepository auditLogRepository;

    @jakarta.persistence.PersistenceContext
    private jakarta.persistence.EntityManager entityManager;

    public AuditService(AuditLogRepository auditLogRepository) {
        this.auditLogRepository = auditLogRepository;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void log(User actorUser, String action, String target, String result) {
        try {
            User managedUser = null;
            if (actorUser != null && actorUser.getId() != null) {
                managedUser = entityManager.find(User.class, actorUser.getId());
            }
            AuditLog auditLog = new AuditLog(managedUser, action, target, result);
            auditLogRepository.save(auditLog);
        } catch (Exception e) {
            System.err.println("Failed to write audit log: " + e.getMessage());
        }
    }
}
