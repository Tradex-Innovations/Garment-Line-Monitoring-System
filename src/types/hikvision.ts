export interface HikvisionCameraConfigRequest {
  baseUrl?: string;
  username: string;
  password?: string;
  pollIntervalSeconds?: number;
  lookbackMinutes?: number;
}

export interface HikvisionDeviceInfo {
  deviceName?: string | null;
  deviceId?: string | null;
  model?: string | null;
  serialNumber?: string | null;
  macAddress?: string | null;
  firmwareVersion?: string | null;
}

export interface HikvisionCameraEndpoint {
  id: string;
  name: string;
  location: string;
  baseUrl: string;
  configured: boolean;
  lastPollAt?: string | null;
  lastSuccessAt?: string | null;
  lastError?: string | null;
  deviceInfo?: HikvisionDeviceInfo | null;
}

export interface HikvisionStatus {
  configured: boolean;
  running: boolean;
  baseUrl?: string | null;
  username?: string | null;
  pollIntervalSeconds: number;
  lookbackMinutes: number;
  lastPollAt?: string | null;
  lastSuccessAt?: string | null;
  lastError?: string | null;
  deviceInfo?: HikvisionDeviceInfo | null;
  eventCount: number;
  matchedEventCount: number;
  cameraCount: number;
  onlineCameraCount: number;
  cameras: HikvisionCameraEndpoint[];
}

export interface HikvisionRecognitionEvent {
  id: string;
  cameraId?: string | null;
  cameraName?: string | null;
  cameraLocation?: string | null;
  cameraBaseUrl?: string | null;
  serialNo?: string | null;
  employeeNo?: string | null;
  devicePersonName?: string | null;
  matchedEmployeeId?: string | null;
  matchedEmployeeName?: string | null;
  matchedDepartment?: string | null;
  matchStatus: "matched" | "unmatched";
  eventTime: string;
  receivedAt: string;
  verifyMode?: string | null;
  attendanceStatus?: string | null;
  accessDecision?: string | null;
  pictureUrl?: string | null;
  visibleLightPicUrl?: string | null;
  thermalPicUrl?: string | null;
  temperature?: number | null;
  mask?: string | null;
  major?: number | null;
  minor?: number | null;
  rawPayload?: Record<string, unknown>;
}

export interface HikvisionEventListResponse {
  events: HikvisionRecognitionEvent[];
  status: HikvisionStatus;
}
