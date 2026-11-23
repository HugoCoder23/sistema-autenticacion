import { Link } from 'react-router-dom';
import { ShieldX } from 'lucide-react';

export function ForbiddenPage() {
  return (
    <div className="status-page">
      <div className="stack" style={{ alignItems: 'center' }}>
        <ShieldX size={44} color="var(--red)" />
        <div className="code">403</div>
        <h2>No tienes permisos para ver esta página</h2>
        <p className="muted">Esta sección es solo para administradores. El backend también rechaza estas peticiones.</p>
        <Link to="/profile" className="btn btn-secondary">Ir a mi perfil</Link>
      </div>
    </div>
  );
}

export function NotFoundPage() {
  return (
    <div className="status-page">
      <div className="stack" style={{ alignItems: 'center' }}>
        <div className="code">404</div>
        <h2>Página no encontrada</h2>
        <Link to="/" className="btn btn-secondary">Volver al inicio</Link>
      </div>
    </div>
  );
}
