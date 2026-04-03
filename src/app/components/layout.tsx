import { useEffect, useMemo, useState, type ComponentType } from "react";
import { NavLink, Outlet, useLocation } from "react-router";
import {
  AlertTriangle,
  ArrowRightLeft,
  BarChart3,
  BriefcaseBusiness,
  ClipboardCheck,
  LayoutDashboard,
  Menu,
  MonitorPlay,
  ScanFace,
  Settings,
  Shield,
  Tv2,
  Users,
  X,
} from "lucide-react";
import { useAuth } from "../auth";
import { useOperations } from "../operations-context";
import { type AppRouteKey, routeTitles } from "../permissions";

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
        path: "/validation-center",
        routeKey: "validation",
        label: "Validation Center",
        description: "Biometric queue",
        icon: ScanFace,
      },
      {
        path: "/workers",
        routeKey: "workers",
        label: "Workers",
        description: "Profiles & status",
        icon: Users,
      },
      {
        path: "/line-assignment",
        routeKey: "lineAssignment",
        label: "Line Assignment",
        description: "Assign & transfer",
        icon: ArrowRightLeft,
      },
      {
        path: "/production-lines",
        routeKey: "productionLines",
        label: "Production Lines",
        description: "Floor map",
        icon: BriefcaseBusiness,
      },
      {
        path: "/alerts-center",
        routeKey: "alerts",
        label: "Alerts Center",
        description: "Exceptions",
        icon: AlertTriangle,
      },
      {
        path: "/attendance-operations",
        routeKey: "attendance",
        label: "Attendance Ops",
        description: "OT, leave, payout",
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
  {
    label: "Views",
    items: [
      {
        path: "/self-service",
        routeKey: "selfService",
        label: "Self-Service",
        description: "Staff portal",
        icon: MonitorPlay,
      },
      {
        path: "/display-mode",
        routeKey: "display",
        label: "Display Mode",
        description: "TV board",
        icon: Tv2,
      },
      {
        path: "/settings",
        routeKey: "settings",
        label: "Settings",
        description: "Controls",
        icon: Settings,
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
  const { currentUser, users, setCurrentUserId, canAccess } = useAuth();
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

  const activeTitle = useMemo(() => {
    if (location.pathname.startsWith("/workers/")) return routeTitles.workerProfile;
    const flatItems = navSections.flatMap((section) => section.items);
    const matched = flatItems.find((item) => matchesPath(location.pathname, item.path));
    if (matched) return routeTitles[matched.routeKey];
    return "Operations Centre";
  }, [location.pathname]);

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
                    `ops-nav-link ${isActive || matchesPath(location.pathname, item.path) ? "active" : ""}`
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

            <select
              className="ops-topbar-select"
              value={currentUser.id}
              onChange={(event) => setCurrentUserId(event.target.value)}
              style={{ minWidth: 220 }}
            >
              {users.map((user) => (
                <option key={user.id} value={user.id}>
                  {user.name} · {user.title}
                </option>
              ))}
            </select>

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
