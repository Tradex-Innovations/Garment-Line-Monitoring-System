package com.garmentline.operations.config;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.tradex.unionnorth.identity.PayrollSessionController;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@WebMvcTest(controllers = {PayrollSessionController.class, PayrollMfaRouteTest.ProtectedEndpoint.class})
@Import({SecurityConfig.class, PayrollSessionController.class, PayrollMfaRouteTest.ProtectedEndpoint.class})
class PayrollMfaRouteTest {
  @Autowired private MockMvc mvc;
  @MockBean private JwtDecoder jwtDecoder;
  @MockBean private com.garmentline.operations.monitoring.MonitoringJournal monitoringJournal;

  @RestController
  static class ProtectedEndpoint {
    @GetMapping("/api/v1/protected-test")
    String get() { return "ok"; }
  }

  @Test
  void privilegedAal1CanReadSetupStateButCannotUsePayrollApi() throws Exception {
    var token = jwt().jwt(value -> value.subject("user-1").claim("aal", "aal1"))
        .authorities(new SimpleGrantedAuthority("ROLE_SYSTEM_ADMIN"));
    mvc.perform(get("/api/v1/auth/me").with(token)).andExpect(status().isOk());
    mvc.perform(get("/api/v1/protected-test").with(token)).andExpect(status().isForbidden());
  }

  @Test
  void privilegedAal2CanUsePayrollApi() throws Exception {
    mvc.perform(get("/api/v1/protected-test").with(jwt()
        .jwt(value -> value.subject("user-1").claim("aal", "aal2"))
        .authorities(new SimpleGrantedAuthority("ROLE_SYSTEM_ADMIN"))))
        .andExpect(status().isOk());
  }

  @Test
  void ordinaryAal1CanUsePayrollApi() throws Exception {
    mvc.perform(get("/api/v1/protected-test").with(jwt()
        .jwt(value -> value.subject("user-1").claim("aal", "aal1"))
        .authorities(new SimpleGrantedAuthority("ROLE_EMPLOYEE"))))
        .andExpect(status().isOk());
  }
}
