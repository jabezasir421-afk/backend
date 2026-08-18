package com.bluecollar.auth.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Configuration
@ConfigurationProperties(prefix = "bluecollar.admin")
@Getter
@Setter
public class AdminBootstrapProperties {

    private String email;
    private String password;
    private String phone;
}
