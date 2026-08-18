package com.bluecollar.analytics.entity;

import com.bluecollar.category.entity.Category;
import com.bluecollar.common.entity.BaseEntity;
import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDate;

@Entity
@Table(name = "analytics_category_ranking")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor(access = AccessLevel.PRIVATE)
public class AnalyticsCategoryRanking extends BaseEntity {

    @Column(name = "snapshot_date", nullable = false)
    private LocalDate snapshotDate;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "category_id", nullable = false)
    private Category category;

    @Column(name = "booking_count", nullable = false)
    private Integer bookingCount;

    @Column(nullable = false)
    private Short rank;
}
