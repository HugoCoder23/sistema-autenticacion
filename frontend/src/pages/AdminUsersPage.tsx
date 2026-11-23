import { useEffect, useState } from 'react';
import { Activity, Ban, CheckCircle2, Search, ShieldAlert, ShieldCheck, ShieldMinus, ShieldPlus, UserCheck, Users, type LucideIcon } from 'lucide-react';
import { ApiError, api } from '../api/client';
import type { Page, Stats, User } from '../api/types';
import { useAuth } from '../auth/AuthContext';
import { Alert, Button, Empty, Pagination, RoleBadges, Spinner, Toast } from '../components/ui';
import { fmtDate, initials } from '../lib/format';
import { useAsync } from '../lib/useAsync';

/** Página de administración (solo ADMIN): demuestra la autorización por roles. */
export function AdminUsersPage() {
  const { user: me } = useAuth();
  const [query, setQuery] = useState('');
  const [debounced, setDebounced] = useState('');
  const [status, setStatus] = useState('');
  const [page, setPage] = useState(0);
  const [busy, setBusy] = useState<number | null>(null);
  const [toast, setToast] = useState<{ msg: string; tone: 'success' | 'error' } | null>(null);

  useEffect(() => {
    const t = window.setTimeout(() => { setDebounced(query); setPage(0); }, 300);
    return () => window.clearTimeout(t);
  }, [query]);

  const stats = useAsync(() => api.get<Stats>('/api/admin/stats'), []);
  const users = useAsync(
    () => api.get<Page<User>>('/api/admin/users', { q: debounced, enabled: status, page, size: 10 }),
    [debounced, status, page],
  );

  const act = async (u: User, fn: () => Promise<User>, msg: string) => {
    setBusy(u.id);
    try {
      const updated = await fn();
      users.setData((d) => d && { ...d, content: d.content.map((x) => (x.id === updated.id ? updated : x)) });
      stats.reload();
      setToast({ msg, tone: 'success' });
    } catch (err) {
      setToast({ msg: err instanceof ApiError ? err.message : 'No se pudo completar la acción', tone: 'error' });
    } finally {
      setBusy(null);
    }
  };

  const toggleAdmin = (u: User) => {
    const isAdmin = u.roles.includes('ADMIN');
    const roles = isAdmin ? ['USER'] : ['USER', 'ADMIN'];
    return act(u, () => api.put<User>(`/api/admin/users/${u.id}/roles`, { roles }),
      isAdmin ? `${u.name} ya no es administrador. Sus sesiones se cerraron` : `${u.name} ahora es administrador. Sus sesiones se cerraron`);
  };

  const toggleEnabled = (u: User) =>
    act(u, () => api.patch<User>(`/api/admin/users/${u.id}/status`, { enabled: !u.enabled }),
      u.enabled ? `Cuenta de ${u.name} deshabilitada y sesiones cerradas` : `Cuenta de ${u.name} habilitada`);

  const s = stats.data;
  return (
    <>
      <div className="page-head">
        <div>
          <h1>Usuarios y roles</h1>
          <p className="muted">Solo <span className="badge badge-violet">ADMIN</span>. Protegido en el backend con <code>hasRole('ADMIN')</code>.</p>
        </div>
      </div>

      <div className="grid-stats">
        <StatCard icon={Users} tone="teal" label="Usuarios" value={s?.totalUsers} />
        <StatCard icon={UserCheck} tone="green" label="Habilitados" value={s?.enabledUsers} />
        <StatCard icon={ShieldCheck} tone="violet" label="Administradores" value={s?.admins} />
        <StatCard icon={Activity} tone="blue" label="Sesiones activas" value={s?.activeSessions} />
        <StatCard icon={ShieldAlert} tone="red" label="Logins fallidos (24 h)" value={s?.failedLogins24h} />
      </div>

      <section className="card">
        <div className="toolbar">
          <div className="input-wrap">
            <Search size={16} />
            <input className="input" placeholder="Buscar por nombre o email" value={query} onChange={(e) => setQuery(e.target.value)} aria-label="Buscar usuarios" />
          </div>
          <select className="input" value={status} onChange={(e) => { setStatus(e.target.value); setPage(0); }} aria-label="Filtrar por estado">
            <option value="">Todos los estados</option>
            <option value="true">Habilitados</option>
            <option value="false">Deshabilitados</option>
          </select>
          {users.loading && <Spinner />}
        </div>

        {users.error ? (
          <div className="card-body"><Alert tone="error">{users.error}</Alert></div>
        ) : users.data && users.data.content.length === 0 ? (
          <Empty icon={Search}>No hay usuarios que coincidan con la búsqueda</Empty>
        ) : (
          <div className="table-wrap">
            <table>
              <thead>
                <tr><th>Usuario</th><th>Roles</th><th>Estado</th><th>Alta</th><th style={{ textAlign: 'right' }}>Acciones</th></tr>
              </thead>
              <tbody>
                {(users.data?.content ?? []).map((u) => {
                  const self = u.id === me?.id;
                  return (
                    <tr key={u.id}>
                      <td>
                        <div className="cell-user">
                          <span className="avatar">{initials(u.name)}</span>
                          <div>
                            <div className="strong">{u.name} {self && <span className="muted small">(tú)</span>}</div>
                            <div className="muted small">{u.email}</div>
                          </div>
                        </div>
                      </td>
                      <td><RoleBadges roles={u.roles} /></td>
                      <td>{u.enabled ? <span className="badge badge-green">Habilitada</span> : <span className="badge badge-red">Deshabilitada</span>}</td>
                      <td className="nowrap muted">{fmtDate(u.createdAt)}</td>
                      <td>
                        <div className="row" style={{ justifyContent: 'flex-end', gap: 6, flexWrap: 'nowrap' }}>
                          <Button variant="secondary" size="sm" disabled={self || busy === u.id}
                            icon={u.roles.includes('ADMIN') ? ShieldMinus : ShieldPlus} onClick={() => toggleAdmin(u)}
                            title={self ? 'No puedes quitarte tu propio rol' : undefined}>
                            {u.roles.includes('ADMIN') ? 'Quitar admin' : 'Hacer admin'}
                          </Button>
                          <Button variant={u.enabled ? 'danger' : 'secondary'} size="sm" disabled={self || busy === u.id}
                            icon={u.enabled ? Ban : CheckCircle2} onClick={() => toggleEnabled(u)}>
                            {u.enabled ? 'Deshabilitar' : 'Habilitar'}
                          </Button>
                        </div>
                      </td>
                    </tr>
                  );
                })}
              </tbody>
            </table>
          </div>
        )}
        {users.data && users.data.totalElements > 0 && (
          <Pagination page={users.data.page} totalPages={users.data.totalPages} totalElements={users.data.totalElements} onPage={setPage} />
        )}
      </section>
      <Toast message={toast?.msg ?? null} tone={toast?.tone} onDone={() => setToast(null)} />
    </>
  );
}

function StatCard({ icon: Icon, tone, label, value }: { icon: LucideIcon; tone: string; label: string; value?: number }) {
  return (
    <div className="card stat">
      <span className={`stat-icon tone-${tone}`}><Icon size={19} /></span>
      <div>
        <div className="stat-value">{value ?? <span className="skeleton" style={{ display: 'block', width: 36, height: 22 }} />}</div>
        <div className="stat-label">{label}</div>
      </div>
    </div>
  );
}
