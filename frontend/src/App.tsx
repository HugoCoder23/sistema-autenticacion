import { Navigate, Route, Routes, useLocation } from 'react-router-dom';
import type { ReactNode } from 'react';
import { useAuth } from './auth/AuthContext';
import type { Role } from './api/types';
import { Layout } from './components/Layout';
import { FullPageSpinner } from './components/ui';
import { LoginPage } from './pages/LoginPage';
import { RegisterPage } from './pages/RegisterPage';
import { ForgotPasswordPage } from './pages/ForgotPasswordPage';
import { ResetPasswordPage } from './pages/ResetPasswordPage';
import { ProfilePage } from './pages/ProfilePage';
import { SecurityPage } from './pages/SecurityPage';
import { AdminUsersPage } from './pages/AdminUsersPage';
import { AdminAuditPage } from './pages/AdminAuditPage';
import { MailboxPage } from './pages/MailboxPage';
import { ForbiddenPage, NotFoundPage } from './pages/StatusPages';

/** Guardas de ruta. Son solo UX: la autorización real la aplica el backend en cada petición. */
function RequireAuth({ role, children }: { role?: Role; children: ReactNode }) {
  const { user, ready, hasRole } = useAuth();
  const location = useLocation();
  if (!ready) return <FullPageSpinner />;
  if (!user) return <Navigate to="/login" replace state={{ from: location.pathname }} />;
  if (role && !hasRole(role)) return <ForbiddenPage />;
  return <>{children}</>;
}

function GuestOnly({ children }: { children: ReactNode }) {
  const { user, ready } = useAuth();
  if (!ready) return <FullPageSpinner />;
  if (user) return <Navigate to="/profile" replace />;
  return <>{children}</>;
}

export default function App() {
  return (
    <Routes>
      <Route path="/login" element={<GuestOnly><LoginPage /></GuestOnly>} />
      <Route path="/register" element={<GuestOnly><RegisterPage /></GuestOnly>} />
      <Route path="/forgot-password" element={<ForgotPasswordPage />} />
      <Route path="/reset-password" element={<ResetPasswordPage />} />
      <Route path="/dev/mailbox" element={<MailboxPage />} />

      <Route element={<RequireAuth><Layout /></RequireAuth>}>
        <Route path="/profile" element={<ProfilePage />} />
        <Route path="/security" element={<SecurityPage />} />
        <Route path="/admin" element={<RequireAuth role="ADMIN"><AdminUsersPage /></RequireAuth>} />
        <Route path="/admin/audit" element={<RequireAuth role="ADMIN"><AdminAuditPage /></RequireAuth>} />
      </Route>

      <Route path="/" element={<Navigate to="/profile" replace />} />
      <Route path="*" element={<NotFoundPage />} />
    </Routes>
  );
}
