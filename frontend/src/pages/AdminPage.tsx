import { useState, type ReactNode } from 'react';
import { ADMIN_SECTIONS } from '../data/mockData';
import { useApp } from '../context/AppContext';
import { useAsyncData } from '../hooks/useAsyncData';
import { adminService, auditService, organizationService, userService } from '../services';
import { adminSectionsFor, isSuperAdmin, type AdminSection } from '../lib/access';
import { Button } from '../components/ui/Button';
import { Card, DrawerCloseButton, PageHead } from '../components/ui/Drawer';

function DrawerShell({ title, onClose, children }: { title: string; onClose: () => void; children: ReactNode }) {
  return (
    <>
      <div className="drawer-head">
        <div style={{ fontWeight: 700, fontSize: 16 }}>{title}</div>
        <DrawerCloseButton onClose={onClose} />
      </div>
      <div className="drawer-body">{children}</div>
    </>
  );
}

function AuditDrawer({ onClose }: { onClose: () => void }) {
  const { data: logs, loading } = useAsyncData(() => auditService.list(), [], []);
  return (
    <DrawerShell title="Audit log" onClose={onClose}>
      {loading && <p className="section-sub">Loading…</p>}
      <div className="table-wrap" style={{ border: 'none' }}>
        <table>
          <thead>
            <tr>
              <th>Date</th>
              <th>Action</th>
              <th>Entity</th>
              <th>Details</th>
            </tr>
          </thead>
          <tbody>
            {logs.map((a) => (
              <tr key={a.id}>
                <td className="cell-sub">{new Date(a.createdAt).toLocaleString()}</td>
                <td>{a.action}</td>
                <td className="mono">
                  {a.entityType}
                  {a.entityId ? ` · ${a.entityId.slice(0, 8)}` : ''}
                </td>
                <td className="cell-sub">{a.details || '—'}</td>
              </tr>
            ))}
          </tbody>
        </table>
      </div>
    </DrawerShell>
  );
}

function UsersDrawer({ onClose }: { onClose: () => void }) {
  const { user } = useApp();
  const [tick, setTick] = useState(0);
  const { data: users, loading } = useAsyncData(() => userService.list(), [], [tick]);
  const { data: roles } = useAsyncData(() => adminService.roles(), [], []);
  const { data: orgs } = useAsyncData(() => organizationService.list(), [], []);
  const [form, setForm] = useState({ name: '', email: '', role: 'VIEWER', organizationNodeId: '' });
  const [busy, setBusy] = useState(false);

  const roleOptions = (roles.length ? roles : [{ code: 'VIEWER', name: 'Viewer' }]).filter(
    (r) => isSuperAdmin(user.role) || r.code !== 'SUPER_ADMIN',
  );

  const create = async () => {
    setBusy(true);
    try {
      await userService.create({
        name: form.name,
        email: form.email,
        role: form.role,
        organizationNodeId: form.organizationNodeId || null,
      });
      setForm({ name: '', email: '', role: 'VIEWER', organizationNodeId: '' });
      setTick((t) => t + 1);
    } catch (e) {
      window.alert(e instanceof Error ? e.message : 'Could not create user');
    } finally {
      setBusy(false);
    }
  };

  return (
    <DrawerShell title="Users" onClose={onClose}>
      <p className="section-sub">Assign roles for people in this institution.</p>
      {loading && <p className="section-sub">Loading…</p>}
      <div className="table-wrap" style={{ border: 'none' }}>
        <table>
          <thead>
            <tr>
              <th>Name</th>
              <th>Email</th>
              <th>Role</th>
              <th>Status</th>
              <th />
            </tr>
          </thead>
          <tbody>
            {users.map((u) => (
              <tr key={u.id}>
                <td className="cell-primary">{u.name}</td>
                <td className="cell-sub">{u.email}</td>
                <td>
                  <select
                    value={u.role}
                    onChange={async (e) => {
                      await userService.update(u.id, { role: e.target.value });
                      setTick((t) => t + 1);
                    }}
                  >
                    {roleOptions.map((r) => (
                      <option key={r.code} value={r.code}>
                        {r.name || r.code}
                      </option>
                    ))}
                    {!roleOptions.some((r) => r.code === u.role) && (
                      <option value={u.role}>{u.role}</option>
                    )}
                  </select>
                </td>
                <td>{u.active ? 'Active' : 'Inactive'}</td>
                <td>
                  <Button
                    size="sm"
                    onClick={async () => {
                      await userService.update(u.id, { active: !u.active });
                      setTick((t) => t + 1);
                    }}
                  >
                    {u.active ? 'Deactivate' : 'Activate'}
                  </Button>
                </td>
              </tr>
            ))}
          </tbody>
        </table>
      </div>
      <div className="section-title" style={{ marginTop: 18 }}>
        Add user
      </div>
      <div className="field">
        <label>Name</label>
        <input value={form.name} onChange={(e) => setForm({ ...form, name: e.target.value })} />
      </div>
      <div className="field">
        <label>Email</label>
        <input value={form.email} onChange={(e) => setForm({ ...form, email: e.target.value })} />
      </div>
      <div className="field">
        <label>Role</label>
        <select value={form.role} onChange={(e) => setForm({ ...form, role: e.target.value })}>
          {roleOptions.map((r) => (
            <option key={r.code} value={r.code}>
              {r.name || r.code}
            </option>
          ))}
        </select>
      </div>
      <div className="field">
        <label>Organization</label>
        <select
          value={form.organizationNodeId}
          onChange={(e) => setForm({ ...form, organizationNodeId: e.target.value })}
        >
          <option value="">Default</option>
          {orgs.map((o) => (
            <option key={o.id} value={o.id}>
              {o.name}
            </option>
          ))}
        </select>
      </div>
      <Button variant="primary" disabled={busy || !form.name || !form.email} onClick={create}>
        {busy ? 'Saving…' : 'Create user'}
      </Button>
    </DrawerShell>
  );
}

