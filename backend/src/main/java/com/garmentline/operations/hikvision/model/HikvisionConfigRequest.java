package com.garmentline.operations.hikvision.model;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;

public record HikvisionConfigRequest(
    String baseUrl,
    @NotBlank String username,
    String password,
    @Min(1) @Max(60) Integer pollIntervalSeconds,
    @Min(1) @Max(1440) Integer lookbackMinutes
) {
}
