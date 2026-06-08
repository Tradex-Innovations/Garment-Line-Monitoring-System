type AuthRouteState = {
  from?: string;
  flash?: string;
  email?: string;
};

function isSafeAppPath(value: string) {
  return value.startsWith("/") && !value.startsWith("//");
}

export function getAuthRouteState(state: unknown): AuthRouteState {
  if (!state || typeof state !== "object") {
    return {};
  }

  return state as AuthRouteState;
}

export function getAuthRedirectPath(state: unknown) {
  const candidate = getAuthRouteState(state).from;

  if (
    !candidate ||
    !isSafeAppPath(candidate) ||
    candidate === "/login" ||
    candidate === "/signup" ||
    candidate === "/sign-up"
  ) {
    return "/";
  }

  return candidate;
}
