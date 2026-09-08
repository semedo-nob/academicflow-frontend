import type { Role } from '../types';

/** Normalize backend role codes to the frontend Role union. */
export function normalizeRole(role: string | undefined | null): Role {
  const r = (role || '').toUpperCase();
  if (r === 'INSTITUTION_ADMIN') return 'INSTITUTION_ADMIN';
  if (r === 'SCHOOL_DEAN' || r === 'SCHOOL_ADMIN') return 'SCHOOL_ADMIN';
  if (r === 'SUPER_ADMIN') return 'SUPER_ADMIN';
  if (r === 'DEPARTMENT_CHAIR') return 'DEPARTMENT_CHAIR';
  if (r === 'LECTURER') return 'LECTURER';
  if (r === 'VIEWER') return 'VIEWER';
  return 'VIEWER';
}

export function roleLabel(role: Role | string): string {
  switch (normalizeRole(role)) {
    case 'SUPER_ADMIN':
      return 'Super Admin';
    case 'INSTITUTION_ADMIN':
      return 'Institution Admin';
    case 'SCHOOL_ADMIN':
      return 'School Dean';
    case 'DEPARTMENT_CHAIR':
      return 'Department Chair';
    case 'LECTURER':
      return 'Lecturer';
    case 'VIEWER':
      return 'Viewer';
    default:
      return String(role);
  }
}

export function isSuperAdmin(role: Role | string): boolean {
  return normalizeRole(role) === 'SUPER_ADMIN';
}

/** Default landing route after login. */
export function homePath(role: Role | string): string {
  return isSuperAdmin(role) ? '/platform/dashboard' : '/dashboard';
}

export type NavKey =
  | 'dashboard'
  | 'organization'
  | 'onboarding'
  | 'lecturers'
  | 'units'
  | 'requests'
  | 'recommendations'
  | 'allocate-context'
  | 'allocation'
  | 'workload'
  | 'timetable'
  | 'conflicts'
  | 'approvals'
  | 'import'
  | 'reports'
  | 'admin'
  | 'platform'
  | 'platform-institutions'
  | 'platform-audit';

const ROLE_NAV: Record<Role, NavKey[]> = {
  /** Platform console only — no institution teaching workspace. */
  SUPER_ADMIN: ['platform', 'platform-institutions', 'platform-audit'],
  INSTITUTION_ADMIN: [
    'dashboard',
    'organization',
    'onboarding',
    'lecturers',
    'units',
    'requests',
    'recommendations',
    'allocate-context',
    'allocation',
    'workload',
    'timetable',
    'conflicts',
    'approvals',
    'import',
    'reports',
    'admin',
  ],
  SCHOOL_ADMIN: [
    'dashboard',
    'organization',
    'units',
    'requests',
    'approvals',
    'workload',
    'timetable',
    'reports',
    'admin',
  ],
  DEPARTMENT_CHAIR: [
    'dashboard',
    'organization',
    'lecturers',
    'units',
    'requests',
    'recommendations',
    'allocate-context',
    'allocation',
    'workload',
    'timetable',
    'conflicts',
    'approvals',
    'import',
    'reports',
  ],
  LECTURER: ['dashboard', 'units', 'workload', 'timetable'],
  VIEWER: ['dashboard', 'workload', 'timetable', 'reports'],
};

export function canAccessNav(role: Role | string, key: NavKey): boolean {
  return ROLE_NAV[normalizeRole(role)].includes(key);
}

export function canAccessPath(role: Role | string, path: string): boolean {
  const normalized = normalizeRole(role);
  const isPlatformPath = path === '/platform' || path.startsWith('/platform/');

  if (normalized === 'SUPER_ADMIN') {
    return isPlatformPath;
  }
  if (isPlatformPath) return false;

  const segment = path.replace(/^\//, '').split('/')[0] || 'dashboard';
  const known: NavKey[] = [
    'dashboard',
    'organization',
    'onboarding',
    'lecturers',
    'units',
    'requests',
    'recommendations',
    'allocate-context',
    'allocation',
    'workload',
    'timetable',
    'conflicts',
    'approvals',
    'import',
    'reports',
    'admin',
  ];
  if (segment === 'profile') return true;
  if (segment === 'onboarding') return normalizeRole(role) === 'INSTITUTION_ADMIN';
  if (!known.includes(segment as NavKey)) return true;
  return canAccessNav(role, segment as NavKey);
}

export type AdminSection =
  | 'Users'
  | 'Roles & Permissions'
  | 'Institutions'
  | 'Organization Types'
  | 'Academic Years'
  | 'Semesters'
  | 'Institutional Rules'
  | 'Import Mapping Profiles'
  | 'System Settings'
  | 'Audit Logs';

const ADMIN_BY_ROLE: Record<Role, AdminSection[]> = {
  SUPER_ADMIN: [],
  INSTITUTION_ADMIN: [
    'Users',
    'Roles & Permissions',
    'Organization Types',
    'Academic Years',
    'Semesters',
    'Institutional Rules',
    'Import Mapping Profiles',
    'System Settings',
    'Audit Logs',
  ],
  SCHOOL_ADMIN: ['Academic Years', 'Semesters', 'Institutional Rules', 'Organization Types'],
  DEPARTMENT_CHAIR: [],
  LECTURER: [],
  VIEWER: [],
};

export function adminSectionsFor(role: Role | string): AdminSection[] {
  return ADMIN_BY_ROLE[normalizeRole(role)];
}

export function canManageInstitutions(role: Role | string): boolean {
  return normalizeRole(role) === 'SUPER_ADMIN';
}

export function canManageOrganization(role: Role | string): boolean {
  const r = normalizeRole(role);
  return (
    r === 'INSTITUTION_ADMIN' ||
    r === 'SCHOOL_ADMIN' ||
    r === 'DEPARTMENT_CHAIR'
  );
}

/** Assignable roles within an institution (not platform Super Admin). */
export const ASSIGNABLE_ROLES: { value: string; label: string }[] = [
  { value: 'INSTITUTION_ADMIN', label: 'Institution Admin' },
  { value: 'SCHOOL_DEAN', label: 'School Dean' },
  { value: 'DEPARTMENT_CHAIR', label: 'Department Chair' },
  { value: 'LECTURER', label: 'Lecturer' },
  { value: 'VIEWER', label: 'Viewer' },
];

/** Public landing copy — institution roles. */
export const ROLE_GUIDE: { role: string; title: string; can: string; cannot: string }[] = [
  {
    role: 'INSTITUTION_ADMIN',
    title: 'Institution Admin',
    can: 'After approval: manage users and roles, organization tree, and full teaching workflow for your school.',
    cannot: 'Cannot approve other institutions on the platform.',
  },
  {
    role: 'SCHOOL_ADMIN',
    title: 'School Dean',
    can: 'Oversee school organization, units, requests, approvals, and workload.',
    cannot: 'Cannot manage platform institutions or all user roles.',
  },
  {
    role: 'DEPARTMENT_CHAIR',
    title: 'Department Chair',
    can: 'Match lecturers, allocate units, resolve conflicts, and publish teaching.',
    cannot: 'No institution-wide user/role administration.',
  },
  {
    role: 'LECTURER',
    title: 'Lecturer',
    can: 'View units, workload, and personal timetable.',
    cannot: 'Cannot allocate, approve, or change admin settings.',
  },
  {
    role: 'VIEWER',
    title: 'Viewer',
    can: 'Read dashboard, reports, timetable, and workload summaries.',
    cannot: 'No edits, approvals, or administration.',
  },
];
