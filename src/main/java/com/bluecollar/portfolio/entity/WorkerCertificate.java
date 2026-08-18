package com.bluecollar.portfolio.entity;

import com.bluecollar.common.entity.BaseEntity;
import com.bluecollar.worker.entity.Worker;
import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDate;
import java.util.UUID;

@Entity
@Table(name = "worker_certificate")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor(access = AccessLevel.PRIVATE)
public class WorkerCertificate extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "worker_id", nullable = false)
    private Worker worker;

    @Column(name = "file_id", nullable = false)
    private UUID fileId;

    @Column(nullable = false, length = 200)
    private String title;

    @Column(name = "issuing_org", length = 200)
    private String issuingOrg;

    @Column(name = "issue_date")
    private LocalDate issueDate;

    @Column(name = "expiry_date")
    private LocalDate expiryDate;

    @Enumerated(EnumType.STRING)
    @Column(name = "verification_status", nullable = false, length = 20)
    @Builder.Default
    private VerificationStatus verificationStatus = VerificationStatus.PENDING;

    @Column(nullable = false)
    @Builder.Default
    private Boolean active = true;

    @PrePersist
    void prePersistCertificate() {
        verificationStatus = verificationStatus == null ? VerificationStatus.PENDING : verificationStatus;
        active = active == null ? Boolean.TRUE : active;
    }
}
