import type { HalfDaySession, LeaveCategory, LeaveRequestStatus, LeaveType } from "./leave-management";

export type EmployeePortalProfile = {
  id: string;
  fullName?: string | null;
  role?: string | null;
  employeeId?: string | null;
  employeeCode?: string | null;
};

export type EmployeePortalEmployee = {
  id: string;
  employeeCode: string;
  fullName: string;
  designation?: string | null;
  department?: string | null;
  photoUrl?: string | null;
  shift?: string | null;
  phone?: string | null;
};

export type EmployeePortalLine = {
  id: string;
  code: string;
  name: string;
  department?: string | null;
  shift?: string | null;
  supervisor?: string | null;
  assignedAt?: string | null;
};

export type EmployeePortalAttendance = {
  id: string;
  date: string;
  status: string;
  timeIn?: string | null;
  timeOut?: string | null;
  faceFirstSeen?: string | null;
  faceLastSeen?: string | null;
  otHours?: number | null;
  lateEarlyHours?: number | null;
  leaveType?: string | null;
  exceptionReason?: string | null;
};

export type EmployeePortalLeaveRequest = {
  id: string;
  leaveType: LeaveType;
  leaveCategory: LeaveCategory;
  startDate: string;
  endDate: string;
  startTime?: string | null;
  endTime?: string | null;
  halfDaySession?: HalfDaySession | null;
  reason?: string | null;
  status: LeaveRequestStatus;
  reviewNote?: string | null;
  requestedAt: string;
  reviewedAt?: string | null;
  dayCount: number;
};

export type EmployeePortalIncentive = {
  id: string;
  monthStart: string;
  amount?: number | null;
  reason?: string | null;
};

export type EmployeePortalLeaveBalance = {
  allowanceDays: number;
  usedDays: number;
  remainingDays: number;
};

export type EmployeePortalSnapshot = {
  linked: boolean;
  profile: EmployeePortalProfile;
  employee: EmployeePortalEmployee | null;
  currentLine: EmployeePortalLine | null;
  attendanceHistory: EmployeePortalAttendance[];
  leaveRequests: EmployeePortalLeaveRequest[];
  incentives: EmployeePortalIncentive[];
  leaveBalance: EmployeePortalLeaveBalance;
};

export type EmployeePortalOtpChallenge = {
  status: "otp_required";
  challengeId: string;
  maskedPhone: string;
  weekStart: string;
  expiresAt: string;
  message?: string;
  deliveryMode?: string;
  developmentOtp?: string;
};

export type EmployeePortalAuthenticated = {
  status: "authenticated";
  token: string;
  expiresAt: string;
  snapshot: EmployeePortalSnapshot;
};

export type EmployeePortalAuthResponse =
  | EmployeePortalOtpChallenge
  | EmployeePortalAuthenticated;

export type EmployeePortalKioskRecognition = {
  eventId: string;
  eventTime: string;
  cameraSerialNo?: string | null;
  employeeCode?: string | null;
  employeeName?: string | null;
  department?: string | null;
  devicePersonName?: string | null;
  verifyMode?: string | null;
  attendanceStatus?: string | null;
  accessDecision?: string | null;
  pictureUrl?: string | null;
};

export type EmployeePortalKioskIdle = {
  status: "idle";
  lastEventId?: string | null;
  message?: string | null;
};

export type EmployeePortalKioskRecognized = {
  status: "recognized";
  eventId: string;
  eventTime: string;
  expiresAt: string;
  token: string;
  recognition: EmployeePortalKioskRecognition;
  snapshot: EmployeePortalSnapshot;
};

export type EmployeePortalKioskResponse =
  | EmployeePortalKioskIdle
  | EmployeePortalKioskRecognized;

export type EmployeePortalLeaveInput = {
  leaveType: LeaveType;
  leaveCategory: LeaveCategory;
  startDate: string;
  endDate: string;
  startTime?: string | null;
  endTime?: string | null;
  halfDaySession?: HalfDaySession | null;
  reason?: string | null;
};
