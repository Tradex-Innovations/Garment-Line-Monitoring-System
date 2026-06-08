package com.garmentline.operations.api;

import com.garmentline.operations.hikvision.HikvisionService;
import com.garmentline.operations.hikvision.model.HikvisionBridgeIngestResponse;
import com.garmentline.operations.hikvision.model.HikvisionBridgePushRequest;
import com.garmentline.operations.hikvision.model.HikvisionConfigRequest;
import com.garmentline.operations.hikvision.model.HikvisionEventListResponse;
import com.garmentline.operations.hikvision.model.HikvisionStatus;
import com.garmentline.operations.security.AuthenticatedUser;
import com.garmentline.operations.security.UserContextService;
import jakarta.validation.Valid;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/hikvision")
@Validated
public class HikvisionController {

  private final HikvisionService hikvisionService;
  private final UserContextService userContextService;

  public HikvisionController(
      HikvisionService hikvisionService, UserContextService userContextService) {
    this.hikvisionService = hikvisionService;
    this.userContextService = userContextService;
  }

  @GetMapping("/status")
  public HikvisionStatus status(@AuthenticationPrincipal Jwt jwt) {
    AuthenticatedUser user = userContextService.loadCurrentUser(jwt);
    return hikvisionService.getStatus(user);
  }

  @PostMapping("/config")
  public HikvisionStatus configure(
      @AuthenticationPrincipal Jwt jwt, @Valid @RequestBody HikvisionConfigRequest request) {
    AuthenticatedUser user = userContextService.loadCurrentUser(jwt);
    return hikvisionService.configure(user, request);
  }

  @PostMapping("/test")
  public HikvisionStatus test(@AuthenticationPrincipal Jwt jwt) {
    AuthenticatedUser user = userContextService.loadCurrentUser(jwt);
    return hikvisionService.testConnection(user);
  }

  @PostMapping("/start")
  public HikvisionStatus start(@AuthenticationPrincipal Jwt jwt) {
    AuthenticatedUser user = userContextService.loadCurrentUser(jwt);
    return hikvisionService.startPolling(user);
  }

  @PostMapping("/stop")
  public HikvisionStatus stop(@AuthenticationPrincipal Jwt jwt) {
    AuthenticatedUser user = userContextService.loadCurrentUser(jwt);
    return hikvisionService.stopPolling(user);
  }

  @PostMapping("/poll")
  public HikvisionEventListResponse poll(@AuthenticationPrincipal Jwt jwt) {
    AuthenticatedUser user = userContextService.loadCurrentUser(jwt);
    return hikvisionService.pollNow(user);
  }

  @GetMapping("/events")
  public HikvisionEventListResponse events(
      @AuthenticationPrincipal Jwt jwt,
      @RequestParam(name = "limit", defaultValue = "80") int limit) {
    AuthenticatedUser user = userContextService.loadCurrentUser(jwt);
    return hikvisionService.listEvents(user, limit);
  }

  @PostMapping("/bridge/events")
  public HikvisionBridgeIngestResponse bridgeEvents(
      @RequestHeader(name = "X-Hikvision-Bridge-Token", required = false) String bridgeToken,
      @Valid @RequestBody HikvisionBridgePushRequest request) {
    return hikvisionService.receiveBridgeEvents(bridgeToken, request);
  }
}
