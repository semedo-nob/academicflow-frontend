import {
  createContext,
  useCallback,
  useContext,
  useEffect,
  useMemo,
  useState,
  type ReactNode,
} from 'react';
import { CURRENT_USER } from '../data/mockData';
import type { Membership, User } from '../types';
import { adminService, authService } from '../services';
import { normalizeRole } from '../lib/access';
import { setActiveDepartmentId } from '../services/api';

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
  login: (email?: string, password?: string) => Promise<void>;
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
      };
      const parts = parsed.name.replace(/^(Dr\.|Prof\.|Mr\.|Ms\.)\s*/, '').split(' ');
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
  return CURRENT_USER;
}

function persistUser(user: User) {
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
    }),
  );
  if (user.activeDepartmentId) setActiveDepartmentId(user.activeDepartmentId);
  else setActiveDepartmentId(null);
}

export function AppProvider({ children }: { children: ReactNode }) {
  const [authenticated, setAuthenticated] = useState(
    () => localStorage.getItem('af_auth') === '1',
  );
  const [user, setUser] = useState<User>(loadUser);
  const [drawer, setDrawer] = useState<ReactNode | null>(null);
  const [selectedRequestId, setSelectedRequestId] = useState('');
  const [selectedOfferingId, setSelectedOfferingId] = useState('');
  const [period, setPeriod] = useState<PeriodState>(defaultPeriod);

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
  }, [authenticated, refreshPeriod]);

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

  const login = useCallback(async (email = 'j.wanjiku@uonbi.ac.ke', password = 'local') => {
    const res = await authService.login(email, password);
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
    setUser(next);
    persistUser(next);
    localStorage.setItem('af_auth', '1');
    setAuthenticated(true);
  }, []);

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
    window.dispatchEvent(new CustomEvent('af-department-changed', { detail: departmentId }));
  }, []);

  const logout = useCallback(() => {
    localStorage.removeItem('af_auth');
    localStorage.removeItem('af_user');
    setActiveDepartmentId(null);
    setAuthenticated(false);
  }, []);

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
      login,
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
      login,
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
