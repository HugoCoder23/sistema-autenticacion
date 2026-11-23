import { useState } from 'react';
import { FileClock } from 'lucide-react';
import { api } from '../api/client';
import type { AuditEvent, Page } from '../api/types';
import { Alert, Empty, Pagination, Spinner } from '../components/ui';
import { AUDIT_LABELS, fmtDateTime } from '../lib/format';
import { useAsync } from '../lib/useAsync';

export function AdminAuditPage() {
  const [type, setType] = useState('');
  const [outcome, setOutcome] = useState('');
  const [page, setPage] = useState(0);
  const events = useAsync(
    () => api.get<Page<AuditEvent>>('/api/admin/audit-events', { type, outcome, page, size: 15 }),
    [type, outcome, page],
  );

  return (
    <>
      <div className="page-head">
        <div>
          <h1>Auditoría</h1>
          <p className="muted">Inicios de sesión y cambios sensibles. Nunca se registran contraseñas, hashes ni tokens.</p>
        </div>
      </div>
      <section className="card">
        <div className="toolbar">
          <select className="input" value={type} onChange={(e) => { setType(e.target.value); setPage(0); }} aria-label="Tipo de evento">
            <option value="">Todos los eventos</option>
            {Object.entries(AUDIT_LABELS).filter(([k]) => k !== 'TOKEN_REFRESH').map(([k, v]) => <option key={k} value={k}>{v}</option>)}
          </select>
          <select className="input" value={outcome} onChange={(e) => { setOutcome(e.target.value); setPage(0); }} aria-label="Resultado">
            <option value="">Cualquier resultado</option>
            <option value="SUCCESS">Éxito</option>
            <option value="FAILURE">Fallo</option>
          </select>
          {events.loading && <Spinner />}
        </div>
        {events.error ? (
          <div className="card-body"><Alert tone="error">{events.error}</Alert></div>
        ) : events.data && events.data.content.length === 0 ? (
          <Empty icon={FileClock}>No hay eventos con esos filtros</Empty>
        ) : (
          <div className="table-wrap">
            <table>
              <thead>
                <tr><th>Fecha</th><th>Evento</th><th>Resultado</th><th>Usuario / email</th><th>Detalle</th><th>Origen</th></tr>
              </thead>
              <tbody>
                {(events.data?.content ?? []).map((e) => (
                  <tr key={e.id}>
                    <td className="nowrap small">{fmtDateTime(e.createdAt)}</td>
                    <td className="nowrap">{AUDIT_LABELS[e.type] ?? e.type}</td>
                    <td>{e.outcome === 'SUCCESS' ? <span className="badge badge-green">Éxito</span> : <span className="badge badge-red">Fallo</span>}</td>
                    <td className="small">
                      {e.userId ? <span className="mono">#{e.userId}</span> : <span className="muted">—</span>}
                      {e.email && <div className="muted">{e.email}</div>}
                      {e.actorId && <div className="muted">por admin #{e.actorId}</div>}
                    </td>
                    <td className="small">{e.detail ?? <span className="muted">—</span>}</td>
                    <td className="small muted">{e.ipAddress}</td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        )}
        {events.data && events.data.totalElements > 0 && (
          <Pagination page={events.data.page} totalPages={events.data.totalPages} totalElements={events.data.totalElements} onPage={setPage} />
        )}
      </section>
    </>
  );
}
