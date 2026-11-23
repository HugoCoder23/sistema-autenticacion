import { useEffect, useState, type FormEvent } from 'react';
import { Link, useNavigate, useSearchParams } from 'react-router-dom';
import { KeyRound, Lock } from 'lucide-react';
import { ApiError, api, setSession } from '../api/client';
import type { Message } from '../api/types';
import { AuthShell } from '../components/AuthShell';
import { Alert, Button, Field, PasswordChecklist, passwordIsValid } from '../components/ui';

export function ResetPasswordPage() {
  const [params] = useSearchParams();
  const navigate = useNavigate();
  const [token] = useState(() => params.get('token') ?? '');
  const [password, setPassword] = useState('');
  const [confirm, setConfirm] = useState('');
  const [error, setError] = useState<string | null>(null);
  const [expired, setExpired] = useState(false);
  const [loading, setLoading] = useState(false);

  // Quita el token de la barra de direcciones y del historial en cuanto se lee.
  useEffect(() => {
    if (params.has('token')) window.history.replaceState(null, '', '/reset-password');
  }, [params]);

  const submit = async (e: FormEvent) => {
    e.preventDefault();
    setError(null);
    if (!passwordIsValid(password)) return setError('La contraseña no cumple los requisitos');
    if (password !== confirm) return setError('Las contraseñas no coinciden');
    setLoading(true);
    try {
      const res = await api.post<Message>('/api/auth/reset-password', { token, newPassword: password }, { auth: false });
      setSession(null); // el servidor cerró todas las sesiones
      navigate('/login', { replace: true, state: { notice: res.message } });
    } catch (err) {
      if (err instanceof ApiError && err.code === 'INVALID_RESET_TOKEN') setExpired(true);
      setError(err instanceof ApiError ? (err.fieldErrors.newPassword ?? err.message) : 'No se pudo cambiar la contraseña');
    } finally {
      setLoading(false);
    }
  };

  if (!token) {
    return (
      <AuthShell title="Enlace incompleto" subtitle="Falta el token de recuperación">
        <div className="stack">
          <Alert tone="warn">Abre el enlace completo que recibiste por correo o solicita uno nuevo.</Alert>
          <Link to="/forgot-password" className="btn btn-primary btn-block">Solicitar enlace</Link>
        </div>
      </AuthShell>
    );
  }

  return (
    <AuthShell title="Nueva contraseña" subtitle="Elige una contraseña que no hayas usado antes">
      <form className="form" onSubmit={submit} noValidate>
        {error && <Alert tone="error">{error}</Alert>}
        {expired ? (
          <Link to="/forgot-password" className="btn btn-primary btn-block">Solicitar un enlace nuevo</Link>
        ) : (
          <>
            <Field label="Nueva contraseña" icon={Lock} type="password" autoComplete="new-password" autoFocus
              value={password} onChange={(e) => setPassword(e.target.value)} />
            <PasswordChecklist value={password} />
            <Field label="Confirmar contraseña" icon={Lock} type="password" autoComplete="new-password"
              value={confirm} onChange={(e) => setConfirm(e.target.value)} />
            <Button type="submit" block loading={loading} icon={KeyRound}>Cambiar contraseña</Button>
            <p className="hint" style={{ textAlign: 'center' }}>Al cambiarla se cerrarán todas tus sesiones abiertas.</p>
          </>
        )}
      </form>
    </AuthShell>
  );
}
