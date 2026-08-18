package com.bluecollar.notification.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Component
@ConfigurationProperties(prefix = "bluecollar.notification")
@Getter
@Setter
public class NotificationProperties {

    private Email email = new Email();
    private Outbox outbox = new Outbox();

    @Getter
    @Setter
    public static class Email {
        private boolean enabled = true;
        private String from = "noreply@bluecollar.com";
    }

    @Getter
    @Setter
    public static class Outbox {
        private long pollIntervalMs = 30000;
        private int maxRetries = 3;
    }
}
