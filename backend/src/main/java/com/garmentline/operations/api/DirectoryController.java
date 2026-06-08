package com.garmentline.operations.api;

import com.garmentline.operations.security.AuthenticatedUser;
import com.garmentline.operations.security.UserContextService;
import com.garmentline.operations.service.DirectoryService;
import java.util.List;
import java.util.Map;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/app-users")
public class DirectoryController {

  private final DirectoryService directoryService;
  private final UserContextService userContextService;

  public DirectoryController(
      DirectoryService directoryService, UserContextService userContextService) {
    this.directoryService = directoryService;
    this.userContextService = userContextService;
  }

  @GetMapping
  public List<Map<String, Object>> listActiveAppUsers(@AuthenticationPrincipal Jwt jwt) {
    AuthenticatedUser user = userContextService.loadCurrentUser(jwt);
    return directoryService.listActiveAppUsers(user);
  }
}
