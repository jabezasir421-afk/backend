package com.bluecollar.notification.repository;

import com.bluecollar.notification.entity.EmailOutbox;
import com.bluecollar.notification.entity.OutboxStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface EmailOutboxRepository extends JpaRepository<EmailOutbox, UUID> {

    List<EmailOutbox> findTop20ByStatusOrderByCreatedAtAsc(OutboxStatus status);
}
