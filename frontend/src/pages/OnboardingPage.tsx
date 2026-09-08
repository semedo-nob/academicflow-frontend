import { useEffect, useMemo, useState } from 'react';
import { Navigate, useNavigate } from 'react-router-dom';
import { useApp } from '../context/AppContext';
import { useFeedback } from '../context/FeedbackContext';
import { useAsyncData } from '../hooks/useAsyncData';
import { organizationService, userService } from '../services';
import { canManageInstitutions, homePath, normalizeRole } from '../lib/access';
import {
  isSetupSkipped,
  markSetupComplete,
  markSetupSkipped,
  needsInstitutionSetup,
} from '../lib/setup';
import { Button } from '../components/ui/Button';
import { PageHead } from '../components/ui/Drawer';
import { SuggestInput } from '../components/ui/SuggestInput';

type ChairDraft = { name: string; email: string };

export function OnboardingPage() {
  const navigate = useNavigate();
  const { user } = useApp();
  const { error: notifyError, success } = useFeedback();
  const role = normalizeRole(user.role);
  const [step, setStep] = useState(0);
  const [tick, setTick] = useState(0);
  const [busy, setBusy] = useState(false);
  const [deptName, setDeptName] = useState('');
  const [parentName, setParentName] = useState('');
  const [chairDrafts, setChairDrafts] = useState<Record<string, ChairDraft>>({});
  const [lastInviteLinks, setLastInviteLinks] = useState<{ dept: string; path: string }[]>([]);

  const { data: orgs, loading: orgsLoading } = useAsyncData(() => organizationService.list(), [], [tick]);
  const { data: memberships } = useAsyncData(() => userService.memberships(), [], [tick]);
  const { data: invitations } = useAsyncData(() => userService.invitations(), [], [tick]);

  const depts = useMemo(() => orgs.filter((o) => o.type === 'Department'), [orgs]);
  const schools = useMemo(
    () => orgs.filter((o) => o.type === 'School' || o.type === 'Faculty' || o.type === 'College'),
    [orgs],
  );
  const chairByDept = useMemo(() => {
    const set = new Set(
      memberships
        .filter((m) => m.role.toUpperCase() === 'DEPARTMENT_CHAIR')
        .map((m) => m.organizationNodeId),
    );
    return set;
  }, [memberships]);
  const pendingInviteDepts = useMemo(() => {
    return new Set(
      invitations
        .filter((i) => i.status === 'PENDING' && i.role.toUpperCase() === 'DEPARTMENT_CHAIR' && i.organizationNodeId)
        .map((i) => i.organizationNodeId as string),
    );
  }, [invitations]);

  const deptsNeedingChair = depts.filter((d) => !chairByDept.has(d.id) && !pendingInviteDepts.has(d.id));

  useEffect(() => {
    setChairDrafts((prev) => {
      const next = { ...prev };
      for (const d of deptsNeedingChair) {
        if (!next[d.id]) next[d.id] = { name: '', email: '' };
      }
      return next;
    });
  }, [deptsNeedingChair.map((d) => d.id).join(',')]);

  if (canManageInstitutions(role) || role !== 'INSTITUTION_ADMIN') {
    return <Navigate to={homePath(role)} replace />;
  }

  const ready =
    depts.length > 0 && (deptsNeedingChair.length === 0 || depts.every((d) => chairByDept.has(d.id)));

  useEffect(() => {
    if (ready && !isSetupSkipped()) {
      markSetupComplete();
    }
  }, [ready]);

  const addDepartment = async () => {
    if (!deptName.trim()) {
      notifyError('Enter a department name');
      return;
    }
    setBusy(true);
    try {
      await organizationService.create({
        name: deptName.trim(),
        type: 'Department',
        parentName: parentName.trim() || null,
      });
      setDeptName('');
      setTick((t) => t + 1);
      success(`Department “${deptName.trim()}” created`);
    } catch (e) {
      notifyError(e instanceof Error ? e.message : 'Could not create department');
    } finally {
      setBusy(false);
    }
  };

  const inviteChairs = async () => {
    const targets = deptsNeedingChair.filter((d) => {
      const draft = chairDrafts[d.id];
      return draft?.name.trim() && draft?.email.trim();
    });
    if (targets.length === 0) {
      notifyError('Add at least one chair name and email for a department without a chair');
      return;
    }
    setBusy(true);
    const links: { dept: string; path: string }[] = [];
    try {
      for (const d of targets) {
        const draft = chairDrafts[d.id];
        const res = await userService.invite({
          name: draft.name.trim(),
          email: draft.email.trim(),
          role: 'DEPARTMENT_CHAIR',
          organization: d.name,
          organizationNodeId: d.id,
        });
        links.push({ dept: d.name, path: res.invitePath });
      }
      setLastInviteLinks(links);
      setTick((t) => t + 1);
      success(`Created ${links.length} chair invitation(s) — copy the links below`);
    } catch (e) {
      notifyError(e instanceof Error ? e.message : 'Could not invite chairs');
    } finally {
      setBusy(false);
    }
  };

  const finish = (skip = false) => {
    if (skip) markSetupSkipped();
    else markSetupComplete();
    navigate('/dashboard');
  };

  return (
    <>
      <PageHead
        title="Set up your institution"
        subtitle="Create departments and invite one chair account per department so cross-department requests and scoped allocation work."
      />

      <div className="onboard-steps">
        {['Departments', 'Department chairs', 'Done'].map((label, i) => (
          <button
            key={label}
            type="button"
            className={`onboard-step ${i === step ? 'active' : i < step ? 'done' : ''}`}
            onClick={() => setStep(i)}
          >
            <span className="onboard-step-num">{i < step ? '✓' : i + 1}</span>
            {label}
          </button>
        ))}
      </div>

      {step === 0 && (
        <div className="card card-pad">
          <h3 style={{ marginTop: 0 }}>Create departments</h3>
          <p className="section-sub">
            Each department gets its own chair account. You can add more later from Organization.
          </p>
          {orgsLoading && <p className="section-sub">Loading organization…</p>}
          {depts.length === 0 ? (
            <p className="section-sub">No departments yet — add your first one below.</p>
          ) : (
            <ul className="onboard-list">
              {depts.map((d) => (
                <li key={d.id}>
                  <b>{d.name}</b>
                  <span className="cell-sub">
                    {chairByDept.has(d.id)
                      ? 'Chair assigned'
                      : pendingInviteDepts.has(d.id)
                        ? 'Invite pending'
                        : 'Needs chair'}
                  </span>
                </li>
              ))}
            </ul>
          )}
          <div className="field" style={{ marginTop: 16 }}>
            <label>Department name</label>
            <input
              value={deptName}
              onChange={(e) => setDeptName(e.target.value)}
              placeholder="e.g. Computer Science"
            />
          </div>
          {schools.length > 0 && (
            <div className="field">
              <label>Parent school / faculty (optional)</label>
              <SuggestInput
                id="onboard-parent"
                value={parentName}
                onChange={setParentName}
                options={schools.map((s) => ({ value: s.id, label: s.name }))}
                placeholder="Type parent name…"
              />
            </div>
          )}
          <div className="btn-row">
            <Button variant="primary" disabled={busy} onClick={() => void addDepartment()}>
              Add department
            </Button>
            <Button disabled={depts.length === 0} onClick={() => setStep(1)}>
              Continue to chairs
            </Button>
          </div>
        </div>
      )}

      {step === 1 && (
        <div className="card card-pad">
          <h3 style={{ marginTop: 0 }}>Invite department chairs</h3>
          <p className="section-sub">
            Each chair signs in via invite link and only sees their department’s lecturers, units, and requests.
          </p>
          {depts.length === 0 && (
            <p className="section-sub">
              Add departments first. <button type="button" className="linkish" onClick={() => setStep(0)}>Go back</button>
            </p>
          )}
          {deptsNeedingChair.length === 0 && depts.length > 0 ? (
            <p className="section-sub">All departments have a chair or a pending invite.</p>
          ) : (
            deptsNeedingChair.map((d) => (
              <div key={d.id} className="onboard-chair-card">
                <div style={{ fontWeight: 700, marginBottom: 8 }}>{d.name}</div>
                <div className="field">
                  <label>Chair name</label>
                  <input
                    value={chairDrafts[d.id]?.name || ''}
                    onChange={(e) =>
                      setChairDrafts((prev) => ({
                        ...prev,
                        [d.id]: { ...(prev[d.id] || { name: '', email: '' }), name: e.target.value },
                      }))
                    }
                    placeholder="Dr. Jane Wanjiku"
                  />
                </div>
                <div className="field">
                  <label>Chair email</label>
                  <input
                    value={chairDrafts[d.id]?.email || ''}
                    onChange={(e) =>
                      setChairDrafts((prev) => ({
                        ...prev,
                        [d.id]: { ...(prev[d.id] || { name: '', email: '' }), email: e.target.value },
                      }))
                    }
                    placeholder="chair@university.ac.ke"
                  />
                </div>
              </div>
            ))
          )}
          {lastInviteLinks.length > 0 && (
            <div style={{ marginTop: 12 }}>
              <div className="section-title">Invite links</div>
              {lastInviteLinks.map((l) => (
                <p key={l.path} className="section-sub">
                  <b>{l.dept}</b>:{' '}
                  <button
                    type="button"
                    className="linkish"
                    onClick={async () => {
                      const url = `${window.location.origin}${l.path}`;
                      try {
                        await navigator.clipboard.writeText(url);
                        success('Invite link copied');
                      } catch {
                        notifyError(url);
                      }
                    }}
                  >
                    Copy link
                  </button>
                </p>
              ))}
            </div>
          )}
          <div className="btn-row" style={{ marginTop: 16 }}>
            <Button onClick={() => setStep(0)}>Back</Button>
            {deptsNeedingChair.length > 0 && (
              <Button variant="primary" disabled={busy} onClick={() => void inviteChairs()}>
                {busy ? 'Sending…' : 'Send chair invites'}
              </Button>
            )}
            <Button onClick={() => setStep(2)}>Continue</Button>
          </div>
        </div>
      )}

      {step === 2 && (
        <div className="card card-pad">
          <h3 style={{ marginTop: 0 }}>You are ready</h3>
          <p className="section-sub">
            Chairs can request lecturers across departments, attach course outlines, import their department
            allocation timetable, and allocate by workload and expertise.
          </p>
          <ul className="onboard-list">
            <li>
              <b>{depts.length}</b> <span className="cell-sub">departments</span>
            </li>
            <li>
              <b>{[...chairByDept].length}</b> <span className="cell-sub">chairs assigned</span>
            </li>
            <li>
              <b>{invitations.filter((i) => i.status === 'PENDING').length}</b>{' '}
              <span className="cell-sub">pending invites</span>
            </li>
          </ul>
          <div className="btn-row" style={{ marginTop: 16 }}>
            <Button variant="primary" onClick={() => finish(false)}>
              Go to dashboard
            </Button>
            <Button
              onClick={() => {
                markSetupComplete();
                navigate('/admin');
              }}
            >
              Open Administration
            </Button>
          </div>
        </div>
      )}

      <p className="section-sub" style={{ marginTop: 16 }}>
        <button type="button" className="linkish" onClick={() => finish(true)}>
          Skip for now
        </button>
        {' · '}
        You can finish this anytime from Administration or Organization.
      </p>
    </>
  );
}

/** Call after login to decide landing path for institution admins. */
export async function resolvePostLoginPath(role: string): Promise<string> {
  const r = normalizeRole(role);
  if (r !== 'INSTITUTION_ADMIN') return homePath(r);
  if (isSetupSkipped()) return homePath(r);
  try {
    const [orgs, memberships] = await Promise.all([
      organizationService.list(),
      userService.memberships(),
    ]);
    if (needsInstitutionSetup(orgs, memberships)) return '/onboarding';
    markSetupComplete();
  } catch {
    /* stay on dashboard if APIs fail */
  }
  return homePath(r);
}
