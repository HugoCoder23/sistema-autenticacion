import type { ReactNode } from 'react';
import { CheckCircle2, ShieldCheck } from 'lucide-react';

const FEATURES = [
  'Contraseñas con hash BCrypt, nunca en texto plano',
  'Access token JWT de 15 minutos firmado con HS256',
  'Refresh token rotatorio en cookie HttpOnly, revocable',
  'Roles USER y ADMIN aplicados en el backend',
  'Recuperación con token de un solo uso y vencimiento',
  'Límite de intentos y auditoría sin secretos',
];

/** Pantallas públicas: panel de marca a la izquierda y tarjeta del formulario a la derecha. */
export function AuthShell({ title, subtitle, children }: { title: string; subtitle: string; children: ReactNode }) {
  return (
    <div className="auth-shell">
      <aside className="auth-brand">
        <div className="brand"><span className="brand-logo"><ShieldCheck size={20} /></span> Auth Demo</div>
        <h1>Sistema de autenticación seguro</h1>
        <p className="lead">API para registro, inicio de sesión y control de acceso con tokens JWT. Proyecto de portafolio con datos ficticios.</p>
        <ul className="feature-list">
          {FEATURES.map((f) => <li key={f}><CheckCircle2 size={17} /> {f}</li>)}
        </ul>
        <div className="tech-row">
          {['Java 21', 'Spring Boot 3', 'Spring Security', 'JWT', 'MySQL', 'Swagger', 'React'].map((t) => <span key={t} className="tech">{t}</span>)}
        </div>
      </aside>
      <main className="auth-panel">
        <div className="auth-card">
          <div className="auth-card-head">
            <div className="shield"><ShieldCheck size={26} /></div>
            <h1 style={{ fontSize: '1.35rem' }}>{title}</h1>
            <p className="muted">{subtitle}</p>
          </div>
          {children}
        </div>
      </main>
    </div>
  );
}
