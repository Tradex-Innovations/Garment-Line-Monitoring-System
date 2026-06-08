package com.garmentline.operations.api;

import com.fasterxml.jackson.databind.node.ArrayNode;
import com.garmentline.operations.security.AuthenticatedUser;
import com.garmentline.operations.security.UserContextService;
import com.garmentline.operations.zkteco.ZktecoAdmsService;
import com.garmentline.operations.zkteco.model.ZktecoStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/zkteco")
public class ZktecoController {

  private final ZktecoAdmsService zktecoAdmsService;
  private final UserContextService userContextService;

  public ZktecoController(
      ZktecoAdmsService zktecoAdmsService, UserContextService userContextService) {
    this.zktecoAdmsService = zktecoAdmsService;
    this.userContextService = userContextService;
  }

  @GetMapping("/status")
  public ZktecoStatus status(@AuthenticationPrincipal Jwt jwt) {
    AuthenticatedUser user = userContextService.loadCurrentUser(jwt);
    return zktecoAdmsService.status(user);
  }

  @GetMapping("/events")
  public ArrayNode events(
      @AuthenticationPrincipal Jwt jwt,
      @RequestParam(name = "limit", defaultValue = "80") int limit) {
    AuthenticatedUser user = userContextService.loadCurrentUser(jwt);
    return zktecoAdmsService.listEvents(user, limit);
  }
}
