import { SignIn, SignUp, useAuth, useClerk } from '@clerk/react';
import { useEffect, useState } from 'react';
import { Link, Navigate, useNavigate, useParams, useSearchParams } from 'react-router-dom';
import { useApp } from '../context/AppContext';
import { authService } from '../services';
import { homePath, normalizeRole } from '../lib/access';
import {
  clearRememberedInviteToken,
  inviteReturnPath,
  rememberInviteToken,
} from '../lib/inviteContext';
import { resolvePostLoginPath } from './OnboardingPage';
import { BrandMark } from '../components/ui/Icons';
import { Button } from '../components/ui/Button';
import { clerkConfigured } from '../lib/clerk';

type Mode = 'signin' | 'register' | 'invite';

const clerkAppearance = {
  variables: {
    colorPrimary: '#4457E8',
    colorText: '#1a1f36',
    borderRadius: '8px',
  },
  elements: {
    card: { boxShadow: 'none', border: 'none' },
    headerTitle: { display: 'none' },
    headerSubtitle: { display: 'none' },
    footer: { display: 'none' },
  },
};

export function LoginPage() {
  const {
    login,
    authenticated,
    user,
    clerkEnabled,
    authLoading,
    applySessionPayload,
    establishClerkSession,
    logout,
  } = useApp();
  const navigate = useNavigate();
  const [params, setParams] = useSearchParams();
  const { token: pathInviteToken } = useParams();
  const inviteToken = pathInviteToken?.trim() || params.get('invite')?.trim() || '';
  const mode: Mode = inviteToken ? 'invite' : params.get('mode') === 'register' ? 'register' : 'signin';
  const useClerkUi = clerkConfigured && clerkEnabled;

  const [email, setEmail] = useState(() => localStorage.getItem('af_remember_email') || '');
  const [password, setPassword] = useState('');
  const [remember, setRemember] = useState(() => localStorage.getItem('af_remember') === '1');
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [success, setSuccess] = useState<string | null>(null);
  const [forgotOpen, setForgotOpen] = useState(false);
  const [forgotMsg, setForgotMsg] = useState<string | null>(null);
  const [inviteName, setInviteName] = useState('');
  const [clerkAuthMode, setClerkAuthMode] = useState<'sign-in' | 'sign-up'>('sign-in');
  const [invitePreview, setInvitePreview] = useState<{
    email: string;
    name: string;
    role: string;
    institutionName: string;
    expired: boolean;
    status?: string;
  } | null>(null);

  const [reg, setReg] = useState({
    name: '',
    code: '',
    adminName: '',
    adminEmail: '',
  });

  useEffect(() => {
    if (inviteToken) rememberInviteToken(inviteToken);
  }, [inviteToken]);

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

  if (authLoading) {
    return (
      <div id="login-screen">
        <div className="login-right" style={{ margin: 'auto' }}>
          <p className="hint">Checking authentication…</p>
        </div>
      </div>
    );
  }

  if (authenticated && mode !== 'invite') {
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
      const path = await resolvePostLoginPath(role);
      navigate(path);
    } catch (e) {
      setError(e instanceof Error ? e.message : 'Sign in failed');
    } finally {
      setBusy(false);
    }
  };

  const handleAcceptInviteLegacy = async () => {
    if (!inviteToken) return;
    setBusy(true);
    setError(null);
    try {
      const res = await authService.acceptInvitation({
        token: inviteToken,
        name: inviteName.trim() || undefined,
        password: password || 'local',
      });
      applySessionPayload({
        ...res,
        memberships: [],
      });
      clearRememberedInviteToken();
      navigate(homePath(normalizeRole(res.role)));
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
      setSuccess(
        `${res.message} After approval, sign in and open Institution setup (sidebar) to create departments and invite chairs.`,
      );
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
      if (e.key === 'Enter' && !forgotOpen && !useClerkUi) {
        if (mode === 'signin') void handleSignIn();
        else if (mode === 'invite') void handleAcceptInviteLegacy();
        else void handleRegister();
      }
    };
    window.addEventListener('keydown', onKey);
    return () => window.removeEventListener('keydown', onKey);
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [mode, email, password, remember, reg, forgotOpen, inviteToken, inviteName, useClerkUi]);

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
            Institutions register for an account. Once approved, admins open <b>Institution setup</b> to invite
            department chairs, then run allocation from request to published timetable.
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
              {useClerkUi ? (
                <ClerkInviteAccept
                  inviteToken={inviteToken}
                  inviteEmail={invitePreview?.email || email}
                  inviteName={inviteName}
                  expired={!!invitePreview?.expired}
                  clerkAuthMode={clerkAuthMode}
                  setClerkAuthMode={setClerkAuthMode}
                  onAccepted={(role) => {
                    clearRememberedInviteToken();
                    navigate(homePath(normalizeRole(role)));
                  }}
                  onError={setError}
                  error={error}
                  busy={busy}
                  setBusy={setBusy}
                  applySessionPayload={applySessionPayload}
                  logout={logout}
                />
              ) : (
                <>
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
                    onClick={() => void handleAcceptInviteLegacy()}
                  >
                    {busy ? 'Activating…' : 'Accept & continue'}
                  </Button>
                </>
              )}
            </>
          ) : mode === 'signin' ? (
            <>
              <h2>Sign in</h2>
              <p className="hint">
                {useClerkUi
                  ? 'Sign in with your AcademicFlow account (Clerk).'
                  : 'One account for your institution workspace.'}
              </p>
              {success && (
                <p className="section-sub" style={{ color: 'var(--success)', marginBottom: 12 }}>
                  {success}
                </p>
              )}
              {useClerkUi ? (
                <ClerkSignInPanel
                  onReady={async () => {
                    try {
                      await establishClerkSession();
                      const raw = localStorage.getItem('af_user');
                      const role = raw ? normalizeRole(JSON.parse(raw).role) : 'VIEWER';
                      navigate(await resolvePostLoginPath(role));
                    } catch (e) {
                      setError(e instanceof Error ? e.message : 'Could not establish session');
                    }
                  }}
                  error={error}
                  setError={setError}
                  clerkAuthMode={clerkAuthMode}
                  setClerkAuthMode={setClerkAuthMode}
                />
              ) : (
                <>
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
              )}
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
                Contact your institution administrator for access. Platform approval is required for new school
                accounts.
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

function ClerkSignInPanel({
  onReady,
  error,
  setError,
  clerkAuthMode,
  setClerkAuthMode,
}: {
  onReady: () => Promise<void>;
  error: string | null;
  setError: (e: string | null) => void;
  clerkAuthMode: 'sign-in' | 'sign-up';
  setClerkAuthMode: (m: 'sign-in' | 'sign-up') => void;
}) {
  const { isLoaded, isSignedIn } = useAuth();
  const [busy, setBusy] = useState(false);

  useEffect(() => {
    if (!isLoaded || !isSignedIn) return;
    let cancelled = false;
    setBusy(true);
    void onReady()
      .catch((e) => {
        if (!cancelled) setError(e instanceof Error ? e.message : 'Session failed');
      })
      .finally(() => {
        if (!cancelled) setBusy(false);
      });
    return () => {
      cancelled = true;
    };
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [isLoaded, isSignedIn]);

  if (!isLoaded) return <p className="hint">Loading sign-in…</p>;
  if (isSignedIn) {
    return (
      <p className="hint">{busy ? 'Linking your AcademicFlow workspace…' : 'Signed in. Redirecting…'}</p>
    );
  }

  return (
    <>
      <div className="btn-row" style={{ marginBottom: 12 }}>
        <Button
          size="sm"
          variant={clerkAuthMode === 'sign-in' ? 'primary' : undefined}
          onClick={() => setClerkAuthMode('sign-in')}
        >
          Sign in
        </Button>
        <Button
          size="sm"
          variant={clerkAuthMode === 'sign-up' ? 'primary' : undefined}
          onClick={() => setClerkAuthMode('sign-up')}
        >
          Create account
        </Button>
      </div>
      {error && (
        <p className="section-sub" style={{ color: 'var(--danger)', marginBottom: 12 }}>
          {error}
        </p>
      )}
      {clerkAuthMode === 'sign-in' ? (
        <SignIn
          routing="hash"
          forceRedirectUrl="/login"
          appearance={clerkAppearance}
        />
      ) : (
        <SignUp
          routing="hash"
          forceRedirectUrl="/login"
          appearance={clerkAppearance}
        />
      )}
    </>
  );
}

function ClerkInviteAccept({
  inviteToken,
  inviteEmail,
  inviteName,
  expired,
  clerkAuthMode,
  setClerkAuthMode,
  onAccepted,
  onError,
  error,
  busy,
  setBusy,
  applySessionPayload,
  logout,
}: {
  inviteToken: string;
  inviteEmail: string;
  inviteName: string;
  expired: boolean;
  clerkAuthMode: 'sign-in' | 'sign-up';
  setClerkAuthMode: (m: 'sign-in' | 'sign-up') => void;
  onAccepted: (role: string) => void;
  onError: (msg: string | null) => void;
  error: string | null;
  busy: boolean;
  setBusy: (b: boolean) => void;
  applySessionPayload: ReturnType<typeof useApp>['applySessionPayload'];
  logout: () => void;
}) {
  const { isLoaded, isSignedIn } = useAuth();
  const { signOut } = useClerk();
  const returnUrl = inviteReturnPath(inviteToken);
  const [attempted, setAttempted] = useState(false);

  useEffect(() => {
    if (!isLoaded || !isSignedIn || expired || !inviteToken || attempted) return;
    setAttempted(true);
    setBusy(true);
    onError(null);
    void authService
      .acceptInvitation({
        token: inviteToken,
        name: inviteName.trim() || undefined,
      })
      .then((res) => {
        applySessionPayload({
          ...res,
          memberships: res.memberships || [],
        });
        onAccepted(res.role);
      })
      .catch((e) => {
        onError(e instanceof Error ? e.message : 'Could not accept invitation');
        setAttempted(false);
      })
      .finally(() => setBusy(false));
  }, [
    isLoaded,
    isSignedIn,
    expired,
    inviteToken,
    inviteName,
    applySessionPayload,
    onAccepted,
    onError,
    setBusy,
    attempted,
  ]);

  if (!isLoaded) return <p className="hint">Loading authentication…</p>;

  if (isSignedIn) {
    return (
      <>
        <p className="hint">
          Signed in. Verifying invitation for <b>{inviteEmail}</b>…
        </p>
        {busy && <p className="hint">Activating membership…</p>}
        {error && (
          <p className="section-sub" style={{ color: 'var(--danger)', marginBottom: 12 }}>
            {error}
          </p>
        )}
        {error && (
          <Button
            fullWidth
            onClick={() => {
              logout();
              void signOut({ redirectUrl: returnUrl });
            }}
          >
            Sign out and use invited email
          </Button>
        )}
      </>
    );
  }

  return (
    <>
      <p className="hint">
        Sign in or create an account with <b>{inviteEmail || 'the invited email'}</b>. Using a different email will be
        rejected.
      </p>
      <div className="btn-row" style={{ marginBottom: 12 }}>
        <Button
          size="sm"
          variant={clerkAuthMode === 'sign-in' ? 'primary' : undefined}
          onClick={() => setClerkAuthMode('sign-in')}
        >
          Sign in
        </Button>
        <Button
          size="sm"
          variant={clerkAuthMode === 'sign-up' ? 'primary' : undefined}
          onClick={() => setClerkAuthMode('sign-up')}
        >
          Create account
        </Button>
      </div>
      {error && (
        <p className="section-sub" style={{ color: 'var(--danger)', marginBottom: 12 }}>
          {error}
        </p>
      )}
      {clerkAuthMode === 'sign-in' ? (
        <SignIn routing="hash" forceRedirectUrl={returnUrl} appearance={clerkAppearance} />
      ) : (
        <SignUp routing="hash" forceRedirectUrl={returnUrl} appearance={clerkAppearance} />
      )}
    </>
  );
}
