import { useEffect, useState } from 'react';
import { useNavigate } from 'react-router-dom';
import { MAT204_CANDIDATES } from '../data/mockData';
import { useApp } from '../context/AppContext';
import { useFeedback } from '../context/FeedbackContext';
import { useAsyncData } from '../hooks/useAsyncData';
import { allocationService, requestService } from '../services';
import { Button } from '../components/ui/Button';
import { Card, DrawerCloseButton, PageHead } from '../components/ui/Drawer';
import { IconAlert, IconCheck } from '../components/ui/Icons';
import type { Candidate } from '../types';

function ExplainDrawer({ onClose }: { onClose: () => void }) {
  return (
    <>
      <div className="drawer-head">
        <div>
          <div style={{ fontWeight: 700, fontSize: 16 }}>How recommendations are calculated</div>
        </div>
        <DrawerCloseButton onClose={onClose} />
      </div>
      <div className="drawer-body">
        <p className="section-sub">
          Hard constraints first (qualified + available). Soft scoring: Expertise 40%, Availability 20%,
          Workload 20%, Student load 10%, Policy 10%. Recommendation ≠ approval.
        </p>
        <div className="def-list">
          <div className="def-row"><span className="def-label">Expertise</span><span className="def-value">40%</span></div>
          <div className="def-row"><span className="def-label">Availability</span><span className="def-value">20%</span></div>
          <div className="def-row"><span className="def-label">Workload fit</span><span className="def-value">20%</span></div>
          <div className="def-row"><span className="def-label">Student load fit</span><span className="def-value">10%</span></div>
          <div className="def-row"><span className="def-label">Departmental rules</span><span className="def-value">10%</span></div>
        </div>
      </div>
    </>
  );
}

function RecCard({
  d,
  topCandidateId,
  onSelect,
  selecting,
}: {
  d: Candidate;
  topCandidateId?: string;
  onSelect: (c: Candidate, overrideReason?: string) => void;
  selecting: boolean;
}) {
  const { openDrawer, closeDrawer } = useApp();
  const { prompt } = useFeedback();
  const metrics: [string, number][] = [
    ['Expertise', d.metrics.expertise],
    ['Availability', d.metrics.availability],
    ['Workload fit', d.metrics.workload],
    ['Student load fit', d.metrics.studentLoad],
  ];

  return (
    <div className={`rec-card ${d.top ? 'top' : ''}`}>
      <div className="rec-head">
        <div>
          <div className="rec-rank">
            Candidate #{d.rank}
            {d.top ? ' · Top match' : ''}
          </div>
          <div className="rec-name">{d.name}</div>
          <div className="rec-dept">{d.dept}</div>
        </div>
        <div className="rec-score">
          <div className="rec-score-num">{d.score}%</div>
          <div className="rec-score-lbl">System match</div>
        </div>
      </div>
      <div className="rec-meta-line">
        <span>
          Current workload: <b>{d.load}</b>
        </span>
        <span>
          Availability: <b>{d.availability}</b>
        </span>
      </div>
      <div className="rec-breakdown">
        {metrics.map(([label, val]) => (
          <div key={label}>
            <div className="rec-metric-label">{label}</div>
            <div className="rec-metric-track">
              <div
                className="rec-metric-fill"
                style={{ width: `${val}%`, background: val < 70 ? 'var(--warning)' : 'var(--primary)' }}
              />
            </div>
            <div className="rec-metric-val">{val}%</div>
          </div>
        ))}
      </div>
      <div className="rec-why">
        <div className="rec-why-title">Why this candidate?</div>
        {d.reasons.map((r) => (
          <div className="rec-why-item" key={r}>
            <IconCheck />
            <span>{r}</span>
          </div>
        ))}
        {d.warn && (
          <div className="rec-why-item" style={{ color: 'var(--warning)' }}>
            <IconAlert />
            <span>{d.warn}</span>
          </div>
        )}
        <div style={{ marginTop: 10, display: 'flex', flexWrap: 'wrap', gap: 8 }}>
          {d.hardConstraints.qualified && <span className="badge badge-success">Qualified</span>}
          {d.hardConstraints.available && <span className="badge badge-success">Available</span>}
          {d.hardConstraints.noConflict && <span className="badge badge-success">No timetable conflict</span>}
          {d.hardConstraints.workloadOk && <span className="badge badge-success">Workload within limit</span>}
          {d.hardConstraints.policyOk && (
            <span className="badge badge-success">Cross-department assignment allowed</span>
          )}
        </div>
      </div>
      <div style={{ display: 'flex', alignItems: 'center', justifyContent: 'space-between' }}>
        <span className="explain-link" onClick={() => openDrawer(<ExplainDrawer onClose={closeDrawer} />)}>
          How was this recommendation calculated?
        </span>
        <div className="btn-row">
          <Button
            size="sm"
            variant="primary"
            disabled={selecting}
            onClick={() => {
              void (async () => {
                if (topCandidateId && topCandidateId !== d.lecturerId) {
                  const reason = await prompt({
                    title: 'Override top recommendation',
                    message: 'Enter a reason for the audit log when selecting a lecturer who is not the top recommendation.',
                    placeholder: 'Reason for override…',
                    confirmLabel: 'Select lecturer',
                  });
                  if (!reason) return;
                  onSelect(d, reason);
                } else {
                  onSelect(d);
                }
              })();
            }}
          >
            Select lecturer
          </Button>
        </div>
      </div>
    </div>
  );
}

