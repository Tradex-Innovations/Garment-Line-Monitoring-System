package com.garmentline.operations.calculations.loader;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import com.garmentline.operations.calculations.model.AggregationRuleSet;
import com.garmentline.operations.calculations.model.CalculationRuleCatalogEntry;
import com.garmentline.operations.calculations.model.CalculationRuleManifest;
import com.garmentline.operations.calculations.model.EfficiencyConstantsRuleSet;
import com.garmentline.operations.calculations.model.FormulaRuleSet;
import com.garmentline.operations.calculations.model.IncentiveLadderRuleSet;
import com.garmentline.operations.calculations.model.IncentivePolicyRuleSet;
import com.garmentline.operations.calculations.validation.CalculationRuleValidator;
import com.garmentline.operations.support.ApiException;
import jakarta.annotation.PostConstruct;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import org.springframework.core.io.ClassPathResource;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

@Service
public class CalculationRuleLoaderService {

  private static final String ROOT = "calculation-rules/";

  private final ObjectMapper yamlMapper = new ObjectMapper(new YAMLFactory());
  private final CalculationRuleValidator validator;
  private LoadedCalculationRules loadedRules;

  public CalculationRuleLoaderService(CalculationRuleValidator validator) {
    this.validator = validator;
  }

  @PostConstruct
  public void initialize() {
    try {
      CalculationRuleManifest manifest = read(ROOT + "rule-set-manifest.yml", CalculationRuleManifest.class);
      String formulasYaml = readRaw(ROOT + manifest.formulaRuleSet());
      String constantsYaml = readRaw(ROOT + manifest.constantsRuleSet());
      String ladderYaml = readRaw(ROOT + manifest.incentiveLadderRuleSet());
      String policyYaml = readRaw(ROOT + manifest.incentivePolicyRuleSet());
      String aggregationYaml = readRaw(ROOT + manifest.aggregationRuleSet());

      FormulaRuleSet formulaRuleSet = yamlMapper.readValue(formulasYaml, FormulaRuleSet.class);
      EfficiencyConstantsRuleSet constantsRuleSet =
          yamlMapper.readValue(constantsYaml, EfficiencyConstantsRuleSet.class);
      IncentiveLadderRuleSet ladderRuleSet =
          yamlMapper.readValue(ladderYaml, IncentiveLadderRuleSet.class);
      IncentivePolicyRuleSet policyRuleSet =
          yamlMapper.readValue(policyYaml, IncentivePolicyRuleSet.class);
      AggregationRuleSet aggregationRuleSet =
          yamlMapper.readValue(aggregationYaml, AggregationRuleSet.class);

      validator.validate(
          formulaRuleSet, constantsRuleSet, ladderRuleSet, policyRuleSet, aggregationRuleSet);

      List<CalculationRuleCatalogEntry> catalogEntries = new ArrayList<>();
      catalogEntries.add(
          new CalculationRuleCatalogEntry(
              "efficiency",
              formulaRuleSet.ruleSetId(),
              formulaRuleSet.version(),
              formulaRuleSet.description(),
              manifest.formulaRuleSet(),
              true,
              checksum(formulasYaml),
              formulasYaml));
      catalogEntries.add(
          new CalculationRuleCatalogEntry(
              "efficiency-constants",
              constantsRuleSet.ruleSetId(),
              constantsRuleSet.version(),
              constantsRuleSet.description(),
              manifest.constantsRuleSet(),
              true,
              checksum(constantsYaml),
              constantsYaml));
      catalogEntries.add(
          new CalculationRuleCatalogEntry(
              "incentive-ladder",
              ladderRuleSet.ruleSetId(),
              ladderRuleSet.version(),
              ladderRuleSet.description(),
              manifest.incentiveLadderRuleSet(),
              true,
              checksum(ladderYaml),
              ladderYaml));
      catalogEntries.add(
          new CalculationRuleCatalogEntry(
              "incentive-policy",
              policyRuleSet.ruleSetId(),
              policyRuleSet.version(),
              policyRuleSet.description(),
              manifest.incentivePolicyRuleSet(),
              true,
              checksum(policyYaml),
              policyYaml));
      catalogEntries.add(
          new CalculationRuleCatalogEntry(
              "aggregation",
              aggregationRuleSet.ruleSetId(),
              aggregationRuleSet.version(),
              aggregationRuleSet.description(),
              manifest.aggregationRuleSet(),
              true,
              checksum(aggregationYaml),
              aggregationYaml));

      this.loadedRules =
          new LoadedCalculationRules(
              formulaRuleSet,
              constantsRuleSet,
              ladderRuleSet,
              policyRuleSet,
              aggregationRuleSet,
              List.copyOf(catalogEntries));
    } catch (IOException exception) {
      throw new ApiException(
          HttpStatus.INTERNAL_SERVER_ERROR,
          "Failed to load calculation rule files: " + exception.getMessage());
    }
  }

  public LoadedCalculationRules getLoadedRules() {
    return loadedRules;
  }

  public FormulaRuleSet getFormulaRuleSet() {
    return loadedRules.formulaRuleSet();
  }

  public EfficiencyConstantsRuleSet getConstantsRuleSet() {
    return loadedRules.constantsRuleSet();
  }

  public IncentiveLadderRuleSet getIncentiveLadderRuleSet() {
    return loadedRules.incentiveLadderRuleSet();
  }

  public IncentivePolicyRuleSet getIncentivePolicyRuleSet() {
    return loadedRules.incentivePolicyRuleSet();
  }

  public AggregationRuleSet getAggregationRuleSet() {
    return loadedRules.aggregationRuleSet();
  }

  public List<CalculationRuleCatalogEntry> listCatalogEntries() {
    return loadedRules.catalogEntries();
  }

  private <T> T read(String path, Class<T> type) throws IOException {
    return yamlMapper.readValue(readRaw(path), type);
  }

  private String readRaw(String path) throws IOException {
    ClassPathResource resource = new ClassPathResource(path);
    if (!resource.exists()) {
      throw new ApiException(
          HttpStatus.INTERNAL_SERVER_ERROR,
          "Calculation rule resource not found: " + path);
    }

    try (InputStream inputStream = resource.getInputStream()) {
      return new String(inputStream.readAllBytes(), StandardCharsets.UTF_8);
    }
  }

  private String checksum(String payload) {
    try {
      MessageDigest digest = MessageDigest.getInstance("SHA-256");
      return HexFormat.of().formatHex(digest.digest(payload.getBytes(StandardCharsets.UTF_8)));
    } catch (NoSuchAlgorithmException exception) {
      throw new IllegalStateException("SHA-256 checksum support is unavailable.", exception);
    }
  }
}
