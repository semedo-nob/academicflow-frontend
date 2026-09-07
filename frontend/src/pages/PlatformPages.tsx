import { useEffect, useState } from 'react';
import { Link, useNavigate, useParams } from 'react-router-dom';
import { useFeedback } from '../context/FeedbackContext';
import { platformApi } from '../services/platformApi';
import { Button } from '../components/ui/Button';
import { PageHead } from '../components/ui/Drawer';

function statusBadge(status: string) {
  const cls =
    status === 'APPROVED' || status === 'ACTIVE'
      ? 'badge-success'
      : status === 'PENDING' || status === 'REQUESTED'
        ? 'badge-warning'
        : 'badge-danger';
  return <span className={`badge ${cls}`}>{status}</span>;
}

export function PlatformDashboardPage() {
  const { error: notifyError } = useFeedback();
  const [data, setData] = useState<Awaited<ReturnType<typeof platformApi.commandCenter>> | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [busyId, setBusyId] = useState<string | null>(null);
  const load = async () => {
    try {
      setData(await platformApi.commandCenter());
    } catch (e) {
      setError(e instanceof Error ? e.message : 'Failed to load command center');
    }
  };
  useEffect(() => {
    void load();
  }, []);

  if (error) return <p className="section-sub" style={{ color: 'var(--danger)' }}>{error}</p>;
  if (!data) return <p className="section-sub">Loading product command center…</p>;

  const decide = async (id: string, approve: boolean) => {
    setBusyId(id);
    try {
      await platformApi.lifecycle(id, { status: approve ? 'APPROVED' : 'REJECTED', note: approve ? 'Approved' : 'Rejected' });
      await load();
    } catch (e) {
      notifyError(e instanceof Error ? e.message : 'Failed');
    } finally {
      setBusyId(null);
    }
  };

  return (
    <>
      <PageHead
        title="Command center"
        subtitle={`How is AcademicFlow performing as a product? · v${data.productVersion}`}
      />
      <div className="plat-kpi-grid">
        <div className="plat-kpi"><div className="plat-kpi-label">Institutions</div><div className="plat-kpi-value">{data.customers.total}</div></div>
        <div className="plat-kpi"><div className="plat-kpi-label">Active customers</div><div className="plat-kpi-value">{data.customers.active}</div></div>
        <div className="plat-kpi"><div className="plat-kpi-label">Pending onboarding</div><div className="plat-kpi-value">{data.customers.pending}</div></div>
        <div className="plat-kpi"><div className="plat-kpi-label">Suspended</div><div className="plat-kpi-value">{data.customers.suspended}</div></div>
        <div className="plat-kpi"><div className="plat-kpi-label">Platform users</div><div className="plat-kpi-value">{data.users.total}</div></div>
        <div className="plat-kpi"><div className="plat-kpi-label">Active this week</div><div className="plat-kpi-value">{data.users.activeThisWeek}</div></div>
        <div className="plat-kpi"><div className="plat-kpi-label">Allocations</div><div className="plat-kpi-value">{data.activity.allocations}</div></div>
        <div className="plat-kpi"><div className="plat-kpi-label">Open conflicts</div><div className="plat-kpi-value">{data.activity.openConflicts}</div></div>
      </div>

      <div className="plat-panel">
        <h3>Issues requiring attention</h3>
        {data.attention.length === 0 ? (
          <p className="section-sub">No critical platform issues right now.</p>
        ) : (
          <ul className="plat-attention">
            {data.attention.map((a) => (
              <li key={a}>{a}</li>
            ))}
          </ul>
        )}
      </div>

      <div className="plat-panel">
        <h3>Product health</h3>
        <div className="plat-health">
          {data.health.map((h) => (
            <div key={h.component} className="plat-health-item">
              <div style={{ fontWeight: 700 }}>{h.component}</div>
              <div className={h.status === 'Healthy' ? 'plat-health-ok' : 'plat-health-bad'}>{h.status}</div>
              <div className="cell-sub">{h.detail}</div>
            </div>
          ))}
        </div>
      </div>

      <PendingQueue busyId={busyId} onDecide={decide} />
    </>
  );
}

