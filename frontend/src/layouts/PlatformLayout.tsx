import { useCallback, useEffect, useRef, useState } from 'react';
import { NavLink, Outlet, useNavigate } from 'react-router-dom';
import { useApp } from '../context/AppContext';
import { defaultNavigationSuggestions, searchNavigation } from '../lib/navSearch';
import { platformApi } from '../services/platformApi';
import { Avatar } from '../components/ui/Badge';
import { BrandMark, IconChart, IconDashboard, IconOrg, IconSearch, IconSettings, IconUsers } from '../components/ui/Icons';
import '../styles/platform.css';

const linkClass = ({ isActive }: { isActive: boolean }) => `plat-nav-item${isActive ? ' active' : ''}`;

type Hit = { type: string; id: string; title: string; subtitle: string; path: string };

export function PlatformLayout() {
  const { user, logout } = useApp();
  const navigate = useNavigate();
  const [searchOpen, setSearchOpen] = useState(false);
  const [query, setQuery] = useState('');
  const [hits, setHits] = useState<Hit[]>([]);
  const [activeIndex, setActiveIndex] = useState(0);
  const [searching, setSearching] = useState(false);
  const inputRef = useRef<HTMLInputElement>(null);

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
        const res = await platformApi.search(trimmed);
        const entityHits: Hit[] = res.map((h, idx) => ({
          type: h.type,
          id: `${h.type}-${idx}`,
          title: h.title,
          subtitle: h.subtitle,
          path: h.path,
        }));
        const seen = new Set(navHits.map((h) => `${h.path}::${h.title}`));
        setHits(
          [
            ...navHits,
            ...entityHits.filter((h) => {
              const key = `${h.path}::${h.title}`;
              if (seen.has(key)) return false;
              seen.add(key);
              return true;
            }),
          ].slice(0, 24),
        );
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
          <div className="plat-top-actions">
            <button type="button" className="plat-search-btn" onClick={() => setSearchOpen(true)}>
              <IconSearch /> Search / go to… <kbd>⌘K</kbd>
            </button>
            <button type="button" className="plat-search-btn" onClick={() => navigate('/platform/profile')}>
              Profile
            </button>
            <div className="plat-top-meta">Cross-tenant · Not institution operations</div>
          </div>
        </header>
        <main className="plat-content">
          <Outlet />
        </main>
      </div>

      {searchOpen && (
        <div className="search-overlay" onClick={() => setSearchOpen(false)}>
          <div className="search-modal" onClick={(e) => e.stopPropagation()}>
            <div className="search-modal-input">
              <IconSearch />
              <input
                ref={inputRef}
                value={query}
                placeholder='Try “institutions”, “security”, “profile”…'
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
              {searching && <p className="section-sub">Searching platform…</p>}
              {!query && <p className="section-sub">Jump to any platform page or look up customers and accounts.</p>}
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
    </div>
  );
}