function RolesDrawer({ onClose }: { onClose: () => void }) {
  const { data: roles, loading } = useAsyncData(() => adminService.roles(), [], []);
  return (
    <DrawerShell title="Roles & permissions" onClose={onClose}>
      {loading && <p className="section-sub">Loading…</p>}
      {roles.map((r) => (
        <div key={r.id} style={{ marginBottom: 16 }}>
          <div style={{ fontWeight: 700 }}>{r.name}</div>
          <div className="cell-sub mono">{r.code}</div>
          <p className="section-sub">{r.description}</p>
          <div style={{ display: 'flex', flexWrap: 'wrap', gap: 6 }}>
            {r.permissions.map((p) => (
              <span className="badge badge-info" key={p}>
                {p}
              </span>
            ))}
          </div>
        </div>
      ))}
    </DrawerShell>
  );
}

function InstitutionsDrawer({ onClose }: { onClose: () => void }) {
  const [tick, setTick] = useState(0);
  const [busyId, setBusyId] = useState<string | null>(null);
  const [error, setError] = useState<string | null>(null);
  const { data: rows, loading } = useAsyncData(() => adminService.institutions(), [], [tick]);

  const pending = rows.filter((r) => r.status === 'PENDING');
  const others = rows.filter((r) => r.status !== 'PENDING');

  const decide = async (id: string, approve: boolean) => {
    setBusyId(id);
    setError(null);
    try {
      await adminService.decideInstitution(id, {
        approve,
        note: approve ? 'Approved' : 'Rejected',
      });
      setTick((t) => t + 1);
    } catch (e) {
      setError(e instanceof Error ? e.message : 'Decision failed');
    } finally {
      setBusyId(null);
    }
  };

  return (
    <DrawerShell title="Institutions" onClose={onClose}>
      <p className="section-sub">
        Schools register from the public auth page. Approve to activate their admin account so they
        can assign roles inside their institution.
      </p>
      {loading && <p className="section-sub">Loading…</p>}
      {error && (
        <p className="section-sub" style={{ color: 'var(--danger)' }}>
          {error}
        </p>
      )}

      <div className="section-title">Pending approval ({pending.length})</div>
      {pending.length === 0 ? (
        <p className="section-sub">No pending signups.</p>
      ) : (
        <div className="table-wrap" style={{ border: 'none', marginBottom: 20 }}>
          <table>
            <thead>
              <tr>
                <th>Institution</th>
                <th>Admin</th>
                <th />
              </tr>
            </thead>
            <tbody>
              {pending.map((r) => (
                <tr key={r.id}>
                  <td>
                    <div className="cell-primary">{r.name}</div>
                    <div className="mono cell-sub">{r.code}</div>
                  </td>
                  <td>
                    <div>{r.adminName || '—'}</div>
                    <div className="cell-sub">{r.adminEmail}</div>
                  </td>
                  <td>
                    <div className="btn-row">
                      <Button
                        size="sm"
                        disabled={busyId === r.id}
                        onClick={() => void decide(r.id, false)}
                      >
                        Reject
                      </Button>
                      <Button
                        size="sm"
                        variant="primary"
                        disabled={busyId === r.id}
                        onClick={() => void decide(r.id, true)}
                      >
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

      <div className="section-title">All institutions</div>
      <div className="table-wrap" style={{ border: 'none' }}>
        <table>
          <thead>
            <tr>
              <th>Name</th>
              <th>Code</th>
              <th>Status</th>
            </tr>
          </thead>
          <tbody>
            {others.map((r) => (
              <tr key={r.id}>
                <td className="cell-primary">{r.name}</td>
                <td className="mono">{r.code}</td>
                <td>
                  <span
                    className={`badge ${
                      r.status === 'APPROVED'
                        ? 'badge-success'
                        : r.status === 'REJECTED'
                          ? 'badge-danger'
                          : 'badge-warning'
                    }`}
                  >
                    {r.status}
                  </span>
                </td>
              </tr>
            ))}
          </tbody>
        </table>
      </div>
    </DrawerShell>
  );
}

function OrgTypesDrawer({ onClose }: { onClose: () => void }) {
  const { data: rows, loading } = useAsyncData(() => adminService.organizationTypes(), [], []);
  return (
    <DrawerShell title="Organization types" onClose={onClose}>
      {loading && <p className="section-sub">Loading…</p>}
      <div className="table-wrap" style={{ border: 'none' }}>
        <table>
          <thead>
            <tr>
              <th>Level</th>
              <th>Name</th>
              <th>Description</th>
            </tr>
          </thead>
          <tbody>
            {rows.map((r) => (
              <tr key={r.id}>
                <td>{r.levelNo}</td>
                <td className="cell-primary">{r.name}</td>
                <td className="cell-sub">{r.description}</td>
              </tr>
            ))}
          </tbody>
        </table>
      </div>
    </DrawerShell>
  );
}

function YearsDrawer({ onClose }: { onClose: () => void }) {
  const [tick, setTick] = useState(0);
  const { data: years, loading } = useAsyncData(() => adminService.years(), [], [tick]);
  const [label, setLabel] = useState('');
  return (
    <DrawerShell title="Academic years" onClose={onClose}>
      {loading && <p className="section-sub">Loading…</p>}
      <div className="table-wrap" style={{ border: 'none' }}>
        <table>
          <thead>
            <tr>
              <th>Label</th>
              <th>Start</th>
              <th>End</th>
            </tr>
          </thead>
          <tbody>
            {years.map((y) => (
              <tr key={y.id}>
                <td className="cell-primary">{y.label}</td>
                <td>{y.startDate || '—'}</td>
                <td>{y.endDate || '—'}</td>
              </tr>
            ))}
          </tbody>
        </table>
      </div>
      <div className="field">
        <label>New academic year</label>
        <input value={label} onChange={(e) => setLabel(e.target.value)} placeholder="2027/2028" />
      </div>
      <Button
        variant="primary"
        disabled={!label}
        onClick={async () => {
          await adminService.createYear({ label });
          setLabel('');
          setTick((t) => t + 1);
        }}
      >
        Add year
      </Button>
    </DrawerShell>
  );
}

function SemestersDrawer({ onClose }: { onClose: () => void }) {
  const [tick, setTick] = useState(0);
  const { data: years } = useAsyncData(() => adminService.years(), [], []);
  const { data: semesters, loading } = useAsyncData(() => adminService.semesters(), [], [tick]);
  const [form, setForm] = useState({ academicYearId: '', name: '', sequenceNo: 1 });
  return (
    <DrawerShell title="Semesters" onClose={onClose}>
      {loading && <p className="section-sub">Loading…</p>}
      <div className="table-wrap" style={{ border: 'none' }}>
        <table>
          <thead>
            <tr>
              <th>Year</th>
              <th>Semester</th>
              <th>Seq</th>
            </tr>
          </thead>
          <tbody>
            {semesters.map((s) => (
              <tr key={s.id}>
                <td>{s.academicYearLabel}</td>
                <td className="cell-primary">{s.name}</td>
                <td>{s.sequenceNo}</td>
              </tr>
            ))}
          </tbody>
        </table>
      </div>
      <div className="field">
        <label>Academic year</label>
        <select
          value={form.academicYearId}
          onChange={(e) => setForm({ ...form, academicYearId: e.target.value })}
        >
          <option value="">Select…</option>
          {years.map((y) => (
            <option key={y.id} value={y.id}>
              {y.label}
            </option>
          ))}
        </select>
      </div>
      <div className="field">
        <label>Name</label>
        <input value={form.name} onChange={(e) => setForm({ ...form, name: e.target.value })} />
      </div>
      <Button
        variant="primary"
        disabled={!form.academicYearId || !form.name}
        onClick={async () => {
          await adminService.createSemester(form);
          setForm({ academicYearId: form.academicYearId, name: '', sequenceNo: form.sequenceNo + 1 });
          setTick((t) => t + 1);
        }}
      >
        Add semester
      </Button>
    </DrawerShell>
  );
}

function RulesDrawer({ onClose }: { onClose: () => void }) {
  const [tick, setTick] = useState(0);
  const { data: rules, loading } = useAsyncData(() => adminService.rules(), [], [tick]);
  return (
    <DrawerShell title="Institutional rules" onClose={onClose}>
      {loading && <p className="section-sub">Loading…</p>}
      {rules.map((r) => (
        <div key={r.id} className="field">
          <label>
            {r.key} {r.description ? `— ${r.description}` : ''}
          </label>
          <div style={{ display: 'flex', gap: 8 }}>
            <input
              defaultValue={r.value}
              id={`rule-${r.id}`}
              onBlur={async (e) => {
                if (e.target.value !== r.value) {
                  await adminService.updateRule(r.id, e.target.value);
                  setTick((t) => t + 1);
                }
              }}
            />
          </div>
        </div>
      ))}
    </DrawerShell>
  );
}

function SettingsDrawer({ onClose }: { onClose: () => void }) {
  const [tick, setTick] = useState(0);
  const { data: settings, loading } = useAsyncData(() => adminService.settings(), [], [tick]);
  return (
    <DrawerShell title="System settings" onClose={onClose}>
      {loading && <p className="section-sub">Loading…</p>}
      {settings.map((s) => (
        <div key={s.id} className="field">
          <label>
            {s.key} {s.description ? `— ${s.description}` : ''}
          </label>
          <input
            defaultValue={s.value}
            onBlur={async (e) => {
              if (e.target.value !== s.value) {
                await adminService.updateSetting(s.id, e.target.value);
                setTick((t) => t + 1);
              }
            }}
          />
        </div>
      ))}
    </DrawerShell>
  );
}

function MappingDrawer({ onClose }: { onClose: () => void }) {
  const [tick, setTick] = useState(0);
  const { data: profiles, loading } = useAsyncData(() => adminService.mappingProfiles(), [], [tick]);
  const [form, setForm] = useState({ name: '', entityType: 'LECTURER', mapText: 'Staff No=staffNumber;Staff Name=name' });
  return (
    <DrawerShell title="Import mapping profiles" onClose={onClose}>
      {loading && <p className="section-sub">Loading…</p>}
      {profiles.map((p) => (
        <div key={p.id} style={{ marginBottom: 14 }}>
          <div style={{ fontWeight: 700 }}>{p.name}</div>
          <div className="cell-sub">
            {p.entityType} · {Object.entries(p.columnMap).map(([k, v]) => `${k}→${v}`).join(', ')}
          </div>
        </div>
      ))}
      <div className="section-title">New profile</div>
      <div className="field">
        <label>Name</label>
        <input value={form.name} onChange={(e) => setForm({ ...form, name: e.target.value })} />
      </div>
      <div className="field">
        <label>Entity type</label>
        <select value={form.entityType} onChange={(e) => setForm({ ...form, entityType: e.target.value })}>
          <option value="LECTURER">LECTURER</option>
          <option value="ACADEMIC_UNIT">ACADEMIC_UNIT</option>
        </select>
      </div>
      <div className="field">
        <label>Column map (Source=field;…)</label>
        <input value={form.mapText} onChange={(e) => setForm({ ...form, mapText: e.target.value })} />
      </div>
      <Button
        variant="primary"
        disabled={!form.name}
        onClick={async () => {
          const columnMap = Object.fromEntries(
            form.mapText
              .split(';')
              .map((p) => p.split('=', 2))
              .filter((p) => p.length === 2) as [string, string][],
          );
          await adminService.createMappingProfile({ name: form.name, entityType: form.entityType, columnMap });
          setForm({ ...form, name: '' });
          setTick((t) => t + 1);
        }}
      >
        Save profile
      </Button>
    </DrawerShell>
  );
}

const DRAWERS: Record<string, (onClose: () => void) => ReactNode> = {
  Users: (c) => <UsersDrawer onClose={c} />,
  'Roles & Permissions': (c) => <RolesDrawer onClose={c} />,
  Institutions: (c) => <InstitutionsDrawer onClose={c} />,
  'Organization Types': (c) => <OrgTypesDrawer onClose={c} />,
  'Academic Years': (c) => <YearsDrawer onClose={c} />,
  Semesters: (c) => <SemestersDrawer onClose={c} />,
  'Institutional Rules': (c) => <RulesDrawer onClose={c} />,
  'Import Mapping Profiles': (c) => <MappingDrawer onClose={c} />,
  'System Settings': (c) => <SettingsDrawer onClose={c} />,
  'Audit Logs': (c) => <AuditDrawer onClose={c} />,
};

export function AdminPage() {
  const { openDrawer, closeDrawer, user } = useApp();
  const allowed = new Set(adminSectionsFor(user.role));
  const sections = ADMIN_SECTIONS.filter(([t]) => allowed.has(t as AdminSection));

  return (
    <>
      <PageHead
        title="Administration"
        subtitle={
          isSuperAdmin(user.role)
            ? 'Approve institution signups, manage platform users, roles, and audit.'
            : 'Manage users and roles for your institution.'
        }
      />
      {sections.length === 0 ? (
        <p className="section-sub">Your role does not include administration sections.</p>
      ) : (
        <div className="kpi-grid" style={{ gridTemplateColumns: 'repeat(3, 1fr)', gap: 12 }}>
          {sections.map(([t, d]) => (
            <Card key={t} pad>
              <div
                style={{ cursor: 'pointer' }}
                onClick={() => {
                  const build = DRAWERS[t];
                  if (build) openDrawer(build(closeDrawer));
                }}
              >
                <div style={{ fontWeight: 700, fontSize: 13.5, marginBottom: 5 }}>{t}</div>
                <div className="cell-sub">{d}</div>
              </div>
            </Card>
          ))}
        </div>
      )}
    </>
  );
}
