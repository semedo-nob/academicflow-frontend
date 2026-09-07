import { useCallback, useEffect, useRef, useState } from 'react';
import { useLocation, useNavigate } from 'react-router-dom';
import { useApp } from '../../context/AppContext';
import { auditService, conflictService, searchService } from '../../services';
import { IconBell, IconChevDown, IconSearch } from '../ui/Icons';
import { DrawerCloseButton } from '../ui/Drawer';

const BREADCRUMBS: Record<string, string> = {
  '/dashboard': 'AcademicFlow / Dashboard',
  '/organization': 'AcademicFlow / Organization',
  '/lecturers': 'AcademicFlow / Lecturers',
  '/units': 'AcademicFlow / Academic Units',
  '/requests': 'AcademicFlow / Requests',
  '/recommendations': 'AcademicFlow / Requests / Recommendations',
  '/allocation': 'AcademicFlow / Allocation Board',
  '/workload': 'AcademicFlow / Workload',
  '/timetable': 'AcademicFlow / Timetable',
  '/conflicts': 'AcademicFlow / Conflicts',
  '/approvals': 'AcademicFlow / Approvals',
  '/import': 'AcademicFlow / Import',
  '/reports': 'AcademicFlow / Reports',
  '/admin': 'AcademicFlow / Administration',
};

function NotificationsDrawer({ onClose }: { onClose: () => void }) {
  const navigate = useNavigate();
  const [items, setItems] = useState<{ text: string; time: string; path?: string }[]>([]);
  const [loading, setLoading] = useState(true);

  useEffect(() => {
    let cancelled = false;
    (async () => {
      try {
        const [conflicts, audit] = await Promise.all([
          conflictService.list().catch(() => []),
          auditService.list().catch(() => []),
        ]);
        if (cancelled) return;
        const rows = [
          ...conflicts.slice(0, 8).map((c) => ({
            text: `${c.category}: ${c.text}`,
            time: 'Open conflict',
            path: '/conflicts',
          })),
          ...audit.slice(0, 10).map((a) => ({
            text: `${a.action}${a.details ? ` — ${a.details}` : ''}`,
            time: new Date(a.createdAt).toLocaleString(),
            path: '/admin',
          })),
        ];
        setItems(rows);
      } finally {
        if (!cancelled) setLoading(false);
      }
    })();
    return () => {
      cancelled = true;
    };
  }, []);

  return (
    <>
      <div className="drawer-head">
        <div style={{ fontWeight: 700, fontSize: 16 }}>Notifications</div>
        <DrawerCloseButton onClose={onClose} />
      </div>
      <div className="drawer-body">
        {loading && <p className="section-sub">Loading…</p>}
        {!loading && items.length === 0 && <p className="section-sub">No notifications right now.</p>}
        <div className="activity-list">
          {items.map((n, i) => (
            <div
              key={`${n.text}-${i}`}
              className="activity-item"
              style={{ cursor: n.path ? 'pointer' : undefined }}
              onClick={() => {
                if (n.path) {
                  navigate(n.path);
                  onClose();
                }
              }}
            >
              <div className="activity-text">{n.text}</div>
              <div className="activity-time">{n.time}</div>
            </div>
          ))}
        </div>
      </div>
    </>
  );
}

