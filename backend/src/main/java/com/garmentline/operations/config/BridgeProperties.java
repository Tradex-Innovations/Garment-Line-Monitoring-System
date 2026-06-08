package com.garmentline.operations.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app.bridge")
public record BridgeProperties(String sharedToken) {
}
