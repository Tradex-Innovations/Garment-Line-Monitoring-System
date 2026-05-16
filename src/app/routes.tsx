import { createBrowserRouter, Navigate } from "react-router";
import { Layout } from "./components/layout";
import { ProtectedPage } from "./components/protected-page";
import { AlertsCenterPage } from "./pages/alerts-center-page";
import { AttendanceOperationsPage } from "./pages/attendance-operations-page";
import { AuditLogPage } from "./pages/audit-log-page";
import { DashboardPage } from "./pages/dashboard-page";
import { DisplayModePage } from "./pages/display-mode-page";
import { ImportCenterPage } from "./pages/import-center-page";
import { HikvisionFacePage } from "./pages/hikvision-face-page";
import { LineAssignmentPage } from "./pages/line-assignment-page";
import { LoginPage } from "./pages/login-page";
import { ProductionLineDetailPage } from "./pages/production-line-detail-page";
import { ProductionLineFloorPlanPage } from "./pages/production-line-floor-plan-page";
import { ProductionLinesPage } from "./pages/production-lines-page";
import { ReportsPage } from "./pages/reports-page";
import { SelfServicePage } from "./pages/self-service-page";
import { SettingsPage } from "./pages/settings-page";
import { SignUpPage } from "./pages/sign-up-page";
import { ValidationCenterPage } from "./pages/validation-center-page";
import { WorkerProfilePage } from "./pages/worker-profile-page";
import { WorkersPage } from "./pages/workers-page";
import { routerBasename } from "./router-base";

export const router = createBrowserRouter(
  [
    {
      path: "/display-mode",
      element: (
        <ProtectedPage routeKey="display">
          <DisplayModePage />
        </ProtectedPage>
      ),
    },
    {
      path: "/login",
      element: <LoginPage />,
    },
    {
      path: "/sign-up",
      element: <SignUpPage />,
    },
    {
      path: "/signup",
      element: <Navigate to="/sign-up" replace />,
    },
    {
      path: "/",
      Component: Layout,
      children: [
        {
          index: true,
          element: (
            <ProtectedPage routeKey="dashboard">
              <DashboardPage />
            </ProtectedPage>
          ),
        },
        {
          path: "imports",
          element: (
            <ProtectedPage routeKey="imports">
              <ImportCenterPage />
            </ProtectedPage>
          ),
        },
        {
          path: "validation-center",
          element: (
            <ProtectedPage routeKey="validation">
              <ValidationCenterPage />
            </ProtectedPage>
          ),
        },
        {
          path: "workers",
          element: (
            <ProtectedPage routeKey="workers">
              <WorkersPage />
            </ProtectedPage>
          ),
        },
        {
          path: "workers/:workerId",
          element: (
            <ProtectedPage routeKey="workerProfile">
              <WorkerProfilePage />
            </ProtectedPage>
          ),
        },
        {
          path: "hikvision-face",
          element: (
            <ProtectedPage routeKey="hikvision">
              <HikvisionFacePage />
            </ProtectedPage>
          ),
        },
        {
          path: "line-assignment",
          element: (
            <ProtectedPage routeKey="lineAssignment">
              <LineAssignmentPage />
            </ProtectedPage>
          ),
        },
        {
          path: "production-lines",
          element: (
            <ProtectedPage routeKey="productionLines">
              <ProductionLinesPage />
            </ProtectedPage>
          ),
        },
        {
          path: "production-lines/:lineId/floor-plan",
          element: (
            <ProtectedPage routeKey="productionLines">
              <ProductionLineFloorPlanPage />
            </ProtectedPage>
          ),
        },
        {
          path: "production-lines/:lineId",
          element: (
            <ProtectedPage routeKey="productionLines">
              <ProductionLineDetailPage />
            </ProtectedPage>
          ),
        },
        {
          path: "alerts-center",
          element: (
            <ProtectedPage routeKey="alerts">
              <AlertsCenterPage />
            </ProtectedPage>
          ),
        },
        {
          path: "incentive-calculation",
          element: (
            <ProtectedPage routeKey="attendance">
              <AttendanceOperationsPage />
            </ProtectedPage>
          ),
        },
        {
          path: "attendance-operations",
          element: <Navigate to="/incentive-calculation" replace />,
        },
        {
          path: "reports",
          element: (
            <ProtectedPage routeKey="reports">
              <ReportsPage />
            </ProtectedPage>
          ),
        },
        {
          path: "audit-log",
          element: (
            <ProtectedPage routeKey="audit">
              <AuditLogPage />
            </ProtectedPage>
          ),
        },
        {
          path: "self-service",
          element: (
            <ProtectedPage routeKey="selfService">
              <SelfServicePage />
            </ProtectedPage>
          ),
        },
        {
          path: "settings",
          element: (
            <ProtectedPage routeKey="settings">
              <SettingsPage />
            </ProtectedPage>
          ),
        },
        {
          path: "*",
          element: <Navigate to="/" replace />,
        },
      ],
    },
    {
      path: "*",
      element: <Navigate to="/" replace />,
    },
  ],
  {
    basename: routerBasename,
  }
);
