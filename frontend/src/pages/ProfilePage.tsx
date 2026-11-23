import { useState, type FormEvent } from 'react';
import { useLocation } from 'react-router-dom';
import { CalendarDays, Mail, Pencil, Save, ShieldCheck, User as UserIcon } from 'lucide-react';
import { ApiError, api } from '../api/client';
import type { User } from '../api/types';
import { useAuth } from '../auth/AuthContext';
import { Alert, Button, Field, RoleBadges, Toast } from '../components/ui';
import { fmtDate, initials } from '../lib/format';

/** Página protegida para cualquier usuario autenticado (rol USER). */
export function ProfilePage() {
  const { user, updateUser, hasRole } = useAuth();
  const location = useLocation();
  const welcome = (location.state as { welcome?: boolean } | null)?.welcome;
  const [editing, setEditing] = useState(false);
  const [name, setName] = useState(user?.name ?? '');
  const [error, setError] = useState<string | null>(null);
  const [saving, setSaving] = useState(false);
  const [toast, setToast] = useState<string | null>(null);

  if (!user) return null;

  const save = async (e: FormEvent) => {
    e.preventDefault();
    setError(null);
    setSaving(true);
    try {
      const updated = await api.patch<User>('/api/users/me', { name: name.trim() });
      updateUser(updated);
      setEditing(false);
      setToast('Perfil actualizado');
    } catch (err) {
      setError(err instanceof ApiError ? (err.fieldErrors.name ?? err.message) : 'No se pudo guardar');
    } finally {
      setSaving(false);
    }
  };

  return (
    <>
      <div className="page-head">
        <div>
          <h1>Mi perfil</h1>
          <p className="muted">Datos de tu cuenta obtenidos de <code>GET /api/users/me</code></p>
        </div>
      </div>

      {welcome && <div style={{ marginBottom: 16 }}><Alert tone="success">¡Cuenta creada! Ya iniciaste sesión.</Alert></div>}

      <div className="grid-2">
        <section className="card">
          <div className="card-body stack">
            <div className="profile-hero">
              <span className="avatar lg">{initials(user.name)}</span>
              <div className="stack" style={{ gap: 6 }}>
                <h2 style={{ fontSize: '1.25rem' }}>{user.name}</h2>
                <RoleBadges roles={user.roles} />
              </div>
              <span className="spacer" />
              {!editing && <Button variant="secondary" size="sm" icon={Pencil} onClick={() => { setName(user.name); setEditing(true); }}>Editar</Button>}
            </div>

            {editing ? (
              <form className="form" onSubmit={save}>
                {error && <Alert tone="error">{error}</Alert>}
                <Field label="Nombre" icon={UserIcon} value={name} onChange={(e) => setName(e.target.value)} autoFocus />
                <div className="row">
                  <Button type="submit" loading={saving} icon={Save}>Guardar</Button>
                  <Button type="button" variant="ghost" onClick={() => setEditing(false)}>Cancelar</Button>
                </div>
              </form>
            ) : (
              <dl className="kv">
                <dt><Mail size={14} /> Email</dt><dd>{user.email}</dd>
                <dt><CalendarDays size={14} /> Miembro desde</dt><dd>{fmtDate(user.createdAt)}</dd>
                <dt><ShieldCheck size={14} /> Estado</dt><dd><span className="badge badge-green">Activa</span></dd>
                <dt>ID</dt><dd className="mono">{user.id}</dd>
              </dl>
            )}
          </div>
        </section>

        <section className="card">
          <div className="card-head"><h2>Qué puedes hacer</h2></div>
          <div className="card-body stack small">
            <Alert tone="info">
              Esta página requiere un access token válido. Si lo borras o caduca, la app lo renueva con el refresh token
              guardado en una cookie <code>HttpOnly</code> que JavaScript no puede leer.
            </Alert>
            {hasRole('ADMIN') ? (
              <Alert tone="success">Tienes rol <strong>ADMIN</strong>: puedes gestionar usuarios, roles y consultar la auditoría.</Alert>
            ) : (
              <Alert tone="warn">
                Tu rol es <strong>USER</strong>. Si intentas abrir <code>/admin</code> verás un 403: el backend rechaza
                <code>GET /api/admin/users</code> aunque el botón esté oculto.
              </Alert>
            )}
          </div>
        </section>
      </div>
      <Toast message={toast} onDone={() => setToast(null)} />
    </>
  );
}
