import { createContext, useCallback, useContext, useEffect, useMemo, useRef, useState, type ReactNode } from 'react';
import { api, onSessionChange, refreshSession, setSession } from '../api/client';
import type { AuthResponse, Role, User } from '../api/types';

interface AuthState {
  user: User | null;
  /** Expiración del access token actual (para mostrarla en el perfil). */
  expiresAt: string | null;
  ready: boolean;
  login: (email: string, password: string) => Promise<void>;
  register: (name: string, email: string, password: string) => Promise<void>;
  logout: () => Promise<void>;
  refreshNow: () => Promise<boolean>;
  updateUser: (user: User) => void;
  hasRole: (role: Role) => boolean;
}

const AuthContext = createContext<AuthState | null>(null);

/** Margen antes de que caduque el access token para renovarlo de forma proactiva. */
const RENEW_BEFORE_MS = 60_000;

export function AuthProvider({ children }: { children: ReactNode }) {
  const [user, setUser] = useState<User | null>(null);
  const [expiresAt, setExpiresAt] = useState<string | null>(null);
  const [ready, setReady] = useState(false);
  const timer = useRef<number>();

  // El cliente HTTP avisa de cada cambio de sesión (login, refresh, refresh fallido).
  useEffect(() => {
    onSessionChange((session: AuthResponse | null) => {
      setUser(session?.user ?? null);
      setExpiresAt(session?.expiresAt ?? null);
    });
    // Al cargar la app: si hay cookie de refresh válida, se recupera la sesión sin pedir contraseña.
    refreshSession().finally(() => setReady(true));
  }, []);

  // Renovación proactiva un minuto antes de que expire el access token.
  useEffect(() => {
    window.clearTimeout(timer.current);
    if (!expiresAt) return;
    const delay = Math.max(5_000, new Date(expiresAt).getTime() - Date.now() - RENEW_BEFORE_MS);
    timer.current = window.setTimeout(() => void refreshSession(), delay);
    return () => window.clearTimeout(timer.current);
  }, [expiresAt]);

  const login = useCallback(async (email: string, password: string) => {
    const session = await api.post<AuthResponse>('/api/auth/login', { email, password }, { auth: false });
    setSession(session);
  }, []);

  const register = useCallback(async (name: string, email: string, password: string) => {
    await api.post<User>('/api/auth/register', { name, email, password }, { auth: false });
    const session = await api.post<AuthResponse>('/api/auth/login', { email, password }, { auth: false });
    setSession(session);
  }, []);

  const logout = useCallback(async () => {
    try {
      await api.post<void>('/api/auth/logout', undefined, { auth: false });
    } finally {
      setSession(null);
    }
  }, []);

  const refreshNow = useCallback(async () => !!(await refreshSession()), []);
  const updateUser = useCallback((u: User) => setUser(u), []);
  const hasRole = useCallback((role: Role) => !!user && user.roles.includes(role), [user]);

  const value = useMemo(
    () => ({ user, expiresAt, ready, login, register, logout, refreshNow, updateUser, hasRole }),
    [user, expiresAt, ready, login, register, logout, refreshNow, updateUser, hasRole],
  );
  return <AuthContext.Provider value={value}>{children}</AuthContext.Provider>;
}

export function useAuth() {
  const ctx = useContext(AuthContext);
  if (!ctx) throw new Error('useAuth debe usarse dentro de AuthProvider');
  return ctx;
}
