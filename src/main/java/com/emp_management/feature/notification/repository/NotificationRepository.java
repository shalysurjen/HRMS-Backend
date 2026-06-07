package com.emp_management.feature.notification.repository;

import com.emp_management.feature.notification.entity.Notification;
import com.emp_management.shared.enums.Channel;
import com.emp_management.shared.enums.NotificationStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface NotificationRepository extends JpaRepository<Notification, Long> {

    // FIX: filter by channel so EMAIL rows never appear in the bell/notification page
    Page<Notification> findByUserIdAndChannelOrderByCreatedAtDesc(
            String userId, Channel channel, Pageable pageable);

    // FIX: unread count only for IN_APP channel
    Long countByUserIdAndNotificationStatusAndChannel(
            String userId, NotificationStatus status, Channel channel);

    // FIX: bulk status ops scoped to IN_APP only
    List<Notification> findByUserIdAndNotificationStatusAndChannel(
            String userId, NotificationStatus status, Channel channel);
}