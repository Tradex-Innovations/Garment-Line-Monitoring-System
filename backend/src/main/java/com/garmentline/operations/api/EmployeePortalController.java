package com.garmentline.operations.api;

import com.garmentline.operations.service.EmployeePortalService;
import com.garmentline.operations.service.EmployeePortalService.EmployeeLeaveRequest;
import com.garmentline.operations.service.EmployeePortalService.PortalLoginRequest;
import com.garmentline.operations.service.EmployeePortalService.PortalOtpVerifyRequest;
import com.garmentline.operations.service.EmployeePortalService.PortalPasswordSetupRequest;
import jakarta.validation.Valid;
import java.util.Map;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/employee-portal")
@Validated
public class EmployeePortalController {

  private static final String PORTAL_TOKEN_HEADER = "X-Employee-Portal-Token";

  private final EmployeePortalService employeePortalService;

  public EmployeePortalController(EmployeePortalService employeePortalService) {
    this.employeePortalService = employeePortalService;
  }

  @PostMapping("/auth/setup")
  public Map<String, Object> setupPassword(@Valid @RequestBody PortalPasswordSetupRequest request) {
    return employeePortalService.setupPassword(request);
  }

  @PostMapping("/auth/login")
  public Map<String, Object> login(@Valid @RequestBody PortalLoginRequest request) {
    return employeePortalService.login(request);
  }

  @PostMapping("/auth/verify-otp")
  public Map<String, Object> verifyOtp(@Valid @RequestBody PortalOtpVerifyRequest request) {
    return employeePortalService.verifyOtp(request);
  }

  @PostMapping("/auth/logout")
  public Map<String, Object> logout(
      @RequestHeader(name = PORTAL_TOKEN_HEADER, required = false) String token) {
    return employeePortalService.logout(token);
  }

  @GetMapping("/me")
  public Map<String, Object> getPortal(
      @RequestHeader(name = PORTAL_TOKEN_HEADER, required = false) String token) {
    return employeePortalService.getPortal(token);
  }

  @GetMapping("/kiosk/latest-recognition")
  public Map<String, Object> latestKioskRecognition(
      @RequestParam(name = "lastEventId", required = false) String lastEventId) {
    return employeePortalService.latestKioskRecognition(lastEventId);
  }

  @PostMapping("/leave-requests")
  public Map<String, Object> createLeaveRequest(
      @RequestHeader(name = PORTAL_TOKEN_HEADER, required = false) String token,
      @Valid @RequestBody EmployeeLeaveRequest request) {
    return employeePortalService.createLeaveRequest(token, request);
  }
}
