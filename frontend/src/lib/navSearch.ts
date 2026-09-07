import type { Role } from '../types';
import { canAccessPath, isSuperAdmin } from './access';

export type NavSearchHit = {
  type: string;
  id: string;
  title: string;
  subtitle: string;
  path: string;
  score: number;
};

type Destination = {
  id: string;
  title: string;
  subtitle: string;
  path: string;
  keywords: string[];
  type?: string;
};

/** Institution app destinations — keyword search navigates here. */
const INSTITUTION_DESTINATIONS: Destination[] = [
  {
    id: 'dashboard',
    title: 'Dashboard',
    subtitle: 'Institution overview and KPIs',
    path: '/dashboard',
    keywords: ['dashboard', 'home', 'overview', 'kpi', 'summary'],
  },
  {
    id: 'organization',
    title: 'Organization',
    subtitle: 'Schools, faculties, and departments',
    path: '/organization',
    keywords: ['organization', 'org', 'school', 'faculty', 'department', 'departments', 'tree'],
  },
  {
    id: 'lecturers',
    title: 'Lecturers',
    subtitle: 'Staff directory and expertise',
    path: '/lecturers',
    keywords: ['lecturer', 'lecturers', 'staff', 'teacher', 'teachers', 'faculty staff'],
  },
  {
    id: 'units',
    title: 'Academic Units',
    subtitle: 'Courses and teaching units',
    path: '/units',
    keywords: ['unit', 'units', 'course', 'courses', 'module', 'academic unit'],
  },
  {
    id: 'requests',
    title: 'Teaching Requests',
    subtitle: 'Cross-department teaching requests',
    path: '/requests',
    keywords: ['request', 'requests', 'teaching request', 'incoming', 'outgoing'],
  },
  {
    id: 'recommendations',
    title: 'Recommendations',
    subtitle: 'Find and rank lecturer candidates',
    path: '/recommendations',
    keywords: ['recommendation', 'recommendations', 'candidates', 'match', 'matching', 'find lecturer'],
  },
  {
    id: 'allocation',
    title: 'Allocation Board',
    subtitle: 'Assign, review, and track allocations',
    path: '/allocation',
    keywords: [
      'board',
      'allocation',
      'allocations',
      'allocated',
      'allocate',
      'assignment',
      'assign',
      'kanban',
      'allocation board',
      'allocation area',
    ],
  },
  {
    id: 'workload',
    title: 'Workload',
    subtitle: 'Teaching load by lecturer',
    path: '/workload',
    keywords: ['workload', 'load', 'hours', 'capacity', 'overloaded'],
  },
  {
    id: 'timetable',
    title: 'Timetable',
    subtitle: 'Weekly teaching schedule',
    path: '/timetable',
    keywords: ['timetable', 'schedule', 'calendar', 'slots', 'time table'],
  },
  {
    id: 'conflicts',
    title: 'Conflicts',
    subtitle: 'Workload, timetable, and policy conflicts',
    path: '/conflicts',
    keywords: ['conflict', 'conflicts', 'clash', 'clashes'],
  },
  {
    id: 'approvals',
    title: 'Approvals',
    subtitle: 'Approve or reject allocations',
    path: '/approvals',
    keywords: ['approval', 'approvals', 'approve', 'reject', 'publish'],
  },
  {
    id: 'import',
    title: 'Import / Export',
    subtitle: 'Upload CSV/PDF and export reports',
    path: '/import',
    keywords: ['import', 'export', 'csv', 'upload', 'pdf', 'data import'],
  },
  {
    id: 'reports',
    title: 'Reports',
    subtitle: 'Institution operational reports',
    path: '/reports',
    keywords: ['report', 'reports', 'analytics', 'export report'],
  },
  {
    id: 'admin',
    title: 'Administration',
    subtitle: 'Users, invitations, years, settings',
    path: '/admin',
    keywords: [
      'admin',
      'administration',
      'users',
      'user management',
      'invitation',
      'invitations',
      'invite',
      'roles',
      'settings',
      'semester',
      'academic year',
      'year',
    ],
  },
  {
    id: 'profile',
    title: 'My profile',
    subtitle: 'Account details and profile management',
    path: '/profile',
    keywords: [
      'profile',
      'my profile',
      'account',
      'my account',
      'profile management',
      'user profile',
      'me',
      'password',
    ],
  },
];

