import { createContext, useContext, useMemo, useState, type ReactNode } from "react";
import { mockUsers } from "./mock-data";
import { canAccessRoute, canPerform, type AppAction, type AppRouteKey } from "./permissions";

const STORAGE_KEY = "garmentline.currentUserId";

type AuthContextValue = {
  users: typeof mockUsers;
  currentUser: (typeof mockUsers)[number];
  setCurrentUserId: (userId: string) => void;
  canAccess: (routeKey: AppRouteKey) => boolean;
  canDo: (action: AppAction) => boolean;
};

const AuthContext = createContext<AuthContextValue | null>(null);

function getInitialUserId() {
  if (typeof window === "undefined") {
    return mockUsers[1].id;
  }

  return window.localStorage.getItem(STORAGE_KEY) || mockUsers[1].id;
}

export function AuthProvider({ children }: { children: ReactNode }) {
  const [currentUserId, setCurrentUserIdState] = useState(getInitialUserId);

  const currentUser =
    mockUsers.find((user) => user.id === currentUserId) || mockUsers[1];

  const value = useMemo<AuthContextValue>(
    () => ({
      users: mockUsers,
      currentUser,
      setCurrentUserId: (userId) => {
        setCurrentUserIdState(userId);
        if (typeof window !== "undefined") {
          window.localStorage.setItem(STORAGE_KEY, userId);
        }
      },
      canAccess: (routeKey) => canAccessRoute(currentUser.role, routeKey),
      canDo: (action) => canPerform(currentUser.role, action),
    }),
    [currentUser]
  );

  return <AuthContext.Provider value={value}>{children}</AuthContext.Provider>;
}

export function useAuth() {
  const context = useContext(AuthContext);

  if (!context) {
    throw new Error("useAuth must be used within AuthProvider");
  }

  return context;
}
