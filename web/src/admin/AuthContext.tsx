import React, { createContext, useCallback, useContext, useEffect, useMemo, useRef, useState } from 'react';
import { api, refreshAccessToken, setAccessToken, setUnauthorizedHandler } from './api';
import { can } from './permissions';
import type { AuthUser, LoginResponse } from './types';

interface SessionInfo {
  https: boolean;
  accessTtlMinutes: number;
  refreshTtlDays: number;
  maxFailedLogins: number;
  lockMinutes: number;
  secureCookies: boolean;
}

interface AuthContextValue {
  user: AuthUser | null;
  loading: boolean;
  error: string | null;
  session: SessionInfo | null;
  secureConnection: boolean;
  login: (username: string, password: string) => Promise<AuthUser>;
  logout: () => Promise<void>;
  refresh: () => Promise<void>;
  hasPermission: (required: string | string[]) => boolean;
  isAdmin: boolean;
  clearError: () => void;
}

const AuthContext = createContext<AuthContextValue | null>(null);

export function AuthProvider({ children }: { children: React.ReactNode }) {
  const [user, setUser] = useState<AuthUser | null>(null);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);
  const [session, setSession] = useState<SessionInfo | null>(null);
  const [secureConnection, setSecureConnection] = useState(
    typeof window !== 'undefined' ? window.location.protocol === 'https:' : true
  );
  const mounted = useRef(true);

  useEffect(() => {
    mounted.current = true;
    return () => {
      mounted.current = false;
    };
  }, []);

  const clearError = useCallback(() => setError(null), []);

  /** Restaura la sesión al cargar usando la cookie httpOnly de refresco. */
  const bootstrap = useCallback(async () => {
    setLoading(true);
    try {
      const policy = await api.get<SessionInfo>('/api/auth/session-policy').catch(() => null);
      if (policy && mounted.current) {
        setSession(policy);
        setSecureConnection(policy.https || window.location.protocol === 'https:');
      }
      const ok = await refreshAccessToken();
      if (!ok) {
        if (mounted.current) setUser(null);
        return;
      }
      const me = await api.get<{ user: AuthUser; permissions: string[] }>('/api/auth/me');
      if (mounted.current) setUser({ ...me.user, permissions: me.permissions });
    } catch {
      if (mounted.current) setUser(null);
    } finally {
      if (mounted.current) setLoading(false);
    }
  }, []);

  useEffect(() => {
    void bootstrap();
  }, [bootstrap]);

  useEffect(() => {
    setUnauthorizedHandler(() => {
      setAccessToken(null);
      setUser(null);
    });
  }, []);

  const login = useCallback(async (username: string, password: string) => {
    setError(null);
    try {
      const res = await api.post<LoginResponse>('/api/auth/login', { username, password });
      setAccessToken(res.accessToken);
      const me = await api.get<{ user: AuthUser; permissions: string[] }>('/api/auth/me');
      const loaded = { ...me.user, permissions: me.permissions };
      setUser(loaded);
      setSecureConnection(res.https || window.location.protocol === 'https:');
      return loaded;
    } catch (err) {
      const message = err instanceof Error ? err.message : 'No se pudo iniciar sesión';
      setError(message);
      throw err;
    }
  }, []);

  const logout = useCallback(async () => {
    try {
      await api.post('/api/auth/logout');
    } catch {
      /* la sesión se limpia localmente igualmente */
    }
    setAccessToken(null);
    setUser(null);
  }, []);

  const refresh = useCallback(async () => {
    const ok = await refreshAccessToken();
    if (!ok) {
      setUser(null);
      return;
    }
    const me = await api.get<{ user: AuthUser; permissions: string[] }>('/api/auth/me');
    setUser({ ...me.user, permissions: me.permissions });
  }, []);

  const hasPermission = useCallback(
    (required: string | string[]) => can(user?.permissions, required),
    [user]
  );

  const value = useMemo<AuthContextValue>(
    () => ({
      user,
      loading,
      error,
      session,
      secureConnection,
      login,
      logout,
      refresh,
      hasPermission,
      isAdmin: user?.roleCode === 'ADMIN',
      clearError,
    }),
    [user, loading, error, session, secureConnection, login, logout, refresh, hasPermission, clearError]
  );

  return <AuthContext.Provider value={value}>{children}</AuthContext.Provider>;
}

export function useAuth(): AuthContextValue {
  const ctx = useContext(AuthContext);
  if (!ctx) throw new Error('useAuth debe usarse dentro de <AuthProvider>');
  return ctx;
}

/** Hook de conveniencia para el control de permisos en componentes. */
export function usePermission(required: string | string[]): boolean {
  const { hasPermission } = useAuth();
  return hasPermission(required);
}
