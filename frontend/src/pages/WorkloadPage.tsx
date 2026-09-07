import { useAsyncData } from '../hooks/useAsyncData';
import { workloadService } from '../services';
import { Avatar, Badge } from '../components/ui/Badge';
import { PageHead } from '../components/ui/Drawer';
import { IconAlert, IconCheck, IconClock, IconGauge, IconUsers } from '../components/ui/Icons';
import type { ReactNode } from 'react';

function Kpi({ icon, tone, label, value }: { icon: ReactNode; tone?: string; label: string; value: string }) {
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

function initials(name: string) {
  const parts = name.replace(/^(Dr\.|Prof\.|Mr\.|Ms\.)\s*/, '').split(' ');
  return ((parts[0]?.[0] || '') + (parts[1]?.[0] || '')).toUpperCase();
}

export function WorkloadPage() {
  const { data, loading, error } = useAsyncData(
    () => workloadService.get(),
    {
      totalHours: 0,
      averageLoad: 0,
      underloaded: 0,
      optimal: 0,
      nearLimit: 0,
      overloaded: 0,
      rows: [],
    },
    [],
  );

  return (
    <>
      <PageHead title="Workload" subtitle="Teaching load across every lecturer this semester." />
      {error && (
        <p className="section-sub" style={{ color: 'var(--danger)' }}>
          Could not load workload from API: {error}
        </p>
      )}
      <div className="kpi-grid" style={{ gridTemplateColumns: 'repeat(5, 1fr)' }}>
        <Kpi icon={<IconClock />} label="Total teaching hours" value={`${data.totalHours} hrs`} />
        <Kpi icon={<IconGauge />} label="Average load" value={`${data.averageLoad} hrs`} />
        <Kpi icon={<IconUsers />} label="Underloaded" value={String(data.underloaded)} />
        <Kpi icon={<IconCheck />} tone="ok" label="Optimal" value={String(data.optimal)} />
        <Kpi icon={<IconAlert />} tone="danger" label="Overloaded" value={String(data.overloaded)} />
      </div>
      <div className="table-wrap">
        <table>
          <thead>
            <tr>
              <th>Lecturer</th>
              <th>Department</th>
              <th>Assigned hours</th>
              <th>Maximum</th>
              <th>Utilization</th>
              <th>Status</th>
            </tr>
          </thead>
          <tbody>
            {loading && (
              <tr>
                <td colSpan={6}>Loading…</td>
              </tr>
            )}
            {data.rows.map((l) => {
              const color =
                l.utilization > 100
                  ? 'var(--danger)'
                  : l.utilization >= 90
                    ? 'var(--warning)'
                    : l.utilization < 50
                      ? 'var(--text-tertiary)'
                      : 'var(--success)';
              const badgeStatus =
                l.status === 'Overloaded' ? 'At limit' : l.status === 'Near limit' ? 'Limited' : 'Available';
              return (
                <tr key={l.lecturerId}>
                  <td>
                    <div className="name-cell">
                      <Avatar initials={initials(l.name)} />
                      <span className="cell-primary">{l.name}</span>
                    </div>
                  </td>
                  <td>{l.department}</td>
                  <td className="mono">
                    {l.currentWorkload} / {l.maximumWorkload}
                  </td>
                  <td>{l.maximumWorkload} hrs</td>
                  <td>
                    <div className="progress-row">
                      <div className="progress-track" style={{ width: 90 }}>
                        <div
                          className="progress-fill"
                          style={{ width: `${Math.min(l.utilization, 100)}%`, background: color }}
                        />
                      </div>
                      <span className="progress-text">{l.utilization}%</span>
                    </div>
                  </td>
                  <td>
                    <Badge status={badgeStatus} />{' '}
                    <span style={{ fontSize: 12, color: 'var(--text-secondary)', marginLeft: 4 }}>{l.status}</span>
                  </td>
                </tr>
              );
            })}
          </tbody>
        </table>
      </div>
    </>
  );
}
