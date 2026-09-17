package com.garmentline.operations.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app.hikvision")
public record HikvisionProperties(
    String baseUrl,
    String cameraUrls,
    String username,
    String password,
    Integer pollIntervalSeconds,
    Integer lookbackMinutes,
    String bridgeToken
) {
}
