import { NavLink, Outlet, useNavigate } from 'react-router-dom';
import { useApp } from '../context/AppContext';
import { Avatar } from '../components/ui/Badge';
import { BrandMark, IconChart, IconDashboard, IconOrg, IconSettings, IconUsers } from '../components/ui/Icons';
import '../styles/platform.css';

const linkClass = ({ isActive }: { isActive: boolean }) => `plat-nav-item${isActive ? ' active' : ''}`;

export function PlatformLayout() {
  const { user, logout } = useApp();
  const navigate = useNavigate();

  return (
    <div className="plat-shell">
      <aside className="plat-sidebar">
        <div className="plat-brand">
          <BrandMark />
          <div>
            <div className="plat-brand-name">AcademicFlow</div>
            <div className="plat-brand-sub">Product Command Center</div>
          </div>
        </div>
        <nav className="plat-nav">
          <div className="plat-nav-label">Command</div>
          <NavLink to="/platform/dashboard" className={linkClass} end>
            <IconDashboard /> Dashboard
          </NavLink>

          <div className="plat-nav-label">Customers</div>
          <NavLink to="/platform/institutions" className={linkClass}>
            <IconOrg /> Institutions
          </NavLink>
          <NavLink to="/platform/users" className={linkClass}>
            <IconUsers /> Accounts
          </NavLink>

          <div className="plat-nav-label">Product</div>
          <NavLink to="/platform/analytics" className={linkClass}>
            <IconChart /> Analytics
          </NavLink>
          <NavLink to="/platform/features" className={linkClass}>
            <IconSettings /> Features
          </NavLink>
          <NavLink to="/platform/configuration" className={linkClass}>
            <IconSettings /> Configuration
          </NavLink>

          <div className="plat-nav-label">Security</div>
          <NavLink to="/platform/security" className={linkClass}>
            <IconSettings /> Security center
          </NavLink>
          <NavLink to="/platform/audit" className={linkClass}>
            <IconChart /> Audit log
          </NavLink>

          <div className="plat-nav-label">Operations</div>
          <NavLink to="/platform/monitoring" className={linkClass}>
            <IconDashboard /> System health
          </NavLink>
          <NavLink to="/platform/data" className={linkClass}>
            <IconOrg /> Data quality
          </NavLink>
          <NavLink to="/platform/support" className={linkClass}>
            <IconUsers /> Support
          </NavLink>
          <NavLink to="/platform/system" className={linkClass}>
            <IconSettings /> System
          </NavLink>
        </nav>
        <div className="plat-sidebar-foot">
          <Avatar initials={user.initials} size="md" />
          <div style={{ flex: 1, minWidth: 0 }}>
            <div className="plat-user-name">{user.name}</div>
            <div className="plat-user-role">Product Owner · Super Admin</div>
          </div>
          <button
            type="button"
            className="plat-signout"
            onClick={() => {
              logout();
              navigate('/login');
            }}
          >
            Sign out
          </button>
        </div>
      </aside>
      <div className="plat-main">
        <header className="plat-topbar">
          <div>
            <div className="plat-eyebrow">AcademicFlow platform</div>
            <div className="plat-top-title">Product owner console</div>
          </div>
          <div className="plat-top-meta">Cross-tenant · Not institution operations</div>
        </header>
        <main className="plat-content">
          <Outlet />
        </main>
      </div>
    </div>
  );
}
