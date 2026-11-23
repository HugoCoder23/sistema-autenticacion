import type { ApiErrorBody, AuthResponse } from './types';

const BASE = import.meta.env.VITE_API_URL ?? '';

export class ApiError extends Error {
  status: number;
  code?: string;
  fieldErrors: Record<string, string>;

  constructor(body: ApiErrorBody) {
    super(body.message);
    this.status = body.status;
    this.code = body.code;
    this.fieldErrors = Object.fromEntries((body.fieldErrors ?? []).map((f) => [f.field, f.message]));
  }
}

/*
 * El access token vive SOLO en memoria (no en localStorage): un XSS no puede robarlo de forma persistente.
 * El refresh token está en una cookie HttpOnly que JavaScript no puede leer; al recargar la página la
 * sesión se recupera con POST /api/auth/refresh.
 */
let accessToken: string | null = null;
let inFlightRefresh: Promise<AuthResponse | null> | null = null;
let sessionListener: (session: AuthResponse | null) => void = () => {};

export function getAccessToken() {
  return accessToken;
}

export function setSession(session: AuthResponse | null) {
  accessToken = session?.accessToken ?? null;
  sessionListener(session);
}

export function onSessionChange(listener: (session: AuthResponse | null) => void) {
  sessionListener = listener;
}

async function callRefresh(): Promise<AuthResponse | null> {
  try {
    const res = await fetch(BASE + '/api/auth/refresh', { method: 'POST', credentials: 'include' });
    return res.ok ? ((await res.json()) as AuthResponse) : null;
  } catch {
    return null;
  }
}

/**
 * Renueva la sesión rotando el refresh token. Las llamadas simultáneas comparten la misma petición y,
 * entre pestañas, Web Locks las serializa: así nunca se envía dos veces el mismo token (lo que el
 * servidor interpretaría como robo y revocaría la sesión).
 */
export function refreshSession(): Promise<AuthResponse | null> {
  if (!inFlightRefresh) {
    const locks = typeof navigator !== 'undefined' ? navigator.locks : undefined;
    const run = locks ? locks.request('auth-refresh', callRefresh) : callRefresh();
    inFlightRefresh = run
      .then((session) => {
        setSession(session);
        return session;
      })
      .finally(() => {
        inFlightRefresh = null;
      });
  }
  return inFlightRefresh;
}

export type Query = Record<string, string | number | boolean | null | undefined>;

export function toQuery(params: Query = {}): string {
  const qs = new URLSearchParams();
  Object.entries(params).forEach(([k, v]) => {
    if (v !== undefined && v !== null && v !== '') qs.set(k, String(v));
  });
  const s = qs.toString();
  return s ? `?${s}` : '';
}

interface Options {
  /** Adjuntar access token y reintentar tras renovar si responde 401. */
  auth?: boolean;
}

async function request<T>(method: string, path: string, body?: unknown, { auth = true }: Options = {}, retried = false): Promise<T> {
  const headers: Record<string, string> = {};
  if (auth && accessToken) headers.Authorization = `Bearer ${accessToken}`;
  if (body !== undefined) headers['Content-Type'] = 'application/json';

  let res: Response;
  try {
    res = await fetch(BASE + path, {
      method,
      headers,
      credentials: 'include',
      body: body === undefined ? undefined : JSON.stringify(body),
    });
  } catch {
    throw new ApiError({ status: 0, code: 'NETWORK', message: 'No se pudo conectar con el servidor' });
  }

  if (res.status === 401 && auth && !retried) {
    const renewed = await refreshSession();
    if (renewed) return request<T>(method, path, body, { auth }, true);
  }

  if (!res.ok) {
    let err: ApiErrorBody;
    try {
      err = await res.json();
    } catch {
      err = {
        status: res.status,
        message: res.status >= 500
          ? 'El servidor no está disponible. Intenta de nuevo en unos segundos'
          : `Error ${res.status}`,
      };
    }
    throw new ApiError({ ...err, status: res.status });
  }
  return res.status === 204 ? (undefined as T) : ((await res.json()) as T);
}

export const api = {
  get: <T>(path: string, params?: Query, opts?: Options) => request<T>('GET', path + toQuery(params), undefined, opts),
  post: <T>(path: string, body?: unknown, opts?: Options) => request<T>('POST', path, body, opts),
  put: <T>(path: string, body?: unknown, opts?: Options) => request<T>('PUT', path, body, opts),
  patch: <T>(path: string, body?: unknown, opts?: Options) => request<T>('PATCH', path, body, opts),
  del: <T>(path: string, opts?: Options) => request<T>('DELETE', path, undefined, opts),
};

/** Decodifica el payload de un JWT para mostrarlo. NO valida la firma: eso solo lo hace el servidor. */
export function decodeJwt(token: string): { header: Record<string, unknown>; payload: Record<string, unknown> } | null {
  try {
    const part = (s: string) => {
      const b64 = s.replace(/-/g, '+').replace(/_/g, '/');
      const bytes = Uint8Array.from(atob(b64.padEnd(b64.length + ((4 - (b64.length % 4)) % 4), '=')), (c) => c.charCodeAt(0));
      return JSON.parse(new TextDecoder().decode(bytes));
    };
    const [h, p] = token.split('.');
    return { header: part(h), payload: part(p) };
  } catch {
    return null;
  }
}
