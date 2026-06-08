package com.garmentline.operations.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app.employee-portal")
public record EmployeePortalProperties(
    String timeZone,
    long sessionHours,
    long otpMinutes,
    int otpMaxAttempts,
    boolean exposeDevelopmentOtp,
    long kioskSessionMinutes,
    long kioskRecognitionWindowSeconds
) {
  public String resolvedTimeZone() {
    return timeZone == null || timeZone.isBlank() ? "Asia/Colombo" : timeZone;
  }

  public long resolvedSessionHours() {
    return sessionHours <= 0 ? 168 : sessionHours;
  }

  public long resolvedOtpMinutes() {
    return otpMinutes <= 0 ? 10 : otpMinutes;
  }

  public int resolvedOtpMaxAttempts() {
    return otpMaxAttempts <= 0 ? 5 : otpMaxAttempts;
  }

  public long resolvedKioskSessionMinutes() {
    return kioskSessionMinutes <= 0 ? 3 : kioskSessionMinutes;
  }

  public long resolvedKioskRecognitionWindowSeconds() {
    return kioskRecognitionWindowSeconds <= 0 ? 45 : kioskRecognitionWindowSeconds;
  }
}
