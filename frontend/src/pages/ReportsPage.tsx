import { useState } from 'react';
import { REPORT_CATS } from '../data/mockData';
import { useApp } from '../context/AppContext';
import { exportService, reportService } from '../services';
import { Button } from '../components/ui/Button';
import { Card, PageHead } from '../components/ui/Drawer';
import { IconChevRight, IconFolder } from '../components/ui/Icons';

function reportType(label: string): string {
  const l = label.toLowerCase();
  if (l.includes('workload')) return 'workload';
  if (l.includes('conflict')) return 'conflicts';
  if (l.includes('cross-department')) return 'cross-department teaching';
  if (l.includes('unallocated')) return 'unallocated units';
  if (l.includes('timetable')) return 'timetable report';
  if (l.includes('teaching request')) return 'teaching request report';
  if (l.includes('approval')) return 'approval report';
  if (l.includes('allocation')) return 'allocation';
  return 'summary';
}

export function ReportsPage() {
  const { period } = useApp();
  const [preview, setPreview] = useState<{ title: string; metrics: Record<string, string> } | null>(null);
  const [busy, setBusy] = useState<string | null>(null);
  const [exporting, setExporting] = useState<string | null>(null);

  const load = async (label: string) => {
    setBusy(label);
    try {
      const res = await reportService.get(reportType(label));
      setPreview({ title: label, metrics: res.metrics });
    } catch (e) {
      window.alert(e instanceof Error ? e.message : 'Report failed');
    } finally {
      setBusy(null);
    }
  };

  const downloadPublished = async (kind: 'allocations' | 'timetable' | 'full', format: 'csv' | 'pdf') => {
    const key = `${kind}-${format}`;
    setExporting(key);
    try {
      await exportService.download(kind, format);
    } catch (e) {
      window.alert(e instanceof Error ? e.message : 'Export failed');
    } finally {
      setExporting(null);
    }
  };

  return (
    <>
      <PageHead
        title="Reports"
        subtitle="Summaries plus published allocation & timetable exports (CSV / PDF)."
        actions={
          <div className="btn-row">
            <Button size="sm" disabled={!!exporting} onClick={() => downloadPublished('allocations', 'csv')}>
              Allocations CSV
            </Button>
            <Button size="sm" disabled={!!exporting} onClick={() => downloadPublished('allocations', 'pdf')}>
              Allocations PDF
            </Button>
            <Button size="sm" disabled={!!exporting} onClick={() => downloadPublished('timetable', 'csv')}>
              Timetable CSV
            </Button>
            <Button size="sm" disabled={!!exporting} onClick={() => downloadPublished('timetable', 'pdf')}>
              Timetable PDF
            </Button>
            <Button size="sm" variant="primary" disabled={!!exporting} onClick={() => downloadPublished('full', 'pdf')}>
              Full pack PDF
            </Button>
            <Button size="sm" variant="primary" disabled={!!exporting} onClick={() => downloadPublished('full', 'csv')}>
              Full pack CSV
            </Button>
          </div>
        }
      />
      <div className="filters-bar">
        <div className="filter-chip">Academic year: {period.academicYearLabel}</div>
        <div className="filter-chip">
          Semester: {period.semesterName} <IconChevRight />
        </div>
      </div>
      <Card pad>
        <div className="section-title">Published teaching pack</div>
        <p className="section-sub">
          After unit allocations are approved, export how lecturers are assigned to units and the lecturer
          timetable as CSV or PDF.
        </p>
        <div className="btn-row">
          <Button disabled={!!exporting} onClick={() => downloadPublished('full', 'csv')}>
            {exporting === 'full-csv' ? 'Preparing…' : 'Download full CSV'}
          </Button>
          <Button variant="primary" disabled={!!exporting} onClick={() => downloadPublished('full', 'pdf')}>
            {exporting === 'full-pdf' ? 'Preparing…' : 'Download full PDF'}
          </Button>
        </div>
      </Card>
      {preview && (
        <div className="card card-pad" style={{ marginBottom: 16, marginTop: 16 }}>
          <div className="section-title">{preview.title}</div>
          <div className="def-list">
            {Object.entries(preview.metrics).map(([k, v]) => (
              <div className="def-row" key={k}>
                <span className="def-label">{k}</span>
                <span className="def-value">{v}</span>
              </div>
            ))}
          </div>
        </div>
      )}
      <div className="kpi-grid" style={{ gridTemplateColumns: 'repeat(3, 1fr)', gap: 12, marginTop: 16 }}>
        {REPORT_CATS.map((c) => (
          <Card key={c} pad>
            <div style={{ display: 'flex', alignItems: 'center', gap: 10, marginBottom: 12 }}>
              <div className="kpi-icon">
                <IconFolder />
              </div>
              <div style={{ fontWeight: 700, fontSize: 13.5 }}>{c}</div>
            </div>
            <div className="btn-row">
              <Button size="sm" onClick={() => load(c)} disabled={busy === c}>
                {busy === c ? 'Loading…' : 'Preview'}
              </Button>
              <Button
                size="sm"
                onClick={() => {
                  const l = c.toLowerCase();
                  if (l.includes('timetable')) void downloadPublished('timetable', 'csv');
                  else if (l.includes('allocation')) void downloadPublished('allocations', 'csv');
                  else void load(c);
                }}
              >
                Export
              </Button>
            </div>
          </Card>
        ))}
      </div>
    </>
  );
}
