import {
  createContext,
  useCallback,
  useContext,
  useEffect,
  useMemo,
  useState,
  type ReactNode,
} from 'react';
import type { Membership, User } from '../types';
import { adminService, authService } from '../services';
import { normalizeRole } from '../lib/access';
import { clearClientSession, setActiveDepartmentId, setTenantId } from '../services/api';

interface PeriodState {
  academicYearLabel: string;
  semesterName: string;
  years: { id: string; label: string }[];
  semesters: { id: string; academicYearId: string; academicYearLabel: string; name: string }[];
  selectedYearId: string | null;
  selectedSemesterId: string | null;
}

interface AppContextValue {
  authenticated: boolean;
  /** Changes on login, logout, and department switch — drives data isolation refetches. */
  sessionKey: string;
  authLoading: boolean;
  clerkEnabled: boolean;
  login: (email?: string, password?: string) => Promise<void>;
  establishClerkSession: () => Promise<void>;
  applySessionPayload: (res: {
    email: string;
    name: string;
    role: string;
    tenantId: string;
    userId?: string;
    organizationNodeId?: string | null;
    departmentName?: string | null;
    activeDepartmentId?: string | null;
    activeDepartmentName?: string | null;
    activeRole?: string | null;
    memberships?: Membership[];
  }) => void;
  logout: () => void;
  user: User;
  setActiveDepartment: (departmentId: string) => void;
  drawer: ReactNode | null;
  openDrawer: (node: ReactNode) => void;
  closeDrawer: () => void;
  selectedRequestId: string;
  setSelectedRequestId: (id: string) => void;
  selectedOfferingId: string;
  setSelectedOfferingId: (id: string) => void;
  period: PeriodState;
  setAcademicYearId: (id: string) => void;
  setSemesterId: (id: string) => void;
  refreshPeriod: () => Promise<void>;
}

const AppContext = createContext<AppContextValue | null>(null);

const defaultPeriod: PeriodState = {
  academicYearLabel: '2026/2027',
  semesterName: 'Semester 1',
  years: [],
  semesters: [],
  selectedYearId: null,
  selectedSemesterId: null,
};

const EMPTY_USER: User = {
  id: '',
  name: '',
  email: '',
  role: 'VIEWER',
  initials: '',
  departmentName: '',
  memberships: [],
};

function loadUser(): User {
  try {
    const raw = localStorage.getItem('af_user');
    if (raw) {
      const parsed = JSON.parse(raw) as {
        id?: string;
        userId?: string;
        name: string;
        email: string;
        role: string;
        departmentName?: string;
        organizationNodeId?: string;
        activeDepartmentId?: string;
        activeDepartmentName?: string;
        activeRole?: string;
        memberships?: Membership[];
        tenantId?: string;
      };
      if (!parsed.email) return EMPTY_USER;
      const parts = (parsed.name || '').replace(/^(Dr\.|Prof\.|Mr\.|Ms\.)\s*/, '').split(' ');
      return {
        id: parsed.userId || parsed.id || 'u-local',
        name: parsed.name,
        email: parsed.email,
        role: normalizeRole(parsed.role),
        initials: ((parts[0]?.[0] || '') + (parts[1]?.[0] || '')).toUpperCase(),
        departmentName: parsed.activeDepartmentName || parsed.departmentName || 'Institution',
        organizationNodeId: parsed.organizationNodeId,
        activeDepartmentId: parsed.activeDepartmentId,
        activeDepartmentName: parsed.activeDepartmentName,
        activeRole: parsed.activeRole,
        memberships: parsed.memberships || [],
      };
    }
  } catch {
    /* ignore */
  }
  return EMPTY_USER;
}

function persistUser(user: User, tenantId?: string) {
  localStorage.setItem(
    'af_user',
    JSON.stringify({
      id: user.id,
      userId: user.id,
      name: user.name,
      email: user.email,
      role: user.role,
      departmentName: user.departmentName,
      organizationNodeId: user.organizationNodeId,
      activeDepartmentId: user.activeDepartmentId,
      activeDepartmentName: user.activeDepartmentName,
      activeRole: user.activeRole,
      memberships: user.memberships || [],
      tenantId: tenantId || localStorage.getItem('af_tenant') || undefined,
    }),
  );
  if (user.activeDepartmentId) setActiveDepartmentId(user.activeDepartmentId);
  else setActiveDepartmentId(null);
}

