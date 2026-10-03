import React from 'react';
import { Navigate, Route, Routes } from 'react-router-dom';
import { AuthProvider } from './AuthContext';
import { RequireAuth, RequirePermission } from './components/Guard';
import { AdminLayout } from './components/AdminLayout';
import { LoginPage } from './pages/LoginPage';
import { DashboardPage } from './pages/DashboardPage';
import { LicensesPage } from './pages/LicensesPage';
import { DevicesPage } from './pages/DevicesPage';
import { KeysPage } from './pages/KeysPage';
import { PlansPage } from './pages/PlansPage';
import { RatesPage } from './pages/RatesPage';
import { UsersPage } from './pages/UsersPage';
import { RolesPage } from './pages/RolesPage';
import { AuditPage } from './pages/AuditPage';
import { SettingsPage } from './pages/SettingsPage';

/**
 * Consola profesional de control de Keygen.
 *
 * Rutas montadas bajo `/admin`:
 *   /admin/login     → acceso por credenciales (público)
 *   /admin           → panel con KPIs               [dashboard.view]
 *   /admin/licenses  → emisión / renovación / revocación
 *   /admin/devices   → huella de dispositivos
 *   /admin/keys      → claves de firma RSA por app
 *   /admin/plans     → planes y precios
 *   /admin/rates     → tasas de cambio
 *   /admin/users     → usuarios
 *   /admin/roles     → matriz RBAC
 *   /admin/audit     → auditoría
 *   /admin/settings  → cuenta, contraseña y estado del servicio
 */
export function AdminConsole() {
  return (
    <AuthProvider>
      <Routes>
        <Route path="/admin/login" element={<LoginPage />} />

        <Route
          path="/admin"
          element={
            <RequireAuth>
              <AdminLayout />
            </RequireAuth>
          }
        >
          <Route
            index
            element={
              <RequirePermission permission="dashboard.view">
                <DashboardPage />
              </RequirePermission>
            }
          />
          <Route
            path="licenses"
            element={
              <RequirePermission permission="licenses.view">
                <LicensesPage />
              </RequirePermission>
            }
          />
          <Route
            path="devices"
            element={
              <RequirePermission permission="licenses.view">
                <DevicesPage />
              </RequirePermission>
            }
          />
          <Route
            path="keys"
            element={
              <RequirePermission permission="keys.view">
                <KeysPage />
              </RequirePermission>
            }
          />
          <Route
            path="plans"
            element={
              <RequirePermission permission="licenses.view">
                <PlansPage />
              </RequirePermission>
            }
          />
          <Route
            path="rates"
            element={
              <RequirePermission permission="rates.view">
                <RatesPage />
              </RequirePermission>
            }
          />
          <Route
            path="users"
            element={
              <RequirePermission permission="users.view">
                <UsersPage />
              </RequirePermission>
            }
          />
          <Route
            path="roles"
            element={
              <RequirePermission permission="roles.view">
                <RolesPage />
              </RequirePermission>
            }
          />
          <Route
            path="audit"
            element={
              <RequirePermission permission="audit.view">
                <AuditPage />
              </RequirePermission>
            }
          />
          <Route path="settings" element={<SettingsPage />} />
          <Route path="*" element={<Navigate to="/admin" replace />} />
        </Route>
      </Routes>
    </AuthProvider>
  );
}

export default AdminConsole;
