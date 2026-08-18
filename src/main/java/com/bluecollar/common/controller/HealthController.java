package com.bluecollar.common.controller;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/api/v1/health")
public class HealthController {

    @Value("${spring.application.name:bluecollar-backend}")
    private String applicationName;

    @GetMapping
    public Map<String, String> health() {
        return Map.of("status", "UP", "service", applicationName);
    }
}
