export type SkillOperation = {
  id: string;
  operationCode: string;
  name: string;
  category?: string | null;
  description?: string | null;
  isActive: boolean;
  createdAt?: string;
  updatedAt?: string;
};

export type SkillMatrixLine = {
  id: string;
  code: string;
  name: string;
  department?: string | null;
  shift?: string | null;
};

export type SkillMatrixEmployee = {
  id: string;
  employeeCode: string;
  fullName: string;
  designation?: string | null;
  department?: string | null;
  photoUrl?: string | null;
};

export type LineOperation = {
  id: string;
  productionLineId: string;
  lineName?: string | null;
  lineCode?: string | null;
  operationId: string;
  operationCode?: string | null;
  operationName?: string | null;
  positionLabel: string;
  requiredSkillPercentage: number;
  plannedOperators: number;
  sequenceNo: number;
  isActive: boolean;
};

export type LinePositionAssignment = {
  id: string;
  lineOperationId: string;
  productionLineId?: string | null;
  lineName?: string | null;
  lineCode?: string | null;
  operationId?: string | null;
  operationCode?: string | null;
  operationName?: string | null;
  positionLabel?: string | null;
  requiredSkillPercentage?: number | null;
  employeeId: string;
  employeeCode?: string | null;
  fullName?: string | null;
  photoUrl?: string | null;
  assignedAt?: string | null;
  isActive: boolean;
};

export type StyleOperationPlan = {
  id: string;
  styleNumber: string;
  version: number;
  description?: string | null;
  isActive: boolean;
  createdAt?: string;
  updatedAt?: string;
};

export type StyleOperationPlanMachine = {
  id: string;
  styleOperationPlanId: string;
  styleNumber?: string | null;
  operationId: string;
  operationCode?: string | null;
  operationName?: string | null;
  positionLabel: string;
  requiredSkillPercentage: number;
  plannedOperators: number;
  stationType: "mo" | "helper" | "other";
  sequenceNo: number;
  isActive: boolean;
};

export type LineStyleScheduleStatus = "draft" | "scheduled" | "active" | "completed" | "cancelled";

export type LineStyleSchedule = {
  id: string;
  productionLineId: string;
  lineName?: string | null;
  lineCode?: string | null;
  styleOperationPlanId: string;
  styleNumber?: string | null;
  styleVersion?: number | null;
  scheduledStartAt: string;
  scheduledEndAt?: string | null;
  shiftName: string;
  status: LineStyleScheduleStatus;
  notes?: string | null;
  activatedAt?: string | null;
  completedAt?: string | null;
  createdAt?: string;
  updatedAt?: string;
};

export type EmployeeOperationSkill = {
  id: string;
  employeeId: string;
  employeeCode?: string | null;
  fullName?: string | null;
  operationId: string;
  operationCode?: string | null;
  operationName?: string | null;
  skillLevelPercentage: number;
  isSpeciality: boolean;
  notes?: string | null;
  certifiedAt?: string | null;
};

export type SkillMatrixSnapshot = {
  operations: SkillOperation[];
  lines: SkillMatrixLine[];
  employees: SkillMatrixEmployee[];
  lineOperations: LineOperation[];
  linePositionAssignments: LinePositionAssignment[];
  stylePlans: StyleOperationPlan[];
  stylePlanMachines: StyleOperationPlanMachine[];
  lineStyleSchedules: LineStyleSchedule[];
  employeeSkills: EmployeeOperationSkill[];
};

export type SkillCandidate = {
  employeeId: string;
  employeeCode: string;
  fullName: string;
  designation?: string | null;
  department?: string | null;
  photoUrl?: string | null;
  operationId: string;
  operationName: string;
  skillLevelPercentage: number;
  isSpeciality: boolean;
  attendanceStatus: string;
  currentLineId?: string | null;
  currentLineName?: string | null;
  availableNow: boolean;
  recommendationReason: string;
};

export type SkillRecommendationResponse = {
  lineOperation: LineOperation;
  candidates: SkillCandidate[];
};

export type LineAutomaticRecommendation = {
  assignment: LinePositionAssignment;
  lineOperation: LineOperation;
  assignedEmployee: SkillMatrixEmployee;
  assignedAttendanceStatus: string;
  bestCandidate?: SkillCandidate | null;
  candidates: SkillCandidate[];
};

export type LineAutomaticRecommendationResponse = {
  lineId: string;
  lineName?: string | null;
  recommendations: LineAutomaticRecommendation[];
};

export type OperationInput = {
  operationCode: string;
  name: string;
  category?: string | null;
  description?: string | null;
  isActive?: boolean;
};

export type LineOperationInput = {
  id?: string;
  productionLineId: string;
  operationId: string;
  positionLabel: string;
  requiredSkillPercentage: number;
  plannedOperators: number;
  sequenceNo?: number;
  isActive?: boolean;
};

export type LinePositionAssignmentInput = {
  id?: string;
  lineOperationId: string;
  employeeId: string;
  isActive?: boolean;
};

export type StylePlanInput = {
  id?: string;
  styleNumber: string;
  version?: number;
  description?: string | null;
  isActive?: boolean;
};

export type StylePlanMachineInput = {
  id?: string;
  styleOperationPlanId: string;
  operationId: string;
  positionLabel: string;
  requiredSkillPercentage: number;
  plannedOperators: number;
  stationType: "mo" | "helper" | "other";
  sequenceNo?: number;
  isActive?: boolean;
};

export type LineStyleScheduleInput = {
  id?: string;
  productionLineId: string;
  styleOperationPlanId: string;
  scheduledStartAt: string;
  scheduledEndAt?: string | null;
  shiftName: string;
  notes?: string | null;
  status?: LineStyleScheduleStatus;
};

export type EmployeeSkillInput = {
  id?: string;
  employeeId: string;
  operationId: string;
  skillLevelPercentage: number;
  isSpeciality: boolean;
  notes?: string | null;
  certifiedAt?: string | null;
};
