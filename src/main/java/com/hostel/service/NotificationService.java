package com.hostel.service;

import com.hostel.dto.NotificationDtos.NotificationResponse;
import com.hostel.entity.Notification;
import com.hostel.entity.User;
import com.hostel.exception.CustomExceptions.ForbiddenException;
import com.hostel.exception.CustomExceptions.ResourceNotFoundException;
import com.hostel.repository.NotificationRepository;
import com.hostel.repository.UserRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class NotificationService {

    private final NotificationRepository notificationRepository;
    private final UserRepository userRepository;

    public NotificationService(NotificationRepository notificationRepository,
                               UserRepository userRepository) {
        this.notificationRepository = notificationRepository;
        this.userRepository = userRepository;
    }

    @Transactional
    public void notifyUser(User user, String title, String message, String referenceType, Long referenceId) {
        if (user == null) return;
        Notification n = new Notification();
        n.setUser(user);
        n.setTitle(title);
        n.setMessage(message);
        n.setReferenceType(referenceType);
        n.setType(resolveNotificationType(referenceType));
        n.setModule(resolveModule(referenceType));
        n.setLink(resolveLink(referenceType, user.getRole()));
        n.setReferenceId(referenceId);
        n.setRead(false);
        notificationRepository.save(n);
    }

    private String resolveNotificationType(String referenceType) {
        if (referenceType == null) return "INFO";
        return switch (referenceType.toUpperCase()) {
            case "OUTPASS_LOCK", "CIRCULAR_URGENT" -> "ALERT";
            case "PARCEL", "MESS_TOKEN" -> "REMINDER";
            default -> referenceType.toUpperCase();
        };
    }

    private String resolveModule(String referenceType) {
        if (referenceType == null) return "SYSTEM";
        String up = referenceType.toUpperCase();
        if (up.startsWith("OUTPASS")) return "OUTPASS";
        if (up.startsWith("ATTENDANCE")) return "ATTENDANCE";
        if (up.startsWith("MAINTENANCE")) return "MAINTENANCE";
        if (up.startsWith("PARCEL")) return "PARCELS";
        if (up.startsWith("CIRCULAR") || up.startsWith("EVENT")) return "CIRCULARS";
        if (up.startsWith("MESS")) return "MESS";
        return up;
    }

    private String resolveLink(String referenceType, String role) {
        String mod = resolveModule(referenceType);
        boolean isWarden = "WARDEN".equalsIgnoreCase(role) || "ADMIN".equalsIgnoreCase(role);
        return switch (mod) {
            case "OUTPASS" -> isWarden ? "/warden/outpasses.html" : "/student/outpasses.html";
            case "ATTENDANCE" -> isWarden ? "/warden/attendance" : "/student/attendance.html";
            case "MAINTENANCE" -> isWarden ? "/warden/maintenance.html?tab=requests" : "/student/maintenance.html?tab=list";
            case "PARCELS" -> isWarden ? "/warden/parcels.html?tab=register" : "/student/my-parcels.html";
            case "CIRCULARS" -> isWarden ? "/warden/circulars.html" : "/student/circulars.html";
            case "MESS" -> isWarden ? "/warden/mess.html?tab=tokens" : "/student/mess-tokens.html?tab=my-tokens";
            default -> isWarden ? "/warden/dashboard" : "/student/dashboard.html";
        };
    }

    @Transactional
    public void notifyRole(String role, String title, String message, String referenceType, Long referenceId) {
        List<User> recipients = userRepository.findByRoleAndStatus(role, User.Status.ACTIVE);
        for (User u : recipients) {
            notifyUser(u, title, message, referenceType, referenceId);
        }
    }

    public Page<NotificationResponse> getMyNotifications(User user, Pageable pageable) {
        return notificationRepository.findByUserIdOrderByCreatedAtDesc(user.getId(), pageable)
                .map(n -> new NotificationResponse(
                        n.getId(),
                        n.getTitle(),
                        n.getMessage(),
                        n.getReferenceType(),
                        n.getReferenceId(),
                        n.isRead(),
                        n.getCreatedAt()
                ));
    }

    public long getUnreadCount(User user) {
        return notificationRepository.countByUserIdAndReadFalse(user.getId());
    }

    @Transactional
    public void markAsRead(User user, Long notificationId) {
        Notification n = notificationRepository.findById(notificationId)
                .orElseThrow(() -> new ResourceNotFoundException("Notification not found"));
        if (!n.getUser().getId().equals(user.getId())) {
            throw new ForbiddenException("You cannot modify another user's notification");
        }
        if (!n.isRead()) {
            n.setRead(true);
            notificationRepository.save(n);
        }
    }

    @Transactional
    public void markAllAsRead(User user) {
        notificationRepository.markAllAsReadByUserId(user.getId());
    }
}

