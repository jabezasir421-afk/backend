package com.bluecollar.storage.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Configuration
@ConfigurationProperties(prefix = "bluecollar.storage")
@Getter
@Setter
public class StorageProperties {

    private String type = "local";
    private Local local = new Local();
    private long presignedUrlExpirySeconds = 3600;

    @Getter
    @Setter
    public static class Local {

        private String basePath = "/var/bluecollar/files";
    }
}
