import { Link, useSearchParams } from 'react-router-dom';
import { ArrowLeft, ExternalLink, Inbox, RefreshCw, Trash2 } from 'lucide-react';
import { api } from '../api/client';
import type { MailMessage } from '../api/types';
import { useAuth } from '../auth/AuthContext';
import { Alert, Button, Empty, Spinner } from '../components/ui';
import { fmtDateTime } from '../lib/format';
import { useAsync } from '../lib/useAsync';

/**
 * Bandeja SIMULADA de desarrollo: muestra los correos que el backend "envió". Solo existe si el backend
 * tiene DEV_OUTBOX_ENABLED=true (perfil local y demo); en producción el endpoint no existe.
 */
export function MailboxPage() {
  const [params, setParams] = useSearchParams();
  const { user } = useAuth();
  const to = params.get('to') ?? '';
  const mails = useAsync(() => api.get<MailMessage[]>('/api/dev/mailbox', { to }, { auth: false }), [to]);

  const clear = async () => {
    await api.del<void>('/api/dev/mailbox', { auth: false });
    mails.reload();
  };

  /** El enlace apunta al frontend configurado en el backend; se abre como ruta interna. */
  const internal = (url: string) => {
    try {
      const u = new URL(url);
      return u.pathname + u.search;
    } catch {
      return url;
    }
  };

  return (
    <div style={{ maxWidth: 820, margin: '0 auto', padding: '28px 16px 48px' }}>
      <div className="page-head">
        <div>
          <h1>Bandeja de correo simulada</h1>
          <p className="muted">Solo desarrollo: así puedes probar la recuperación de contraseña sin un servidor SMTP.</p>
        </div>
        <div className="row">
          <Link to={user ? '/profile' : '/login'} className="btn btn-ghost btn-sm"><ArrowLeft size={14} /> Volver</Link>
          <Button variant="secondary" size="sm" icon={RefreshCw} onClick={mails.reload}>Actualizar</Button>
          <Button variant="danger" size="sm" icon={Trash2} onClick={clear}>Vaciar</Button>
        </div>
      </div>

      <div className="stack">
        <Alert tone="warn">En un sistema real estos correos llegarían al buzón del usuario. Esta página no existe en producción.</Alert>
        {to && (
          <div className="row small">
            <span className="muted">Filtrando por <strong>{to}</strong></span>
            <button className="btn btn-ghost btn-sm" onClick={() => setParams({})}>Ver todos</button>
          </div>
        )}
        {mails.loading && !mails.data ? (
          <div className="empty"><Spinner /></div>
        ) : mails.error ? (
          <Alert tone="error">{mails.error.includes('Ruta') ? 'La bandeja simulada está desactivada en este entorno.' : mails.error}</Alert>
        ) : !mails.data?.length ? (
          <div className="card"><Empty icon={Inbox}>No hay correos. Solicita una recuperación en <Link to="/forgot-password">¿Olvidaste tu contraseña?</Link></Empty></div>
        ) : (
          mails.data.map((m) => (
            <article key={m.id} className="mail">
              <div className="mail-head">
                <div>
                  <div className="strong">{m.subject}</div>
                  <div className="muted small">Para: {m.to} · {fmtDateTime(m.sentAt)}</div>
                </div>
              </div>
              <div className="mail-body">{m.body}</div>
              {m.actionUrl && (
                <div style={{ padding: '0 18px 18px' }}>
                  <Link to={internal(m.actionUrl)} className="btn btn-primary"><ExternalLink size={16} /> Restablecer contraseña</Link>
                </div>
              )}
            </article>
          ))
        )}
      </div>
    </div>
  );
}
