package com.bluecollar.analytics.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Component
@ConfigurationProperties(prefix = "bluecollar.analytics")
@Getter
@Setter
public class AnalyticsProperties {
    private String snapshotCron = "0 0 2 * * *";
}
