import { useState, type FormEvent } from 'react';
import { Link } from 'react-router-dom';
import { ArrowLeft, Mail, Send } from 'lucide-react';
import { ApiError, api } from '../api/client';
import type { Message } from '../api/types';
import { AuthShell } from '../components/AuthShell';
import { Alert, Button, Field } from '../components/ui';

export function ForgotPasswordPage() {
  const [email, setEmail] = useState('');
  const [sent, setSent] = useState<string | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [loading, setLoading] = useState(false);

  const submit = async (e: FormEvent) => {
    e.preventDefault();
    setError(null);
    setLoading(true);
    try {
      const res = await api.post<Message>('/api/auth/forgot-password', { email: email.trim() }, { auth: false });
      setSent(res.message);
    } catch (err) {
      setError(err instanceof ApiError ? (err.fieldErrors.email ?? err.message) : 'No se pudo enviar la solicitud');
    } finally {
      setLoading(false);
    }
  };

  return (
    <AuthShell title="Recuperar contraseña" subtitle="Te enviaremos un enlace de un solo uso">
      {sent ? (
        <div className="stack">
          {/* El mismo mensaje exista o no la cuenta: no permite averiguar qué emails están registrados. */}
          <Alert tone="success">{sent}.</Alert>
          <Alert tone="info">
            Entorno de demostración: el correo no se envía de verdad. Ábrelo en la{' '}
            <Link to={`/dev/mailbox?to=${encodeURIComponent(email.trim().toLowerCase())}`}>bandeja simulada</Link>.
          </Alert>
          <Link to="/login" className="btn btn-secondary btn-block"><ArrowLeft size={16} /> Volver al inicio de sesión</Link>
        </div>
      ) : (
        <form className="form" onSubmit={submit} noValidate>
          {error && <Alert tone="error">{error}</Alert>}
          <Field label="Correo electrónico" icon={Mail} type="email" autoComplete="email" autoFocus
            value={email} onChange={(e) => setEmail(e.target.value)} placeholder="tu@correo.com" />
          <Button type="submit" block loading={loading} icon={Send} disabled={!email}>Enviar enlace</Button>
          <div className="auth-links" style={{ marginTop: 4 }}><Link to="/login">Volver al inicio de sesión</Link></div>
        </form>
      )}
    </AuthShell>
  );
}
