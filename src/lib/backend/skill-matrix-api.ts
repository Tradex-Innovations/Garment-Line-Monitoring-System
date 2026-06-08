import { backendJsonRequest } from "./client";
import type {
  EmployeeSkillInput,
  LineStyleScheduleInput,
  LineAutomaticRecommendationResponse,
  LineOperationInput,
  LinePositionAssignmentInput,
  OperationInput,
  SkillMatrixSnapshot,
  SkillRecommendationResponse,
  StylePlanInput,
  StylePlanMachineInput,
} from "@/types/skill-matrix";

export function getSkillMatrixFromBackend() {
  return backendJsonRequest<SkillMatrixSnapshot>("/api/skill-matrix");
}

export function saveSkillOperationFromBackend(input: OperationInput) {
  return backendJsonRequest<SkillMatrixSnapshot>("/api/skill-matrix/operations", {
    method: "POST",
    body: JSON.stringify(input),
  });
}

export function saveLineOperationFromBackend(input: LineOperationInput) {
  return backendJsonRequest<SkillMatrixSnapshot>("/api/skill-matrix/line-operations", {
    method: "POST",
    body: JSON.stringify(input),
  });
}

export function deleteLineOperationFromBackend(id: string) {
  return backendJsonRequest<SkillMatrixSnapshot>(`/api/skill-matrix/line-operations/${id}`, {
    method: "DELETE",
  });
}

export function saveLinePositionAssignmentFromBackend(input: LinePositionAssignmentInput) {
  return backendJsonRequest<SkillMatrixSnapshot>("/api/skill-matrix/line-position-assignments", {
    method: "POST",
    body: JSON.stringify(input),
  });
}

export function deleteLinePositionAssignmentFromBackend(id: string) {
  return backendJsonRequest<SkillMatrixSnapshot>(`/api/skill-matrix/line-position-assignments/${id}`, {
    method: "DELETE",
  });
}

export function saveStylePlanFromBackend(input: StylePlanInput) {
  return backendJsonRequest<SkillMatrixSnapshot>("/api/skill-matrix/style-plans", {
    method: "POST",
    body: JSON.stringify(input),
  });
}

export function deleteStylePlanFromBackend(id: string) {
  return backendJsonRequest<SkillMatrixSnapshot>(`/api/skill-matrix/style-plans/${id}`, {
    method: "DELETE",
  });
}

export function saveStylePlanMachineFromBackend(input: StylePlanMachineInput) {
  return backendJsonRequest<SkillMatrixSnapshot>("/api/skill-matrix/style-plan-machines", {
    method: "POST",
    body: JSON.stringify(input),
  });
}

export function deleteStylePlanMachineFromBackend(id: string) {
  return backendJsonRequest<SkillMatrixSnapshot>(`/api/skill-matrix/style-plan-machines/${id}`, {
    method: "DELETE",
  });
}

export function saveLineStyleScheduleFromBackend(input: LineStyleScheduleInput) {
  return backendJsonRequest<SkillMatrixSnapshot>("/api/skill-matrix/line-style-schedules", {
    method: "POST",
    body: JSON.stringify(input),
  });
}

export function cancelLineStyleScheduleFromBackend(id: string) {
  return backendJsonRequest<SkillMatrixSnapshot>(`/api/skill-matrix/line-style-schedules/${id}`, {
    method: "DELETE",
  });
}

export function saveEmployeeSkillFromBackend(input: EmployeeSkillInput) {
  return backendJsonRequest<SkillMatrixSnapshot>("/api/skill-matrix/employee-skills", {
    method: "POST",
    body: JSON.stringify(input),
  });
}

export function deleteEmployeeSkillFromBackend(id: string) {
  return backendJsonRequest<SkillMatrixSnapshot>(`/api/skill-matrix/employee-skills/${id}`, {
    method: "DELETE",
  });
}

export function getSkillRecommendationsFromBackend(args: {
  lineOperationId: string;
  absentEmployeeId?: string | null;
}) {
  return backendJsonRequest<SkillRecommendationResponse>(
    "/api/skill-matrix/recommendations",
    {},
    {
      lineOperationId: args.lineOperationId,
      absentEmployeeId: args.absentEmployeeId || null,
    }
  );
}

export function getLineAutomaticRecommendationsFromBackend(lineId: string) {
  return backendJsonRequest<LineAutomaticRecommendationResponse>(
    "/api/skill-matrix/line-recommendations",
    {},
    { lineId }
  );
}
