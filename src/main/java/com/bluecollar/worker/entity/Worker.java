package com.bluecollar.worker.entity;

import com.bluecollar.auth.entity.UserAccount;
import com.bluecollar.category.entity.Category;
import com.bluecollar.common.entity.BaseEntity;
import com.bluecollar.skill.entity.Skill;
import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.UUID;

@Entity
@Table(name = "worker")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor(access = AccessLevel.PRIVATE)
public class Worker extends BaseEntity {

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_account_id", unique = true)
    private UserAccount userAccount;

    @Column(name = "first_name", nullable = false, length = 100)
    private String firstName;

    @Column(name = "last_name", nullable = false, length = 100)
    private String lastName;

    @Column(name = "phone_number", nullable = false, unique = true, length = 30)
    private String phoneNumber;

    @Column(unique = true, length = 255)
    private String email;

    @Enumerated(EnumType.STRING)
    @Column(length = 30)
    private Gender gender;

    @Column(name = "date_of_birth")
    private LocalDate dateOfBirth;

    @Column(name = "experience_years")
    private Integer experienceYears;

    @Column(length = 1000)
    private String bio;

    @Column(name = "hourly_rate", precision = 10, scale = 2)
    private BigDecimal hourlyRate;

    @Column(nullable = false)
    private Boolean available;

    @Column(nullable = false)
    private Boolean verified;

    @Column(nullable = false)
    private Boolean active;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "category_id", nullable = false)
    private Category category;

    @Builder.Default
    @ManyToMany
    @JoinTable(
            name = "worker_skill",
            joinColumns = @JoinColumn(name = "worker_id"),
            inverseJoinColumns = @JoinColumn(name = "skill_id")
    )
    private Set<Skill> skills = new LinkedHashSet<>();

    @Column(name = "average_rating", nullable = false, precision = 3, scale = 2)
    @Builder.Default
    private BigDecimal averageRating = BigDecimal.ZERO;

    @Column(name = "review_count", nullable = false)
    @Builder.Default
    private Integer reviewCount = 0;

    @Column(name = "primary_city", length = 100)
    private String primaryCity;

    @Column(name = "primary_state", length = 100)
    private String primaryState;

    @Column(name = "profile_photo_file_id")
    private UUID profilePhotoFileId;

    @Column(name = "profile_completion_percent", nullable = false)
    @Builder.Default
    private Short profileCompletionPercent = 0;

    @Enumerated(EnumType.STRING)
    @Column(name = "online_status", nullable = false, length = 20)
    @Builder.Default
    private OnlineStatus onlineStatus = OnlineStatus.OFFLINE;

    @Column(name = "last_seen_at")
    private Instant lastSeenAt;

    @Column(name = "vacation_mode", nullable = false)
    @Builder.Default
    private Boolean vacationMode = false;

    @Column(name = "vacation_start")
    private LocalDate vacationStart;

    @Column(name = "vacation_end")
    private LocalDate vacationEnd;

    @PrePersist
    void prePersistWorker() {
        available = available == null ? Boolean.TRUE : available;
        verified = verified == null ? Boolean.FALSE : verified;
        active = active == null ? Boolean.TRUE : active;

        averageRating = averageRating == null ? BigDecimal.ZERO : averageRating;

        reviewCount = reviewCount == null
                ? Integer.valueOf(0)
                : reviewCount;

        profileCompletionPercent = profileCompletionPercent == null
                ? Short.valueOf((short) 0)
                : profileCompletionPercent;

        onlineStatus = onlineStatus == null
                ? OnlineStatus.OFFLINE
                : onlineStatus;

        vacationMode = vacationMode == null
                ? Boolean.FALSE
                : vacationMode;
    }
}
