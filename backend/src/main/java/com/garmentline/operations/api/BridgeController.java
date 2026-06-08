package com.garmentline.operations.api;

import com.garmentline.operations.config.BridgeProperties;
import com.garmentline.operations.hikvision.HikvisionService;
import com.garmentline.operations.hikvision.model.HikvisionBridgeIngestResponse;
import com.garmentline.operations.hikvision.model.HikvisionBridgePushRequest;
import com.garmentline.operations.support.ApiException;
import com.garmentline.operations.zkteco.ZktecoAdmsService;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/bridge")
public class BridgeController {

  private final BridgeProperties bridgeProperties;
  private final HikvisionService hikvisionService;
  private final ZktecoAdmsService zktecoAdmsService;

  public BridgeController(
      BridgeProperties bridgeProperties,
      HikvisionService hikvisionService,
      ZktecoAdmsService zktecoAdmsService) {
    this.bridgeProperties = bridgeProperties;
    this.hikvisionService = hikvisionService;
    this.zktecoAdmsService = zktecoAdmsService;
  }

  @GetMapping("/health")
  public Map<String, Object> health(@RequestHeader(name = "X-Bridge-Token", required = false) String token) {
    validateToken(token);
    return Map.of("ok", true, "at", OffsetDateTime.now().toString());
  }

  @PostMapping("/hikvision/events")
  public Map<String, Object> hikvisionEvents(
      @RequestHeader(name = "X-Bridge-Token", required = false) String token,
      @RequestBody(required = false) List<Map<String, Object>> events) {
    validateToken(token);
    HikvisionBridgeIngestResponse response = hikvisionService.receiveBridgeEvents(token, hikvisionRequest(events));
    return Map.of("received", response.receivedEvents(), "accepted", response.acceptedEvents());
  }

  @PostMapping("/zkteco/punches")
  public Map<String, Object> zktecoPunches(
      @RequestHeader(name = "X-Bridge-Token", required = false) String token,
      @RequestBody(required = false) List<Map<String, Object>> punches) {
    validateToken(token);
    int received = punches == null ? 0 : punches.size();
    int accepted = zktecoAdmsService.receiveBridgePunches(punches);
    return Map.of("received", received, "accepted", accepted);
  }

  private HikvisionBridgePushRequest hikvisionRequest(List<Map<String, Object>> events) {
    Map<String, Object> first = events == null || events.isEmpty() ? Map.of() : events.get(0);
    String cameraBaseUrl = text(first, "cameraBaseUrl", "bridge");
    return new HikvisionBridgePushRequest(
        text(first, "cameraId", "hikvision-bridge"),
        text(first, "cameraName", cameraBaseUrl),
        text(first, "cameraLocation", null),
        cameraBaseUrl,
        OffsetDateTime.now(),
        null,
        events == null ? List.of() : events);
  }

  private void validateToken(String suppliedToken) {
    String expectedToken = bridgeProperties.sharedToken();
    if (expectedToken == null || expectedToken.isBlank()) {
      throw new ApiException(HttpStatus.UNAUTHORIZED, "Bridge token is not configured.");
    }
    if (!expectedToken.trim().equals(suppliedToken == null ? null : suppliedToken.trim())) {
      throw new ApiException(HttpStatus.UNAUTHORIZED, "Invalid bridge token.");
    }
  }

  private String text(Map<String, Object> source, String key, String fallback) {
    Object value = source.get(key);
    String text = value == null ? null : value.toString().trim();
    return text == null || text.isBlank() ? fallback : text;
  }
}
