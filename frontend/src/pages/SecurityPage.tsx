import { useEffect, useState, type FormEvent } from 'react';
import { useNavigate } from 'react-router-dom';
import { KeyRound, Laptop, Lock, LogOut, RefreshCw, Smartphone, Terminal } from 'lucide-react';
import { ApiError, api, decodeJwt, getAccessToken } from '../api/client';
import type { Message, SessionInfo } from '../api/types';
import { useAuth } from '../auth/AuthContext';
import { Alert, Button, Empty, Field, PasswordChecklist, Spinner, Toast, passwordIsValid } from '../components/ui';
import { fmtDateTime, relative } from '../lib/format';
import { useAsync } from '../lib/useAsync';

export function SecurityPage() {
  const [toast, setToast] = useState<string | null>(null);
  const sessions = useAsync(() => api.get<SessionInfo[]>('/api/users/me/sessions'), []);

  return (
    <>
      <div className="page-head">
        <div>
          <h1>Seguridad y sesiones</h1>
          <p className="muted">Tu token actual, los dispositivos con sesión abierta y tu contraseña.</p>
        </div>
      </div>
      <div className="stack" style={{ gap: 18 }}>
        <TokenInspector onRefreshed={() => { setToast('Token renovado: el refresh token anterior quedó invalidado'); sessions.reload(); }} />
        <div className="grid-2">
          <SessionsCard {...sessions} onToast={setToast} />
          <ChangePasswordCard onDone={(msg) => { setToast(msg); sessions.reload(); }} />
        </div>
      </div>
      <Toast message={toast} onDone={() => setToast(null)} />
    </>
  );
}

/** Muestra el JWT actual decodificado: demuestra que el payload es legible (codificado, no cifrado). */
function TokenInspector({ onRefreshed }: { onRefreshed: () => void }) {
  const { expiresAt, refreshNow } = useAuth();
  const [now, setNow] = useState(Date.now());
  const [busy, setBusy] = useState(false);
  const token = getAccessToken();
  const decoded = token ? decodeJwt(token) : null;

  useEffect(() => {
    const t = window.setInterval(() => setNow(Date.now()), 1000);
    return () => window.clearInterval(t);
  }, []);

  const exp = expiresAt ? new Date(expiresAt).getTime() : 0;
  const iat = typeof decoded?.payload.iat === 'number' ? decoded.payload.iat * 1000 : exp - 900_000;
  const left = Math.max(0, Math.round((exp - now) / 1000));
  const pct = exp > iat ? Math.max(0, Math.min(100, ((exp - now) / (exp - iat)) * 100)) : 0;
  const [h, p, s] = (token ?? '..').split('.');

  const renew = async () => {
    setBusy(true);
    try {
      if (await refreshNow()) onRefreshed();
    } finally {
      setBusy(false);
    }
  };

  return (
    <section className="card">
      <div className="card-head">
        <div>
          <h2>Access token actual</h2>
          <p className="muted small">Vive solo en memoria. El payload se puede leer: por eso no lleva email ni datos sensibles.</p>
        </div>
        <Button variant="secondary" size="sm" icon={RefreshCw} loading={busy} onClick={renew}>Renovar ahora</Button>
      </div>
      <div className="card-body stack">
        <div className="row small">
          <span className="muted">Expira {expiresAt ? relative(expiresAt) : '—'}</span>
          <span className="spacer" />
          <span className="mono">{Math.floor(left / 60)}:{String(left % 60).padStart(2, '0')}</span>
        </div>
        <div className="countdown"><span style={{ width: `${pct}%` }} /></div>
        <div className="token-raw" aria-label="JWT codificado">
          <span className="h">{h}</span>.<span className="p">{p}</span>.<span className="s">{s}</span>
        </div>
        <div className="grid-2">
          <div className="stack" style={{ gap: 6 }}>
            <h3>Header</h3>
            <pre className="json">{JSON.stringify(decoded?.header, null, 2)}</pre>
          </div>
          <div className="stack" style={{ gap: 6 }}>
            <h3>Payload</h3>
            <pre className="json">{JSON.stringify(decoded?.payload, null, 2)}</pre>
          </div>
        </div>
        <p className="hint">
          <code>sub</code> = id de usuario · <code>sid</code> = sesión (se revisa en cada petición, por eso el logout surte efecto al
          instante) · <code>aud</code>/<code>iss</code> se validan en el servidor · la firma HS256 usa una clave que solo conoce el backend.
        </p>
      </div>
    </section>
  );
}

function deviceIcon(label?: string) {
  if (!label) return Laptop;
  if (/Android|iOS/.test(label)) return Smartphone;
  if (/Postman|curl|HTTP/.test(label)) return Terminal;
  return Laptop;
}

