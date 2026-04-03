import type { UserRole } from "./types";

export type AppRouteKey =
  | "dashboard"
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
  workers: "Workers",
  workerProfile: "Worker Profile",
  validation: "Validation Center",
  productionLines: "Production Lines",
  lineAssignment: "Line Assignment",
  alerts: "Alerts Center",
  attendance: "Attendance Operations",
  reports: "Reports",
  settings: "Settings",
  audit: "Audit Log",
  selfService: "Self-Service Portal",
  display: "Display Mode",
};

export const routePermissions: Record<AppRouteKey, UserRole[]> = {
  dashboard: ["admin", "supervisor", "hr", "viewer"],
  workers: ["admin", "supervisor", "hr"],
  workerProfile: ["admin", "supervisor", "hr"],
  validation: ["admin", "hr"],
  productionLines: ["admin", "supervisor", "viewer"],
  lineAssignment: ["admin", "supervisor"],
  alerts: ["admin", "supervisor"],
  attendance: ["admin", "hr"],
  reports: ["admin", "supervisor", "hr", "viewer"],
  settings: ["admin"],
  audit: ["admin"],
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