export function Topbar() {
  const { pathname } = useLocation();
  const navigate = useNavigate();
  const { user, period, setAcademicYearId, setSemesterId, openDrawer, closeDrawer, logout } = useApp();
  const crumb = BREADCRUMBS[pathname] || 'AcademicFlow';
  const [searchOpen, setSearchOpen] = useState(false);
  const [query, setQuery] = useState('');
  const [hits, setHits] = useState<{ type: string; id: string; title: string; subtitle: string; path: string }[]>([]);
  const [searching, setSearching] = useState(false);
  const inputRef = useRef<HTMLInputElement>(null);
  const [menuOpen, setMenuOpen] = useState(false);

  const yearOptions = period.years.length
    ? period.years
    : [{ id: 'default-year', label: period.academicYearLabel }];
  const semesterOptions = period.semesters.filter((s) =>
    period.selectedYearId ? s.academicYearId === period.selectedYearId : true,
  );
  const semesters =
    semesterOptions.length > 0
      ? semesterOptions
      : [{ id: 'default-sem', academicYearId: '', academicYearLabel: '', name: period.semesterName }];

  const runSearch = useCallback(async (q: string) => {
    setQuery(q);
    if (!q.trim()) {
      setHits([]);
      return;
    }
    setSearching(true);
    try {
      const res = await searchService.search(q.trim());
      setHits(res.hits);
    } catch {
      setHits([]);
    } finally {
      setSearching(false);
    }
  }, []);

  useEffect(() => {
    const onKey = (e: KeyboardEvent) => {
      if ((e.metaKey || e.ctrlKey) && e.key.toLowerCase() === 'k') {
        e.preventDefault();
        setSearchOpen(true);
      }
      if (e.key === 'Escape') setSearchOpen(false);
    };
    window.addEventListener('keydown', onKey);
    return () => window.removeEventListener('keydown', onKey);
  }, []);

  useEffect(() => {
    if (searchOpen) {
      setTimeout(() => inputRef.current?.focus(), 50);
    } else {
      setQuery('');
      setHits([]);
    }
  }, [searchOpen]);

  return (
    <>
      <header className="topbar">
        <div className="topbar-left">
          <div className="breadcrumb">{crumb}</div>
          <div className="context-line">
            {user.departmentName || 'Department'} <span className="sep">·</span>{' '}
            <span className="period">
              {period.academicYearLabel} · {period.semesterName}
            </span>
          </div>
        </div>
        <div className="topbar-center">
          <button type="button" className="search-box search-box-btn" onClick={() => setSearchOpen(true)}>
            <IconSearch />
            <span>Search lecturers, units, requests…</span>
            <kbd>⌘K</kbd>
          </button>
        </div>
        <div className="topbar-right">
          <label className="selector-pill" style={{ cursor: 'pointer' }}>
            <select
              value={period.selectedYearId || yearOptions[0]?.id}
              onChange={(e) => setAcademicYearId(e.target.value)}
              style={{ border: 'none', background: 'transparent', font: 'inherit', color: 'inherit' }}
            >
              {yearOptions.map((y) => (
                <option key={y.id} value={y.id}>
                  {y.label}
                </option>
              ))}
            </select>
            <IconChevDown />
          </label>
          <label className="selector-pill" style={{ cursor: 'pointer' }}>
            <select
              value={period.selectedSemesterId || semesters[0]?.id}
              onChange={(e) => setSemesterId(e.target.value)}
              style={{ border: 'none', background: 'transparent', font: 'inherit', color: 'inherit' }}
            >
              {semesters.map((s) => (
                <option key={s.id} value={s.id}>
                  {s.name}
                </option>
              ))}
            </select>
            <IconChevDown />
          </label>
          <button
            type="button"
            className="icon-btn"
            title="Notifications"
            onClick={() => openDrawer(<NotificationsDrawer onClose={closeDrawer} />)}
          >
            <IconBell />
            <span className="dot-flag" />
          </button>
          <div className="avatar-menu">
            <button
              type="button"
              className="avatar"
              style={{ cursor: 'pointer', border: 'none' }}
              title={user.name}
              onClick={() => setMenuOpen((o) => !o)}
            >
              {user.initials}
            </button>
            {menuOpen && (
              <div className="avatar-dropdown">
                <div className="avatar-dropdown-name">{user.name}</div>
                <div className="cell-sub">{user.email}</div>
                <button
                  type="button"
                  className="btn btn-sm"
                  style={{ width: '100%', marginTop: 10 }}
                  onClick={() => {
                    setMenuOpen(false);
                    logout();
                    navigate('/login');
                  }}
                >
                  Sign out
                </button>
              </div>
            )}
          </div>
        </div>
      </header>

      {searchOpen && (
        <div className="search-overlay" onClick={() => setSearchOpen(false)}>
          <div className="search-modal" onClick={(e) => e.stopPropagation()}>
            <div className="search-modal-input">
              <IconSearch />
              <input
                ref={inputRef}
                value={query}
                placeholder="Search anything across AcademicFlow…"
                onChange={(e) => void runSearch(e.target.value)}
              />
              <kbd>Esc</kbd>
            </div>
            <div className="search-modal-body">
              {searching && <p className="section-sub">Searching…</p>}
              {!searching && query && hits.length === 0 && (
                <p className="section-sub">No matches for “{query}”.</p>
              )}
              {!query && <p className="section-sub">Try a lecturer name, unit code, or request status.</p>}
              {hits.map((h) => (
                <button
                  key={`${h.type}-${h.id}`}
                  type="button"
                  className="search-hit"
                  onClick={() => {
                    setSearchOpen(false);
                    navigate(h.path);
                  }}
                >
                  <span className="badge badge-info">{h.type}</span>
                  <span className="search-hit-main">
                    <span className="search-hit-title">{h.title}</span>
                    <span className="cell-sub">{h.subtitle}</span>
                  </span>
                </button>
              ))}
            </div>
          </div>
        </div>
      )}
    </>
  );
}
