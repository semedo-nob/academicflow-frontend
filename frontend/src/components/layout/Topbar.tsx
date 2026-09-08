import { useCallback, useEffect, useRef, useState } from 'react';
import { useLocation, useNavigate } from 'react-router-dom';
import { useApp } from '../../context/AppContext';
import { defaultNavigationSuggestions, searchNavigation } from '../../lib/navSearch';
import { auditService, conflictService, searchService } from '../../services';
import { IconBell, IconSearch } from '../ui/Icons';
import { DrawerCloseButton } from '../ui/Drawer';
import { SuggestInput } from '../ui/SuggestInput';

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
  '/profile': 'AcademicFlow / Profile',
};

type Hit = { type: string; id: string; title: string; subtitle: string; path: string };

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
  const { user, period, setAcademicYearId, setSemesterId, openDrawer, closeDrawer, logout, setActiveDepartment } =
    useApp();
  const crumb = BREADCRUMBS[pathname] || 'AcademicFlow';
  const [searchOpen, setSearchOpen] = useState(false);
  const [query, setQuery] = useState('');
  const [hits, setHits] = useState<Hit[]>([]);
  const [searching, setSearching] = useState(false);
  const [activeIndex, setActiveIndex] = useState(0);
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

  const selectedYearLabel =
    yearOptions.find((y) => y.id === (period.selectedYearId || yearOptions[0]?.id))?.label ||
    period.academicYearLabel;
  const selectedSemesterName =
    semesters.find((s) => s.id === (period.selectedSemesterId || semesters[0]?.id))?.name ||
    period.semesterName;
  const [yearDraft, setYearDraft] = useState(selectedYearLabel);
  const [semesterDraft, setSemesterDraft] = useState(selectedSemesterName);

  useEffect(() => {
    setYearDraft(selectedYearLabel);
  }, [selectedYearLabel]);
  useEffect(() => {
    setSemesterDraft(selectedSemesterName);
  }, [selectedSemesterName]);

  const go = useCallback(
    (path: string) => {
      setSearchOpen(false);
      navigate(path);
    },
    [navigate],
  );

  const runSearch = useCallback(
    async (q: string) => {
      setQuery(q);
      setActiveIndex(0);
      const trimmed = q.trim();
      if (!trimmed) {
        setHits(defaultNavigationSuggestions(user.role));
        return;
      }
      const navHits = searchNavigation(trimmed, user.role);
      setHits(navHits);
      setSearching(true);
      try {
        const res = await searchService.search(trimmed);
        const entityHits: Hit[] = res.hits.map((h) => ({
          type: h.type,
          id: h.id,
          title: h.title,
          subtitle: h.subtitle,
          path: h.path,
        }));
        // Prefer navigation matches first, then entity results (dedupe by path+title)
        const seen = new Set(navHits.map((h) => `${h.path}::${h.title}`));
        const merged = [
          ...navHits,
          ...entityHits.filter((h) => {
            const key = `${h.path}::${h.title}`;
            if (seen.has(key)) return false;
            seen.add(key);
            return true;
          }),
        ];
        setHits(merged.slice(0, 24));
      } catch {
        setHits(navHits);
      } finally {
        setSearching(false);
      }
    },
    [user.role],
  );

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
      setHits(defaultNavigationSuggestions(user.role));
      setTimeout(() => inputRef.current?.focus(), 50);
    } else {
      setQuery('');
      setHits([]);
      setActiveIndex(0);
    }
  }, [searchOpen, user.role]);

  return (
    <>
      <header className="topbar">
        <div className="topbar-left">
          <div className="breadcrumb">{crumb}</div>
          <div className="context-line">
            {(user.memberships || []).filter((m) => m.organizationType === 'Department').length > 1 ? (
              <select
                className="dept-context-select"
                value={user.activeDepartmentId || ''}
                onChange={(e) => setActiveDepartment(e.target.value)}
                aria-label="Active department"
              >
                {(user.memberships || [])
                  .filter((m) => m.organizationType === 'Department')
                  .map((m) => (
                    <option key={m.organizationNodeId} value={m.organizationNodeId}>
                      {m.organizationName}
                    </option>
                  ))}
              </select>
            ) : (
              <span>{user.activeDepartmentName || user.departmentName || 'Department'}</span>
            )}{' '}
            <span className="sep">·</span>{' '}
            <span className="period">
              {period.academicYearLabel} · {period.semesterName}
            </span>
          </div>
        </div>
        <div className="topbar-center">
          <button type="button" className="search-box search-box-btn" onClick={() => setSearchOpen(true)}>
            <IconSearch />
            <span>Go to page, lecturer, unit…</span>
            <kbd>⌘K</kbd>
          </button>
        </div>
        <div className="topbar-right">
          <label className="selector-pill selector-pill-input">
            <SuggestInput
              id="topbar-year"
              value={yearDraft}
              onChange={(v) => {
                setYearDraft(v);
                const match = yearOptions.find((y) => y.label === v || y.id === v);
                if (match) setAcademicYearId(match.id);
              }}
              onCommit={(v) => {
                const match = yearOptions.find(
                  (y) => y.label.toLowerCase() === v.trim().toLowerCase() || y.id === v.trim(),
                );
                if (match) setAcademicYearId(match.id);
                else setYearDraft(selectedYearLabel);
              }}
              options={yearOptions.map((y) => ({ value: y.id, label: y.label }))}
              hint=""
              placeholder="Academic year"
            />
          </label>
          <label className="selector-pill selector-pill-input">
            <SuggestInput
              id="topbar-semester"
              value={semesterDraft}
              onChange={(v) => {
                setSemesterDraft(v);
                const match = semesters.find((s) => s.name === v || s.id === v);
                if (match) setSemesterId(match.id);
              }}
              onCommit={(v) => {
                const match = semesters.find(
                  (s) => s.name.toLowerCase() === v.trim().toLowerCase() || s.id === v.trim(),
                );
                if (match) setSemesterId(match.id);
                else setSemesterDraft(selectedSemesterName);
              }}
              options={semesters.map((s) => ({ value: s.id, label: s.name }))}
              hint=""
              placeholder="Semester"
            />
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
                    navigate('/profile');
                  }}
                >
                  Profile
                </button>
                <button
                  type="button"
                  className="btn btn-sm"
                  style={{ width: '100%', marginTop: 8 }}
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
                placeholder='Try “board”, “allocated”, “profile”, unit codes…'
                onChange={(e) => void runSearch(e.target.value)}
                onKeyDown={(e) => {
                  if (e.key === 'ArrowDown') {
                    e.preventDefault();
                    setActiveIndex((i) => Math.min(i + 1, Math.max(hits.length - 1, 0)));
                  } else if (e.key === 'ArrowUp') {
                    e.preventDefault();
                    setActiveIndex((i) => Math.max(i - 1, 0));
                  } else if (e.key === 'Enter' && hits[activeIndex]) {
                    e.preventDefault();
                    go(hits[activeIndex].path);
                  }
                }}
              />
              <kbd>Esc</kbd>
            </div>
            <div className="search-modal-body">
              {searching && <p className="section-sub">Searching records…</p>}
              {!query && (
                <p className="section-sub">Jump to a page, or search lecturers, units, and requests.</p>
              )}
              {!searching && query && hits.length === 0 && (
                <p className="section-sub">No matches for “{query}”.</p>
              )}
              {hits.map((h, i) => (
                <button
                  key={`${h.type}-${h.id}-${h.path}`}
                  type="button"
                  className={`search-hit${i === activeIndex ? ' active' : ''}`}
                  onMouseEnter={() => setActiveIndex(i)}
                  onClick={() => go(h.path)}
                >
                  <span className={`badge ${h.type === 'Go to' ? 'badge-neutral' : 'badge-info'}`}>{h.type}</span>
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
