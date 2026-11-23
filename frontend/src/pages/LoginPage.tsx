import { useState, type FormEvent } from 'react';
import { Link, useLocation, useNavigate } from 'react-router-dom';
import { Lock, LogIn, Mail } from 'lucide-react';
import { ApiError } from '../api/client';
import { useAuth } from '../auth/AuthContext';
import { AuthShell } from '../components/AuthShell';
import { Alert, Button, Field } from '../components/ui';

const DEMO = [
  { email: 'admin@auth.demo', label: 'ADMIN' },
  { email: 'ana@auth.demo', label: 'USER' },
];

export function LoginPage() {
  const { login } = useAuth();
  const navigate = useNavigate();
  const location = useLocation();
  const state = (location.state ?? {}) as { from?: string; loggedOut?: boolean; notice?: string };
  const [email, setEmail] = useState('');
  const [password, setPassword] = useState('');
  const [error, setError] = useState<string | null>(null);
  const [loading, setLoading] = useState(false);

  const submit = async (e: FormEvent) => {
    e.preventDefault();
    setError(null);
    setLoading(true);
    try {
      await login(email.trim(), password);
      navigate(state.from ?? '/profile', { replace: true });
    } catch (err) {
      setError(err instanceof ApiError ? err.message : 'No se pudo iniciar sesión');
    } finally {
      setLoading(false);
    }
  };

  return (
    <AuthShell title="Iniciar sesión" subtitle="Ingresa tus credenciales">
      <form className="form" onSubmit={submit} noValidate>
        {state.loggedOut && !error && <Alert tone="success">Cerraste sesión correctamente.</Alert>}
        {state.notice && !error && <Alert tone="success">{state.notice}</Alert>}
        {error && <Alert tone="error">{error}</Alert>}
        <Field label="Correo electrónico" icon={Mail} type="email" autoComplete="username" required autoFocus
          value={email} onChange={(e) => setEmail(e.target.value)} placeholder="tu@correo.com" />
        <Field label="Contraseña" icon={Lock} type="password" autoComplete="current-password" required
          value={password} onChange={(e) => setPassword(e.target.value)} placeholder="••••••••" />
        <Button type="submit" block loading={loading} icon={LogIn} disabled={!email || !password}>Entrar</Button>
      </form>
      <div className="auth-links">
        <Link to="/forgot-password">¿Olvidaste tu contraseña?</Link>
        <span className="muted">¿No tienes cuenta? <Link to="/register">Crear cuenta</Link></span>
      </div>
      <div className="demo-box small">
        <strong>Cuentas demo</strong> <span className="muted">(contraseña <code>Demo1234!</code>)</span>
        <ul>
          {DEMO.map((d) => (
            <li key={d.email}>
              <button type="button" onClick={() => { setEmail(d.email); setPassword('Demo1234!'); }}>{d.email}</button>{' '}
              <span className={`badge ${d.label === 'ADMIN' ? 'badge-violet' : 'badge-teal'}`}>{d.label}</span>
            </li>
          ))}
        </ul>
      </div>
    </AuthShell>
  );
}
