package com.bluecollar.notification.repository;

import com.bluecollar.notification.entity.Notification;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

import java.time.Instant;
import java.util.UUID;

public interface NotificationRepository extends JpaRepository<Notification, UUID> {

    Page<Notification> findByRecipientUserIdAndActiveTrueOrderByCreatedAtDesc(
            UUID recipientUserId, Pageable pageable
    );

    long countByRecipientUserIdAndActiveTrueAndReadAtIsNull(UUID recipientUserId);

    @Modifying
    @Query("""
            UPDATE Notification n
            SET n.readAt = :readAt
            WHERE n.recipientUserId = :userId
              AND n.readAt IS NULL
              AND n.active = true
            """)
    int markAllRead(UUID userId, Instant readAt);
}
