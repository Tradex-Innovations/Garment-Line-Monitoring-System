import { type ReactNode } from "react";
import { Navigate, useLocation } from "react-router";
import { useAuth } from "../auth";
import { routeTitles, type AppRouteKey } from "../permissions";
import { AccessDeniedState } from "./ops-ui";

export function ProtectedPage({
  routeKey,
  children,
}: {
  routeKey: AppRouteKey;
  children: ReactNode;
}) {
  const {
    currentUser,
    canAccess,
    loading,
    isAuthenticated,
    isConfigured,
  } = useAuth();
  const location = useLocation();

  if (!isConfigured) {
    return (
      <AccessDeniedState
        title="Supabase configuration required"
        description="Set the Vite Supabase environment variables and run the Supabase migrations before using the live operations workspace."
      />
    );
  }

  if (loading) {
    return (
      <AccessDeniedState
        title="Loading secure session"
        description="The application is checking your Supabase session and profile before opening the requested module."
      />
    );
  }

  if (!isAuthenticated) {
    return (
      <Navigate
        to="/login"
        replace
        state={{ from: `${location.pathname}${location.search}` }}
      />
    );
  }

  if (!canAccess(routeKey)) {
    return (
      <AccessDeniedState
        title={`${routeTitles[routeKey]} is not available for ${currentUser.title}`}
        description={`The current session is running as ${currentUser.name}. Sign in with a profile that has ${routeTitles[routeKey]} access to open this module.`}
      />
    );
  }

  return <>{children}</>;
}
