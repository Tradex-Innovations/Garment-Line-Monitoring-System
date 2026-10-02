package com.tradex.unionnorth.identity;

import com.fasterxml.jackson.databind.JsonNode;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

/** Server-only access to Supabase Auth administration. */
@Component
public class SupabaseIdentityClient {
  public record UserPage(List<JsonNode> users, long total) {}

  private final RestClient client;
  private final String redirectUrl;

  public SupabaseIdentityClient(
      RestClient.Builder builder,
      @Value("${app.linematrix.url}") String url,
      @Value("${app.linematrix.service-role-key}") String secret,
      @Value("${app.payroll.invite-redirect-url:}") String redirectUrl) {
    if (url.isBlank() || secret.isBlank()) {
      throw new IllegalStateException("Supabase Auth administration is not configured");
    }
    this.client = builder.baseUrl(url)
        .defaultHeader("apikey", secret)
        .defaultHeader("Authorization", "Bearer " + secret)
        .build();
    this.redirectUrl = redirectUrl;
  }

  public UserPage users(int page, int size) {
    try {
      ResponseEntity<JsonNode> response = client.get()
          .uri(uri -> uri.path("/auth/v1/admin/users")
              .queryParam("page", page + 1)
              .queryParam("per_page", size)
              .build())
          .retrieve().toEntity(JsonNode.class);
      JsonNode values = response.getBody() == null ? null : response.getBody().path("users");
      if (values == null || !values.isArray()) throw new IllegalStateException("Invalid user list");
      String count = response.getHeaders().getFirst("x-total-count");
      long total = count == null ? (long) page * size + values.size() : Long.parseLong(count);
      return new UserPage(values.valueStream().toList(), total);
    } catch (RestClientException | IllegalStateException exception) {
      throw unavailable();
    }
  }

  public JsonNode user(UUID id) {
    try {
      JsonNode response = client.get().uri("/auth/v1/admin/users/{id}", id)
          .retrieve().body(JsonNode.class);
      return unwrap(response);
    } catch (RestClientException exception) {
      throw unavailable();
    }
  }

  public JsonNode invite(String email, Map<String, Object> metadata) {
    try {
      JsonNode response = client.post()
          .uri(uri -> {
            var result = uri.path("/auth/v1/invite");
            if (!redirectUrl.isBlank()) result.queryParam("redirect_to", redirectUrl);
            return result.build();
          })
          .body(Map.of("email", email, "data", metadata))
          .retrieve().body(JsonNode.class);
      return unwrap(response);
    } catch (RestClientException exception) {
      throw unavailable();
    }
  }

  public JsonNode update(UUID id, Map<String, Object> attributes) {
    try {
      JsonNode response = client.put().uri("/auth/v1/admin/users/{id}", id)
          .body(attributes).retrieve().body(JsonNode.class);
      return unwrap(response);
    } catch (RestClientException exception) {
      throw unavailable();
    }
  }

  /** Keep the auth row so historical references to this identity remain valid. */
  public void softDelete(UUID id) {
    try {
      client.method(HttpMethod.DELETE).uri("/auth/v1/admin/users/{id}", id)
          .body(Map.of("should_soft_delete", true))
          .retrieve().toBodilessEntity();
    } catch (RestClientException exception) {
      throw unavailable();
    }
  }

  public void sendRecovery(String email) {
    try {
      client.post()
          .uri(uri -> {
            var result = uri.path("/auth/v1/recover");
            if (!redirectUrl.isBlank()) result.queryParam("redirect_to", redirectUrl);
            return result.build();
          })
          .body(Map.of("email", email))
          .retrieve().toBodilessEntity();
    } catch (RestClientException exception) {
      throw unavailable();
    }
  }

  private JsonNode unwrap(JsonNode response) {
    JsonNode user = response == null ? null : response.has("user") ? response.path("user") : response;
    if (user == null || !user.isObject() || !user.hasNonNull("id")) throw unavailable();
    return user;
  }

  private IdentityAdminException unavailable() {
    return new IdentityAdminException(
        "IDENTITY_UNAVAILABLE", "User account service is unavailable", HttpStatus.BAD_GATEWAY);
  }
}
