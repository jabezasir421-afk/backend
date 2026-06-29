package com.bluecollar.analytics.job;

import com.bluecollar.analytics.service.AnalyticsService;
import lombok.RequiredArgsConstructor;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.ZoneOffset;

@Component
@RequiredArgsConstructor
public class AnalyticsSnapshotJob {

    private final AnalyticsService analyticsService;

    @Scheduled(cron = "${bluecollar.analytics.snapshot-cron:0 0 2 * * *}")
    public void runDailySnapshot() {
        analyticsService.refreshSnapshot(LocalDate.now(ZoneOffset.UTC).minusDays(1));
    }
}
