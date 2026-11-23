const dateTime = new Intl.DateTimeFormat('es-MX', { dateStyle: 'medium', timeStyle: 'short' });
const dateOnly = new Intl.DateTimeFormat('es-MX', { dateStyle: 'medium' });

export const fmtDateTime = (iso?: string) => (iso ? dateTime.format(new Date(iso)) : '—');
export const fmtDate = (iso?: string) => (iso ? dateOnly.format(new Date(iso)) : '—');

export function relative(iso: string): string {
  const diff = (new Date(iso).getTime() - Date.now()) / 1000;
  const abs = Math.abs(diff);
  const rtf = new Intl.RelativeTimeFormat('es', { numeric: 'auto' });
  if (abs < 60) return rtf.format(Math.round(diff), 'second');
  if (abs < 3600) return rtf.format(Math.round(diff / 60), 'minute');
  if (abs < 86400) return rtf.format(Math.round(diff / 3600), 'hour');
  return rtf.format(Math.round(diff / 86400), 'day');
}

export function initials(name: string) {
  return name.split(/\s+/).filter(Boolean).slice(0, 2).map((p) => p[0]!.toUpperCase()).join('');
}

/** Requisitos de la política de contraseñas (espejo de @StrongPassword en el backend). */
export function passwordChecks(pw: string) {
  return [
    { ok: pw.length >= 8 && new TextEncoder().encode(pw).length <= 72, label: 'Entre 8 y 72 caracteres' },
    { ok: /\p{Lu}/u.test(pw), label: 'Una mayúscula' },
    { ok: /\p{Ll}/u.test(pw), label: 'Una minúscula' },
    { ok: /\p{Nd}/u.test(pw), label: 'Un número' },
    { ok: pw.length > 0 && pw === pw.trim(), label: 'Sin espacios al inicio o final' },
  ];
}

export const AUDIT_LABELS: Record<string, string> = {
  REGISTER: 'Registro',
  LOGIN: 'Inicio de sesión',
  LOGIN_LOCKED: 'Login bloqueado',
  LOGOUT: 'Cierre de sesión',
  TOKEN_REFRESH: 'Renovación',
  REFRESH_TOKEN_REUSE: 'Reutilización de token',
  SESSION_REVOKED: 'Sesión revocada',
  PASSWORD_CHANGE: 'Cambio de contraseña',
  PASSWORD_RESET_REQUEST: 'Solicitud de recuperación',
  PASSWORD_RESET: 'Contraseña restablecida',
  PROFILE_UPDATE: 'Perfil actualizado',
  ROLES_CHANGE: 'Cambio de roles',
  ACCOUNT_STATUS_CHANGE: 'Estado de cuenta',
};
