import { useEffect, useId, useState, type ButtonHTMLAttributes, type InputHTMLAttributes, type ReactNode } from 'react';
import { AlertCircle, CheckCircle2, Circle, Eye, EyeOff, Info, TriangleAlert, type LucideIcon } from 'lucide-react';
import type { Role } from '../api/types';
import { passwordChecks } from '../lib/format';

export function Spinner() {
  return <span className="spinner" role="status" aria-label="Cargando" />;
}

export function FullPageSpinner() {
  return <div className="full-center"><Spinner /></div>;
}

type Tone = 'error' | 'success' | 'info' | 'warn';
const ALERT_ICONS: Record<Tone, LucideIcon> = { error: AlertCircle, success: CheckCircle2, info: Info, warn: TriangleAlert };

export function Alert({ tone, children }: { tone: Tone; children: ReactNode }) {
  const Icon = ALERT_ICONS[tone];
  return (
    <div className={`alert alert-${tone}`} role={tone === 'error' ? 'alert' : 'status'}>
      <Icon size={17} />
      <div>{children}</div>
    </div>
  );
}

interface ButtonProps extends ButtonHTMLAttributes<HTMLButtonElement> {
  variant?: 'primary' | 'secondary' | 'danger' | 'ghost';
  size?: 'sm';
  block?: boolean;
  loading?: boolean;
  icon?: LucideIcon;
}

export function Button({ variant = 'primary', size, block, loading, icon: Icon, children, disabled, className = '', ...rest }: ButtonProps) {
  const cls = ['btn', `btn-${variant}`, size ? `btn-${size}` : '', block ? 'btn-block' : '', className].filter(Boolean).join(' ');
  return (
    <button className={cls} disabled={disabled || loading} {...rest}>
      {loading ? <Spinner /> : Icon ? <Icon size={size === 'sm' ? 14 : 16} /> : null}
      {children}
    </button>
  );
}

interface FieldProps extends InputHTMLAttributes<HTMLInputElement> {
  label: string;
  icon?: LucideIcon;
  error?: string;
  hint?: ReactNode;
}

export function Field({ label, icon: Icon, error, hint, type, ...rest }: FieldProps) {
  const id = useId();
  const [visible, setVisible] = useState(false);
  const isPassword = type === 'password';
  return (
    <div className="field">
      <label htmlFor={id}>{label}</label>
      <div className="input-wrap">
        {Icon && <Icon size={16} />}
        <input
          id={id}
          className="input"
          type={isPassword && visible ? 'text' : type}
          aria-invalid={!!error}
          aria-describedby={error ? `${id}-err` : undefined}
          style={isPassword ? { paddingRight: 40 } : undefined}
          {...rest}
        />
        {isPassword && (
          <button type="button" className="input-action" onClick={() => setVisible((v) => !v)}
            aria-label={visible ? 'Ocultar contraseña' : 'Mostrar contraseña'}>
            {visible ? <EyeOff size={16} /> : <Eye size={16} />}
          </button>
        )}
      </div>
      {error ? <span id={`${id}-err`} className="field-error">{error}</span> : hint ? <span className="hint">{hint}</span> : null}
    </div>
  );
}

/** Indicador de requisitos de la contraseña, en vivo mientras se escribe. */
export function PasswordChecklist({ value }: { value: string }) {
  const checks = passwordChecks(value);
  const score = checks.filter((c) => c.ok).length;
  const color = score <= 2 ? 'var(--red)' : score < checks.length ? 'var(--amber)' : 'var(--green)';
  return (
    <div className="stack" style={{ gap: 6 }}>
      <div className="strength" aria-hidden><span style={{ width: `${(score / checks.length) * 100}%`, background: color }} /></div>
      <ul className="checklist">
        {checks.map((c) => (
          <li key={c.label} className={c.ok ? 'ok' : ''}>
            {c.ok ? <CheckCircle2 size={13} /> : <Circle size={13} />} {c.label}
          </li>
        ))}
      </ul>
    </div>
  );
}

export const passwordIsValid = (pw: string) => passwordChecks(pw).every((c) => c.ok);

export function RoleBadges({ roles }: { roles: Role[] }) {
  return (
    <span className="row" style={{ gap: 5 }}>
      {roles.map((r) => <span key={r} className={`badge ${r === 'ADMIN' ? 'badge-violet' : 'badge-teal'}`}>{r}</span>)}
    </span>
  );
}

export function Empty({ icon: Icon, children }: { icon: LucideIcon; children: ReactNode }) {
  return <div className="empty"><Icon size={26} /><div>{children}</div></div>;
}

export function Pagination({ page, totalPages, totalElements, onPage }: { page: number; totalPages: number; totalElements: number; onPage: (p: number) => void }) {
  return (
    <div className="pagination">
      <span>{totalElements} resultado{totalElements === 1 ? '' : 's'}</span>
      <div className="row">
        <Button variant="secondary" size="sm" disabled={page <= 0} onClick={() => onPage(page - 1)}>Anterior</Button>
        <span>Página {totalPages === 0 ? 0 : page + 1} de {totalPages}</span>
        <Button variant="secondary" size="sm" disabled={page + 1 >= totalPages} onClick={() => onPage(page + 1)}>Siguiente</Button>
      </div>
    </div>
  );
}

/** Mensaje flotante temporal tras una acción. */
export function Toast({ message, tone = 'success', onDone }: { message: string | null; tone?: Tone; onDone: () => void }) {
  useEffect(() => {
    if (!message) return;
    const t = window.setTimeout(onDone, 3500);
    return () => window.clearTimeout(t);
  }, [message, onDone]);
  if (!message) return null;
  return <div className="toast"><Alert tone={tone}>{message}</Alert></div>;
}
