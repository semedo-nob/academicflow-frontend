import { useEffect, useRef, useState } from 'react';
import { useNavigate } from 'react-router-dom';
import { useAsyncData } from '../hooks/useAsyncData';
import { useApp } from '../context/AppContext';
import { useFeedback } from '../context/FeedbackContext';
import {
  importService,
  type ImportResultSummary,
  type ImportUploadResult,
} from '../services';
import { normalizeRole } from '../lib/access';
import { Button } from '../components/ui/Button';
import { Card, PageHead } from '../components/ui/Drawer';
import { IconChevRight, IconUpload } from '../components/ui/Icons';
import { SuggestInput } from '../components/ui/SuggestInput';

const STATUS_TO_STEP: Record<string, number> = {
  MAP: 2,
  VALIDATE: 3,
  PREVIEW: 4,
  IMPORTED: 5,
};

const FIELD_LABELS: Record<string, string> = {
  staffNumber: 'Lecturer ID',
  name: 'Lecturer name',
  lecturerName: 'Lecturer name',
  email: 'Email',
  organizationNode: 'Department',
  maximumWorkload: 'Max workload',
  qualifications: 'Qualifications',
  availability: 'Availability',
  code: 'Unit code',
  unitCode: 'Unit code',
  unitName: 'Unit name',
  contactHours: 'Teaching hours',
  studentCount: 'Students',
  requiredExpertise: 'Expertise',
  academicYear: 'Academic year',
  semester: 'Semester / term',
  IGNORE: '— Ignore (keep as metadata) —',
};

