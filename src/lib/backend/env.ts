export const backendEnv = {
  url: (import.meta.env.VITE_BACKEND_URL || "").trim().replace(/\/$/, ""),
};

export function isBackendConfigured() {
  return Boolean(backendEnv.url);
}
