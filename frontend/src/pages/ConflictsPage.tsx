import { useState } from 'react';
import { useNavigate } from 'react-router-dom';
import { useAsyncData } from '../hooks/useAsyncData';
import { useFeedback } from '../context/FeedbackContext';
import { conflictService } from '../services';
import { Badge } from '../components/ui/Badge';
import { Button } from '../components/ui/Button';
import { Card, PageHead, Tabs } from '../components/ui/Drawer';

export function ConflictsPage() {
  const [tab, setTab] = useState(0);
  const [tick, setTick] = useState(0);
  const navigate = useNavigate();
  const { confirm, error: notifyError, success } = useFeedback();
  const { data: conflicts, fromApi } = useAsyncData(() => conflictService.list(), [], [tick]);
  const filtered = conflicts.filter((c) => {
    if (tab === 1) return c.category === 'Workload';
    if (tab === 2) return c.category === 'Timetable';
    if (tab === 3) return c.category === 'Availability';
    if (tab === 4) return c.category === 'Expertise';
    if (tab === 5) return c.category === 'Policy';
    return true;
  });

  const resolve = async (id: string) => {
    const ok = await confirm({
      title: 'Resolve conflict',
      message: 'Mark this conflict as resolved? This is recorded for the allocation trail.',
      confirmLabel: 'Resolve',
    });
    if (!ok) return;
    try {
      await conflictService.resolve(id);
      setTick((t) => t + 1);
      success('Conflict resolved');
    } catch (e) {
      notifyError(e instanceof Error ? e.message : 'Resolve failed');
    }
  };

  return (
    <>
      <PageHead title="Conflicts" subtitle={fromApi ? 'Open conflicts from the API.' : 'Local fallback conflicts.'} />
      <Tabs tabs={[`All (${conflicts.length})`, 'Workload', 'Timetable', 'Availability', 'Expertise', 'Policy']} active={tab} onChange={setTab} />
      <Card>
        {filtered.length === 0 && <p className="section-sub">No open conflicts in this category.</p>}
        {filtered.map((c) => (
          <div className="conflict-item" key={c.id}>
            <div className={`sev-dot sev-${c.severity}`} />
            <div style={{ flex: 1 }}>
              <div style={{ display: 'flex', gap: 8, alignItems: 'center', marginBottom: 3 }}>
                <Badge status={c.severity === 'high' ? 'High' : 'Medium'} />
                <span style={{ fontWeight: 700, fontSize: 13 }}>{c.category} conflict</span>
              </div>
              <div style={{ fontSize: 13, marginBottom: 3 }}><b>{c.who}</b></div>
              <div style={{ fontSize: 12.5, color: 'var(--text-secondary)' }}>{c.text}</div>
            </div>
            <div className="btn-row">
              <Button size="sm" onClick={() => navigate('/timetable')}>View timetable</Button>
              <Button size="sm" variant="primary" onClick={() => resolve(c.id)}>Resolve</Button>
            </div>
          </div>
        ))}
      </Card>
    </>
  );
}
