import { useNavigate } from 'react-router-dom';
import { ACTIVITY, DASHBOARD_STATS, REQUESTS } from '../data/mockData';
import { dashboardService } from '../services';
import { useAsyncData } from '../hooks/useAsyncData';
import { Badge } from '../components/ui/Badge';
import { Button } from '../components/ui/Button';
import {
  IconAlert,
  IconBook,
  IconCheck,
  IconClock,
  IconExchange,
  IconGauge,
  IconUsers,
} from '../components/ui/Icons';
import type { ReactNode } from 'react';

function Kpi({
  icon,
  tone,
  label,
  value,
}: {
  icon: ReactNode;
  tone?: string;
  label: string;
  value: string | number;
}) {
  return (
    <div className="kpi-card">
      <div className="kpi-top">
        <div className={`kpi-icon ${tone || ''}`}>{icon}</div>
      </div>
      <div className="kpi-value">{value}</div>
      <div className="kpi-label">{label}</div>
    </div>
  );
}

function Donut({ pct }: { pct: number }) {
  const r = 42;
  const c = 2 * Math.PI * r;
  const off = c * (1 - pct / 100);
  return (
    <svg width="120" height="120" viewBox="0 0 100 100" style={{ flexShrink: 0 }}>
      <circle cx="50" cy="50" r={r} fill="none" stroke="#EEF0F3" strokeWidth="10" />
      <circle
        cx="50"
        cy="50"
        r={r}
        fill="none"
        stroke="var(--primary)"
        strokeWidth="10"
        strokeLinecap="round"
        strokeDasharray={c}
        strokeDashoffset={off}
        transform="rotate(-90 50 50)"
      />
      <text x="50" y="47" textAnchor="middle" fontSize="19" fontWeight="800" fill="var(--text-primary)" fontFamily="Inter">
        {pct}%
      </text>
      <text x="50" y="61" textAnchor="middle" fontSize="7.5" fill="var(--text-tertiary)" fontFamily="Inter">
        complete
      </text>
    </svg>
  );
}

function WlBar({ label, count, total, color }: { label: string; count: number; total: number; color: string }) {
  const pct = total ? Math.round((count / total) * 100) : 0;
  return (
    <div className="wl-row">
      <div style={{ width: 170, fontSize: 12.5, color: 'var(--text-secondary)', flexShrink: 0 }}>{label}</div>
      <div className="wl-track">
        <div className="wl-fill" style={{ width: `${pct}%`, background: color }} />
      </div>
      <div style={{ width: 40, textAlign: 'right', fontSize: 12.5, fontWeight: 700 }}>{count}</div>
    </div>
  );
}

