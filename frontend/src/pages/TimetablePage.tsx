import type { ReactNode } from 'react';
import { useMemo, useState } from 'react';
import { useAsyncData } from '../hooks/useAsyncData';
import { useFeedback } from '../context/FeedbackContext';
import { exportService, timetableService } from '../services';
import { Button } from '../components/ui/Button';
import { PageHead } from '../components/ui/Drawer';

const DAYS = ['Monday', 'Tuesday', 'Wednesday', 'Thursday', 'Friday'];
const HOURS = [8, 9, 10, 11, 12, 13, 14, 15, 16];

export function TimetablePage() {
  const { error: notifyError, success } = useFeedback();
  const [tick, setTick] = useState(0);
  const [checkMsg, setCheckMsg] = useState<string | null>(null);
  const [exporting, setExporting] = useState(false);
  const { data: entries, loading, error } = useAsyncData(() => timetableService.list(), [], [tick]);

  const blocks = useMemo(() => {
    const map = new Map<string, { title: string; sub: string; span: number; conflict?: boolean }>();
    entries.forEach((e) => {
      const dayName = DAYS[e.dayOfWeek - 1];
      if (!dayName) return;
      const startH = Number(e.startTime.split(':')[0]);
      const endH = Number(e.endTime.split(':')[0]);
      const span = Math.max(1, endH - startH);
      map.set(`${dayName}-${startH}`, {
        title: e.unitCode || 'Slot',
        sub: `${e.lecturerName || 'Unassigned'}${e.room ? ` · ${e.room}` : ''}`,
        span,
        conflict: e.conflict,
      });
    });
    return map;
  }, [entries]);

  const cells: ReactNode[] = [];
  const skip = new Set<string>();
  HOURS.forEach((h) => {
    cells.push(
      <div className="tt-time" key={`t-${h}`}>
        {h}:00
      </div>,
    );
    DAYS.forEach((d) => {
      const key = `${d}-${h}`;
      if (skip.has(key)) return;
      const b = blocks.get(key);
      if (b) {
        for (let s = 1; s < b.span; s++) skip.add(`${d}-${h + s}`);
        cells.push(
          <div className="tt-cell" style={{ gridRow: `span ${b.span}` }} key={key}>
            <div className={`tt-block ${b.conflict ? 'conflict' : ''}`}>
              <div className="tt-block-title">{b.title}</div>
              <div className="tt-block-sub">{b.sub}</div>
              {b.conflict && (
                <div className="tt-block-sub" style={{ color: 'var(--danger)', fontWeight: 700 }}>
                  ⚠ Timetable conflict
                </div>
              )}
            </div>
          </div>,
        );
      } else {
        cells.push(<div className="tt-cell" key={key} />);
      }
    });
  });

  return (
    <>
      <PageHead
        title="Timetable"
        subtitle="Week view from published allocations — export lecturer timetable as CSV or PDF."
        actions={
          <div className="filters-bar" style={{ margin: 0 }}>
            <div
              className="filter-chip"
              style={{ background: 'var(--primary-tint)', color: 'var(--primary)', borderColor: 'var(--primary)' }}
            >
              Week
            </div>
            <Button
              size="sm"
              disabled={exporting}
              onClick={async () => {
                setExporting(true);
                try {
                  await exportService.download('timetable', 'csv');
                  success('Timetable CSV downloaded');
                } catch (e) {
                  notifyError(e instanceof Error ? e.message : 'Export failed');
                } finally {
                  setExporting(false);
                }
              }}
            >
              Export CSV
            </Button>
            <Button
              size="sm"
              disabled={exporting}
              onClick={async () => {
                setExporting(true);
                try {
                  await exportService.download('timetable', 'pdf');
                  success('Timetable PDF downloaded');
                } catch (e) {
                  notifyError(e instanceof Error ? e.message : 'Export failed');
                } finally {
                  setExporting(false);
                }
              }}
            >
              Export PDF
            </Button>
            <Button
              size="sm"
              onClick={() => {
                setTick((t) => t + 1);
                const n = entries.filter((e) => e.conflict).length;
                setCheckMsg(
                  n === 0
                    ? 'No overlapping slots detected across lecturers.'
                    : `${n} timetable slot(s) overlap — highlighted in red.`,
                );
              }}
            >
              Auto-check conflicts
            </Button>
          </div>
        }
      />
      {checkMsg && <p className="section-sub">{checkMsg}</p>}
      {error && (
        <p className="section-sub" style={{ color: 'var(--danger)' }}>
          {error}
        </p>
      )}
      {loading && <p className="section-sub">Loading timetable…</p>}
      <div className="tt-grid">
        <div className="tt-head-cell" />
        {DAYS.map((d) => (
          <div className="tt-head-cell" key={d}>
            {d}
          </div>
        ))}
        {cells}
      </div>
    </>
  );
}
