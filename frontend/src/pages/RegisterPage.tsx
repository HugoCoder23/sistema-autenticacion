import { useState, type FormEvent } from 'react';
import { Link, useNavigate } from 'react-router-dom';
import { Lock, Mail, User, UserPlus } from 'lucide-react';
import { ApiError } from '../api/client';
import { useAuth } from '../auth/AuthContext';
import { AuthShell } from '../components/AuthShell';
import { Alert, Button, Field, PasswordChecklist, passwordIsValid } from '../components/ui';

export function RegisterPage() {
  const { register } = useAuth();
  const navigate = useNavigate();
  const [form, setForm] = useState({ name: '', email: '', password: '', confirm: '' });
  const [errors, setErrors] = useState<Record<string, string>>({});
  const [error, setError] = useState<string | null>(null);
  const [loading, setLoading] = useState(false);

  const set = (k: keyof typeof form) => (e: React.ChangeEvent<HTMLInputElement>) => setForm({ ...form, [k]: e.target.value });

  const submit = async (e: FormEvent) => {
    e.preventDefault();
    setError(null);
    const local: Record<string, string> = {};
    if (form.name.trim().length < 2) local.name = 'Escribe tu nombre (mínimo 2 caracteres)';
    if (!/^\S+@\S+\.\S+$/.test(form.email.trim())) local.email = 'Escribe un email válido';
    if (!passwordIsValid(form.password)) local.password = 'La contraseña no cumple los requisitos';
    if (form.confirm !== form.password) local.confirm = 'Las contraseñas no coinciden';
    setErrors(local);
    if (Object.keys(local).length) return;

    setLoading(true);
    try {
      await register(form.name.trim(), form.email.trim(), form.password);
      navigate('/profile', { replace: true, state: { welcome: true } });
    } catch (err) {
      if (err instanceof ApiError) {
        setErrors(err.fieldErrors);
        setError(err.code === 'EMAIL_IN_USE' ? 'Ya existe una cuenta con ese email. ¿Quieres iniciar sesión?' : err.message);
      } else {
        setError('No se pudo crear la cuenta');
      }
    } finally {
      setLoading(false);
    }
  };

  return (
    <AuthShell title="Crear cuenta" subtitle="Regístrate con tu nombre, email y contraseña">
      <form className="form" onSubmit={submit} noValidate>
        {error && <Alert tone="error">{error}</Alert>}
        <Field label="Nombre" icon={User} autoComplete="name" value={form.name} onChange={set('name')} error={errors.name} placeholder="María López" autoFocus />
        <Field label="Correo electrónico" icon={Mail} type="email" autoComplete="email" value={form.email} onChange={set('email')} error={errors.email} placeholder="tu@correo.com" />
        <Field label="Contraseña" icon={Lock} type="password" autoComplete="new-password" value={form.password} onChange={set('password')} error={errors.password} />
        <PasswordChecklist value={form.password} />
        <Field label="Confirmar contraseña" icon={Lock} type="password" autoComplete="new-password" value={form.confirm} onChange={set('confirm')} error={errors.confirm} />
        <Button type="submit" block loading={loading} icon={UserPlus}>Crear cuenta</Button>
      </form>
      <div className="auth-links">
        <span className="muted">¿Ya tienes cuenta? <Link to="/login">Inicia sesión</Link></span>
      </div>
    </AuthShell>
  );
}
