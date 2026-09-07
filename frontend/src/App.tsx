import { BrowserRouter, Navigate, Route, Routes, useLocation } from 'react-router-dom';
import type { ReactNode } from 'react';
import { AppProvider, useApp } from './context/AppContext';
import { FeedbackProvider } from './context/FeedbackContext';
import { AppLayout } from './layouts/AppLayout';
import { PlatformLayout } from './layouts/PlatformLayout';
import { canAccessPath, homePath, isSuperAdmin } from './lib/access';
import { LandingPage } from './pages/LandingPage';
import { LoginPage } from './pages/LoginPage';
import { DashboardPage } from './pages/DashboardPage';
import { OrganizationPage } from './pages/OrganizationPage';
import { LecturersPage } from './pages/LecturersPage';
import { UnitsPage } from './pages/UnitsPage';
import { RequestsPage } from './pages/RequestsPage';
import { RecommendationsPage } from './pages/RecommendationsPage';
import { AllocationPage } from './pages/AllocationPage';
import { WorkloadPage } from './pages/WorkloadPage';
import { TimetablePage } from './pages/TimetablePage';
import { ConflictsPage } from './pages/ConflictsPage';
import { ApprovalsPage } from './pages/ApprovalsPage';
import { ImportPage } from './pages/ImportPage';
import { ReportsPage } from './pages/ReportsPage';
import { AdminPage } from './pages/AdminPage';
import {
  PlatformAnalyticsPage,
  PlatformAuditPage,
  PlatformConfigurationPage,
  PlatformDashboardPage,
  PlatformDataPage,
  PlatformFeaturesPage,
  PlatformInstitutionDetailPage,
  PlatformInstitutionsPage,
  PlatformMonitoringPage,
  PlatformSecurityPage,
  PlatformSupportPage,
  PlatformSystemPage,
  PlatformUsersPage,
} from './pages/PlatformPages';

function InstitutionGate({ children }: { children: ReactNode }) {
  const { user } = useApp();
  const location = useLocation();
  if (isSuperAdmin(user.role)) {
    return <Navigate to="/platform/dashboard" replace />;
  }
  if (!canAccessPath(user.role, location.pathname)) {
    return <Navigate to={homePath(user.role)} replace />;
  }
  return <>{children}</>;
}

function SuperAdminGate({ children }: { children: ReactNode }) {
  const { user } = useApp();
  if (!isSuperAdmin(user.role)) {
    return <Navigate to="/dashboard" replace />;
  }
  return <>{children}</>;
}

function AuthHomeRedirect() {
  const { authenticated, user } = useApp();
  if (!authenticated) return <Navigate to="/" replace />;
  return <Navigate to={homePath(user.role)} replace />;
}

export default function App() {
  return (
    <AppProvider>
      <FeedbackProvider>
        <BrowserRouter>
          <Routes>
            <Route path="/" element={<LandingPage />} />
            <Route path="/login" element={<LoginPage />} />

            <Route
              path="/platform"
              element={
                <RequireAuth>
                  <SuperAdminGate>
                    <PlatformLayout />
                  </SuperAdminGate>
                </RequireAuth>
              }
            >
              <Route index element={<Navigate to="dashboard" replace />} />
              <Route path="dashboard" element={<PlatformDashboardPage />} />
              <Route path="institutions" element={<PlatformInstitutionsPage />} />
              <Route path="institutions/:id" element={<PlatformInstitutionDetailPage />} />
              <Route path="users" element={<PlatformUsersPage />} />
              <Route path="analytics" element={<PlatformAnalyticsPage />} />
              <Route path="security" element={<PlatformSecurityPage />} />
              <Route path="audit" element={<PlatformAuditPage />} />
              <Route path="monitoring" element={<PlatformMonitoringPage />} />
              <Route path="features" element={<PlatformFeaturesPage />} />
              <Route path="configuration" element={<PlatformConfigurationPage />} />
              <Route path="data" element={<PlatformDataPage />} />
              <Route path="support" element={<PlatformSupportPage />} />
              <Route path="system" element={<PlatformSystemPage />} />
            </Route>

            <Route
              element={
                <RequireAuth>
                  <AppLayout />
                </RequireAuth>
              }
            >
              <Route path="/dashboard" element={<InstitutionGate><DashboardPage /></InstitutionGate>} />
              <Route path="/organization" element={<InstitutionGate><OrganizationPage /></InstitutionGate>} />
              <Route path="/lecturers" element={<InstitutionGate><LecturersPage /></InstitutionGate>} />
              <Route path="/units" element={<InstitutionGate><UnitsPage /></InstitutionGate>} />
              <Route path="/requests" element={<InstitutionGate><RequestsPage /></InstitutionGate>} />
              <Route path="/recommendations" element={<InstitutionGate><RecommendationsPage /></InstitutionGate>} />
              <Route path="/allocation" element={<InstitutionGate><AllocationPage /></InstitutionGate>} />
              <Route path="/workload" element={<InstitutionGate><WorkloadPage /></InstitutionGate>} />
              <Route path="/timetable" element={<InstitutionGate><TimetablePage /></InstitutionGate>} />
              <Route path="/conflicts" element={<InstitutionGate><ConflictsPage /></InstitutionGate>} />
              <Route path="/approvals" element={<InstitutionGate><ApprovalsPage /></InstitutionGate>} />
              <Route path="/import" element={<InstitutionGate><ImportPage /></InstitutionGate>} />
              <Route path="/reports" element={<InstitutionGate><ReportsPage /></InstitutionGate>} />
              <Route path="/admin" element={<InstitutionGate><AdminPage /></InstitutionGate>} />
            </Route>

            <Route path="*" element={<AuthHomeRedirect />} />
          </Routes>
        </BrowserRouter>
      </FeedbackProvider>
    </AppProvider>
  );
}

function RequireAuth({ children }: { children: ReactNode }) {
  const { authenticated } = useApp();
  if (!authenticated) return <Navigate to="/login" replace />;
  return <>{children}</>;
}