function SessionsCard({ data, loading, error, reload, onToast }: {
  data: SessionInfo[] | null; loading: boolean; error: string | null; reload: () => void; onToast: (m: string) => void;
}) {
  const { logout } = useAuth();
  const navigate = useNavigate();
  const [busyId, setBusyId] = useState<string | null>(null);

  const revoke = async (s: SessionInfo) => {
    setBusyId(s.id);
    try {
      if (s.current) {
        await logout();
        navigate('/login', { replace: true, state: { loggedOut: true } });
        return;
      }
      await api.del<void>(`/api/users/me/sessions/${s.id}`);
      onToast(`Sesión cerrada en ${s.deviceLabel ?? 'el dispositivo'}`);
      reload();
    } catch (err) {
      onToast(err instanceof ApiError ? err.message : 'No se pudo cerrar la sesión');
    } finally {
      setBusyId(null);
    }
  };

  return (
    <section className="card">
      <div className="card-head">
        <div>
          <h2>Sesiones activas</h2>
          <p className="muted small">Cada inicio de sesión crea una sesión revocable de forma independiente.</p>
        </div>
      </div>
      {loading && !data ? (
        <div className="empty"><Spinner /></div>
      ) : error ? (
        <div className="card-body"><Alert tone="error">{error}</Alert></div>
      ) : !data?.length ? (
        <Empty icon={Laptop}>No hay sesiones activas</Empty>
      ) : (
        <div className="session-list">
          {data.map((s) => {
            const Icon = deviceIcon(s.deviceLabel);
            return (
              <div key={s.id} className={`session${s.current ? ' current' : ''}`}>
                <span className="session-icon"><Icon size={18} /></span>
                <div style={{ minWidth: 0 }}>
                  <div className="row" style={{ gap: 8 }}>
                    <span className="strong">{s.deviceLabel ?? 'Dispositivo'}</span>
                    {s.current && <span className="badge badge-teal">Esta sesión</span>}
                  </div>
                  <div className="muted small">
                    {s.ipAddress} · activa {relative(s.lastUsedAt)} · inicio {fmtDateTime(s.createdAt)}
                  </div>
                </div>
                <span className="spacer" />
                <Button variant={s.current ? 'secondary' : 'danger'} size="sm" icon={LogOut}
                  loading={busyId === s.id} onClick={() => revoke(s)}>
                  {s.current ? 'Salir' : 'Cerrar'}
                </Button>
              </div>
            );
          })}
        </div>
      )}
    </section>
  );
}

function ChangePasswordCard({ onDone }: { onDone: (message: string) => void }) {
  const [form, setForm] = useState({ currentPassword: '', newPassword: '', confirm: '' });
  const [errors, setErrors] = useState<Record<string, string>>({});
  const [error, setError] = useState<string | null>(null);
  const [saving, setSaving] = useState(false);
  const set = (k: keyof typeof form) => (e: React.ChangeEvent<HTMLInputElement>) => setForm({ ...form, [k]: e.target.value });

  const submit = async (e: FormEvent) => {
    e.preventDefault();
    setError(null);
    const local: Record<string, string> = {};
    if (!form.currentPassword) local.currentPassword = 'Escribe tu contraseña actual';
    if (!passwordIsValid(form.newPassword)) local.newPassword = 'La contraseña no cumple los requisitos';
    if (form.confirm !== form.newPassword) local.confirm = 'Las contraseñas no coinciden';
    setErrors(local);
    if (Object.keys(local).length) return;

    setSaving(true);
    try {
      const res = await api.post<Message>('/api/users/me/password', { currentPassword: form.currentPassword, newPassword: form.newPassword });
      setForm({ currentPassword: '', newPassword: '', confirm: '' });
      onDone(res.message);
    } catch (err) {
      if (err instanceof ApiError) {
        setErrors(err.code === 'INVALID_CURRENT_PASSWORD' ? { currentPassword: err.message } : err.fieldErrors);
        if (err.code !== 'INVALID_CURRENT_PASSWORD') setError(err.message);
      } else setError('No se pudo cambiar la contraseña');
    } finally {
      setSaving(false);
    }
  };

  return (
    <section className="card">
      <div className="card-head">
        <div>
          <h2>Cambiar contraseña</h2>
          <p className="muted small">Se cerrarán tus sesiones en otros dispositivos.</p>
        </div>
      </div>
      <form className="card-body form" onSubmit={submit} noValidate>
        {error && <Alert tone="error">{error}</Alert>}
        <Field label="Contraseña actual" icon={Lock} type="password" autoComplete="current-password" value={form.currentPassword} onChange={set('currentPassword')} error={errors.currentPassword} />
        <Field label="Nueva contraseña" icon={KeyRound} type="password" autoComplete="new-password" value={form.newPassword} onChange={set('newPassword')} error={errors.newPassword} />
        <PasswordChecklist value={form.newPassword} />
        <Field label="Confirmar nueva contraseña" icon={KeyRound} type="password" autoComplete="new-password" value={form.confirm} onChange={set('confirm')} error={errors.confirm} />
        <div><Button type="submit" loading={saving} icon={KeyRound}>Actualizar contraseña</Button></div>
      </form>
    </section>
  );
}
