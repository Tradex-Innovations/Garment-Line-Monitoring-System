package com.garmentline.operations.hikvision.model;

public record HikvisionCameraConfig(
    String baseUrl,
    String username,
    String password,
    int pollIntervalSeconds,
    int lookbackMinutes
) {
  public HikvisionCameraConfig sanitized() {
    return new HikvisionCameraConfig(baseUrl, username, null, pollIntervalSeconds, lookbackMinutes);
  }
}
