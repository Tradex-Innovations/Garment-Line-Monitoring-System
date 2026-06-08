package com.garmentline.operations.api;

import com.garmentline.operations.security.AuthenticatedUser;
import com.garmentline.operations.security.UserContextService;
import com.garmentline.operations.service.LeaveManagementService;
import com.garmentline.operations.service.LeaveManagementService.LeaveRequest;
import com.garmentline.operations.service.LeaveManagementService.LeaveReviewRequest;
import jakarta.validation.Valid;
import java.util.Map;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/leave-management")
@Validated
public class LeaveManagementController {

  private final LeaveManagementService leaveManagementService;
  private final UserContextService userContextService;

  public LeaveManagementController(
      LeaveManagementService leaveManagementService, UserContextService userContextService) {
    this.leaveManagementService = leaveManagementService;
    this.userContextService = userContextService;
  }

  @GetMapping
  public Map<String, Object> getLeaveManagement(
      @AuthenticationPrincipal Jwt jwt,
      @RequestParam(required = false) String status,
      @RequestParam(required = false) String employeeId,
      @RequestParam(required = false) String dateFrom,
      @RequestParam(required = false) String dateTo) {
    AuthenticatedUser user = userContextService.loadCurrentUser(jwt);
    return leaveManagementService.getLeaveManagement(user, status, employeeId, dateFrom, dateTo);
  }

  @PostMapping("/requests")
  public Map<String, Object> createLeaveRequest(
      @AuthenticationPrincipal Jwt jwt, @Valid @RequestBody LeaveRequest request) {
    AuthenticatedUser user = userContextService.loadCurrentUser(jwt);
    return leaveManagementService.createLeaveRequest(user, request);
  }

  @PostMapping("/requests/{id}/review")
  public Map<String, Object> reviewLeaveRequest(
      @AuthenticationPrincipal Jwt jwt,
      @PathVariable String id,
      @Valid @RequestBody LeaveReviewRequest request) {
    AuthenticatedUser user = userContextService.loadCurrentUser(jwt);
    return leaveManagementService.reviewLeaveRequest(user, id, request);
  }
}
