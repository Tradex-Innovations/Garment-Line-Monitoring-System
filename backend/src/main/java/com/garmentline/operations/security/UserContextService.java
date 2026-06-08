package com.garmentline.operations.security;

import com.fasterxml.jackson.databind.node.ObjectNode;
import com.garmentline.operations.supabase.SupabaseAdminClient;
import com.garmentline.operations.support.JsonSupport;
import java.util.Map;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Service;

@Service
public class UserContextService {

  private final SupabaseAdminClient supabaseAdminClient;

  public UserContextService(SupabaseAdminClient supabaseAdminClient) {
    this.supabaseAdminClient = supabaseAdminClient;
  }

  public AuthenticatedUser loadCurrentUser(Jwt jwt) {
    String userId = jwt.getSubject();
    ObjectNode profile =
        supabaseAdminClient.selectSingle(
            "profiles",
            supabaseAdminClient.filters(Map.of("id", "eq." + userId)));

    return new AuthenticatedUser(
        userId,
        JsonSupport.text(profile, "full_name") == null ? "Supabase User" : JsonSupport.text(profile, "full_name"),
        JsonSupport.text(profile, "role") == null ? "viewer" : JsonSupport.text(profile, "role"));
  }
}
