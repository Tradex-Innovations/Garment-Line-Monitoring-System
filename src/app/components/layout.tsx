import { useEffect, useMemo, useState, type ComponentType } from "react";
import { Link, NavLink, Outlet, useLocation } from "react-router";
import {
  AlertTriangle,
  BarChart3,
  BriefcaseBusiness,
  ClipboardCheck,
  LayoutDashboard,
  Menu,
  Shield,
  Users,
  X,
} from "lucide-react";
import { useAuth } from "../auth";
import { useOperations } from "../operations-context";
import { type AppRouteKey, routeTitles } from "../permissions";
import { normalizeRouterPathname } from "../router-base";

type NavItem = {
  path: string;
  routeKey: AppRouteKey;
  label: string;
  description: string;
  icon: ComponentType<{ size?: number; className?: string }>;
};

const navSections: Array<{ label: string; items: NavItem[] }> = [
  {
    label: "Operations",
    items: [
      {
        path: "/",
        routeKey: "dashboard",
        label: "Dashboard",
        description: "Live KPIs",
        icon: LayoutDashboard,
      },
      {
        path: "/production-lines",
        routeKey: "productionLines",
        label: "Production Lines",
        description: "Assigned vs came",
        icon: BriefcaseBusiness,
      },
      {
        path: "/workers",
        routeKey: "workers",
        label: "Workers",
        description: "Profiles & attendance",
        icon: Users,
      },
      {
        path: "/alerts-center",
        routeKey: "alerts",
        label: "Alerts Center",
        description: "Exceptions",
        icon: AlertTriangle,
      },
      {
        path: "/incentive-calculation",
        routeKey: "attendance",
        label: "Incentive Calculation",
        description: "Attendance & payouts",
        icon: ClipboardCheck,
      },
    ],
  },
  {
    label: "Reporting",
    items: [
      {
        path: "/reports",
        routeKey: "reports",
        label: "Reports",
        description: "Analytics",
        icon: BarChart3,
      },
      {
        path: "/audit-log",
        routeKey: "audit",
        label: "Audit Log",
        description: "History",
        icon: Shield,
      },
    ],
  },
];

function matchesPath(pathname: string, itemPath: string) {
  if (itemPath === "/") return pathname === "/";
  return pathname === itemPath || pathname.startsWith(`${itemPath}/`);
}

export function Layout() {
  const location = useLocation();
  const { isConfigured, currentUser, canAccess, isAuthenticated, signOut } = useAuth();
  const { alerts } = useOperations();
  const [mobileOpen, setMobileOpen] = useState(false);
  const [now, setNow] = useState(new Date());

  useEffect(() => {
    const timer = window.setInterval(() => setNow(new Date()), 1000);
    return () => window.clearInterval(timer);
  }, []);

  const visibleSections = useMemo(
    () =>
      navSections
        .map((section) => ({
          ...section,
          items: section.items.filter((item) => canAccess(item.routeKey)),
        }))
        .filter((section) => section.items.length),
    [canAccess]
  );

  const openAlerts = alerts.filter((alert) => alert.status !== "Resolved").length;
  const currentPathname = normalizeRouterPathname(location.pathname);

  const activeTitle = useMemo(() => {
    if (currentPathname.startsWith("/workers/")) return routeTitles.workerProfile;
    const flatItems = navSections.flatMap((section) => section.items);
    const matched = flatItems.find((item) => matchesPath(currentPathname, item.path));
    if (matched) return routeTitles[matched.routeKey];
    return "Operations Centre";
  }, [currentPathname]);

  const SidebarContent = (
    <div className="ops-sidebar">
      <div className="ops-sidebar-header">
        <div className="ops-brand">
          <div className="ops-brand-mark">
            <LayoutDashboard size={18} />
          </div>
          <div>
            <div className="ops-brand-title">
              Garment<span>Line</span>
            </div>
            <div className="ops-brand-subtitle">Operations Centre</div>
          </div>
        </div>
      </div>

      <nav className="ops-nav">
        {visibleSections.map((section) => (
          <div key={section.label} className="ops-nav-section">
            <div className="ops-nav-label">{section.label}</div>
            {section.items.map((item) => {
              const Icon = item.icon;
              return (
                <NavLink
                  key={item.path}
                  to={item.path}
                  className={({ isActive }) =>
                    `ops-nav-link ${isActive || matchesPath(currentPathname, item.path) ? "active" : ""}`
                  }
                  onClick={() => setMobileOpen(false)}
                >
                  <Icon size={18} className="ops-nav-link-icon" />
                  <div className="ops-nav-link-body">
                    <div className="ops-nav-link-title">{item.label}</div>
                    <div className="ops-nav-link-meta">{item.description}</div>
                  </div>
                </NavLink>
              );
            })}
          </div>
        ))}
      </nav>

      <div className="ops-sidebar-footer">
        <div className="ops-user-card">
          <div className="ops-user-meta">
            <div className="ops-user-name">{currentUser.name}</div>
            <div className="ops-user-role">{currentUser.title}</div>
          </div>
        </div>
      </div>
    </div>
  );

  return (
    <div className="ops-layout">
      {SidebarContent}

      {mobileOpen ? (
        <div
          className="ops-mobile-sidebar"
          onClick={(event) => {
            if (event.target === event.currentTarget) {
              setMobileOpen(false);
            }
          }}
        >
          {SidebarContent}
        </div>
      ) : null}

      <div className="ops-shell">
        <header className="ops-topbar">
          <div className="ops-topbar-left">
            <button
              type="button"
              className="ops-mobile-toggle"
              onClick={() => setMobileOpen((open) => !open)}
              aria-label="Toggle navigation"
            >
              {mobileOpen ? <X size={16} /> : <Menu size={16} />}
            </button>

            <div>
              <div className="ops-topbar-title">{activeTitle}</div>
              <div className="ops-topbar-subtitle">
                {now.toLocaleDateString("en-GB", {
                  weekday: "long",
                  day: "2-digit",
                  month: "long",
                  year: "numeric",
                })}
              </div>
            </div>
          </div>

          <div className="ops-topbar-right">
            <div className="ops-live-pill">
              <span className="ops-live-dot" /> Live
            </div>
            <span className="ops-badge tone-warning">{openAlerts} notifications</span>
            <span className="ops-badge tone-neutral ops-monospace">
              {now.toLocaleTimeString("en-GB", {
                hour: "2-digit",
                minute: "2-digit",
                second: "2-digit",
              })}
            </span>

            {!isConfigured ? (
              <span className="ops-badge tone-warning">Supabase not configured</span>
            ) : isAuthenticated ? (
              <button className="ops-button ops-button-ghost" onClick={() => void signOut()}>
                Sign Out
              </button>
            ) : (
              <>
                <Link to="/login" className="ops-button ops-button-secondary">
                  Sign In
                </Link>
                <Link to="/sign-up" className="ops-button ops-button-ghost">
                  Sign Up
                </Link>
              </>
            )}

            <div className="ops-user-card">
              <div className="ops-user-avatar">{currentUser.initials}</div>
              <div className="ops-user-meta">
                <div className="ops-user-name">{currentUser.name}</div>
                <div className="ops-user-role">{currentUser.title}</div>
              </div>
            </div>
          </div>
        </header>

        <main className="ops-main">
          <Outlet />
        </main>
      </div>
    </div>
  );
}

export default Layout;
