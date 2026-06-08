package com.garmentline.operations.api;

import com.garmentline.operations.security.AuthenticatedUser;
import com.garmentline.operations.security.UserContextService;
import com.garmentline.operations.service.SkillMatrixService;
import com.garmentline.operations.service.SkillMatrixService.EmployeeSkillRequest;
import com.garmentline.operations.service.SkillMatrixService.LineOperationRequest;
import com.garmentline.operations.service.SkillMatrixService.LinePositionAssignmentRequest;
import com.garmentline.operations.service.SkillMatrixService.LineStyleScheduleRequest;
import com.garmentline.operations.service.SkillMatrixService.OperationRequest;
import com.garmentline.operations.service.SkillMatrixService.StylePlanMachineRequest;
import com.garmentline.operations.service.SkillMatrixService.StylePlanRequest;
import jakarta.validation.Valid;
import java.util.Map;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/skill-matrix")
@Validated
public class SkillMatrixController {

  private final SkillMatrixService skillMatrixService;
  private final UserContextService userContextService;

  public SkillMatrixController(
      SkillMatrixService skillMatrixService, UserContextService userContextService) {
    this.skillMatrixService = skillMatrixService;
    this.userContextService = userContextService;
  }

  @GetMapping
  public Map<String, Object> getMatrix(@AuthenticationPrincipal Jwt jwt) {
    AuthenticatedUser user = userContextService.loadCurrentUser(jwt);
    return skillMatrixService.getMatrix(user);
  }

  @PostMapping("/operations")
  public Map<String, Object> saveOperation(
      @AuthenticationPrincipal Jwt jwt, @Valid @RequestBody OperationRequest request) {
    AuthenticatedUser user = userContextService.loadCurrentUser(jwt);
    return skillMatrixService.saveOperation(user, request);
  }

  @PostMapping("/line-operations")
  public Map<String, Object> saveLineOperation(
      @AuthenticationPrincipal Jwt jwt, @Valid @RequestBody LineOperationRequest request) {
    AuthenticatedUser user = userContextService.loadCurrentUser(jwt);
    return skillMatrixService.saveLineOperation(user, request);
  }

  @DeleteMapping("/line-operations/{id}")
  public Map<String, Object> deleteLineOperation(
      @AuthenticationPrincipal Jwt jwt, @PathVariable String id) {
    AuthenticatedUser user = userContextService.loadCurrentUser(jwt);
    return skillMatrixService.deleteLineOperation(user, id);
  }

  @PostMapping("/line-position-assignments")
  public Map<String, Object> saveLinePositionAssignment(
      @AuthenticationPrincipal Jwt jwt, @Valid @RequestBody LinePositionAssignmentRequest request) {
    AuthenticatedUser user = userContextService.loadCurrentUser(jwt);
    return skillMatrixService.saveLinePositionAssignment(user, request);
  }

  @DeleteMapping("/line-position-assignments/{id}")
  public Map<String, Object> deleteLinePositionAssignment(
      @AuthenticationPrincipal Jwt jwt, @PathVariable String id) {
    AuthenticatedUser user = userContextService.loadCurrentUser(jwt);
    return skillMatrixService.deleteLinePositionAssignment(user, id);
  }

  @PostMapping("/style-plans")
  public Map<String, Object> saveStylePlan(
      @AuthenticationPrincipal Jwt jwt, @Valid @RequestBody StylePlanRequest request) {
    AuthenticatedUser user = userContextService.loadCurrentUser(jwt);
    return skillMatrixService.saveStylePlan(user, request);
  }

  @DeleteMapping("/style-plans/{id}")
  public Map<String, Object> deleteStylePlan(
      @AuthenticationPrincipal Jwt jwt, @PathVariable String id) {
    AuthenticatedUser user = userContextService.loadCurrentUser(jwt);
    return skillMatrixService.deleteStylePlan(user, id);
  }

  @PostMapping("/style-plan-machines")
  public Map<String, Object> saveStylePlanMachine(
      @AuthenticationPrincipal Jwt jwt, @Valid @RequestBody StylePlanMachineRequest request) {
    AuthenticatedUser user = userContextService.loadCurrentUser(jwt);
    return skillMatrixService.saveStylePlanMachine(user, request);
  }

  @DeleteMapping("/style-plan-machines/{id}")
  public Map<String, Object> deleteStylePlanMachine(
      @AuthenticationPrincipal Jwt jwt, @PathVariable String id) {
    AuthenticatedUser user = userContextService.loadCurrentUser(jwt);
    return skillMatrixService.deleteStylePlanMachine(user, id);
  }

  @PostMapping("/line-style-schedules")
  public Map<String, Object> saveLineStyleSchedule(
      @AuthenticationPrincipal Jwt jwt, @Valid @RequestBody LineStyleScheduleRequest request) {
    AuthenticatedUser user = userContextService.loadCurrentUser(jwt);
    return skillMatrixService.saveLineStyleSchedule(user, request);
  }

  @DeleteMapping("/line-style-schedules/{id}")
  public Map<String, Object> cancelLineStyleSchedule(
      @AuthenticationPrincipal Jwt jwt, @PathVariable String id) {
    AuthenticatedUser user = userContextService.loadCurrentUser(jwt);
    return skillMatrixService.cancelLineStyleSchedule(user, id);
  }

  @PostMapping("/employee-skills")
  public Map<String, Object> saveEmployeeSkill(
      @AuthenticationPrincipal Jwt jwt, @Valid @RequestBody EmployeeSkillRequest request) {
    AuthenticatedUser user = userContextService.loadCurrentUser(jwt);
    return skillMatrixService.saveEmployeeSkill(user, request);
  }

  @DeleteMapping("/employee-skills/{id}")
  public Map<String, Object> deleteEmployeeSkill(
      @AuthenticationPrincipal Jwt jwt, @PathVariable String id) {
    AuthenticatedUser user = userContextService.loadCurrentUser(jwt);
    return skillMatrixService.deleteEmployeeSkill(user, id);
  }

  @GetMapping("/recommendations")
  public Map<String, Object> recommend(
      @AuthenticationPrincipal Jwt jwt,
      @RequestParam String lineOperationId,
      @RequestParam(required = false) String absentEmployeeId) {
    AuthenticatedUser user = userContextService.loadCurrentUser(jwt);
    return skillMatrixService.recommend(user, lineOperationId, absentEmployeeId);
  }

  @GetMapping("/line-recommendations")
  public Map<String, Object> lineRecommendations(
      @AuthenticationPrincipal Jwt jwt, @RequestParam String lineId) {
    AuthenticatedUser user = userContextService.loadCurrentUser(jwt);
    return skillMatrixService.lineRecommendations(user, lineId);
  }
}
