export interface CalculationRuleCatalogEntry {
  ruleType: string;
  ruleSetId: string;
  version: number;
  description: string;
  sourcePath: string;
  active: boolean;
  checksum: string;
  rawYaml: string;
}

export interface FormulaDefinition {
  expression: string;
}

export interface FormulaRuleSet {
  ruleSetId: string;
  description: string;
  version: number;
  safeDivideDefault: number;
  formulas: Record<string, FormulaDefinition>;
}

export interface EfficiencyConstantsRuleSet {
  ruleSetId: string;
  description: string;
  version: number;
  calculationScale: number;
  calculationRoundingMode: string;
  numericConstants: Record<string, number>;
}

export interface AggregationRuleSet {
  ruleSetId: string;
  description: string;
  version: number;
  useRealRowTotals: boolean;
  useConfiguredWorkingHoursForTotals: boolean;
  configuredWorkingHours: number;
  rollupStrategy: string;
}

export interface IncentiveBand {
  label: string;
  minEfficiency: number;
  maxEfficiencyExclusive: number;
  incentiveAmount: number;
}

export interface IncentiveLadderRuleSet {
  ruleSetId: string;
  description: string;
  version: number;
  currency: string;
  bands: IncentiveBand[];
}

export interface IncentivePolicyRuleSet {
  ruleSetId: string;
  description: string;
  version: number;
  basis: string;
  rounding: {
    scale: number;
    mode: string;
  };
  payoutRules: {
    allowZeroWhenNoCadre: boolean;
    allowZeroWhenNoActualPcs: boolean;
    produceWarningWhenClockHoursZero: boolean;
  };
  flags: string[];
}

export interface EfficiencyRulesPreview {
  formulaRuleSet: FormulaRuleSet;
  constantsRuleSet: EfficiencyConstantsRuleSet;
  aggregationRuleSet: AggregationRuleSet;
}

export interface IncentiveRulesPreview {
  incentiveLadderRuleSet: IncentiveLadderRuleSet;
  incentivePolicyRuleSet: IncentivePolicyRuleSet;
}

export interface EfficiencyCalculationInputPayload {
  productionLineId: string;
  productionDate: string;
  shiftCode?: string | null;
  plannedMo?: number | null;
  plannedHel?: number | null;
  actualMo?: number | null;
  actualHel?: number | null;
  teamMembers?: number | null;
  workingHours?: number | null;
  smv?: number | null;
  plannedPcs?: number | null;
  forecastPcs?: number | null;
  actualPcs?: number | null;
  remarks?: string | null;
  lostTimeMinutes?: number | null;
  sourceMetadata?: Record<string, unknown>;
}

export interface CalculationExecutionResult {
  input: EfficiencyCalculationInputPayload;
  metrics: {
    plannedCadreTotal: number;
    actualCadreTotal: number;
    clockHours: number;
    plannedSah: number;
    plannedEfficiency: number;
    forecastSah: number;
    forecastEfficiency: number;
    actualSah: number;
    actualEfficiency: number;
    pieceVariance: number;
    sahVariance: number;
    incentiveAmount: number;
    incentiveBand?: string | null;
    ruleSetId: string;
    ruleSetVersion: number;
    incentiveRuleSetId?: string | null;
    incentiveRuleVersion: number;
    warnings: string[];
    debugSnapshot: Record<string, unknown>;
  };
  incentive: {
    basisMetric: string;
    basisValue: number;
    incentiveBandLabel?: string | null;
    incentiveAmount: number;
    incentiveRuleSetId: string;
    incentiveRuleVersion: number;
    warnings: string[];
  };
  metricRecordId?: string | null;
  incentiveRecordId?: string | null;
}

export interface LineMetricReportRow {
  id: string;
  productionLineId: string;
  lineCode: string;
  lineName?: string | null;
  productionDate: string;
  shiftCode?: string | null;
  plannedMo?: number | null;
  plannedHel?: number | null;
  actualMo?: number | null;
  actualHel?: number | null;
  teamMembers?: number | null;
  workingHours?: number | null;
  smv?: number | null;
  plannedPcs?: number | null;
  forecastPcs?: number | null;
  actualPcs?: number | null;
  plannedCadreTotal?: number | null;
  actualCadreTotal?: number | null;
  clockHours?: number | null;
  plannedSah?: number | null;
  plannedEfficiency?: number | null;
  forecastSah?: number | null;
  forecastEfficiency?: number | null;
  actualSah?: number | null;
  actualEfficiency?: number | null;
  pieceVariance?: number | null;
  sahVariance?: number | null;
  warnings: string[];
  formulaRuleSetId?: string | null;
  formulaRuleVersion?: number | null;
  linkedIncentiveRecordId?: string | null;
  linkedIncentiveAmount?: number | null;
  linkedIncentiveBand?: string | null;
  incentiveRuleSetId?: string | null;
  incentiveRuleVersion?: number | null;
  latestAuditSnapshot?: Record<string, unknown>;
}

export interface IncentiveReportRow {
  id: string;
  sourceMetricRecordId?: string | null;
  productionLineId?: string | null;
  lineCode?: string | null;
  lineName?: string | null;
  productionDate?: string | null;
  shiftCode?: string | null;
  basisMetric: string;
  basisValue?: number | null;
  actualEfficiency?: number | null;
  incentiveBandLabel?: string | null;
  incentiveAmount: number;
  incentiveRuleSetId?: string | null;
  incentiveRuleVersion?: number | null;
  warnings: string[];
}

export interface CalculationAuditView {
  id: string;
  metricRecordId?: string | null;
  incentiveRecordId?: string | null;
  inputPayload: Record<string, unknown>;
  outputPayload: Record<string, unknown>;
  warnings: string[];
  formulaRuleSetId?: string | null;
  formulaRuleVersion?: number | null;
  incentiveRuleSetId?: string | null;
  incentiveRuleVersion?: number | null;
  createdAt: string;
}

