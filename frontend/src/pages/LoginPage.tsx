import { useEffect, useState } from 'react';
import { Link, Navigate, useNavigate, useSearchParams } from 'react-router-dom';
import { useApp } from '../context/AppContext';
import { authService } from '../services';
import { homePath, normalizeRole } from '../lib/access';
import { BrandMark } from '../components/ui/Icons';
import { Button } from '../components/ui/Button';

type Mode = 'signin' | 'register' | 'invite';

export function LoginPage() {
  const { login, authenticated, user } = useApp();
  const navigate = useNavigate();
  const [params, setParams] = useSearchParams();
  const inviteToken = params.get('invite')?.trim() || '';
  const mode: Mode = inviteToken ? 'invite' : params.get('mode') === 'register' ? 'register' : 'signin';

  const [email, setEmail] = useState(() => localStorage.getItem('af_remember_email') || '');
  const [password, setPassword] = useState('');
  const [remember, setRemember] = useState(() => localStorage.getItem('af_remember') === '1');
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [success, setSuccess] = useState<string | null>(null);
  const [forgotOpen, setForgotOpen] = useState(false);
  const [forgotMsg, setForgotMsg] = useState<string | null>(null);
  const [inviteName, setInviteName] = useState('');
  const [invitePreview, setInvitePreview] = useState<{
    email: string;
    name: string;
    role: string;
    institutionName: string;
    expired: boolean;
  } | null>(null);

  const [reg, setReg] = useState({
    name: '',
    code: '',
    adminName: '',
    adminEmail: '',
  });

  useEffect(() => {
    if (!inviteToken) {
      setInvitePreview(null);
      return;
    }
    let cancelled = false;
    void authService
      .previewInvitation(inviteToken)
      .then((p) => {
        if (cancelled) return;
        setInvitePreview(p);
        setInviteName(p.name);
        setEmail(p.email);
      })
      .catch((e) => {
        if (!cancelled) setError(e instanceof Error ? e.message : 'Invalid invitation');
      });
    return () => {
      cancelled = true;
    };
  }, [inviteToken]);

  if (authenticated) {
    return <Navigate to={homePath(user.role)} replace />;
  }

  const setMode = (next: Mode) => {
    setError(null);
    setSuccess(null);
    const p = new URLSearchParams(params);
    p.delete('invite');
    if (next === 'register') p.set('mode', 'register');
    else p.delete('mode');
    setParams(p, { replace: true });
  };

  const handleSignIn = async () => {
    setBusy(true);
    setError(null);
    setSuccess(null);
    try {
      if (remember) {
        localStorage.setItem('af_remember', '1');
        localStorage.setItem('af_remember_email', email);
      } else {
        localStorage.removeItem('af_remember');
        localStorage.removeItem('af_remember_email');
      }
      await login(email.trim(), password || 'local');
      const raw = localStorage.getItem('af_user');
      const role = raw ? normalizeRole(JSON.parse(raw).role) : 'VIEWER';
      navigate(homePath(role));
    } catch (e) {
      setError(e instanceof Error ? e.message : 'Sign in failed');
    } finally {
      setBusy(false);
    }
  };

  const handleAcceptInvite = async () => {
    if (!inviteToken) return;
    setBusy(true);
    setError(null);
    try {
      const res = await authService.acceptInvitation({
        token: inviteToken,
        name: inviteName.trim() || undefined,
        password: password || 'local',
      });
      localStorage.setItem('af_auth', '1');
      const role = normalizeRole(res.role);
      navigate(homePath(role));
      window.location.reload();
    } catch (e) {
      setError(e instanceof Error ? e.message : 'Could not accept invitation');
    } finally {
      setBusy(false);
    }
  };

  const handleRegister = async () => {
    setBusy(true);
    setError(null);
    setSuccess(null);
    try {
      const res = await authService.registerInstitution({
        name: reg.name.trim(),
        code: reg.code.trim(),
        adminEmail: reg.adminEmail.trim(),
        adminName: reg.adminName.trim(),
      });
      setSuccess(res.message);
      setEmail(reg.adminEmail.trim());
      setMode('signin');
    } catch (e) {
      setError(e instanceof Error ? e.message : 'Registration failed');
    } finally {
      setBusy(false);
    }
  };

  useEffect(() => {
    const onKey = (e: KeyboardEvent) => {
      if (e.key === 'Enter' && !forgotOpen) {
        if (mode === 'signin') void handleSignIn();
        else if (mode === 'invite') void handleAcceptInvite();
        else void handleRegister();
      }
    };
    window.addEventListener('keydown', onKey);
    return () => window.removeEventListener('keydown', onKey);
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [mode, email, password, remember, reg, forgotOpen, inviteToken, inviteName]);

  return (
    <div id="login-screen">
      <div className="login-left">
        <div className="login-left-inner">
          <div className="login-brand">
            <BrandMark size={32} />
            <div className="login-brand-name">AcademicFlow</div>
          </div>
          <div className="login-headline">
            Plan teaching. Match expertise.
            <br />
            Allocate with confidence.
          </div>
          <p className="login-sub">
            Institutions register for an account. Once approved, admins invite colleagues and run
            allocation from request to published timetable.
          </p>
          <p className="login-sub" style={{ marginTop: 16 }}>
            <Link to="/" style={{ color: '#AEB6CC', textDecoration: 'underline' }}>
              ← Back to landing
            </Link>
          </p>
        </div>
      </div>
      <div className="login-right">
        <div className="login-card">
          {mode !== 'invite' && (
            <div className="btn-row" style={{ marginBottom: 16 }}>
              <Button size="sm" variant={mode === 'signin' ? 'primary' : undefined} onClick={() => setMode('signin')}>
                Sign in
              </Button>
              <Button
                size="sm"
                variant={mode === 'register' ? 'primary' : undefined}
                onClick={() => setMode('register')}
              >
                Register institution
              </Button>
            </div>
          )}

          {mode === 'invite' ? (
            <>
              <h2>Accept invitation</h2>
              <p className="hint">
                {invitePreview
                  ? `${invitePreview.institutionName} invited you as ${invitePreview.role}.`
                  : 'Loading invitation…'}
              </p>
              {invitePreview?.expired && (
                <p className="section-sub" style={{ color: 'var(--danger)', marginBottom: 12 }}>
                  This invitation is expired or no longer valid.
                </p>
              )}
              <div className="field">
                <label>Email</label>
                <input value={invitePreview?.email || email} disabled />
              </div>
              <div className="field">
                <label>Full name</label>
                <input value={inviteName} onChange={(e) => setInviteName(e.target.value)} />
              </div>
              <div className="field">
                <label>Password (optional in demo)</label>
                <input type="password" value={password} onChange={(e) => setPassword(e.target.value)} />
              </div>
              {error && (
                <p className="section-sub" style={{ color: 'var(--danger)', marginBottom: 12 }}>
                  {error}
                </p>
              )}
              <Button
                variant="primary"
                fullWidth
                disabled={busy || !inviteToken || !!invitePreview?.expired}
                onClick={() => void handleAcceptInvite()}
              >
                {busy ? 'Activating…' : 'Accept & continue'}
              </Button>
            </>
          ) : mode === 'signin' ? (
            <>
              <h2>Sign in</h2>
              <p className="hint">One account for your institution workspace.</p>
              {success && (
                <p className="section-sub" style={{ color: 'var(--success)', marginBottom: 12 }}>
                  {success}
                </p>
              )}
              <div className="field">
                <label>Work email</label>
                <input
                  id="login-email"
                  type="email"
                  value={email}
                  onChange={(e) => setEmail(e.target.value)}
                  autoComplete="username"
                />
              </div>
              <div className="field">
                <label>Password</label>
                <input
                  type="password"
                  value={password}
                  onChange={(e) => setPassword(e.target.value)}
                  autoComplete="current-password"
                />
              </div>
              {error && (
                <p className="section-sub" style={{ color: 'var(--danger)', marginBottom: 12 }}>
                  {error}
                </p>
              )}
              <div className="field-row">
                <label className="checkbox-row" style={{ cursor: 'pointer' }}>
                  <input
                    type="checkbox"
                    checked={remember}
                    onChange={(e) => setRemember(e.target.checked)}
                    style={{ width: 14, height: 14, margin: 0 }}
                  />{' '}
                  Remember me
                </label>
                <button type="button" className="linkish" onClick={() => setForgotOpen(true)}>
                  Forgot password?
                </button>
              </div>
              <Button variant="primary" fullWidth onClick={() => void handleSignIn()} disabled={busy || !email.trim()}>
                {busy ? 'Signing in…' : 'Sign in'}
              </Button>
            </>
          ) : (
            <>
              <h2>Register your institution</h2>
              <p className="hint">Submit for approval. You sign in after the platform administrator accepts your school.</p>
              <div className="field">
                <label>Institution name</label>
                <input
                  placeholder="e.g. Kenyatta University"
                  value={reg.name}
                  onChange={(e) => setReg({ ...reg, name: e.target.value })}
                />
              </div>
              <div className="field">
                <label>Institution code</label>
                <input
                  placeholder="e.g. KU"
                  value={reg.code}
                  onChange={(e) => setReg({ ...reg, code: e.target.value.toUpperCase() })}
                />
              </div>
              <div className="field">
                <label>Admin full name</label>
                <input
                  value={reg.adminName}
                  onChange={(e) => setReg({ ...reg, adminName: e.target.value })}
                />
              </div>
              <div className="field">
                <label>Admin work email</label>
                <input
                  type="email"
                  value={reg.adminEmail}
                  onChange={(e) => setReg({ ...reg, adminEmail: e.target.value })}
                />
              </div>
              {error && (
                <p className="section-sub" style={{ color: 'var(--danger)', marginBottom: 12 }}>
                  {error}
                </p>
              )}
              <Button
                variant="primary"
                fullWidth
                disabled={
                  busy ||
                  !reg.name.trim() ||
                  !reg.code.trim() ||
                  !reg.adminEmail.trim() ||
                  !reg.adminName.trim()
                }
                onClick={() => void handleRegister()}
              >
                {busy ? 'Submitting…' : 'Submit for approval'}
              </Button>
            </>
          )}
        </div>
      </div>

      {forgotOpen && (
        <div className="search-overlay" onClick={() => setForgotOpen(false)}>
          <div className="search-modal" style={{ maxWidth: 420 }} onClick={(e) => e.stopPropagation()}>
            <div className="card-pad">
              <div className="section-title">Reset password</div>
              <p className="section-sub">
                Contact your institution administrator for access. Platform approval is required for
                new school accounts.
              </p>
              <div className="field">
                <label>Work email</label>
                <input value={email} onChange={(e) => setEmail(e.target.value)} />
              </div>
              {forgotMsg && <p className="section-sub">{forgotMsg}</p>}
              <div className="btn-row">
                <Button onClick={() => setForgotOpen(false)}>Close</Button>
                <Button
                  variant="primary"
                  onClick={() => {
                    setForgotMsg(`Request noted for ${email}. Your institution admin can restore access.`);
                  }}
                >
                  Send request
                </Button>
              </div>
            </div>
          </div>
        </div>
      )}
    </div>
  );
}
