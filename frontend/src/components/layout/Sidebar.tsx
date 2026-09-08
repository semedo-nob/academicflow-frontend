import { NavLink } from 'react-router-dom';
import type { ReactNode } from 'react';
import { useApp } from '../../context/AppContext';
import { canAccessNav, roleLabel, type NavKey } from '../../lib/access';
import { Avatar } from '../ui/Badge';
import {
  BrandMark,
  IconAlert,
  IconApprove,
  IconBook,
  IconCalendar,
  IconChart,
  IconDashboard,
  IconExchange,
  IconGauge,
  IconHelp,
  IconLayers,
  IconOrg,
  IconSettings,
  IconTarget,
  IconUpload,
  IconUsers,
} from '../ui/Icons';

const linkClass = ({ isActive }: { isActive: boolean }) =>
  `nav-item${isActive ? ' active' : ''}`;

type Item = { key: NavKey; to: string; label: string; icon: ReactNode; badge?: string; danger?: boolean };

const MAIN: Item[] = [
  { key: 'dashboard', to: '/dashboard', label: 'Dashboard', icon: <IconDashboard /> },
  { key: 'organization', to: '/organization', label: 'Organization', icon: <IconOrg /> },
  { key: 'lecturers', to: '/lecturers', label: 'Lecturers', icon: <IconUsers /> },
  { key: 'units', to: '/units', label: 'Academic Units', icon: <IconBook /> },
];

const REQUESTS: Item[] = [
  { key: 'requests', to: '/requests', label: 'Teaching Requests', icon: <IconExchange /> },
  { key: 'recommendations', to: '/recommendations', label: 'Recommendations', icon: <IconTarget /> },
];

const ALLOCATION: Item[] = [
  { key: 'allocate-context', to: '/allocate-context', label: 'Allocate by context', icon: <IconTarget /> },
  { key: 'allocation', to: '/allocation', label: 'Allocation Board', icon: <IconLayers /> },
  { key: 'workload', to: '/workload', label: 'Workload', icon: <IconGauge /> },
  { key: 'timetable', to: '/timetable', label: 'Timetable', icon: <IconCalendar /> },
  { key: 'conflicts', to: '/conflicts', label: 'Conflicts', icon: <IconAlert />, danger: true },
  { key: 'approvals', to: '/approvals', label: 'Approvals', icon: <IconApprove /> },
];

const DATA: Item[] = [
  { key: 'import', to: '/import', label: 'Import / Export', icon: <IconUpload /> },
  { key: 'reports', to: '/reports', label: 'Reports', icon: <IconChart /> },
  { key: 'admin', to: '/admin', label: 'Administration', icon: <IconSettings /> },
];

function NavGroup({
  label,
  items,
  role,
}: {
  label?: string;
  items: Item[];
  role: string;
}) {
  const visible = items.filter((i) => canAccessNav(role, i.key));
  if (!visible.length) return null;
  return (
    <>
      {label ? <div className="nav-group-label">{label}</div> : null}
      {visible.map((i) => (
        <NavLink
          key={i.key}
          to={i.to}
          end={i.to === '/platform'}
          className={({ isActive }) =>
            `${linkClass({ isActive })}${i.danger ? ' danger-badge' : ''}`
          }
        >
          {i.icon} {i.label}
          {i.badge ? <span className="nav-badge">{i.badge}</span> : null}
        </NavLink>
      ))}
    </>
  );
}

export function Sidebar() {
  const { user } = useApp();

  return (
    <nav className="sidebar">
      <div className="brand">
        <BrandMark />
        <div className="brand-name">AcademicFlow</div>
      </div>
      <div className="nav-scroll">
        <NavGroup items={MAIN} role={user.role} />
        <NavGroup label="Requests" items={REQUESTS} role={user.role} />
        <NavGroup label="Allocation" items={ALLOCATION} role={user.role} />
        <NavGroup label="Data & Reporting" items={DATA} role={user.role} />
      </div>
      <div className="sidebar-footer">
        <button type="button" className="nav-item" style={{ opacity: 0.85 }}>
          <IconHelp /> Help
        </button>
        <div className="sidebar-user">
          <Avatar initials={user.initials} size="md" />
          <div>
            <div className="sidebar-user-name">{user.name}</div>
            <div className="sidebar-user-role">
              {roleLabel(user.role)}
              {user.departmentName ? ` · ${user.departmentName}` : ''}
            </div>
          </div>
        </div>
      </div>
    </nav>
  );
}
