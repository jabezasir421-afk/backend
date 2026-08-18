package com.bluecollar.common.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Configuration
@ConfigurationProperties(prefix = "bluecollar.encryption")
@Getter
@Setter
public class EncryptionProperties {

    private String documentKey = "dev-default-32-byte-key-change!!";
}
