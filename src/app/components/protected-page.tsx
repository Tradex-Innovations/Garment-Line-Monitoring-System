import type { ReactNode } from "react";
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
  const { currentUser, canAccess } = useAuth();

  if (!canAccess(routeKey)) {
    return (
      <AccessDeniedState
        title={`${routeTitles[routeKey]} is not available for ${currentUser.title}`}
        description={`The current session is running as ${currentUser.name}. Switch to a role with ${routeTitles[routeKey]} access from the top bar to preview this module.`}
      />
    );
  }

  return <>{children}</>;
}
