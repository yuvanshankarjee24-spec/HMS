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
        n.setReferenceId(referenceId);
        n.setRead(false);
        notificationRepository.save(n);
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

