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
import type { User } from '../types';
import { adminService, authService } from '../services';
import { normalizeRole } from '../lib/access';

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
  drawer: ReactNode | null;
  openDrawer: (node: ReactNode) => void;
  closeDrawer: () => void;
  selectedRequestId: string;
  setSelectedRequestId: (id: string) => void;
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
        name: string;
        email: string;
        role: string;
        departmentName?: string;
        organizationNodeId?: string;
      };
      const parts = parsed.name.replace(/^(Dr\.|Prof\.|Mr\.|Ms\.)\s*/, '').split(' ');
      return {
        id: parsed.id || 'u-local',
        name: parsed.name,
        email: parsed.email,
        role: normalizeRole(parsed.role),
        initials: ((parts[0]?.[0] || '') + (parts[1]?.[0] || '')).toUpperCase(),
        departmentName: parsed.departmentName || 'Institution',
        organizationNodeId: parsed.organizationNodeId,
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
      name: user.name,
      email: user.email,
      role: user.role,
      departmentName: user.departmentName,
      organizationNodeId: user.organizationNodeId,
    }),
  );
}

export function AppProvider({ children }: { children: ReactNode }) {
  const [authenticated, setAuthenticated] = useState(
    () => localStorage.getItem('af_auth') === '1',
  );
  const [user, setUser] = useState<User>(loadUser);
  const [drawer, setDrawer] = useState<ReactNode | null>(null);
  const [selectedRequestId, setSelectedRequestId] = useState('');
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
    const next: User = {
      id: 'u-local',
      name: res.name,
      email: res.email,
      role,
      initials: ((parts[0]?.[0] || '') + (parts[1]?.[0] || '')).toUpperCase(),
      departmentName:
        role === 'SUPER_ADMIN' ? 'Platform' : role === 'INSTITUTION_ADMIN' ? 'Institution' : 'Department',
    };
    setUser(next);
    persistUser(next);
    localStorage.setItem('af_auth', '1');
    setAuthenticated(true);
  }, []);

  const logout = useCallback(() => {
    localStorage.removeItem('af_auth');
    localStorage.removeItem('af_user');
    setAuthenticated(false);
  }, []);

  const openDrawer = useCallback((node: ReactNode) => setDrawer(node), []);
  const closeDrawer = useCallback(() => setDrawer(null), []);

  const value = useMemo(
    () => ({
      authenticated,
      login,
      logout,
      user,
      drawer,
      openDrawer,
      closeDrawer,
      selectedRequestId,
      setSelectedRequestId,
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
      drawer,
      openDrawer,
      closeDrawer,
      selectedRequestId,
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