function PendingQueue({
  busyId,
  onDecide,
}: {
  busyId: string | null;
  onDecide: (id: string, approve: boolean) => void;
}) {
  const [rows, setRows] = useState<Awaited<ReturnType<typeof platformApi.institutions>>>([]);
  useEffect(() => {
    void platformApi.institutions('PENDING').then(setRows).catch(() => setRows([]));
  }, [busyId]);
  return (
    <div className="plat-panel">
      <h3>Customer onboarding queue</h3>
      <p className="section-sub">Approve registrations to activate institution administrators.</p>
      {rows.length === 0 ? (
        <p className="section-sub">No pending institution requests.</p>
      ) : (
        <div className="table-wrap" style={{ border: 'none' }}>
          <table>
            <thead>
              <tr>
                <th>Institution</th>
                <th>Admin</th>
                <th>Stage</th>
                <th />
              </tr>
            </thead>
            <tbody>
              {rows.map((r) => (
                <tr key={r.id}>
                  <td>
                    <Link to={`/platform/institutions/${r.id}`} className="cell-primary">
                      {r.name}
                    </Link>
                    <div className="mono cell-sub">{r.code}</div>
                  </td>
                  <td>
                    <div>{r.adminName}</div>
                    <div className="cell-sub">{r.adminEmail}</div>
                  </td>
                  <td>{statusBadge(r.onboardingStage || r.status)}</td>
                  <td>
                    <div className="btn-row">
                      <Button size="sm" disabled={busyId === r.id} onClick={() => onDecide(r.id, false)}>
                        Reject
                      </Button>
                      <Button size="sm" variant="primary" disabled={busyId === r.id} onClick={() => onDecide(r.id, true)}>
                        Approve
                      </Button>
                    </div>
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      )}
    </div>
  );
}

export function PlatformInstitutionsPage() {
  const { confirm, error: notifyError, success } = useFeedback();
  const [filter, setFilter] = useState('');
  const [rows, setRows] = useState<Awaited<ReturnType<typeof platformApi.institutions>>>([]);
  const [error, setError] = useState<string | null>(null);
  const [busyId, setBusyId] = useState<string | null>(null);
  const load = () =>
    platformApi
      .institutions(filter || undefined)
      .then(setRows)
      .catch((e) => setError(e instanceof Error ? e.message : 'Failed'));
  useEffect(() => {
    void load();
  }, [filter]);

  const act = async (id: string, status: string) => {
    const ok = await confirm({
      title: `${status} institution`,
      message: `Are you sure you want to mark this institution as ${status}? This affects customer access to AcademicFlow.`,
      confirmLabel: status,
      danger: status === 'SUSPENDED' || status === 'REJECTED' || status === 'ARCHIVED',
    });
    if (!ok) return;
    setBusyId(id);
    try {
      await platformApi.lifecycle(id, { status, note: `${status} by product owner` });
      success(`Institution set to ${status}`);
      await load();
    } catch (e) {
      notifyError(e instanceof Error ? e.message : 'Failed');
    } finally {
      setBusyId(null);
    }
  };

  return (
    <>
      <PageHead
        title="Institutions"
        subtitle="AcademicFlow customers — not their departments or lecturers."
        actions={
          <div className="btn-row">
            {['', 'PENDING', 'APPROVED', 'SUSPENDED', 'REJECTED', 'ARCHIVED'].map((f) => (
              <Button key={f || 'ALL'} size="sm" variant={filter === f ? 'primary' : undefined} onClick={() => setFilter(f)}>
                {f || 'ALL'}
              </Button>
            ))}
          </div>
        }
      />
      {error && <p className="section-sub" style={{ color: 'var(--danger)' }}>{error}</p>}
      <div className="table-wrap">
        <table>
          <thead>
            <tr>
              <th>Customer</th>
              <th>Users</th>
              <th>Status</th>
              <th>Plan</th>
              <th />
            </tr>
          </thead>
          <tbody>
            {rows.map((r) => (
              <tr key={r.id}>
                <td>
                  <Link to={`/platform/institutions/${r.id}`} className="cell-primary">
                    {r.name}
                  </Link>
                  <div className="mono cell-sub">{r.code}</div>
                </td>
                <td>{r.userCount}</td>
                <td>{statusBadge(r.status)}</td>
                <td className="cell-sub">{r.planCode}</td>
                <td>
                  <div className="btn-row">
                    {r.status === 'APPROVED' && (
                      <Button size="sm" disabled={busyId === r.id} onClick={() => void act(r.id, 'SUSPENDED')}>
                        Suspend
                      </Button>
                    )}
                    {(r.status === 'SUSPENDED' || r.status === 'REJECTED' || r.status === 'ARCHIVED') && (
                      <Button size="sm" variant="primary" disabled={busyId === r.id} onClick={() => void act(r.id, 'APPROVED')}>
                        Activate
                      </Button>
                    )}
                    {r.status === 'APPROVED' && (
                      <Button size="sm" disabled={busyId === r.id} onClick={() => void act(r.id, 'ARCHIVED')}>
                        Archive
                      </Button>
                    )}
                  </div>
                </td>
              </tr>
            ))}
          </tbody>
        </table>
      </div>
    </>
  );
}

export function PlatformInstitutionDetailPage() {
  const { id = '' } = useParams();
  const navigate = useNavigate();
  const [data, setData] = useState<Awaited<ReturnType<typeof platformApi.institution>> | null>(null);
  const [error, setError] = useState<string | null>(null);
  useEffect(() => {
    void platformApi
      .institution(id)
      .then(setData)
      .catch((e) => setError(e instanceof Error ? e.message : 'Failed'));
  }, [id]);
  if (error) return <p className="section-sub" style={{ color: 'var(--danger)' }}>{error}</p>;
  if (!data) return <p className="section-sub">Loading customer overview…</p>;
  return (
    <>
      <PageHead
        title={data.name}
        subtitle={`Customer overview · ${data.code} · ${data.planCode}`}
        actions={
          <Button size="sm" onClick={() => navigate('/platform/institutions')}>
            Back
          </Button>
        }
      />
      <div className="plat-panel">
        <div className="btn-row" style={{ marginBottom: 12 }}>
          {statusBadge(data.status)}
          <span className="cell-sub">Onboarding: {data.onboardingStage}</span>
          <span className="cell-sub">Created {new Date(data.createdAt).toLocaleString()}</span>
        </div>
        <div className="plat-kpi-grid">
          {Object.entries(data.usage).map(([k, v]) => (
            <div key={k} className="plat-kpi">
              <div className="plat-kpi-label">{k}</div>
              <div className="plat-kpi-value">{v}</div>
            </div>
          ))}
        </div>
      </div>
      <div className="plat-panel">
        <h3>Administrators</h3>
        <div className="table-wrap" style={{ border: 'none' }}>
          <table>
            <thead>
              <tr>
                <th>Name</th>
                <th>Email</th>
                <th>Role</th>
                <th>Status</th>
                <th>Last login</th>
              </tr>
            </thead>
            <tbody>
              {data.administrators.map((a) => (
                <tr key={a.id}>
                  <td className="cell-primary">{a.name}</td>
                  <td>{a.email}</td>
                  <td className="mono">{a.role}</td>
                  <td>{a.accountStatus}</td>
                  <td className="cell-sub">{a.lastLoginAt ? new Date(a.lastLoginAt).toLocaleString() : '—'}</td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      </div>
      <div className="plat-panel">
        <h3>Recent institution activity</h3>
        <div className="table-wrap" style={{ border: 'none' }}>
          <table>
            <thead>
              <tr>
                <th>When</th>
                <th>Action</th>
                <th>Details</th>
              </tr>
            </thead>
            <tbody>
              {data.recentAudit.map((a) => (
                <tr key={a.id}>
                  <td className="cell-sub">{new Date(a.createdAt).toLocaleString()}</td>
                  <td>{a.action}</td>
                  <td className="cell-sub">{a.details || '—'}</td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      </div>
    </>
  );
}

export function PlatformUsersPage() {
  const { confirm, error: notifyError, success } = useFeedback();
  const [q, setQ] = useState('');
  const [rows, setRows] = useState<Awaited<ReturnType<typeof platformApi.users>>>([]);
  const load = () => void platformApi.users(q || undefined).then(setRows).catch(() => setRows([]));
  useEffect(() => {
    void load();
  }, []);
  return (
    <>
      <PageHead title="Platform accounts" subtitle="Who is using AcademicFlow across all institutions?" />
      <div className="filters-bar" style={{ marginBottom: 12 }}>
        <input
          placeholder="Search email, name, role…"
          value={q}
          onChange={(e) => setQ(e.target.value)}
          style={{ minWidth: 260 }}
        />
        <Button size="sm" variant="primary" onClick={() => load()}>
          Search
        </Button>
      </div>
      <div className="table-wrap">
        <table>
          <thead>
            <tr>
              <th>User</th>
              <th>Institution</th>
              <th>Role</th>
              <th>Status</th>
              <th>Last login</th>
              <th />
            </tr>
          </thead>
          <tbody>
            {rows.map((u) => (
              <tr key={u.id}>
                <td>
                  <div className="cell-primary">{u.name}</div>
                  <div className="cell-sub">{u.email}</div>
                </td>
                <td>
                  {u.institutionName}
                  <div className="mono cell-sub">{u.institutionCode}</div>
                </td>
                <td className="mono">{u.role}</td>
                <td>{statusBadge(u.accountStatus)}</td>
                <td className="cell-sub">{u.lastLoginAt ? new Date(u.lastLoginAt).toLocaleString() : '—'}</td>
                <td>
                  {u.role !== 'SUPER_ADMIN' && (
                    <div className="btn-row">
                      {u.accountStatus === 'ACTIVE' ? (
                        <Button
                          size="sm"
                          onClick={async () => {
                            const ok = await confirm({
                              title: 'Suspend account',
                              message: `Suspend ${u.name} (${u.email})? They will not be able to sign in until reactivated.`,
                              confirmLabel: 'Suspend',
                              danger: true,
                            });
                            if (!ok) return;
                            try {
                              await platformApi.setUserStatus(u.id, { status: 'SUSPENDED' });
                              success('Account suspended');
                              load();
                            } catch (e) {
                              notifyError(e instanceof Error ? e.message : 'Failed');
                            }
                          }}
                        >
                          Suspend
                        </Button>
                      ) : (
                        <Button
                          size="sm"
                          variant="primary"
                          onClick={async () => {
                            try {
                              await platformApi.setUserStatus(u.id, { status: 'ACTIVE' });
                              success('Account activated');
                              load();
                            } catch (e) {
                              notifyError(e instanceof Error ? e.message : 'Failed');
                            }
                          }}
                        >
                          Activate
                        </Button>
                      )}
                    </div>
                  )}
                </td>
              </tr>
            ))}
          </tbody>
        </table>
      </div>
    </>
  );
}

export function PlatformAnalyticsPage() {
  const [data, setData] = useState<Record<string, unknown> | null>(null);
  useEffect(() => {
    void platformApi.analytics().then(setData).catch(() => setData(null));
  }, []);
  if (!data) return <p className="section-sub">Loading analytics…</p>;
  const totals = data.totals as Record<string, number>;
  const featureUsage = data.featureUsage as Record<string, number>;
  const institutions = data.institutions as { id: string; name: string; code: string; status: string; users: number; requests: number; allocations: number; imports: number }[];
  return (
    <>
      <PageHead title="Product analytics" subtitle="Adoption and feature usage across AcademicFlow — not one university’s operations." />
      <div className="plat-kpi-grid">
        {Object.entries(totals).map(([k, v]) => (
          <div key={k} className="plat-kpi">
            <div className="plat-kpi-label">{k}</div>
            <div className="plat-kpi-value">{v}</div>
          </div>
        ))}
      </div>
      <div className="plat-panel">
        <h3>Feature usage</h3>
        <div className="plat-kpi-grid">
          {Object.entries(featureUsage).map(([k, v]) => (
            <div key={k} className="plat-kpi">
              <div className="plat-kpi-label">{k}</div>
              <div className="plat-kpi-value">{v}</div>
            </div>
          ))}
        </div>
      </div>
      <div className="plat-panel">
        <h3>Institution comparison (aggregate)</h3>
        <div className="table-wrap" style={{ border: 'none' }}>
          <table>
            <thead>
              <tr>
                <th>Institution</th>
                <th>Users</th>
                <th>Requests</th>
                <th>Allocations</th>
                <th>Imports</th>
              </tr>
            </thead>
            <tbody>
              {institutions.map((i) => (
                <tr key={i.id}>
                  <td>
                    <Link to={`/platform/institutions/${i.id}`}>{i.name}</Link>
                    <div className="mono cell-sub">{i.code} · {i.status}</div>
                  </td>
                  <td>{i.users}</td>
                  <td>{i.requests}</td>
                  <td>{i.allocations}</td>
                  <td>{i.imports}</td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      </div>
    </>
  );
}

export function PlatformSecurityPage() {
  const [rows, setRows] = useState<Awaited<ReturnType<typeof platformApi.securityEvents>>>([]);
  useEffect(() => {
    void platformApi.securityEvents().then(setRows).catch(() => setRows([]));
  }, []);
  return (
    <>
      <PageHead title="Security center" subtitle="Failed logins, locks, and security events. Secrets are never shown." />
      <div className="table-wrap">
        <table>
          <thead>
            <tr>
              <th>Severity</th>
              <th>Event</th>
              <th>Email</th>
              <th>Details</th>
              <th>When</th>
            </tr>
          </thead>
          <tbody>
            {rows.map((r) => (
              <tr key={r.id}>
                <td>
                  <span className={`badge ${r.severity === 'HIGH' ? 'badge-danger' : r.severity === 'MEDIUM' ? 'badge-warning' : 'badge-info'}`}>
                    {r.severity}
                  </span>
                </td>
                <td className="mono">{r.eventType}</td>
                <td>{r.email || '—'}</td>
                <td className="cell-sub">{r.details || '—'}</td>
                <td className="cell-sub">{new Date(r.createdAt).toLocaleString()}</td>
              </tr>
            ))}
          </tbody>
        </table>
      </div>
    </>
  );
}

export function PlatformAuditPage() {
  const [rows, setRows] = useState<Awaited<ReturnType<typeof platformApi.audit>>>([]);
  useEffect(() => {
    void platformApi.audit().then(setRows).catch(() => setRows([]));
  }, []);
  return (
    <>
      <PageHead title="Platform audit" subtitle="Product-owner actions: institutions, features, settings, accounts." />
      <div className="table-wrap">
        <table>
          <thead>
            <tr>
              <th>When</th>
              <th>Action</th>
              <th>Entity</th>
              <th>Details</th>
            </tr>
          </thead>
          <tbody>
            {rows.map((r) => (
              <tr key={r.id}>
                <td className="cell-sub">{new Date(r.createdAt).toLocaleString()}</td>
                <td className="cell-primary">{r.action}</td>
                <td className="mono cell-sub">{r.entityType}</td>
                <td className="cell-sub">{r.details || '—'}</td>
              </tr>
            ))}
          </tbody>
        </table>
      </div>
    </>
  );
}

export function PlatformMonitoringPage() {
  const [checks, setChecks] = useState<{ component: string; status: string; detail: string }[]>([]);
  useEffect(() => {
    void platformApi.health().then((r) => setChecks(r.checks)).catch(() => setChecks([]));
  }, []);
  return (
    <>
      <PageHead title="System health" subtitle="Live checks from the AcademicFlow backend. No fabricated status." />
      <div className="plat-health">
        {checks.map((h) => (
          <div key={h.component} className="plat-health-item">
            <div style={{ fontWeight: 700 }}>{h.component}</div>
            <div className={h.status === 'Healthy' ? 'plat-health-ok' : 'plat-health-bad'}>{h.status}</div>
            <div className="cell-sub">{h.detail}</div>
          </div>
        ))}
      </div>
    </>
  );
}

export function PlatformFeaturesPage() {
  const [rows, setRows] = useState<Awaited<ReturnType<typeof platformApi.features>>>([]);
  const load = () => void platformApi.features().then(setRows);
  useEffect(() => {
    load();
  }, []);
  return (
    <>
      <PageHead title="Feature flags" subtitle="Product availability controls — not authorization." />
      <div className="table-wrap">
        <table>
          <thead>
            <tr>
              <th>Feature</th>
              <th>Key</th>
              <th>Status</th>
              <th />
            </tr>
          </thead>
          <tbody>
            {rows.map((f) => (
              <tr key={f.id}>
                <td>
                  <div className="cell-primary">{f.name}</div>
                  <div className="cell-sub">{f.description}</div>
                </td>
                <td className="mono">{f.key}</td>
                <td>{f.enabled ? statusBadge('APPROVED') : statusBadge('SUSPENDED')}</td>
                <td>
                  <Button
                    size="sm"
                    variant={f.enabled ? undefined : 'primary'}
                    onClick={async () => {
                      await platformApi.setFeature(f.id, !f.enabled);
                      load();
                    }}
                  >
                    {f.enabled ? 'Disable' : 'Enable'}
                  </Button>
                </td>
              </tr>
            ))}
          </tbody>
        </table>
      </div>
    </>
  );
}

export function PlatformConfigurationPage() {
  const [rows, setRows] = useState<Awaited<ReturnType<typeof platformApi.configuration>>>([]);
  const load = () => void platformApi.configuration().then(setRows);
  useEffect(() => {
    load();
  }, []);
  return (
    <>
      <PageHead title="Platform configuration" subtitle="Global AcademicFlow settings — separate from institution settings." />
      <div className="table-wrap">
        <table>
          <thead>
            <tr>
              <th>Key</th>
              <th>Value</th>
              <th>Description</th>
              <th />
            </tr>
          </thead>
          <tbody>
            {rows.map((s) => (
              <tr key={s.id}>
                <td className="mono">{s.key}</td>
                <td>
                  <input
                    defaultValue={s.value}
                    id={`cfg-${s.id}`}
                    style={{ width: '100%' }}
                  />
                </td>
                <td className="cell-sub">{s.description}</td>
                <td>
                  <Button
                    size="sm"
                    variant="primary"
                    onClick={async () => {
                      const el = document.getElementById(`cfg-${s.id}`) as HTMLInputElement | null;
                      if (!el) return;
                      await platformApi.updateConfiguration(s.id, el.value);
                      load();
                    }}
                  >
                    Save
                  </Button>
                </td>
              </tr>
            ))}
          </tbody>
        </table>
      </div>
    </>
  );
}

export function PlatformDataPage() {
  const [data, setData] = useState<Record<string, unknown> | null>(null);
  useEffect(() => {
    void platformApi.dataQuality().then(setData);
  }, []);
  if (!data) return <p className="section-sub">Loading data quality…</p>;
  return (
    <>
      <PageHead title="Data quality" subtitle="Platform-level problems — not day-to-day institution data entry." />
      <div className="plat-panel">
        <h3>Pending onboarding</h3>
        <pre style={{ whiteSpace: 'pre-wrap', fontSize: 12 }}>{JSON.stringify(data.pendingOnboarding, null, 2)}</pre>
      </div>
      <div className="plat-panel">
        <h3>Institutions missing admin</h3>
        <pre style={{ whiteSpace: 'pre-wrap', fontSize: 12 }}>{JSON.stringify(data.institutionsMissingAdmin, null, 2)}</pre>
      </div>
      <div className="plat-panel">
        <h3>Failed imports</h3>
        <pre style={{ whiteSpace: 'pre-wrap', fontSize: 12 }}>{JSON.stringify(data.failedImports, null, 2)}</pre>
      </div>
      <div className="plat-panel">
        <h3>Orphaned accounts</h3>
        <p className="section-sub">{String(data.orphanedAccounts)}</p>
      </div>
    </>
  );
}

export function PlatformSupportPage() {
  const [q, setQ] = useState('');
  const [result, setResult] = useState<Record<string, unknown> | null>(null);
  return (
    <>
      <PageHead
        title="Support"
        subtitle="Lookup customers and accounts. Impersonation is not enabled in this auth model."
      />
      <div className="filters-bar" style={{ marginBottom: 12 }}>
        <input placeholder="Institution or user…" value={q} onChange={(e) => setQ(e.target.value)} style={{ minWidth: 280 }} />
        <Button
          size="sm"
          variant="primary"
          onClick={async () => {
            setResult(await platformApi.supportLookup(q));
          }}
        >
          Lookup
        </Button>
      </div>
      {result && (
        <div className="plat-panel">
          <pre style={{ whiteSpace: 'pre-wrap', fontSize: 12 }}>{JSON.stringify(result, null, 2)}</pre>
        </div>
      )}
    </>
  );
}

export function PlatformSystemPage() {
  const [info, setInfo] = useState<Record<string, unknown> | null>(null);
  useEffect(() => {
    void platformApi.system().then(setInfo);
  }, []);
  if (!info) return <p className="section-sub">Loading system info…</p>;
  return (
    <>
      <PageHead title="System" subtitle="Version and environment. No secrets exposed." />
      <div className="plat-panel">
        <div className="def-list">
          {Object.entries(info).map(([k, v]) => (
            <div className="def-row" key={k}>
              <span className="def-label">{k}</span>
              <span className="def-value mono">{String(v)}</span>
            </div>
          ))}
        </div>
      </div>
    </>
  );
}