export function DashboardPage() {
  const navigate = useNavigate();
  const { data } = useAsyncData(
    () => dashboardService.get(),
    { stats: DASHBOARD_STATS, activity: ACTIVITY, requests: REQUESTS.slice(0, 3) },
    [],
  );
  const s = data.stats;
  const recent = data.requests.slice(0, 3);
  const activity = data.activity;

  const attn = (
    tone: 'danger' | 'warn' | 'info',
    icon: ReactNode,
    title: string,
    sub: string,
    target: string,
  ) => {
    const toneMap = {
      danger: { bg: 'var(--danger-tint)', fg: 'var(--danger)' },
      warn: { bg: 'var(--warning-tint)', fg: 'var(--warning)' },
      info: { bg: 'var(--info-tint)', fg: 'var(--info)' },
    };
    const t = toneMap[tone];
    return (
      <div className="attn-item" key={title}>
        <div className="attn-icon" style={{ background: t.bg, color: t.fg }}>
          {icon}
        </div>
        <div className="attn-text">
          <div className="attn-title">{title}</div>
          <div className="attn-sub">{sub}</div>
        </div>
        <Button size="sm" variant="ghost" onClick={() => navigate(`/${target}`)}>
          View
        </Button>
      </div>
    );
  };

  return (
    <>
      <div className="page-head">
        <div>
          <p className="page-sub" style={{ marginBottom: 4 }}>
            Good morning, Dr. Wanjiku
          </p>
          <h1 className="page-title">Computer Science Department</h1>
          <p className="page-sub">2026/2027 · Semester 1</p>
        </div>
        <Badge status="Allocation cycle: In progress" style={{ padding: '6px 12px', fontSize: 12.5 }} />
      </div>

      <div className="kpi-grid">
        <Kpi icon={<IconUsers />} label="Total Lecturers" value={s.totalLecturers} />
        <Kpi icon={<IconBook />} label="Academic Units" value={s.academicUnits} />
        <Kpi icon={<IconCheck />} tone="ok" label="Allocated" value={s.allocated} />
        <Kpi icon={<IconClock />} tone="warn" label="Pending" value={s.pending} />
        <Kpi icon={<IconAlert />} tone="danger" label="Conflicts" value={s.conflicts} />
        <Kpi icon={<IconExchange />} label="Cross-Dept Requests" value={s.crossDeptRequests} />
      </div>

      <div className="split-3" style={{ marginBottom: 20 }}>
        <div className="card card-pad">
          <div className="section-title">Recent cross-department requests</div>
          <p className="section-sub">Units another department has asked your department to help teach.</p>
          <div className="table-wrap" style={{ border: 'none' }}>
            <table>
              <thead>
                <tr>
                  <th>Request</th>
                  <th>From</th>
                  <th>Unit</th>
                  <th>Students</th>
                  <th>Required expertise</th>
                  <th>Status</th>
                </tr>
              </thead>
              <tbody>
                {recent.map((r) => (
                  <tr key={r.id} onClick={() => navigate('/requests')}>
                    <td className="mono cell-primary">{r.id}</td>
                    <td>{r.requestingDepartment}</td>
                    <td>{r.academicUnit.split(' — ')[1] || r.academicUnit}</td>
                    <td>{r.studentCount}</td>
                    <td>{r.requiredExpertise}</td>
                    <td>
                      <Badge status={r.status} />
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        </div>

        <div className="card card-pad">
          <div className="section-title">Attention required</div>
          <p className="section-sub">Issues that need a decision this week.</p>
          <div className="attn-list">
            {attn('danger', <IconAlert />, `${s.pending} units remain unallocated`, 'Needs allocation this cycle', 'units')}
            {attn('warn', <IconGauge />, 'Lecturer workload needs review', 'Check overloaded staff', 'workload')}
            {attn('danger', <IconClock />, `${s.conflicts} conflicts detected`, 'Blocking publication', 'conflicts')}
            {attn('info', <IconExchange />, `${s.crossDeptRequests} cross-dept requests`, 'From other departments', 'requests')}
          </div>
        </div>
      </div>

      <div className="split-3">
        <div className="card card-pad">
          <div className="section-title">Allocation overview</div>
          <p className="section-sub">
            {s.allocated} of {s.academicUnits} academic units allocated this semester.
          </p>
          <div className="donut-wrap">
            <Donut pct={s.completionPct} />
            <div className="donut-legend">
              <div className="legend-row">
                <span className="legend-dot" style={{ background: 'var(--success)' }} />
                <span className="legend-label">Allocated</span>
                <span className="legend-val">{s.allocatedCount}</span>
              </div>
              <div className="legend-row">
                <span className="legend-dot" style={{ background: 'var(--info)' }} />
                <span className="legend-label">Awaiting approval</span>
                <span className="legend-val">{s.awaitingApproval}</span>
              </div>
              <div className="legend-row">
                <span className="legend-dot" style={{ background: 'var(--warning)' }} />
                <span className="legend-label">Pending</span>
                <span className="legend-val">{s.pendingUnits}</span>
              </div>
              <div className="legend-row">
                <span className="legend-dot" style={{ background: 'var(--danger)' }} />
                <span className="legend-label">Conflicted</span>
                <span className="legend-val">{s.conflicted}</span>
              </div>
            </div>
          </div>
        </div>

        <div className="card card-pad">
          <div className="section-title">Recent activity</div>
          <p className="section-sub">What&apos;s happened across the department.</p>
          {activity.map((a) => (
            <div className="activity-item" key={a.time + a.text}>
              <div className="activity-dot" />
              <div>
                <div className="activity-text" dangerouslySetInnerHTML={{ __html: a.text }} />
                <div className="activity-time">{a.time}</div>
              </div>
            </div>
          ))}
        </div>
      </div>

      <div className="card card-pad" style={{ marginTop: 16 }}>
        <div className="section-title">Workload overview</div>
        <p className="section-sub">Lecturer teaching load distribution across the department.</p>
        <WlBar label="Underloaded (< 60%)" count={s.underloaded || 9} total={s.totalLecturers || 42} color="var(--text-tertiary)" />
        <WlBar label="Optimal (60–90%)" count={s.optimal || 21} total={s.totalLecturers || 42} color="var(--success)" />
        <WlBar label="Near limit (90–100%)" count={s.nearLimit || 8} total={s.totalLecturers || 42} color="var(--warning)" />
        <WlBar label="Overloaded (> 100%)" count={s.overloaded || 4} total={s.totalLecturers || 42} color="var(--danger)" />
      </div>
    </>
  );
}
