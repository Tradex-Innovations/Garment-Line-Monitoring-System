package com.garmentline.operations.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app.zkteco")
public record ZktecoProperties(
    String commKey,
    String timeZone,
    Integer admsDelaySeconds,
    String serverVersion
) {
}
