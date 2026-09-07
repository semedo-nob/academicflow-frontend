import { Link, Navigate } from 'react-router-dom';
import { useApp } from '../context/AppContext';
import { ROLE_GUIDE, homePath } from '../lib/access';
import { BrandMark } from '../components/ui/Icons';
import '../styles/landing.css';

export function LandingPage() {
  const { authenticated, user } = useApp();
  if (authenticated) {
    return <Navigate to={homePath(user.role)} replace />;
  }

  return (
    <div className="lp">
      <header className="lp-nav">
        <div className="lp-nav-brand">
          <BrandMark size={28} />
          <span>AcademicFlow</span>
        </div>
        <div className="lp-nav-actions">
          <a className="lp-link" href="#how">
            How it works
          </a>
          <a className="lp-link" href="#roles">
            Roles
          </a>
          <a className="lp-link" href="#schools">
            Institutions
          </a>
          <Link className="lp-btn lp-btn-ghost" to="/login">
            Sign in
          </Link>
          <Link className="lp-btn lp-btn-solid" to="/login?mode=register">
            Register institution
          </Link>
        </div>
      </header>

      <section className="lp-hero">
        <div className="lp-hero-glow" aria-hidden />
        <div className="lp-hero-grid" aria-hidden />
        <div className="lp-hero-copy">
          <div className="lp-brand-hero">AcademicFlow</div>
          <h1 className="lp-headline">Teaching allocation that stays clear across every school.</h1>
          <p className="lp-lede">
            Register your institution, get approved, then assign roles and run matching from first
            request to published timetable — one shared workspace per school.
          </p>
          <div className="lp-cta">
            <Link className="lp-btn lp-btn-solid lp-btn-lg" to="/login?mode=register">
              Register your institution
            </Link>
            <Link className="lp-btn lp-btn-ghost lp-btn-lg" to="/login">
              Sign in
            </Link>
          </div>
        </div>
        <div className="lp-hero-visual" aria-hidden>
          <div className="lp-orbit lp-orbit-a" />
          <div className="lp-orbit lp-orbit-b" />
          <div className="lp-flow-card lp-flow-1">
            <span>Request</span>
            <strong>MAT 204 · Linear Algebra</strong>
          </div>
          <div className="lp-flow-card lp-flow-2">
            <span>Match</span>
            <strong>93 · Dr. Wanjiku</strong>
          </div>
          <div className="lp-flow-card lp-flow-3">
            <span>Publish</span>
            <strong>Timetable · Lab 2</strong>
          </div>
        </div>
      </section>

      <section className="lp-section" id="how">
        <div className="lp-section-head">
          <h2>From signup to published teaching</h2>
          <p>Institutions join the platform, then run a continuous allocation path inside their school.</p>
        </div>
        <ol className="lp-steps">
          <li>
            <strong>Register</strong>
            <span>Submit your school account. Access opens after platform approval.</span>
          </li>
          <li>
            <strong>Assign roles</strong>
            <span>Institution admins create chairs, deans, lecturers, and viewers.</span>
          </li>
          <li>
            <strong>Allocate</strong>
            <span>Request, match, resolve conflicts, and publish teaching.</span>
          </li>
          <li>
            <strong>Export</strong>
            <span>CSV and PDF packs for allocations and lecturer timetables.</span>
          </li>
        </ol>
      </section>

      <section className="lp-section lp-section-alt" id="roles">
        <div className="lp-section-head">
          <h2>Roles inside your institution</h2>
          <p>
            After approval, your institution admin assigns what each person can see and do — without
            exposing platform controls.
          </p>
        </div>
        <div className="lp-roles">
          {ROLE_GUIDE.map((r) => (
            <article key={r.role} className="lp-role">
              <h3>{r.title}</h3>
              <p>
                <em>Can</em> — {r.can}
              </p>
              <p>
                <em>Limited</em> — {r.cannot}
              </p>
            </article>
          ))}
        </div>
      </section>

      <section className="lp-section" id="schools">
        <div className="lp-section-head">
          <h2>Many institutions, one platform</h2>
          <p>
            Each school registers as its own account. Pending signups wait for approval. Approved
            institutions get an isolated organization tree, users, and academic calendar.
          </p>
        </div>
        <div className="lp-schools-row">
          <div>
            <h3>For schools</h3>
            <p>
              Register once, then build University → School → Department and allocate teaching with
              roles that match how your campus works.
            </p>
          </div>
          <div>
            <h3>After you are approved</h3>
            <ul>
              <li>Sign in with your institution admin email</li>
              <li>Invite users and assign roles</li>
              <li>Run requests, matching, and approvals</li>
              <li>Export allocation and timetable packs</li>
            </ul>
          </div>
        </div>
        <div className="lp-cta lp-cta-center">
          <Link className="lp-btn lp-btn-solid lp-btn-lg" to="/login?mode=register">
            Register institution
          </Link>
          <Link className="lp-btn lp-btn-ghost lp-btn-lg" to="/login">
            Already registered? Sign in
          </Link>
        </div>
      </section>

      <footer className="lp-footer">
        <div className="lp-nav-brand">
          <BrandMark size={22} />
          <span>AcademicFlow</span>
        </div>
        <span>Teaching allocation for multi-school universities</span>
      </footer>
    </div>
  );
}
