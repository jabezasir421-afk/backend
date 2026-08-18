package com.bluecollar.review.entity;

import com.bluecollar.auth.entity.UserAccount;
import com.bluecollar.booking.entity.Booking;
import com.bluecollar.common.entity.BaseEntity;
import com.bluecollar.customer.entity.Customer;
import com.bluecollar.worker.entity.Worker;
import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;

@Entity
@Table(name = "review")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor(access = AccessLevel.PRIVATE)
public class Review extends BaseEntity {

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "booking_id", nullable = false, unique = true)
    private Booking booking;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "customer_id", nullable = false)
    private Customer customer;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "worker_id", nullable = false)
    private Worker worker;

    @Column(nullable = false)
    private Short rating;

    @Column(length = 1000)
    private String comment;

    @Column(nullable = false)
    private Boolean active;

    @Enumerated(EnumType.STRING)
    @Column(name = "moderation_status", nullable = false, length = 20)
    private ModerationStatus moderationStatus;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "moderated_by")
    private UserAccount moderatedBy;

    @Column(name = "moderated_at")
    private Instant moderatedAt;

    @Column(name = "moderation_notes", length = 500)
    private String moderationNotes;

    @PrePersist
    void prePersistReview() {
        active = active == null ? Boolean.TRUE : active;
        moderationStatus = moderationStatus == null ? ModerationStatus.APPROVED : moderationStatus;
    }
}
