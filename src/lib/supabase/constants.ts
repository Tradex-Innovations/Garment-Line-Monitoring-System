import type {
  AttendanceState,
  ConfidenceLevel,
  ImportStatus,
  ReconciliationStatus,
  SourceType,
  UserRole,
} from "@/types/pipeline";
import {
  ATTENDANCE_STATES,
  CONFIDENCE_LEVELS,
  IMPORT_STATUSES,
  RECONCILIATION_STATUSES,
  SOURCE_TYPES,
  USER_ROLES,
} from "@/types/pipeline";

export const STORAGE_BUCKET = "imports";

export const USER_ROLE_OPTIONS: readonly UserRole[] = USER_ROLES;
export const SOURCE_TYPE_OPTIONS: readonly SourceType[] = SOURCE_TYPES;
export const IMPORT_STATUS_OPTIONS: readonly ImportStatus[] = IMPORT_STATUSES;
export const ATTENDANCE_STATE_OPTIONS: readonly AttendanceState[] = ATTENDANCE_STATES;
export const RECONCILIATION_STATUS_OPTIONS: readonly ReconciliationStatus[] =
  RECONCILIATION_STATUSES;
export const CONFIDENCE_LEVEL_OPTIONS: readonly ConfidenceLevel[] =
  CONFIDENCE_LEVELS;
