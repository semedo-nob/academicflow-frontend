import { useEffect, useState } from 'react';
import { useNavigate } from 'react-router-dom';
import { useApp } from '../context/AppContext';
import { useFeedback } from '../context/FeedbackContext';
import { useAsyncData } from '../hooks/useAsyncData';
import {
  courseOfferingService,
  requestService,
  type SuitabilityCandidate,
  type CourseOfferingDetail,
} from '../services';
import { Button } from '../components/ui/Button';
import { Card, PageHead } from '../components/ui/Drawer';
import { IconCheck, IconAlert } from '../components/ui/Icons';

function classificationClass(c: string) {
  if (c.includes('STRONG')) return 'badge-success';
  if (c.includes('REVIEW')) return 'badge-warning';
  return 'badge-danger';
}

function CandidateCard({
  c,
  topId,
  selected,
  onSelect,
}: {
  c: SuitabilityCandidate;
  topId?: string;
  selected: boolean;
  onSelect: (c: SuitabilityCandidate) => void;
}) {
  const isTop = c.lecturerId === topId;
  const tip = c.positives[0] || c.warnings[0] || null;
  return (
    <button
      type="button"
      className={`ctx-cand ${isTop ? 'top' : ''} ${selected ? 'selected' : ''}`}
      onClick={() => onSelect(c)}
      aria-pressed={selected}
    >
      <div className="ctx-cand-top">
        <div className="ctx-cand-score">{c.score}%</div>
        <div className={`badge ${classificationClass(c.classification)}`}>{c.classification}</div>
      </div>
      <div className="ctx-cand-name">
        {c.name}
        {isTop ? <span className="ctx-cand-rec">Recommended</span> : null}
      </div>
      <div className="ctx-cand-meta">
        {c.department}
        {c.crossDepartment ? ' · Cross-dept' : ''}
        {' · '}
        {c.load}
      </div>
      <div className="ctx-cand-metrics">
        {c.breakdown.slice(0, 4).map((b) => (
          <span key={b.key} title={`${b.label}: ${Math.round(b.earned)}/${Math.round(b.max)}`}>
            {b.label.split(' ')[0]} {Math.round(b.earned)}
          </span>
        ))}
      </div>
      {tip && (
        <div className={`ctx-cand-tip ${c.warnings[0] && !c.positives[0] ? 'warn' : ''}`}>
          {c.positives[0] ? <IconCheck /> : <IconAlert />}
          <span>{tip}</span>
        </div>
      )}
      {c.missingEvidence.length > 0 && (
        <div className="ctx-cand-missing">Missing: {c.missingEvidence.slice(0, 2).join('; ')}</div>
      )}
      <div className="ctx-cand-pick">{selected ? 'Selected' : 'Click to select'}</div>
    </button>
  );
}

