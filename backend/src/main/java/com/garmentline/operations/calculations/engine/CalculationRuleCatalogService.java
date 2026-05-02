package com.garmentline.operations.calculations.engine;

import com.garmentline.operations.calculations.loader.CalculationRuleLoaderService;
import com.garmentline.operations.calculations.model.CalculationRuleCatalogEntry;
import com.garmentline.operations.config.SupabaseProperties;
import com.garmentline.operations.supabase.SupabaseAdminClient;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Service;

@Service
public class CalculationRuleCatalogService {

  private static final Logger LOGGER = LoggerFactory.getLogger(CalculationRuleCatalogService.class);

  private final CalculationRuleLoaderService ruleLoaderService;
  private final SupabaseAdminClient supabaseAdminClient;
  private final SupabaseProperties supabaseProperties;

  public CalculationRuleCatalogService(
      CalculationRuleLoaderService ruleLoaderService,
      SupabaseAdminClient supabaseAdminClient,
      SupabaseProperties supabaseProperties) {
    this.ruleLoaderService = ruleLoaderService;
    this.supabaseAdminClient = supabaseAdminClient;
    this.supabaseProperties = supabaseProperties;
  }

  @EventListener(ApplicationReadyEvent.class)
  public void publishActiveRuleSets() {
    if (supabaseProperties.serviceRoleKey() == null || supabaseProperties.serviceRoleKey().isBlank()) {
      LOGGER.debug("Skipping calculation rule catalog publication because no Supabase service role key is configured.");
      return;
    }

    try {
      List<Map<String, Object>> payload = new ArrayList<>();
      for (CalculationRuleCatalogEntry entry : ruleLoaderService.listCatalogEntries()) {
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("rule_type", entry.ruleType());
        row.put("rule_set_id", entry.ruleSetId());
        row.put("version", entry.version());
        row.put("description", entry.description());
        row.put("source_path", entry.sourcePath());
        row.put("is_active", entry.active());
        row.put("checksum", entry.checksum());
        payload.add(row);
      }
      supabaseAdminClient.upsertMany("calculation_rule_sets", payload, "rule_type,rule_set_id,version");
    } catch (Exception exception) {
      LOGGER.warn("Unable to publish calculation rule catalog to Supabase: {}", exception.getMessage());
    }
  }
}