export function RecommendationsPage() {
  const navigate = useNavigate();
  const { selectedRequestId, setSelectedRequestId } = useApp();
  const { error: notifyError, success } = useFeedback();
  const [selecting, setSelecting] = useState(false);
  const [requestMeta, setRequestMeta] = useState<{ unit: string; from: string; to: string; students: number; hours: number } | null>(null);

  useEffect(() => {
    let cancelled = false;
    (async () => {
      try {
        const requests = await requestService.list();
        if (cancelled) return;
        let req = requests.find((r) => r.rawId === selectedRequestId);
        if (!req) {
          req =
            requests.find((r) => r.status.toLowerCase().includes('await') || r.status.includes('PENDING')) ||
            requests[0];
          if (req) setSelectedRequestId(req.rawId);
        }
        if (req) {
          setRequestMeta({
            unit: req.academicUnit,
            from: req.requestingDepartment,
            to: req.sourceDepartment,
            students: req.studentCount,
            hours: req.contactHours,
          });
        }
      } catch {
        /* mock fallback */
      }
    })();
    return () => {
      cancelled = true;
    };
  }, [selectedRequestId, setSelectedRequestId]);

  const requestId = selectedRequestId;
  const isUuid = requestId.includes('-') && requestId.length > 20;
  const { data: candidates, loading, error, fromApi } = useAsyncData(
    () => (isUuid ? requestService.findCandidates(requestId) : Promise.resolve(MAT204_CANDIDATES)),
    MAT204_CANDIDATES,
    [requestId],
  );

  const handleSelect = async (c: Candidate, overrideReason?: string) => {
    setSelecting(true);
    try {
      if (!isUuid) {
        notifyError('Open a real teaching request from Requests before allocating.');
        return;
      }
      const requests = await requestService.list();
      const req = requests.find((r) => r.rawId === requestId);
      if (!req?.academicUnitId) {
        notifyError('This request has no academic unit linked.');
        return;
      }
      const topId = candidates.find((x) => x.rank === 1)?.lecturerId;
      await allocationService.create({
        academicUnitId: req.academicUnitId,
        lecturerId: c.lecturerId,
        teachingRequestId: requestId,
        matchScore: c.score,
        recommendedLecturerId: topId,
        overrideReason,
        courseOfferingId: req.courseOfferingId || undefined,
      });
      navigate('/allocation');
      success('Lecturer selected — review on the allocation board');
    } catch (err) {
      notifyError(err instanceof Error ? err.message : 'Allocation failed');
    } finally {
      setSelecting(false);
    }
  };

  return (
    <>
      <PageHead
        title={requestMeta ? `Find a lecturer for ${requestMeta.unit.split('—')[0].trim()}` : 'Find candidates'}
        subtitle={
          requestMeta
            ? `${requestMeta.unit.split('—')[1]?.trim() || requestMeta.unit} · ${requestMeta.students} students · ${requestMeta.hours} contact hours`
            : 'Linear Algebra · 180 students · 4 contact hours'
        }
        eyebrow={
          <>
            {requestMeta?.from || 'Computer Science'}{' '}
            <span style={{ color: 'var(--text-tertiary)' }}>→</span> {requestMeta?.to || 'Mathematics'}
            {fromApi ? ' · live matching' : ''}
          </>
        }
      />
      {loading && <p className="section-sub">Running matching service…</p>}
      {error && (
        <p className="section-sub" style={{ color: 'var(--danger)' }}>
          {error}
        </p>
      )}
      {candidates.map((c) => (
        <RecCard
          key={c.lecturerId}
          d={c}
          topCandidateId={candidates.find((x) => x.rank === 1)?.lecturerId}
          onSelect={handleSelect}
          selecting={selecting}
        />
      ))}
      <Card pad>
        <div style={{ textAlign: 'center', color: 'var(--text-secondary)' }}>
          <p style={{ margin: '0 0 12px 0', fontSize: 13 }}>Not seeing the right fit?</p>
          <div className="btn-row" style={{ justifyContent: 'center' }}>
            <Button size="sm" onClick={() => navigate('/lecturers')}>
              Browse lecturers
            </Button>
            <Button size="sm" onClick={() => navigate('/requests')}>
              Back to requests
            </Button>
          </div>
        </div>
      </Card>
    </>
  );
}
