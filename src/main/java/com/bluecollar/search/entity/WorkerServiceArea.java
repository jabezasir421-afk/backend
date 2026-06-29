package com.bluecollar.search.entity;

import com.bluecollar.common.entity.BaseEntity;
import com.bluecollar.worker.entity.Worker;
import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "worker_service_area")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor(access = AccessLevel.PRIVATE)
public class WorkerServiceArea extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "worker_id", nullable = false)
    private Worker worker;

    @Column(nullable = false, length = 100)
    private String city;

    @Column(nullable = false, length = 100)
    private String state;

    @Column(nullable = false)
    private Boolean active;

    @PrePersist
    protected void prePersistServiceArea() {
        active = active == null ? Boolean.TRUE : active;
    }
}
