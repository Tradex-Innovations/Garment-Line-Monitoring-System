package com.garmentline.operations.calculations.api;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.garmentline.operations.api.CalculationMetricsController;
import com.garmentline.operations.api.CalculationRulesController;
import com.garmentline.operations.calculations.engine.CalculationBatchService;
import com.garmentline.operations.calculations.engine.ProductionMetricsService;
import com.garmentline.operations.calculations.loader.CalculationRuleLoaderService;
import com.garmentline.operations.calculations.model.CalculationRuleCatalogEntry;
import com.garmentline.operations.calculations.reports.CalculationReportService;
import com.garmentline.operations.config.SecurityConfig;
import com.garmentline.operations.security.AuthenticatedUser;
import com.garmentline.operations.security.RoleGuard;
import com.garmentline.operations.security.UserContextService;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(controllers = {CalculationRulesController.class, CalculationMetricsController.class})
@Import(SecurityConfig.class)
class CalculationControllersIntegrationTest {

  @Autowired private MockMvc mockMvc;

  @MockBean private CalculationRuleLoaderService calculationRuleLoaderService;
  @MockBean private ProductionMetricsService productionMetricsService;
  @MockBean private CalculationBatchService calculationBatchService;
  @MockBean private CalculationReportService calculationReportService;
  @MockBean private UserContextService userContextService;
  @MockBean private RoleGuard roleGuard;
  @MockBean private JwtDecoder jwtDecoder;

  private AuthenticatedUser adminUser;

  @BeforeEach
  void setUp() {
    adminUser = new AuthenticatedUser("user-1", "Admin User", "admin");
    when(userContextService.loadCurrentUser(any())).thenReturn(adminUser);
  }

  @Test
  void listsActiveRuleSetsForAuthenticatedUser() throws Exception {
    when(calculationRuleLoaderService.listCatalogEntries())
        .thenReturn(
            List.of(
                new CalculationRuleCatalogEntry(
                    "efficiency",
                    "efficiency-v1",
                    1,
                    "Workbook formulas",
                    "calculation-rules/efficiency/default-efficiency-formulas.yml",
                    true,
                    "abc123",
                    "payload")));

    mockMvc
        .perform(get("/api/calculation-rules").with(jwt().jwt(jwt -> jwt.subject("user-1"))))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$[0].ruleType").value("efficiency"))
        .andExpect(jsonPath("$[0].ruleSetId").value("efficiency-v1"))
        .andExpect(jsonPath("$[0].version").value(1));
  }

  @Test
  void triggersRecalculationForAdmin() throws Exception {
    when(calculationBatchService.recalculate(eq(adminUser), eq("2026-05-01"), eq("2026-05-31"), eq("LN-01")))
        .thenReturn(Map.of("recalculatedCount", 2, "items", List.of()));

    mockMvc
        .perform(
            post("/api/calculations/recalculate")
                .with(jwt().jwt(jwt -> jwt.subject("user-1")))
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {
                      "dateFrom": "2026-05-01",
                      "dateTo": "2026-05-31",
                      "lineCode": "LN-01"
                    }
                    """))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.recalculatedCount").value(2));
  }
}
