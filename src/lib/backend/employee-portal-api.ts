import { backendEnv, isBackendConfigured } from "./env";
import type {
  EmployeePortalAuthResponse,
  EmployeePortalKioskResponse,
  EmployeePortalLeaveInput,
  EmployeePortalSnapshot,
} from "@/types/employee-portal";

const PORTAL_TOKEN_HEADER = "X-Employee-Portal-Token";

function buildPortalUrl(path: string) {
  if (!isBackendConfigured()) {
    throw new Error("VITE_BACKEND_URL is not configured.");
  }
  return `${backendEnv.url}${path}`;
}

async function portalRequest<T>(path: string, options: RequestInit = {}, token?: string | null): Promise<T> {
  const headers: HeadersInit = {
    ...(options.body ? { "Content-Type": "application/json" } : {}),
    ...(token ? { [PORTAL_TOKEN_HEADER]: token } : {}),
    ...(options.headers || {}),
  };

  const response = await fetch(buildPortalUrl(path), {
    ...options,
    headers,
  });

  if (!response.ok) {
    const payload = await safeReadJson(response);
    throw new Error(payload?.message || response.statusText);
  }

  return response.json() as Promise<T>;
}

export function setupEmployeePortalPassword(input: {
  employeeCode: string;
  phoneNumber: string;
  password: string;
}) {
  return portalRequest<EmployeePortalAuthResponse>("/api/employee-portal/auth/setup", {
    method: "POST",
    body: JSON.stringify(input),
  });
}

export function loginEmployeePortal(input: { phoneNumber: string; password: string }) {
  return portalRequest<EmployeePortalAuthResponse>("/api/employee-portal/auth/login", {
    method: "POST",
    body: JSON.stringify(input),
  });
}

export function verifyEmployeePortalOtp(input: { challengeId: string; code: string }) {
  return portalRequest<EmployeePortalAuthResponse>("/api/employee-portal/auth/verify-otp", {
    method: "POST",
    body: JSON.stringify(input),
  });
}

export function getEmployeePortalFromBackend(token: string) {
  return portalRequest<EmployeePortalSnapshot>("/api/employee-portal/me", {}, token);
}

export function getEmployeePortalKioskRecognition(lastEventId?: string | null) {
  const query = lastEventId ? `?lastEventId=${encodeURIComponent(lastEventId)}` : "";
  return portalRequest<EmployeePortalKioskResponse>(
    `/api/employee-portal/kiosk/latest-recognition${query}`
  );
}

export function createEmployeePortalLeaveRequestFromBackend(
  token: string,
  input: EmployeePortalLeaveInput
) {
  return portalRequest<EmployeePortalSnapshot>(
    "/api/employee-portal/leave-requests",
    {
      method: "POST",
      body: JSON.stringify(input),
    },
    token
  );
}

export function logoutEmployeePortal(token: string) {
  return portalRequest<{ ok: boolean }>("/api/employee-portal/auth/logout", { method: "POST" }, token);
}

async function safeReadJson(response: Response) {
  try {
    return (await response.json()) as { message?: string };
  } catch (_error) {
    return null;
  }
}
