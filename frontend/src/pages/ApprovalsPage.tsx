import { useState } from 'react';
import { approvalService, allocationService, exportService } from '../services';
import { useAsyncData } from '../hooks/useAsyncData';
import { useFeedback } from '../context/FeedbackContext';
import { Badge } from '../components/ui/Badge';
import { Button } from '../components/ui/Button';
import { PageHead, Tabs } from '../components/ui/Drawer';

export function ApprovalsPage() {
  const { confirm, error: notifyError, success } = useFeedback();
  const [tab, setTab] = useState(0);
  const [tick, setTick] = useState(0);
  const [exporting, setExporting] = useState<string | null>(null);
  const { data: approvals, fromApi } = useAsyncData(() => approvalService.list(), [], [tick]);
  const pending = approvals.filter((a) => a.status === 'Pending Approval').length;
  const approved = approvals.filter((a) => a.status === 'Approved').length;
  const rejected = approvals.filter((a) => a.status === 'Rejected' || a.status === 'REJECTED').length;
  const filtered = approvals.filter((a) => {
    if (tab === 0) return a.status === 'Pending Approval';
    if (tab === 1) return a.status === 'Approved';
    if (tab === 2) return a.status === 'Rejected' || a.status === 'REJECTED';
    return true;
  });

  const decide = async (allocationId: string | undefined, approve: boolean) => {
    if (!allocationId) return;
    const ok = await confirm({
      title: approve ? 'Approve allocation' : 'Reject allocation',
      message: approve
        ? 'Approve this allocation and publish the timetable slot?'
        : 'Reject this allocation? The request will need to be reassigned.',
      confirmLabel: approve ? 'Approve' : 'Reject',
      danger: !approve,
    });
    if (!ok) return;
    try {
      await allocationService.approve(allocationId, approve, approve ? 'Approved' : 'Rejected');
      setTick((t) => t + 1);
      success(approve ? 'Allocation approved' : 'Allocation rejected');
      if (approve) {
        setTab(1);
      }
    } catch (e) {
      notifyError(e instanceof Error ? e.message : 'Decision failed');
    }
  };

  const download = async (kind: 'allocations' | 'timetable' | 'full', format: 'csv' | 'pdf') => {
    const key = `${kind}-${format}`;
    setExporting(key);
    try {
      await exportService.download(kind, format);
      success(`${kind} ${format.toUpperCase()} downloaded`);
    } catch (e) {
      notifyError(e instanceof Error ? e.message : 'Export failed');
    } finally {
      setExporting(null);
    }
  };

  return (
    <>
      <PageHead
        title="Approval Queue"
        subtitle={
          fromApi
            ? 'Approve to publish — then export lecturer–unit allocations and timetable as CSV or PDF.'
            : 'Local fallback approval queue.'
        }
        actions={
          approved > 0 ? (
            <div className="btn-row">
              <Button size="sm" disabled={!!exporting} onClick={() => download('allocations', 'csv')}>
                {exporting === 'allocations-csv' ? '…' : 'Allocations CSV'}
              </Button>
              <Button size="sm" disabled={!!exporting} onClick={() => download('allocations', 'pdf')}>
                {exporting === 'allocations-pdf' ? '…' : 'Allocations PDF'}
              </Button>
              <Button size="sm" disabled={!!exporting} onClick={() => download('timetable', 'csv')}>
                {exporting === 'timetable-csv' ? '…' : 'Timetable CSV'}
              </Button>
              <Button size="sm" disabled={!!exporting} onClick={() => download('timetable', 'pdf')}>
                {exporting === 'timetable-pdf' ? '…' : 'Timetable PDF'}
              </Button>
              <Button size="sm" variant="primary" disabled={!!exporting} onClick={() => download('full', 'pdf')}>
                {exporting === 'full-pdf' ? '…' : 'Full pack PDF'}
              </Button>
              <Button size="sm" variant="primary" disabled={!!exporting} onClick={() => download('full', 'csv')}>
                {exporting === 'full-csv' ? '…' : 'Full pack CSV'}
              </Button>
            </div>
          ) : undefined
        }
      />
      {approved > 0 && (
        <p className="section-sub">
          Published allocations are ready to export: lecturer–unit mapping and lecturer timetable (CSV &amp; PDF).
        </p>
      )}
      <Tabs
        tabs={[`Pending (${pending})`, `Approved (${approved})`, `Rejected (${rejected})`, 'All']}
        active={tab}
        onChange={setTab}
      />
      <div className="table-wrap">
        <table>
          <thead>
            <tr>
              <th>Approval</th>
              <th>Unit</th>
              <th>Lecturer</th>
              <th>Submitted</th>
              <th>Conflicts</th>
              <th>Status</th>
              <th />
            </tr>
          </thead>
          <tbody>
            {filtered.map((a) => (
              <tr key={a.id + (a as { allocationId?: string }).allocationId}>
                <td className="mono cell-primary">{a.id}</td>
                <td>{a.unit}</td>
                <td>{a.lecturer}</td>
                <td>
                  {a.submittedBy}
                  <div className="cell-sub">{a.date}</div>
                </td>
                <td>
                  {a.conflicts > 0 ? (
                    <span className="badge badge-danger">{a.conflicts}</span>
                  ) : (
                    <span className="badge badge-success">0</span>
                  )}
                </td>
                <td>
                  <Badge status={a.status} />
                </td>
                <td>
                  {a.status === 'Pending Approval' && (
                    <div className="btn-row">
                      <Button
                        size="sm"
                        onClick={() => decide((a as { allocationId?: string }).allocationId, false)}
                      >
                        Reject
                      </Button>
                      <Button
                        size="sm"
                        variant="primary"
                        onClick={() => decide((a as { allocationId?: string }).allocationId, true)}
                      >
                        Approve & publish
                      </Button>
                    </div>
                  )}
                  {a.status === 'Approved' && (
                    <div className="btn-row">
                      <Button size="sm" onClick={() => download('full', 'csv')}>
                        Export CSV
                      </Button>
                      <Button size="sm" onClick={() => download('full', 'pdf')}>
                        Export PDF
                      </Button>
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