const PLATFORM_DESTINATIONS: Destination[] = [
  {
    id: 'plat-dashboard',
    title: 'Command center',
    subtitle: 'Product owner dashboard',
    path: '/platform/dashboard',
    keywords: ['dashboard', 'command', 'command center', 'overview', 'home'],
  },
  {
    id: 'plat-institutions',
    title: 'Institutions',
    subtitle: 'Customer / tenant management',
    path: '/platform/institutions',
    keywords: ['institution', 'institutions', 'customers', 'tenants', 'schools', 'onboarding'],
  },
  {
    id: 'plat-users',
    title: 'Platform accounts',
    subtitle: 'Users across all institutions',
    path: '/platform/users',
    keywords: ['users', 'accounts', 'people', 'profile', 'account'],
  },
  {
    id: 'plat-analytics',
    title: 'Product analytics',
    subtitle: 'Adoption and feature usage',
    path: '/platform/analytics',
    keywords: ['analytics', 'usage', 'adoption', 'metrics'],
  },
  {
    id: 'plat-security',
    title: 'Security center',
    subtitle: 'Failed logins and security events',
    path: '/platform/security',
    keywords: ['security', 'login', 'threats', 'locked'],
  },
  {
    id: 'plat-audit',
    title: 'Platform audit',
    subtitle: 'Product-owner audit trail',
    path: '/platform/audit',
    keywords: ['audit', 'log', 'changes', 'history'],
  },
  {
    id: 'plat-monitoring',
    title: 'System health',
    subtitle: 'API, database, and jobs',
    path: '/platform/monitoring',
    keywords: ['health', 'monitoring', 'status', 'system'],
  },
  {
    id: 'plat-features',
    title: 'Feature flags',
    subtitle: 'Enable or disable product features',
    path: '/platform/features',
    keywords: ['features', 'flags', 'beta'],
  },
  {
    id: 'plat-config',
    title: 'Configuration',
    subtitle: 'Global platform settings',
    path: '/platform/configuration',
    keywords: ['configuration', 'config', 'settings'],
  },
  {
    id: 'plat-data',
    title: 'Data quality',
    subtitle: 'Platform data issues',
    path: '/platform/data',
    keywords: ['data', 'quality', 'imports'],
  },
  {
    id: 'plat-support',
    title: 'Support',
    subtitle: 'Customer diagnostics',
    path: '/platform/support',
    keywords: ['support', 'help', 'diagnostics', 'lookup'],
  },
  {
    id: 'plat-system',
    title: 'System',
    subtitle: 'Version and environment',
    path: '/platform/system',
    keywords: ['system', 'version', 'environment', 'release'],
  },
  {
    id: 'plat-profile',
    title: 'My profile',
    subtitle: 'Product owner account',
    path: '/platform/profile',
    keywords: ['profile', 'my profile', 'account', 'me'],
  },
];

function scoreDestination(q: string, dest: Destination): number {
  const query = q.trim().toLowerCase();
  if (!query) return 0;
  const title = dest.title.toLowerCase();
  const subtitle = dest.subtitle.toLowerCase();
  let score = 0;
  if (title === query) score += 100;
  if (title.startsWith(query)) score += 80;
  if (title.includes(query)) score += 50;
  if (subtitle.includes(query)) score += 20;
  for (const kw of dest.keywords) {
    if (kw === query) score += 95;
    else if (kw.startsWith(query)) score += 70;
    else if (kw.includes(query)) score += 40;
    else if (query.includes(kw) && kw.length >= 3) score += 35;
  }
  // multi-word: all tokens appear somewhere
  const tokens = query.split(/\s+/).filter(Boolean);
  if (tokens.length > 1) {
    const blob = `${title} ${subtitle} ${dest.keywords.join(' ')}`;
    if (tokens.every((t) => blob.includes(t))) score += 25;
  }
  return score;
}

export function searchNavigation(query: string, role: Role | string): NavSearchHit[] {
  const destinations = isSuperAdmin(role) ? PLATFORM_DESTINATIONS : INSTITUTION_DESTINATIONS;
  return destinations
    .map((d) => {
      const score = scoreDestination(query, d);
      return {
        type: d.type || 'Go to',
        id: d.id,
        title: d.title,
        subtitle: d.subtitle,
        path: d.path,
        score,
      } satisfies NavSearchHit;
    })
    .filter((h) => h.score > 0 && canAccessPath(role, h.path))
    .sort((a, b) => b.score - a.score)
    .slice(0, 12);
}

export function defaultNavigationSuggestions(role: Role | string): NavSearchHit[] {
  const destinations = isSuperAdmin(role) ? PLATFORM_DESTINATIONS : INSTITUTION_DESTINATIONS;
  return destinations
    .filter((d) => canAccessPath(role, d.path))
    .slice(0, 8)
    .map((d) => ({
      type: 'Go to',
      id: d.id,
      title: d.title,
      subtitle: d.subtitle,
      path: d.path,
      score: 1,
    }));
}
