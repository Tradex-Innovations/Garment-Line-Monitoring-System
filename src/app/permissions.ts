import type { UserRole } from "./types";

export type AppRouteKey =
  | "dashboard"
  | "imports"
  | "workers"
  | "workerProfile"
  | "validation"
  | "productionLines"
  | "lineAssignment"
  | "alerts"
  | "attendance"
  | "reports"
  | "settings"
  | "audit"
  | "selfService"
  | "display";

export type AppAction =
  | "manageWorkers"
  | "assignLine"
  | "transferLine"
  | "resolveValidation"
  | "markValidationVerified"
  | "escalateValidation"
  | "manageAlerts"
  | "exportAttendance"
  | "exportReports"
  | "editSettings"
  | "addWorkerNote"
  | "markException"
  | "viewAudit";

export const roleLabels: Record<UserRole, string> = {
  admin: "Admin",
  supervisor: "Supervisor",
  hr: "HR",
  viewer: "Viewer / Management",
};

export const routeTitles: Record<AppRouteKey, string> = {
  dashboard: "Dashboard",
  imports: "Import Center",
  workers: "Workers",
  workerProfile: "Worker Profile",
  validation: "Validation Center",
  productionLines: "Production Lines",
  lineAssignment: "Line Assignment",
  alerts: "Alerts Center",
  attendance: "Incentive Calculation",
  reports: "Reports",
  settings: "Settings",
  audit: "Audit Log",
  selfService: "Self-Service Portal",
  display: "Display Mode",
};

export const routePermissions: Record<AppRouteKey, UserRole[]> = {
  dashboard: ["admin", "supervisor", "hr", "viewer"],
  imports: ["admin", "hr"],
  workers: ["admin", "supervisor", "hr"],
  workerProfile: ["admin", "supervisor", "hr"],
  validation: ["admin", "supervisor", "hr"],
  productionLines: ["admin", "supervisor", "hr", "viewer"],
  lineAssignment: ["admin", "supervisor"],
  alerts: ["admin", "supervisor"],
  attendance: ["admin", "supervisor", "hr", "viewer"],
  reports: ["admin", "supervisor", "hr", "viewer"],
  settings: ["admin"],
  audit: ["admin", "hr"],
  selfService: ["admin", "supervisor", "hr", "viewer"],
  display: ["admin", "supervisor", "hr", "viewer"],
};

export const actionPermissions: Record<AppAction, UserRole[]> = {
  manageWorkers: ["admin", "supervisor", "hr"],
  assignLine: ["admin", "supervisor"],
  transferLine: ["admin", "supervisor"],
  resolveValidation: ["admin", "hr"],
  markValidationVerified: ["admin", "hr"],
  escalateValidation: ["admin", "hr"],
  manageAlerts: ["admin", "supervisor"],
  exportAttendance: ["admin", "hr"],
  exportReports: ["admin", "supervisor", "hr", "viewer"],
  editSettings: ["admin"],
  addWorkerNote: ["admin", "supervisor", "hr"],
  markException: ["admin", "supervisor", "hr"],
  viewAudit: ["admin"],
};

export function canAccessRoute(role: UserRole, routeKey: AppRouteKey) {
  return routePermissions[routeKey].includes(role);
}

export function canPerform(role: UserRole, action: AppAction) {
  return actionPermissions[action].includes(role);
}
