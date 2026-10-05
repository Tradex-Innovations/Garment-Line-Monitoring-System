export type LeaveType = "full_day" | "half_day" | "short_leave";

export type LeaveCategory =
  | "annual"
  | "casual"
  | "sick"
  | "no_pay"
  | "emergency"
  | "personal"
  | "medical"
  | "other";

export type LeaveRequestStatus = "pending" | "approved" | "rejected" | "cancelled";

export type HalfDaySession = "first_half" | "second_half";

export type LeaveEmployee = {
  id: string;
  employeeCode: string;
  fullName: string;
  designation?: string | null;
  department?: string | null;
  photoUrl?: string | null;
};

export type LeaveRequestRecord = {
  id: string;
  employeeId: string;
  employeeCode?: string | null;
  employeeName: string;
  designation?: string | null;
  department?: string | null;
  photoUrl?: string | null;
  leaveType: LeaveType;
  leaveCategory: LeaveCategory;
  startDate: string;
  endDate: string;
  startTime?: string | null;
  endTime?: string | null;
  halfDaySession?: HalfDaySession | null;
  reason?: string | null;
  status: LeaveRequestStatus;
  requestedBy?: string | null;
  requestedAt: string;
  reviewedBy?: string | null;
  reviewedAt?: string | null;
  reviewNote?: string | null;
  createdAt: string;
  updatedAt: string;
  dayCount: number;
};

export type LeaveManagementSnapshot = {
  employees: LeaveEmployee[];
  requests: LeaveRequestRecord[];
  policies: LeavePolicy[];
  approvers: LeaveApprover[];
  managers: LeaveManager[];
};

export type LeavePolicy = {
  policyYear: number;
  employeeCategory: "permanent" | "new_joiner" | "intern";
  leaveCategory: Exclude<LeaveCategory, "no_pay">;
  entitlementDays: number;
  approverRole: "hr" | "assigned_manager";
};

export type LeaveApprover = { employeeId: string; approverUserId: string };
export type LeaveManager = { id: string; name: string };

export type LeaveRequestInput = {
  employeeId: string;
  leaveType: LeaveType;
  leaveCategory: LeaveCategory;
  startDate: string;
  endDate: string;
  startTime?: string | null;
  endTime?: string | null;
  halfDaySession?: HalfDaySession | null;
  reason?: string | null;
};

export type LeaveReviewInput = {
  status: "approved" | "rejected";
  reviewNote?: string | null;
};
