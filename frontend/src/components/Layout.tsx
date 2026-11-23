import { useEffect, useState } from 'react';
import { NavLink, Outlet, useLocation, useNavigate } from 'react-router-dom';
import { FileClock, KeyRound, LogOut, Mail, Menu, ShieldCheck, UserRound, Users } from 'lucide-react';
import { useAuth } from '../auth/AuthContext';
import { initials } from '../lib/format';

export function Layout() {
  const { user, hasRole, logout } = useAuth();
  const navigate = useNavigate();
  const location = useLocation();
  const [open, setOpen] = useState(false);

  useEffect(() => setOpen(false), [location.pathname]);

  const doLogout = async () => {
    await logout();
    navigate('/login', { replace: true, state: { loggedOut: true } });
  };

  const link = ({ isActive }: { isActive: boolean }) => `nav-link${isActive ? ' active' : ''}`;

  return (
    <div className="shell">
      <div className="mobile-bar">
        <button className="btn btn-ghost btn-sm" style={{ color: '#fff' }} onClick={() => setOpen((o) => !o)} aria-label="Menú"><Menu size={18} /></button>
        <span className="brand"><span className="brand-logo" style={{ width: 28, height: 28 }}><ShieldCheck size={16} /></span> Auth Demo</span>
      </div>
      <aside className={`sidebar${open ? ' open' : ''}`}>
        <div className="brand"><span className="brand-logo"><ShieldCheck size={20} /></span> Auth Demo</div>
        <nav>
          <div className="nav-section">Mi cuenta</div>
          <NavLink to="/profile" className={link}><UserRound size={18} /> Perfil</NavLink>
          <NavLink to="/security" className={link}><KeyRound size={18} /> Seguridad y sesiones</NavLink>
          {hasRole('ADMIN') && (
            <>
              <div className="nav-section">Administración</div>
              <NavLink to="/admin" end className={link}><Users size={18} /> Usuarios y roles</NavLink>
              <NavLink to="/admin/audit" className={link}><FileClock size={18} /> Auditoría</NavLink>
            </>
          )}
          <div className="nav-section">Desarrollo</div>
          <NavLink to="/dev/mailbox" className={link}><Mail size={18} /> Bandeja simulada</NavLink>
        </nav>
        <div className="sidebar-foot">
          {user && (
            <div className="user-chip">
              <span className="avatar">{initials(user.name)}</span>
              <div style={{ minWidth: 0 }}>
                <div className="name">{user.name}</div>
                <div className="email">{user.email}</div>
              </div>
            </div>
          )}
          <button className="nav-link" onClick={doLogout}><LogOut size={18} /> Cerrar sesión</button>
        </div>
      </aside>
      <main className="main"><Outlet /></main>
    </div>
  );
}
