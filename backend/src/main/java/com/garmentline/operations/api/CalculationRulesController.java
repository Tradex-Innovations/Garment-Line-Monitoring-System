package com.garmentline.operations.api;

import com.garmentline.operations.calculations.loader.CalculationRuleLoaderService;
import com.garmentline.operations.security.AuthenticatedUser;
import com.garmentline.operations.security.RoleGuard;
import com.garmentline.operations.security.UserContextService;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/calculation-rules")
public class CalculationRulesController {

  private final CalculationRuleLoaderService ruleLoaderService;
  private final UserContextService userContextService;
  private final RoleGuard roleGuard;

  public CalculationRulesController(
      CalculationRuleLoaderService ruleLoaderService,
      UserContextService userContextService,
      RoleGuard roleGuard) {
    this.ruleLoaderService = ruleLoaderService;
    this.userContextService = userContextService;
    this.roleGuard = roleGuard;
  }

  @GetMapping
  public List<?> listRuleSets(@AuthenticationPrincipal Jwt jwt) {
    AuthenticatedUser user = userContextService.loadCurrentUser(jwt);
    roleGuard.requireAnyRole(user, "admin", "hr", "supervisor", "ie", "viewer");
    return ruleLoaderService.listCatalogEntries();
  }

  @GetMapping("/efficiency")
  public Map<String, Object> getEfficiencyRules(@AuthenticationPrincipal Jwt jwt) {
    AuthenticatedUser user = userContextService.loadCurrentUser(jwt);
    roleGuard.requireAnyRole(user, "admin", "hr", "supervisor", "ie", "viewer");
    Map<String, Object> payload = new LinkedHashMap<>();
    payload.put("formulaRuleSet", ruleLoaderService.getFormulaRuleSet());
    payload.put("constantsRuleSet", ruleLoaderService.getConstantsRuleSet());
    payload.put("aggregationRuleSet", ruleLoaderService.getAggregationRuleSet());
    return payload;
  }

  @GetMapping("/incentives")
  public Map<String, Object> getIncentiveRules(@AuthenticationPrincipal Jwt jwt) {
    AuthenticatedUser user = userContextService.loadCurrentUser(jwt);
    roleGuard.requireAnyRole(user, "admin", "hr", "supervisor", "ie", "viewer");
    Map<String, Object> payload = new LinkedHashMap<>();
    payload.put("incentiveLadderRuleSet", ruleLoaderService.getIncentiveLadderRuleSet());
    payload.put("incentivePolicyRuleSet", ruleLoaderService.getIncentivePolicyRuleSet());
    return payload;
  }
}
