import { useEffect, useRef, useState } from 'react';
import { useNavigate } from 'react-router-dom';
import { useAsyncData } from '../hooks/useAsyncData';
import { useFeedback } from '../context/FeedbackContext';
import { importService } from '../services';
import { Button } from '../components/ui/Button';
import { Card, PageHead } from '../components/ui/Drawer';
import { IconChevRight, IconUpload } from '../components/ui/Icons';

const STATUS_TO_STEP: Record<string, number> = {
  MAP: 2,
  VALIDATE: 3,
  PREVIEW: 4,
  IMPORTED: 5,
};

type UploadResult = {
  sessionId: string;
  fileName: string;
  entityType: string;
  status: string;
  detectedColumns: string[];
  suggestedMap: Record<string, string>;
  rowCount: number;
  preview: Record<string, string>[];
  warnings: string[];
};

export function ImportPage() {
  const navigate = useNavigate();
  const { confirm, error: notifyError, success, toast } = useFeedback();
  const steps = ['Upload', 'Detect', 'Map', 'Validate', 'Preview', 'Import'];
  const [current, setCurrent] = useState(0);
  const [tick, setTick] = useState(0);
  const { data: sessions } = useAsyncData(() => importService.list(), [], [tick]);
  const [busy, setBusy] = useState(false);
  const [entityType, setEntityType] = useState('');
  const [activeSessionId, setActiveSessionId] = useState<string | null>(null);
  const [upload, setUpload] = useState<UploadResult | null>(null);
  const [rows, setRows] = useState<Record<string, string>[]>([]);
  const [dragOver, setDragOver] = useState(false);
  const fileRef = useRef<HTMLInputElement>(null);

  useEffect(() => {
    if (!activeSessionId) {
      setRows([]);
      return;
    }
    importService
      .rows(activeSessionId)
      .then(setRows)
      .catch(() => setRows([]));
  }, [activeSessionId, tick]);

  const handleFile = async (file: File | null | undefined) => {
    if (!file) return;
    const lower = file.name.toLowerCase();
    if (!lower.endsWith('.csv') && !lower.endsWith('.pdf') && !lower.endsWith('.tsv') && !lower.endsWith('.txt')) {
      toast('Supported formats: CSV, TSV, TXT, or PDF.', 'warning');
      return;
    }
    setBusy(true);
    try {
      const result = await importService.upload(file, entityType || undefined);
      setUpload(result);
      setActiveSessionId(result.sessionId);
      setCurrent(2);
      setTick((t) => t + 1);
      success(`Parsed ${result.rowCount} rows from ${result.fileName}`);
    } catch (e) {
      notifyError(e instanceof Error ? e.message : 'Upload/parse failed');
    } finally {
      setBusy(false);
      if (fileRef.current) fileRef.current.value = '';
    }
  };

  const advance = async () => {
    const id = activeSessionId || upload?.sessionId;
    if (!id) {
      fileRef.current?.click();
      return;
    }
    if (current >= 4) {
      const ok = await confirm({
        title: 'Commit import',
        message: 'Write validated rows into AcademicFlow? This creates lecturers or academic units for your institution.',
        confirmLabel: 'Import now',
      });
      if (!ok) return;
    }
    setBusy(true);
    try {
      const updated = await importService.advance(id);
      setActiveSessionId(updated.id);
      setCurrent(STATUS_TO_STEP[updated.status] ?? current);
      setTick((t) => t + 1);
      if (updated.status === 'IMPORTED') {
        success(
          `Import committed. ${updated.entityType === 'ACADEMIC_UNIT' ? 'Units' : 'Lecturers'} are now in the system.`,
        );
        navigate(updated.entityType === 'ACADEMIC_UNIT' ? '/units' : '/lecturers');
      }
    } catch (e) {
      notifyError(e instanceof Error ? e.message : 'Could not advance import');
    } finally {
      setBusy(false);
    }
  };

  const progressPct = Math.round(((current + (busy ? 0.45 : 0)) / Math.max(steps.length - 1, 1)) * 100);
  const progressLabel = busy
    ? current >= 4
      ? 'Committing import…'
      : current === 0
        ? 'Uploading and detecting columns…'
        : 'Processing next import step…'
    : current >= 5
      ? 'Import complete'
      : `Step ${current + 1} of ${steps.length}: ${steps[current]}`;

  return (
    <>
      <PageHead
        title="Import / Export"
        subtitle="Upload CSV or PDF — AcademicFlow detects columns, maps fields, validates, and commits. Academic year and semester columns update the period for imported units."
      />
      <Card>
        <div className="steps-row">
          {steps.map((s, i) => (
            <div key={s} style={{ display: 'contents' }}>
              <div className={`step-item ${i < current ? 'done' : i === current ? 'current' : ''}`}>
                <div className="step-num">{i < current ? '✓' : i + 1}</div>
                <span className="step-label">{s}</span>
              </div>
              {i < steps.length - 1 && <div className="step-line" />}
            </div>
          ))}
        </div>

        <div className="import-progress" aria-live="polite">
          <div className="import-progress-meta">
            <span>{progressLabel}</span>
            <span className="mono">{Math.min(100, progressPct)}%</span>
          </div>
          <div className="import-progress-track">
            <div
              className={`import-progress-bar${busy ? ' pulsing' : ''}`}
              style={{ width: `${Math.min(100, Math.max(6, progressPct))}%` }}
            />
          </div>
        </div>

        <div
          className={`dropzone ${dragOver ? 'dragover' : ''}`}
          onDragOver={(e) => {
            e.preventDefault();
            setDragOver(true);
          }}
          onDragLeave={() => setDragOver(false)}
          onDrop={(e) => {
            e.preventDefault();
            setDragOver(false);
            void handleFile(e.dataTransfer.files?.[0]);
          }}
          onClick={() => fileRef.current?.click()}
        >
          <IconUpload />
          <div style={{ fontWeight: 700, marginTop: 8 }}>Drop CSV or PDF here, or click to browse</div>
          <p className="section-sub" style={{ marginBottom: 0 }}>
            Lecturers and academic units · auto-detects entity type and column mapping
          </p>
          <input
            ref={fileRef}
            type="file"
            accept=".csv,.tsv,.txt,.pdf,text/csv,application/pdf"
            hidden
            onChange={(e) => void handleFile(e.target.files?.[0])}
          />
        </div>

        <div className="field" style={{ marginTop: 16 }}>
          <label>Force entity type (optional)</label>
          <select value={entityType} onChange={(e) => setEntityType(e.target.value)}>
            <option value="">Auto-detect from file</option>
            <option value="LECTURER">Lecturers</option>
            <option value="ACADEMIC_UNIT">Academic units</option>
          </select>
        </div>

        {upload && (
          <div style={{ marginTop: 18 }}>
            <div className="section-title">Detected file</div>
            <p className="section-sub">
              <b>{upload.fileName}</b> · {upload.entityType} · {upload.rowCount} rows · status {upload.status}
            </p>
            {upload.warnings.length > 0 && (
              <p className="section-sub" style={{ color: 'var(--warning)' }}>
                {upload.warnings.join(' ')}
              </p>
            )}
            <div className="section-title">Suggested field map</div>
            <div className="table-wrap" style={{ border: 'none' }}>
              <table>
                <thead>
                  <tr>
                    <th>Source column</th>
                    <th />
                    <th>AcademicFlow field</th>
                  </tr>
                </thead>
                <tbody>
                  {Object.entries(upload.suggestedMap).map(([src, dest]) => (
                    <tr key={src}>
                      <td className="mono">{src}</td>
                      <td>
                        <IconChevRight />
                      </td>
                      <td className="cell-primary">{dest}</td>
                    </tr>
                  ))}
                </tbody>
              </table>
            </div>
            <div className="section-title" style={{ marginTop: 14 }}>
              Preview
            </div>
            <div className="table-wrap">
              <table>
                <thead>
                  <tr>
                    {upload.detectedColumns.map((c) => (
                      <th key={c}>{c}</th>
                    ))}
                  </tr>
                </thead>
                <tbody>
                  {upload.preview.map((row, i) => (
                    <tr key={i}>
                      {upload.detectedColumns.map((c) => (
                        <td key={c}>{row[c] || ''}</td>
                      ))}
                    </tr>
                  ))}
                </tbody>
              </table>
            </div>
          </div>
        )}

        {rows.length > 0 && (
          <div style={{ marginTop: 16 }}>
            <div className="section-title">Staging rows</div>
            <div className="table-wrap" style={{ marginTop: 8 }}>
              <table>
                <thead>
                  <tr>
                    <th>#</th>
                    <th>Data</th>
                    <th>Valid</th>
                  </tr>
                </thead>
                <tbody>
                  {rows.map((r) => (
                    <tr key={r._row}>
                      <td>{r._row}</td>
                      <td className="cell-sub">
                        {Object.entries(r)
                          .filter(([k]) => !k.startsWith('_'))
                          .map(([k, v]) => `${k}=${v}`)
                          .join(' · ')}
                      </td>
                      <td>{r._valid === 'true' ? 'Yes' : 'No'}</td>
                    </tr>
                  ))}
                </tbody>
              </table>
            </div>
          </div>
        )}

        <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginTop: 20 }}>
          <span className="cell-sub">{sessions.length} import session(s) stored</span>
          <div className="btn-row">
            <Button
              onClick={() => {
                setUpload(null);
                setActiveSessionId(null);
                setCurrent(0);
              }}
            >
              Reset
            </Button>
            <Button variant="primary" onClick={advance} disabled={busy}>
              {busy
                ? 'Working…'
                : !activeSessionId
                  ? 'Upload file'
                  : current < 5
                    ? 'Continue / commit next step'
                    : 'Already imported'}
            </Button>
          </div>
        </div>

        {sessions.length > 0 && (
          <div style={{ marginTop: 20 }}>
            <div className="section-title">Recent import sessions</div>
            <div className="table-wrap" style={{ marginTop: 10 }}>
              <table>
                <thead>
                  <tr>
                    <th>File</th>
                    <th>Type</th>
                    <th>Status</th>
                    <th>Created</th>
                    <th />
                  </tr>
                </thead>
                <tbody>
                  {sessions.map((s) => (
                    <tr key={s.id}>
                      <td>{s.fileName}</td>
                      <td>{s.entityType}</td>
                      <td>
                        <span className="badge badge-info">{s.status}</span>
                      </td>
                      <td className="cell-sub">{new Date(s.createdAt).toLocaleString()}</td>
                      <td>
                        <Button
                          size="sm"
                          onClick={() => {
                            setActiveSessionId(s.id);
                            setCurrent(STATUS_TO_STEP[s.status] ?? 2);
                            setTick((t) => t + 1);
                          }}
                        >
                          Resume
                        </Button>
                      </td>
                    </tr>
                  ))}
                </tbody>
              </table>
            </div>
          </div>
        )}
      </Card>
    </>
  );
}
