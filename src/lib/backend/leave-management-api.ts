import { backendJsonRequest } from "./client";
import type {
  LeaveManagementSnapshot,
  LeaveRequestInput,
  LeaveReviewInput,
  LeaveRequestStatus,
} from "@/types/leave-management";

export function getLeaveManagementFromBackend(filters?: {
  status?: LeaveRequestStatus | "all";
  employeeId?: string;
  dateFrom?: string;
  dateTo?: string;
}) {
  return backendJsonRequest<LeaveManagementSnapshot>("/api/leave-management", {}, filters);
}

export function createLeaveRequestFromBackend(input: LeaveRequestInput) {
  return backendJsonRequest<LeaveManagementSnapshot>("/api/leave-management/requests", {
    method: "POST",
    body: JSON.stringify(input),
  });
}

export function reviewLeaveRequestFromBackend(id: string, input: LeaveReviewInput) {
  return backendJsonRequest<LeaveManagementSnapshot>(`/api/leave-management/requests/${id}/review`, {
    method: "POST",
    body: JSON.stringify(input),
  });
}