export function ImportPage() {
  const navigate = useNavigate();
  const { user } = useApp();
  const { confirm, error: notifyError, success, toast } = useFeedback();
  const steps = ['Upload', 'Detect', 'Map', 'Validate', 'Preview', 'Import'];
  const [current, setCurrent] = useState(0);
  const [tick, setTick] = useState(0);
  const { data: sessions } = useAsyncData(() => importService.list(), [], [tick]);
  const [busy, setBusy] = useState(false);
  const [entityType, setEntityType] = useState('');
  const [activeSessionId, setActiveSessionId] = useState<string | null>(null);
  const [upload, setUpload] = useState<ImportUploadResult | null>(null);
  const [columnMap, setColumnMap] = useState<Record<string, string>>({});
  const [ignored, setIgnored] = useState<string[]>([]);
  const [profileName, setProfileName] = useState('');
  const [rows, setRows] = useState<Record<string, string>[]>([]);
  const [result, setResult] = useState<ImportResultSummary | null>(null);
  const [importedType, setImportedType] = useState<string | null>(null);
  const [dragOver, setDragOver] = useState(false);
  const fileRef = useRef<HTMLInputElement>(null);
  const isChair = normalizeRole(user.role) === 'DEPARTMENT_CHAIR';
  const scopeDept = user.activeDepartmentName || user.departmentName;

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

  const applyUpload = (result: ImportUploadResult) => {
    setUpload(result);
    setColumnMap({ ...result.suggestedMap });
    setIgnored([]);
    setActiveSessionId(result.sessionId);
    setCurrent(STATUS_TO_STEP[result.status] ?? 2);
    setResult(null);
    setTick((t) => t + 1);
  };

  const handleFile = async (file: File | null | undefined) => {
    if (!file) return;
    const lower = file.name.toLowerCase();
    const ok =
      lower.endsWith('.csv') ||
      lower.endsWith('.pdf') ||
      lower.endsWith('.tsv') ||
      lower.endsWith('.txt') ||
      lower.endsWith('.xlsx') ||
      lower.endsWith('.xls');
    if (!ok) {
      toast('Supported formats: CSV, TSV, TXT, XLSX, XLS, or PDF.', 'warning');
      return;
    }
    setBusy(true);
    const isPdf = lower.endsWith('.pdf');
    if (isPdf) {
      toast('PDF uploaded — OCR for scanned sheets can take 15–30 minutes. Keep this tab open.', 'info');
    }
    try {
      const parsed = await importService.upload(file, entityType || undefined);
      applyUpload(parsed);
      success(`Parsed ${parsed.rowCount} rows from ${parsed.fileName}`);
      if (parsed.warnings?.length) {
        toast(parsed.warnings[0], 'warning');
      }
    } catch (e) {
      const msg = e instanceof Error ? e.message : 'Upload/parse failed';
      notifyError(
        isPdf && (/failed to fetch|network|timeout|aborted/i.test(msg) || msg === 'Failed to fetch')
          ? 'Upload timed out while OCR was still running. Wait and refresh Import sessions — a large scan can take 15–30 minutes. Or retry and leave the tab open.'
          : msg,
      );
    } finally {
      setBusy(false);
      if (fileRef.current) fileRef.current.value = '';
    }
  };

  const saveMapping = async (andContinue = false) => {
    const id = activeSessionId || upload?.sessionId;
    if (!id) return;
    setBusy(true);
    try {
      const updated = await importService.updateMapping(id, {
        columnMap,
        entityType: upload?.entityType,
        ignoredColumns: ignored,
        saveAsProfileName: profileName.trim() || undefined,
      });
      applyUpload(updated);
      if (profileName.trim()) success(`Mapping saved as profile “${profileName.trim()}”`);
      else success('Column mapping updated');
      if (andContinue) await advanceAfterMap(id);
    } catch (e) {
      notifyError(e instanceof Error ? e.message : 'Could not save mapping');
    } finally {
      setBusy(false);
    }
  };

  const advanceAfterMap = async (id: string) => {
    const updated = await importService.advance(id);
    setActiveSessionId(updated.id);
    setCurrent(STATUS_TO_STEP[updated.status] ?? current);
    setTick((t) => t + 1);
    if (updated.resultSummary) setResult(updated.resultSummary);
    if (updated.status === 'IMPORTED') {
      finishImport(updated.entityType, updated.resultSummary);
    }
  };

  const finishImport = (type: string, summary: ImportResultSummary | null | undefined) => {
    const empty =
      !!summary && summary.allocations === 0 && summary.lecturers === 0 && summary.units === 0;
    setImportedType(type);
    success(
      summary
        ? `Imported ${summary.allocations} allocations · ${summary.lecturers} lecturers · ${summary.units} units`
        : 'Import committed.',
    );
    if (summary?.errors) {
      toast(`${summary.errors} row(s) skipped (see details below).`, 'warning');
    }
    if (empty) {
      toast('Nothing was written — staging rows may have failed validation. Use Retry commit after fixing mapping.', 'warning');
    }
  };

  const retryCommit = async () => {
    const id = activeSessionId || upload?.sessionId;
    if (!id) return;
    setBusy(true);
    try {
      const updated = await importService.reprocess(id);
      setActiveSessionId(updated.id);
      setCurrent(STATUS_TO_STEP[updated.status] ?? current);
      setTick((t) => t + 1);
      if (updated.resultSummary) setResult(updated.resultSummary);
      if (updated.status === 'IMPORTED') {
        finishImport(updated.entityType, updated.resultSummary);
      }
    } catch (e) {
      notifyError(e instanceof Error ? e.message : 'Could not reprocess import');
    } finally {
      setBusy(false);
    }
  };

  const advance = async () => {
    const id = activeSessionId || upload?.sessionId;
    if (!id) {
      fileRef.current?.click();
      return;
    }
    if (current === 2) {
      await saveMapping(true);
      return;
    }
    if (current >= 4) {
      const ok = await confirm({
        title: 'Commit import',
        message:
          'Write validated rows into AcademicFlow? Lecturers, units, and allocations are upserted into the canonical model for this institution only.',
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
      if (updated.resultSummary) setResult(updated.resultSummary);
      if (updated.status === 'IMPORTED') {
        finishImport(updated.entityType, updated.resultSummary);
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

  const canonicalOptions = [
    ...(upload?.canonicalFields || []).map((f) => ({
      value: f,
      label: FIELD_LABELS[f] || f,
    })),
    { value: 'IGNORE', label: FIELD_LABELS.IGNORE },
  ];

  const unmappedCount =
    upload?.detectedColumns.filter((c) => !columnMap[c] || columnMap[c] === 'IGNORE' || ignored.includes(c))
      .length ?? 0;

  return (
    <>
      <PageHead
        title="Import / Export"
        subtitle="Upload institution allocation files (CSV, Excel, PDF). AcademicFlow maps their columns into a stable canonical model — no per-university schema changes."
      />
      {isChair && (
        <div className="import-scope-note">
          Import commits only to {scopeDept || 'your department'}. Rows for other departments are skipped with
          warnings.
        </div>
      )}
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
          <div style={{ fontWeight: 700, marginTop: 8 }}>Drop CSV, Excel, or PDF here — or click to browse</div>
          <p className="section-sub" style={{ marginBottom: 0 }}>
            Lecturers, academic units, or lecturer↔unit allocations · institution-agnostic column mapping
          </p>
          <input
            ref={fileRef}
            type="file"
            accept=".csv,.tsv,.txt,.pdf,.xlsx,.xls,text/csv,application/pdf,application/vnd.openxmlformats-officedocument.spreadsheetml.sheet,application/vnd.ms-excel"
            hidden
            onChange={(e) => void handleFile(e.target.files?.[0])}
          />
        </div>

        <div className="field" style={{ marginTop: 16 }}>
          <label>Force entity type (optional)</label>
          <SuggestInput
            id="import-entity-type"
            value={entityType}
            onChange={setEntityType}
            options={[
              { value: 'ALLOCATION', label: 'ALLOCATION (lecturer → unit)' },
              { value: 'LECTURER', label: 'LECTURER' },
              { value: 'ACADEMIC_UNIT', label: 'ACADEMIC_UNIT' },
            ]}
            placeholder="Leave blank to auto-detect"
          />
        </div>

        {upload && (
          <div style={{ marginTop: 18 }}>
            <div className="section-title">Detected file</div>
            <p className="section-sub">
              <b>{upload.fileName}</b> · {upload.entityType} · {upload.rowCount} rows · status {upload.status}
              {upload.profileApplied ? ` · profile “${upload.profileApplied}”` : ''}
            </p>
            {upload.warnings.length > 0 && (
              <p className="section-sub" style={{ color: 'var(--warning)' }}>
                {upload.warnings.join(' ')}
              </p>
            )}

            <div className="section-title">Field mapping</div>
            <p className="section-sub">
              Review how this institution&apos;s columns map into AcademicFlow. Change any mapping before continuing.
              {unmappedCount > 0 ? ` ${unmappedCount} column(s) not mapped to a core field.` : ''}
            </p>
            <div className="table-wrap" style={{ border: 'none' }}>
              <table>
                <thead>
                  <tr>
                    <th>Uploaded field</th>
                    <th />
                    <th>AcademicFlow field</th>
                    <th>Confidence</th>
                    <th>Method</th>
                  </tr>
                </thead>
                <tbody>
                  {(upload.mappingSuggestions.length
                    ? upload.mappingSuggestions
                    : upload.detectedColumns.map((c) => ({
                        sourceColumn: c,
                        targetField: columnMap[c] || null,
                        confidence: columnMap[c] ? 1 : 0,
                        method: 'manual',
                        sampleValues: [] as string[],
                        unmapped: !columnMap[c],
                      }))
                  ).map((s) => (
                    <tr key={s.sourceColumn}>
                      <td>
                        <div className="mono">{s.sourceColumn}</div>
                        {s.sampleValues?.length > 0 && (
                          <div className="cell-sub">{s.sampleValues.slice(0, 3).join(' · ')}</div>
                        )}
                      </td>
                      <td>
                        <IconChevRight />
                      </td>
                      <td style={{ minWidth: 220 }}>
                        <SuggestInput
                          id={`map-${s.sourceColumn}`}
                          value={
                            FIELD_LABELS[columnMap[s.sourceColumn]] ||
                            columnMap[s.sourceColumn] ||
                            (ignored.includes(s.sourceColumn) ? FIELD_LABELS.IGNORE : '')
                          }
                          onChange={(v) => {
                            const match = canonicalOptions.find(
                              (o) => o.label === v || o.value === v || o.value.toLowerCase() === v.toLowerCase(),
                            );
                            const target = match?.value || v.trim();
                            if (!target || target === 'IGNORE') {
                              setIgnored((prev) => [...prev.filter((x) => x !== s.sourceColumn), s.sourceColumn]);
                              setColumnMap((prev) => {
                                const next = { ...prev };
                                delete next[s.sourceColumn];
                                return next;
                              });
                            } else {
                              setIgnored((prev) => prev.filter((x) => x !== s.sourceColumn));
                              setColumnMap((prev) => ({ ...prev, [s.sourceColumn]: target }));
                            }
                          }}
                          options={canonicalOptions}
                          hint=""
                          placeholder="Type or pick a field…"
                        />
                      </td>
                      <td>
                        <span
                          className="mono"
                          style={{
                            color:
                              s.confidence >= 0.9
                                ? 'var(--success, #1a7f4b)'
                                : s.confidence >= 0.75
                                  ? 'var(--text-primary)'
                                  : 'var(--warning)',
                          }}
                        >
                          {Math.round(s.confidence * 100)}%
                        </span>
                      </td>
                      <td className="cell-sub">{s.method}</td>
                    </tr>
                  ))}
                </tbody>
              </table>
            </div>

            <div className="field" style={{ marginTop: 12 }}>
              <label>Save this mapping as an institution profile (optional)</label>
              <input
                value={profileName}
                onChange={(e) => setProfileName(e.target.value)}
                placeholder="e.g. Engineering allocation 2027"
              />
              <p className="field-hint">Future uploads from this institution can reuse the approved mapping.</p>
            </div>
            <div className="btn-row" style={{ marginBottom: 8 }}>
              <Button onClick={() => void saveMapping(false)} disabled={busy}>
                Save mapping
              </Button>
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

        {result && (
          <div style={{ marginTop: 18 }} className="card card-pad">
            <div className="section-title">Import result</div>
            <div className="stat-grid-3">
              <div className="stat-box">
                <div className="stat-box-value">{result.allocations}</div>
                <div className="stat-box-label">Allocations</div>
              </div>
              <div className="stat-box">
                <div className="stat-box-value">{result.lecturers}</div>
                <div className="stat-box-label">Lecturers</div>
              </div>
              <div className="stat-box">
                <div className="stat-box-value">{result.units}</div>
                <div className="stat-box-label">Units</div>
              </div>
            </div>
            <p className="section-sub" style={{ marginTop: 12 }}>
              Warnings: {result.warnings} · Errors: {result.errors} · Duplicates skipped: {result.duplicatesSkipped} ·
              Unmapped fields: {result.unmappedFields}
            </p>
            {result.details.length > 0 && (
              <ul className="section-sub">
                {result.details.map((d) => (
                  <li key={d}>{d}</li>
                ))}
              </ul>
            )}
            {result.allocations === 0 && result.lecturers === 0 && result.units === 0 && (
              <div style={{ marginTop: 12 }}>
                <Button variant="default" disabled={busy} onClick={() => void retryCommit()}>
                  Retry commit
                </Button>
              </div>
            )}
            {(result.units > 0 || result.allocations > 0 || importedType === 'ACADEMIC_UNIT') && (
              <div className="import-bridge">
                <h4>Next: allocate smartly</h4>
                <p className="section-sub" style={{ marginTop: 0 }}>
                  Review unallocated units and open allocate-by-context so workload and expertise drive who teaches
                  each unit.
                </p>
                <div className="btn-row">
                  <Button onClick={() => navigate('/units?status=Unallocated')}>View unallocated units</Button>
                  <Button variant="primary" onClick={() => navigate('/allocate-context')}>
                    Allocate by context
                  </Button>
                  {result.allocations > 0 && (
                    <Button onClick={() => navigate('/allocation')}>Open allocation board</Button>
                  )}
                </div>
              </div>
            )}
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
                        {r._errors ? ` · ${r._errors}` : ''}
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
                setResult(null);
                setImportedType(null);
                setColumnMap({});
              }}
            >
              Reset
            </Button>
            <Button variant="primary" onClick={() => void advance()} disabled={busy}>
              {busy
                ? 'Working…'
                : !activeSessionId
                  ? 'Upload file'
                  : current === 2
                    ? 'Confirm mapping & continue'
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
                            if (s.resultSummary) setResult(s.resultSummary);
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