export function AllocateOfferingPage() {
  const navigate = useNavigate();
  const { selectedRequestId, selectedOfferingId, setSelectedOfferingId } = useApp();
  const { confirm, error: notifyError, success, prompt } = useFeedback();
  const { data: offerings, loading: offeringsLoading } = useAsyncData(
    () => courseOfferingService.list(),
    [],
    [],
  );
  const { data: requests } = useAsyncData(() => requestService.list(), [], []);
  const [selectedId, setSelectedId] = useState('');
  const [detail, setDetail] = useState<CourseOfferingDetail | null>(null);
  const [candidates, setCandidates] = useState<SuitabilityCandidate[]>([]);
  const [selectedLecturerId, setSelectedLecturerId] = useState<string | null>(null);
  const [loadingCandidates, setLoadingCandidates] = useState(false);
  const [allocating, setAllocating] = useState(false);
  const [outlineBusy, setOutlineBusy] = useState(false);
  const [outlineStatus, setOutlineStatus] = useState<string | null>(null);

  useEffect(() => {
    if (selectedId) return;
    if (selectedOfferingId && offerings.some((o) => o.id === selectedOfferingId)) {
      setSelectedId(selectedOfferingId);
      return;
    }
    const fromRequest = requests.find((r) => r.rawId === selectedRequestId)?.courseOfferingId;
    if (fromRequest && offerings.some((o) => o.id === fromRequest)) {
      setSelectedId(fromRequest);
      return;
    }
    if (offerings.length) setSelectedId(offerings[0].id);
  }, [offerings, selectedId, requests, selectedRequestId, selectedOfferingId]);

  useEffect(() => {
    if (selectedId && selectedId !== selectedOfferingId) {
      setSelectedOfferingId(selectedId);
    }
  }, [selectedId]);

  useEffect(() => {
    if (!selectedId) return;
    let cancelled = false;
    setLoadingCandidates(true);
    setSelectedLecturerId(null);
    Promise.all([courseOfferingService.get(selectedId), courseOfferingService.suitability(selectedId)])
      .then(([d, c]) => {
        if (cancelled) return;
        setDetail(d);
        setCandidates(c);
        if (c[0]) setSelectedLecturerId(c[0].lecturerId);
      })
      .catch((e) => {
        if (!cancelled) notifyError(e instanceof Error ? e.message : 'Failed to load offering');
      })
      .finally(() => {
        if (!cancelled) setLoadingCandidates(false);
      });
    return () => {
      cancelled = true;
    };
  }, [selectedId]);

  const allocateSelected = async () => {
    if (!selectedId || !selectedLecturerId) {
      notifyError('Select a recommended lecturer first');
      return;
    }
    const c = candidates.find((x) => x.lecturerId === selectedLecturerId);
    if (!c) {
      notifyError('Selected lecturer is no longer in the recommendation list — refresh and try again');
      return;
    }
    const top = candidates[0];
    const isOverride = top && top.lecturerId !== c.lecturerId;
    let note: string | undefined;
    if (isOverride) {
      const reason = await prompt({
        title: 'Record override',
        message: `System recommended ${top!.name} (${top!.score}%). Explain why you are selecting ${c.name} (${c.score}%).`,
        confirmLabel: 'Allocate with note',
      });
      if (reason === null) return;
      note = reason || 'Chairperson override';
    } else {
      const ok = await confirm({
        title: 'Confirm allocation',
        message: `Allocate ${c.name} (${c.score}% — ${c.classification}) to this course offering?`,
        confirmLabel: 'Allocate',
      });
      if (!ok) return;
    }
    setAllocating(true);
    try {
      await courseOfferingService.allocate({
        courseOfferingId: selectedId,
        lecturerId: c.lecturerId,
        decisionNote: note,
      });
      success(`Allocated ${c.name}. Decision recorded for audit.`);
      navigate('/allocation');
    } catch (e) {
      notifyError(e instanceof Error ? e.message : 'Allocation failed');
    } finally {
      setAllocating(false);
    }
  };

  const statusMessage = (res: {
    processingStatus?: string;
    processingMessage?: string | null;
    needsReview: boolean;
    extractionConfidence: number;
    versionNo: number;
    warnings?: string[];
  }) => {
    const st = res.processingStatus || '';
    if (st === 'OCR_REQUIRED') {
      return `Course outline uploaded (v${res.versionNo}). Scanned document detected — OCR processing required.`;
    }
    if (st === 'LOW_CONFIDENCE') {
      return `Course outline uploaded (v${res.versionNo}). Low-confidence extraction — please review.`;
    }
    if (st === 'REVIEW_REQUIRED') {
      return res.processingMessage || `Course outline uploaded (v${res.versionNo}). Review recommended.`;
    }
    if (st === 'EXTRACTED' || st === 'COMPLETED') {
      return `Course outline analyzed successfully (v${res.versionNo}).`;
    }
    if (res.needsReview) {
      return `Outline stored (v${res.versionNo}). Extraction needs review (${Math.round(Number(res.extractionConfidence) * 100)}% confidence).`;
    }
    return `Outline stored (v${res.versionNo}).`;
  };

  const onOutline = async (file: File | null) => {
    if (!file || !selectedId) return;
    setOutlineBusy(true);
    setOutlineStatus(
      file.name.toLowerCase().endsWith('.pdf')
        ? 'Uploading PDF — scanned documents need OCR (often 15–30 minutes for large sheets). Keep this tab open…'
        : 'Uploading course outline…',
    );
    try {
      setOutlineStatus('Course outline uploaded. Analyzing document…');
      const res = await courseOfferingService.uploadOutline(selectedId, file);
      const msg = statusMessage(res);
      setOutlineStatus(msg);
      success(msg);
      const [d, c] = await Promise.all([
        courseOfferingService.get(selectedId),
        courseOfferingService.suitability(selectedId),
      ]);
      setDetail(d);
      setCandidates(c);
      if (c[0]) setSelectedLecturerId(c[0].lecturerId);
    } catch (e) {
      const err = e as Error & { code?: string };
      const msg = err?.message || 'We could not process this document.';
      setOutlineStatus(null);
      notifyError(
        /failed to fetch|network|timeout|aborted/i.test(msg)
          ? 'Request timed out while OCR was still running. Wait up to 15–30 minutes and refresh this page — the file may still have been stored.'
          : msg,
      );
    } finally {
      setOutlineBusy(false);
    }
  };

  const o = detail?.offering;
  const selected = candidates.find((c) => c.lecturerId === selectedLecturerId);

  return (
    <>
      <PageHead
        title="Allocate by course context"
        subtitle="Match lecturers to a programme-specific offering — not just a unit title. Scores are advisory; you make the final decision."
      />

      <Card>
        <div className="field">
          <label>Course offering</label>
          <select
            value={selectedId}
            onChange={(e) => setSelectedId(e.target.value)}
            disabled={offeringsLoading || !offerings.length}
          >
            {!offerings.length && <option value="">No offerings yet — create one from a teaching request</option>}
            {offerings.map((off) => (
              <option key={off.id} value={off.id}>
                {off.unitCode} — {off.displayTitle || off.unitName} ({off.contextLabel || 'General'})
              </option>
            ))}
          </select>
          {!offerings.length && (
            <p className="field-hint">
              Department chairs create offerings automatically when submitting a cross-department teaching request.
              Go to Requests → New request.
            </p>
          )}
        </div>

        {o && (
          <div style={{ marginTop: 8 }}>
            <div className="section-title">{o.unitCode} — {o.displayTitle || o.unitName}</div>
            <div className="def-list">
              <div className="def-row">
                <span className="def-label">Programme</span>
                <span className="def-value">{o.programme || '—'}</span>
              </div>
              <div className="def-row">
                <span className="def-label">Level</span>
                <span className="def-value">{o.levelLabel || '—'}</span>
              </div>
              <div className="def-row">
                <span className="def-label">Context</span>
                <span className="def-value">{o.contextLabel || '—'}</span>
              </div>
              <div className="def-row">
                <span className="def-label">Requested by</span>
                <span className="def-value">{o.requestingDepartment || '—'}</span>
              </div>
              <div className="def-row">
                <span className="def-label">Owning department</span>
                <span className="def-value">{o.owningDepartment || '—'}</span>
              </div>
            </div>

            <div className="section-title" style={{ marginTop: 16 }}>
              Course outline
            </div>
            {detail?.outlines?.[0] ? (
              <div className="section-sub">
                <p>
                  Latest: <b>{detail.outlines[0].fileName}</b> · v{detail.outlines[0].versionNo}
                  {detail.outlines[0].processingStatus ? ` · ${detail.outlines[0].processingStatus}` : ''}
                  {' · '}
                  confidence {Math.round(Number(detail.outlines[0].extractionConfidence) * 100)}%
                  {detail.outlines[0].needsReview ? ' · Needs review' : ''}
                </p>
                {detail.outlines[0].processingMessage && <p>{detail.outlines[0].processingMessage}</p>}
                {detail.outlines[0].extractionMethod && (
                  <p className="cell-sub">Extraction method: {detail.outlines[0].extractionMethod}</p>
                )}
              </div>
            ) : (
              <p className="section-sub">No outline attached yet.</p>
            )}
            <div className="field">
              <label>Attach / replace outline (PDF, DOCX, XLSX, CSV, TXT, PNG/JPG)</label>
              <input
                type="file"
                accept=".pdf,.docx,.xlsx,.xls,.csv,.txt,.md,.png,.jpg,.jpeg,application/pdf,image/*"
                disabled={outlineBusy}
                onChange={(e) => void onOutline(e.target.files?.[0] || null)}
              />
              {outlineBusy && <p className="field-hint">{outlineStatus || 'Uploading course outline…'}</p>}
              {!outlineBusy && outlineStatus && <p className="field-hint">{outlineStatus}</p>}
            </div>

            <div className="section-title" style={{ marginTop: 16 }}>
              Requirements
            </div>
            <div className="btn-row" style={{ flexWrap: 'wrap', gap: 8 }}>
              {(detail?.requirements || []).map((r) => (
                <span key={r.id} className="badge badge-info">
                  {r.type}: {r.label}
                </span>
              ))}
              {!detail?.requirements?.length && <span className="cell-sub">No requirements yet.</span>}
            </div>
          </div>
        )}
      </Card>

      <div
        style={{
          marginTop: 18,
          display: 'flex',
          justifyContent: 'space-between',
          alignItems: 'flex-end',
          gap: 12,
          flexWrap: 'wrap',
        }}
      >
        <div>
          <div className="section-title" style={{ margin: 0 }}>
            Recommended lecturers
          </div>
          <p className="section-sub" style={{ marginTop: 4 }}>
            Click a card to select, then allocate. AcademicFlow recommends — you decide.
          </p>
        </div>
        <div className="btn-row">
          <Button
            size="sm"
            disabled={loadingCandidates || !selectedId}
            onClick={() => {
              if (!selectedId) return;
              setLoadingCandidates(true);
              courseOfferingService
                .suitability(selectedId)
                .then((c) => {
                  setCandidates(c);
                  if (c[0]) setSelectedLecturerId(c[0].lecturerId);
                })
                .catch((e) => notifyError(e instanceof Error ? e.message : 'Refresh failed'))
                .finally(() => setLoadingCandidates(false));
            }}
          >
            Refresh
          </Button>
          <Button
            variant="primary"
            size="sm"
            disabled={!selected || allocating || loadingCandidates}
            onClick={() => void allocateSelected()}
          >
            {allocating
              ? 'Allocating…'
              : selected
                ? `Allocate ${selected.name}`
                : 'Select a lecturer'}
          </Button>
        </div>
      </div>

      {loadingCandidates && <p className="section-sub">Scoring lecturers…</p>}
      {!loadingCandidates && candidates.length === 0 && selectedId && (
        <p className="section-sub">No ranked lecturers yet for this offering. Attach an outline or refresh.</p>
      )}
      {candidates.length > 0 && (
        <div className="ctx-cand-grid">
          {candidates.map((c) => (
            <CandidateCard
              key={c.lecturerId}
              c={c}
              topId={candidates[0]?.lecturerId}
              selected={c.lecturerId === selectedLecturerId}
              onSelect={(pick) => setSelectedLecturerId(pick.lecturerId)}
            />
          ))}
        </div>
      )}
    </>
  );
}