function buildSessionKey(user: User, epoch: number): string {
  const tenant = localStorage.getItem('af_tenant') || 'none';
  return [tenant, user.id || 'anon', user.email || '', user.activeDepartmentId || '', String(epoch)].join('|');
}

export function AppProvider({ children }: { children: ReactNode }) {
  const [authenticated, setAuthenticated] = useState(
    () => localStorage.getItem('af_auth') === '1' && !!loadUser().email,
  );
  const [user, setUser] = useState<User>(() => (localStorage.getItem('af_auth') === '1' ? loadUser() : EMPTY_USER));
  const [authLoading, setAuthLoading] = useState(true);
  const [clerkEnabled, setClerkEnabled] = useState(false);
  const [sessionEpoch, setSessionEpoch] = useState(0);
  const [drawer, setDrawer] = useState<ReactNode | null>(null);
  const [selectedRequestId, setSelectedRequestId] = useState('');
  const [selectedOfferingId, setSelectedOfferingId] = useState('');
  const [period, setPeriod] = useState<PeriodState>(defaultPeriod);

  const sessionKey = useMemo(() => buildSessionKey(user, sessionEpoch), [user, sessionEpoch]);

  const resetWorkspace = useCallback(() => {
    setDrawer(null);
    setSelectedRequestId('');
    setSelectedOfferingId('');
    setPeriod(defaultPeriod);
  }, []);

  const refreshPeriod = useCallback(async () => {
    try {
      const p = await adminService.period();
      setPeriod({
        academicYearLabel: p.academicYear?.label || defaultPeriod.academicYearLabel,
        semesterName: p.semester?.name || defaultPeriod.semesterName,
        years: p.years,
        semesters: p.semesters,
        selectedYearId: p.academicYear?.id || null,
        selectedSemesterId: p.semester?.id || null,
      });
    } catch {
      /* keep defaults */
    }
  }, []);

  useEffect(() => {
    if (authenticated) void refreshPeriod();
  }, [authenticated, sessionKey, refreshPeriod]);

  const setAcademicYearId = useCallback(
    (id: string) => {
      setPeriod((prev) => {
        const year = prev.years.find((y) => y.id === id);
        const sem =
          prev.semesters.find((s) => s.academicYearId === id && s.id === prev.selectedSemesterId) ||
          prev.semesters.find((s) => s.academicYearId === id);
        return {
          ...prev,
          selectedYearId: id,
          academicYearLabel: year?.label || prev.academicYearLabel,
          selectedSemesterId: sem?.id || null,
          semesterName: sem?.name || prev.semesterName,
        };
      });
    },
    [],
  );

  const setSemesterId = useCallback((id: string) => {
    setPeriod((prev) => {
      const sem = prev.semesters.find((s) => s.id === id);
      return {
        ...prev,
        selectedSemesterId: id,
        semesterName: sem?.name || prev.semesterName,
        selectedYearId: sem?.academicYearId || prev.selectedYearId,
        academicYearLabel: sem?.academicYearLabel || prev.academicYearLabel,
      };
    });
  }, []);

  const applySessionPayload = useCallback(
    (res: {
      email: string;
      name: string;
      role: string;
      tenantId: string;
      userId?: string;
      organizationNodeId?: string | null;
      departmentName?: string | null;
      activeDepartmentId?: string | null;
      activeDepartmentName?: string | null;
      activeRole?: string | null;
      memberships?: Membership[];
    }) => {
      const parts = res.name.replace(/^(Dr\.|Prof\.|Mr\.|Ms\.)\s*/, '').split(' ');
      const role = normalizeRole(res.role);
      const memberships = (res.memberships || []) as Membership[];
      const next: User = {
        id: res.userId || 'u-local',
        name: res.name,
        email: res.email,
        role,
        initials: ((parts[0]?.[0] || '') + (parts[1]?.[0] || '')).toUpperCase(),
        departmentName:
          res.activeDepartmentName ||
          res.departmentName ||
          (role === 'SUPER_ADMIN' ? 'Platform' : role === 'INSTITUTION_ADMIN' ? 'Institution' : 'Department'),
        organizationNodeId: res.organizationNodeId || undefined,
        activeDepartmentId: res.activeDepartmentId || res.organizationNodeId || undefined,
        activeDepartmentName: res.activeDepartmentName || res.departmentName || undefined,
        activeRole: res.activeRole || res.role,
        memberships,
      };
      setTenantId(res.tenantId);
      persistUser(next, res.tenantId);
      localStorage.setItem('af_auth', '1');
      setUser(next);
      setSessionEpoch((e) => e + 1);
      setAuthenticated(true);
      window.dispatchEvent(new CustomEvent('af-session-changed', { detail: { email: next.email } }));
    },
    [],
  );

  useEffect(() => {
    let cancelled = false;
    void authService
      .authMode()
      .then((m) => {
        if (!cancelled) setClerkEnabled(!!m.clerkEnabled);
      })
      .catch(() => {
        if (!cancelled) setClerkEnabled(false);
      })
      .finally(() => {
        if (!cancelled) setAuthLoading(false);
      });
    return () => {
      cancelled = true;
    };
  }, []);

  const login = useCallback(
    async (email = 'j.wanjiku@uonbi.ac.ke', password = 'local') => {
      // Wipe prior identity so no request can still use the old email/dept/tenant
      clearClientSession({ keepRemember: true });
      resetWorkspace();
      setUser(EMPTY_USER);
      setAuthenticated(false);

      const res = await authService.login(email, password);
      applySessionPayload(res);
    },
    [resetWorkspace, applySessionPayload],
  );

  const establishClerkSession = useCallback(async () => {
    const res = await authService.establishClerkSession();
    applySessionPayload(res);
  }, [applySessionPayload]);

  const setActiveDepartment = useCallback((departmentId: string) => {
    setUser((prev) => {
      const m = (prev.memberships || []).find((x) => x.organizationNodeId === departmentId);
      const next: User = {
        ...prev,
        activeDepartmentId: departmentId,
        activeDepartmentName: m?.organizationName || prev.activeDepartmentName,
        activeRole: m?.role || prev.activeRole,
        departmentName: m?.organizationName || prev.departmentName,
      };
      persistUser(next);
      return next;
    });
    setSelectedRequestId('');
    setSelectedOfferingId('');
    setDrawer(null);
    setSessionEpoch((e) => e + 1);
    window.dispatchEvent(new CustomEvent('af-department-changed', { detail: departmentId }));
    window.dispatchEvent(new CustomEvent('af-session-changed', { detail: { departmentId } }));
  }, []);

  const logout = useCallback(() => {
    clearClientSession({ keepRemember: true });
    resetWorkspace();
    setUser(EMPTY_USER);
    setAuthenticated(false);
    setSessionEpoch((e) => e + 1);
    window.dispatchEvent(new CustomEvent('af-session-changed', { detail: { email: null } }));
  }, [resetWorkspace]);

  const openDrawer = useCallback((node: ReactNode) => setDrawer(node), []);
  const closeDrawer = useCallback(() => setDrawer(null), []);

  useEffect(() => {
    const onUserUpdated = () => setUser(loadUser());
    window.addEventListener('af-user-updated', onUserUpdated);
    return () => window.removeEventListener('af-user-updated', onUserUpdated);
  }, []);

  const value = useMemo(
    () => ({
      authenticated,
      sessionKey,
      authLoading,
      clerkEnabled,
      login,
      establishClerkSession,
      applySessionPayload,
      logout,
      user,
      setActiveDepartment,
      drawer,
      openDrawer,
      closeDrawer,
      selectedRequestId,
      setSelectedRequestId,
      selectedOfferingId,
      setSelectedOfferingId,
      period,
      setAcademicYearId,
      setSemesterId,
      refreshPeriod,
    }),
    [
      authenticated,
      sessionKey,
      authLoading,
      clerkEnabled,
      login,
      establishClerkSession,
      applySessionPayload,
      logout,
      user,
      setActiveDepartment,
      drawer,
      openDrawer,
      closeDrawer,
      selectedRequestId,
      selectedOfferingId,
      period,
      setAcademicYearId,
      setSemesterId,
      refreshPeriod,
    ],
  );

  return <AppContext.Provider value={value}>{children}</AppContext.Provider>;
}

export function useApp() {
  const ctx = useContext(AppContext);
  if (!ctx) throw new Error('useApp must be used within AppProvider');
  return ctx;
}
