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
        log(actorUser, inferModule(action), action, null, null, null, target, result);
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void log(User actorUser, String module, String action, Long referenceId,
                    String oldValue, String newValue, String target, String result) {
        try {
            User managedUser = null;
            if (actorUser != null && actorUser.getId() != null) {
                managedUser = entityManager.find(User.class, actorUser.getId());
            }
            AuditLog auditLog = new AuditLog(managedUser, truncate(action, 100), truncate(target, 255), truncate(result != null ? result : "SUCCESS", 50));
            auditLog.setModule(truncate(module != null ? module : inferModule(action), 50));
            auditLog.setReferenceId(referenceId);
            auditLog.setOldValue(truncate(oldValue, 500));
            auditLog.setNewValue(truncate(newValue, 500));
            auditLogRepository.save(auditLog);
        } catch (Exception e) {
            System.err.println("Failed to write audit log: " + e.getMessage());
        }
    }

    private String truncate(String val, int maxLen) {
        if (val == null) return null;
        return val.length() <= maxLen ? val : val.substring(0, maxLen);
    }

    private String inferModule(String action) {
        if (action == null) return "SYSTEM";
        String up = action.toUpperCase();
        if (up.contains("OUTPASS")) return "OUTPASS";
        if (up.contains("ATTENDANCE")) return "ATTENDANCE";
        if (up.contains("MAINTENANCE") || up.contains("TICKET")) return "MAINTENANCE";
        if (up.contains("PARCEL")) return "PARCELS";
        if (up.contains("CIRCULAR") || up.contains("EVENT")) return "CIRCULARS";
        if (up.contains("MESS") || up.contains("MENU") || up.contains("TOKEN")) return "MESS";
        if (up.contains("ROOM") || up.contains("BED") || up.contains("VACATE")) return "ROOMS";
        if (up.contains("LOGIN") || up.contains("PASSWORD")) return "AUTH";
        return "ADMIN";
    }
}
