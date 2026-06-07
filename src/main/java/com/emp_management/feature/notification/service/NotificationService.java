package com.emp_management.feature.notification.service;

import com.emp_management.feature.notification.dto.NotificationResponse;
import com.emp_management.feature.notification.entity.Notification;
import com.emp_management.feature.notification.repository.NotificationRepository;
import com.emp_management.infrastructure.messaging.EmailSender;
import com.emp_management.infrastructure.messaging.NotificationMessageBuilder;
import com.emp_management.shared.dto.EmailMessage;
import com.emp_management.shared.enums.Channel;
import com.emp_management.shared.enums.EventType;
import com.emp_management.shared.enums.NotificationStatus;
import com.emp_management.shared.exceptions.BadRequestException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class NotificationService {

    private final NotificationRepository notificationRepository;
    private final EmailSender emailSender;
    private final NotificationMessageBuilder notificationMessageBuilder;

    public NotificationService(NotificationRepository notificationRepository,
                               EmailSender emailSender,
                               NotificationMessageBuilder notificationMessageBuilder) {
        this.notificationRepository = notificationRepository;
        this.emailSender = emailSender;
        this.notificationMessageBuilder = notificationMessageBuilder;
    }

    // ==================== EXISTING METHOD (unchanged — used by other features) ====================

    public Notification createNotification(String userId,
                                           String fromEmail,
                                           String toEmail,
                                           EventType eventType,
                                           Channel channel,
                                           String context) {

        EmailMessage emailMessage = notificationMessageBuilder.buildmessage(eventType, context);

        Notification notification = new Notification();
        notification.setUserId(userId);
        notification.setEventType(eventType);
        notification.setChannel(channel);
        notification.setMessage(emailMessage.getBody());
        notification.setNotificationStatus(NotificationStatus.UNREAD);

        Notification saved = notificationRepository.save(notification);

        if (channel == Channel.EMAIL) {
            emailSender.sendEmail(
                    fromEmail,
                    toEmail,
                    emailMessage.getSubject(),
                    emailMessage.getBody()
            );
        }

        return saved;
    }

    // ==================== NEW OVERLOAD — bypasses the builder, uses message directly ====================

    public Notification createNotification(String userId,
                                           String fromEmail,
                                           String toEmail,
                                           EventType eventType,
                                           Channel channel,
                                           String subject,
                                           String messageBody) {

        Notification notification = new Notification();
        notification.setUserId(userId);
        notification.setEventType(eventType);
        notification.setChannel(channel);
        notification.setMessage(messageBody);
        notification.setNotificationStatus(NotificationStatus.UNREAD);

        Notification saved = notificationRepository.save(notification);

        if (channel == Channel.EMAIL) {
            emailSender.sendEmail(
                    fromEmail,
                    toEmail,
                    subject,
                    messageBody
            );
        }

        return saved;
    }

    // ==================== EXISTING METHODS (fixed to scope to IN_APP only) ====================

    // FIX: only IN_APP rows are shown in the bell / notifications page
    public Page<NotificationResponse> getNotifications(String userId, Pageable pageable) {
        Page<Notification> notifications = notificationRepository
                .findByUserIdAndChannelOrderByCreatedAtDesc(userId, Channel.IN_APP, pageable);
        return notifications.map(this::mapToResponse);
    }

    // FIX: unread badge count only counts IN_APP rows
    public Long getUnreadCount(String userId) {
        return notificationRepository.countByUserIdAndNotificationStatusAndChannel(
                userId, NotificationStatus.UNREAD, Channel.IN_APP);
    }

    @Transactional
    public void markAsRead(Long notificationId) {
        Notification notification = notificationRepository.findById(notificationId)
                .orElseThrow(() -> new BadRequestException(
                        "Notification not found with ID: " + notificationId));
        notification.setNotificationStatus(NotificationStatus.READ);
        notificationRepository.save(notification);
    }

    // FIX: mark-all-read scoped to IN_APP rows only (EMAIL rows don't need a read state)
    @Transactional
    public void markAllAsRead(String userId) {
        List<Notification> unreadNotifications = notificationRepository
                .findByUserIdAndNotificationStatusAndChannel(
                        userId, NotificationStatus.UNREAD, Channel.IN_APP);
        for (Notification notification : unreadNotifications) {
            notification.setNotificationStatus(NotificationStatus.READ);
        }
        notificationRepository.saveAll(unreadNotifications);
    }

    @Transactional
    public void deleteNotification(Long notificationId) {
        Notification notification = notificationRepository.findById(notificationId)
                .orElseThrow(() -> new BadRequestException(
                        "Notification not found with ID: " + notificationId));
        notificationRepository.delete(notification);
    }

    @Transactional
    public void clearReadNotifications(String userId) {
        // FIX: scoped to IN_APP — EMAIL rows are not managed via the UI
        List<Notification> readNotifications = notificationRepository
                .findByUserIdAndNotificationStatusAndChannel(
                        userId, NotificationStatus.READ, Channel.IN_APP);
        notificationRepository.deleteAll(readNotifications);
    }

    public NotificationResponse getNotification(Long notificationId) {
        Notification notification = notificationRepository.findById(notificationId)
                .orElseThrow(() -> new BadRequestException(
                        "Notification not found with ID: " + notificationId));
        return mapToResponse(notification);
    }

    private NotificationResponse mapToResponse(Notification notification) {
        NotificationResponse response = new NotificationResponse();
        response.setId(notification.getId());
        response.setUserId(notification.getUserId());
        response.setEventType(notification.getEventType());
        response.setMessage(notification.getMessage());
        response.setChannel(notification.getChannel());
        response.setCreatedAt(notification.getCreatedAt());
        response.setNotificationStatus(notification.getNotificationStatus());
        return response;
    }
}