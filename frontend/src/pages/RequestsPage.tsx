import { useEffect, useMemo, useState } from 'react';
import { useNavigate } from 'react-router-dom';
import { REQUESTS } from '../data/mockData';
import { useApp } from '../context/AppContext';
import { useFeedback } from '../context/FeedbackContext';
import { useAsyncData } from '../hooks/useAsyncData';
import {
  lecturerService,
  organizationService,
  requestService,
  unitService,
  type ApiTeachingRequest,
  type RequestAttachment,
  type RequestMessage,
  type TeachingRequestDetail,
} from '../services';
import { Badge } from '../components/ui/Badge';
import { Button } from '../components/ui/Button';
import { PageHead, Tabs } from '../components/ui/Drawer';
import { FormActions, Modal } from '../components/ui/Modal';
import { IconChevRight } from '../components/ui/Icons';
import { SuggestInput } from '../components/ui/SuggestInput';

function isAwaiting(status: string) {
  const s = status.toLowerCase();
  return s.includes('await') || s.includes('pending') || s.includes('response');
}

function isCompleted(status: string) {
  const s = status.toLowerCase();
  return (
    s.includes('complet') ||
    s.includes('publish') ||
    s.includes('assigned') ||
    s.includes('declined') ||
    s.includes('accept')
  );
}

function messageTypeLabel(type: string) {
  switch (type.toUpperCase()) {
    case 'ELIGIBILITY_NOTE':
      return 'Eligibility';
    case 'AUTHORITY_NOTICE':
      return 'Authority';
    case 'ACCEPT_NOTE':
      return 'Accepted';
    case 'DECLINE_NOTE':
      return 'Declined';
    case 'SYSTEM':
      return 'System';
    default:
      return 'Message';
  }
}

function formatBytes(n: number | null | undefined) {
  if (!n) return '';
  if (n < 1024) return `${n} B`;
  if (n < 1024 * 1024) return `${(n / 1024).toFixed(1)} KB`;
  return `${(n / (1024 * 1024)).toFixed(1)} MB`;
}

function RequestCard({
  r,
  onRespond,
  onOpen,
  busyId,
}: {
  r: ApiTeachingRequest;
  onRespond: (id: string, action: 'ACCEPT' | 'DECLINE') => void;
  onOpen: (id: string) => void;
  busyId: string | null;
}) {
  const navigate = useNavigate();
  const { setSelectedRequestId, user } = useApp();
  const rawId = r.rawId;
  const incoming = r.direction === 'incoming';
  const canRespond =
    incoming &&
    (r.status.toUpperCase().includes('AWAITING') || r.status.toUpperCase().includes('PENDING'));
  const canMatch =
    r.status.toUpperCase().includes('ACCEPT') ||
    r.status.toUpperCase().includes('RECOMMENDED') ||
    r.status.toUpperCase().includes('MATCH') ||
    (!incoming && isAwaiting(r.status));

  return (
    <div className="card card-pad" style={{ marginBottom: 12 }}>
      <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'flex-start', gap: 16 }}>
        <div style={{ minWidth: 0 }}>
          <div style={{ display: 'flex', alignItems: 'center', gap: 10, marginBottom: 6, flexWrap: 'wrap' }}>
            <span className="mono cell-primary">{r.id}</span>
            <span className="flow-chip">
              <b>{r.requestingDepartment}</b> <IconChevRight /> <b>{r.sourceDepartment || 'Any dept'}</b>
            </span>
            {r.direction && (
              <span className={`badge ${incoming ? 'badge-warning' : 'badge-info'}`}>
                {incoming ? 'Incoming' : r.direction === 'outgoing' ? 'Outgoing' : r.direction}
              </span>
            )}
            {(r.attachmentCount ?? 0) > 0 && (
              <span className="badge badge-neutral">{r.attachmentCount} doc{(r.attachmentCount ?? 0) === 1 ? '' : 's'}</span>
            )}
            {(r.messageCount ?? 0) > 0 && (
              <span className="badge badge-neutral">{r.messageCount} msg{(r.messageCount ?? 0) === 1 ? '' : 's'}</span>
            )}
          </div>
          <div style={{ fontWeight: 700, fontSize: 14, marginBottom: 8 }}>{r.academicUnit}</div>
          <div className="rec-meta-line" style={{ marginBottom: 0 }}>
            <span>
              <b>{r.studentCount}</b> students
            </span>
            <span>
              <b>{r.contactHours}</b> contact hours
            </span>
            <span>
              Required expertise: <b>{r.requiredExpertise || '—'}</b>
            </span>
            <span>
              Requested <b>{r.createdAt}</b>
            </span>
          </div>
          {r.briefingNote && (
            <p className="field-hint" style={{ marginTop: 8 }}>
              {r.briefingNote.length > 160 ? `${r.briefingNote.slice(0, 160)}…` : r.briefingNote}
            </p>
          )}
          {incoming && canRespond && (
            <p className="field-hint" style={{ marginTop: 8 }}>
              {user.departmentName || 'Your department'} is the preferred source — open the thread to review docs,
              reply, then accept or decline.
            </p>
          )}
        </div>
        <div style={{ textAlign: 'right', flexShrink: 0 }}>
          <Badge status={r.status} />
          <div className="btn-row" style={{ marginTop: 12, justifyContent: 'flex-end' }}>
            {canRespond && (
              <>
                <Button size="sm" disabled={busyId === rawId} onClick={() => onRespond(rawId, 'DECLINE')}>
                  Decline
                </Button>
                <Button
                  size="sm"
                  variant="primary"
                  disabled={busyId === rawId}
                  onClick={() => onRespond(rawId, 'ACCEPT')}
                >
                  Accept request
                </Button>
              </>
            )}
            <Button size="sm" variant="primary" onClick={() => onOpen(rawId)}>
              Open thread
            </Button>
            {canMatch && (
              <Button
                size="sm"
                onClick={() => {
                  setSelectedRequestId(rawId);
                  if (r.courseOfferingId) navigate('/allocate-context');
                  else navigate('/recommendations');
                }}
              >
                Find candidates
              </Button>
            )}
          </div>
        </div>
      </div>
    </div>
  );
}

function RequestThreadModal({
  open,
  requestId,
  onClose,
  onChanged,
  onRespond,
}: {
  open: boolean;
  requestId: string | null;
  onClose: () => void;
  onChanged: () => void;
  onRespond: (id: string, action: 'ACCEPT' | 'DECLINE', note?: string) => Promise<void>;
}) {
  const { error: notifyError, success } = useFeedback();
  const [detail, setDetail] = useState<TeachingRequestDetail | null>(null);
  const [loading, setLoading] = useState(false);
  const [busy, setBusy] = useState(false);
  const [msgBody, setMsgBody] = useState('');
  const [msgType, setMsgType] = useState<'COMMENT' | 'ELIGIBILITY_NOTE' | 'AUTHORITY_NOTICE'>('COMMENT');
  const [lecturerName, setLecturerName] = useState('');
  const [notifyAuthority, setNotifyAuthority] = useState(false);
  const [respondNote, setRespondNote] = useState('');
  const [docType, setDocType] = useState('COURSE_OUTLINE');
  const { data: lecturers } = useAsyncData(() => lecturerService.list(), [], [open]);

  const load = async (id: string) => {
    setLoading(true);
    try {
      const d = await requestService.getDetail(id);
      setDetail(d);
    } catch (e) {
      notifyError(e instanceof Error ? e.message : 'Could not load request thread');
      onClose();
    } finally {
      setLoading(false);
    }
  };

  useEffect(() => {
    if (open && requestId) void load(requestId);
    if (!open) {
      setDetail(null);
      setMsgBody('');
      setLecturerName('');
      setRespondNote('');
      setMsgType('COMMENT');
      setNotifyAuthority(false);
    }
  }, [open, requestId]);

  const r = detail?.request;
  const incoming = r?.direction === 'incoming';
  const canRespond =
    !!r &&
    incoming &&
    (r.status.toUpperCase().includes('AWAITING') || r.status.toUpperCase().includes('PENDING'));

  const sendMessage = async () => {
    if (!requestId || !msgBody.trim()) {
      notifyError('Enter a message');
      return;
    }
    setBusy(true);
    try {
      await requestService.postMessage(requestId, {
        body: msgBody.trim(),
        messageType: msgType,
        relatedLecturerName: lecturerName.trim() || undefined,
        notifyAuthority: notifyAuthority || msgType === 'AUTHORITY_NOTICE',
      });
      setMsgBody('');
      setLecturerName('');
      setNotifyAuthority(false);
      await load(requestId);
      onChanged();
      success(msgType === 'AUTHORITY_NOTICE' ? 'Authority notice posted' : 'Message sent');
    } catch (e) {
      notifyError(e instanceof Error ? e.message : 'Could not send message');
    } finally {
      setBusy(false);
    }
  };

  const uploadFile = async (file: File | null) => {
    if (!requestId || !file) return;
    setBusy(true);
    try {
      await requestService.uploadAttachment(requestId, file, docType);
      await load(requestId);
      onChanged();
      success('Document attached');
    } catch (e) {
      notifyError(e instanceof Error ? e.message : 'Upload failed');
    } finally {
      setBusy(false);
    }
  };

  const download = async (a: RequestAttachment) => {
    try {
      await requestService.downloadAttachment(a.id, a.fileName);
    } catch (e) {
      notifyError(e instanceof Error ? e.message : 'Download failed');
    }
  };

  return (
      <Modal
      open={open}
      title={r ? `Request thread · ${r.id}` : 'Request thread'}
      onClose={onClose}
      wide
      footer={
        <div className="btn-row" style={{ justifyContent: 'space-between', width: '100%' }}>
          <Button onClick={onClose}>Close</Button>
          {canRespond && requestId && (
            <div className="btn-row">
              <Button
                disabled={busy}
                onClick={() => void onRespond(requestId, 'DECLINE', respondNote.trim() || undefined)}
              >
                Decline
              </Button>
              <Button
                variant="primary"
                disabled={busy}
                onClick={() => void onRespond(requestId, 'ACCEPT', respondNote.trim() || undefined)}
              >
                Accept request
              </Button>
            </div>
          )}
        </div>
      }
    >
      {loading && <p className="section-sub">Loading thread…</p>}
      {!loading && r && (
        <>
          <div className="flow-chip" style={{ marginBottom: 10 }}>
            <b>{r.requestingDepartment}</b> <IconChevRight /> <b>{r.sourceDepartment || 'Any dept'}</b>
          </div>
          <div style={{ fontWeight: 700, marginBottom: 6 }}>{r.academicUnit}</div>
          <div className="rec-meta-line" style={{ marginBottom: 12 }}>
            <span>
              <b>{r.studentCount}</b> students
            </span>
            <span>
              <b>{r.contactHours}</b> hrs
            </span>
            <span>
              Expertise: <b>{r.requiredExpertise || '—'}</b>
            </span>
            <Badge status={r.status} />
          </div>

          <div className="req-collab-grid">
            <section className="req-collab-panel">
              <h4 className="req-collab-title">Documents</h4>
              <p className="field-hint" style={{ marginTop: 0 }}>
                Course outlines and supporting docs shared with the other department.
              </p>
              <div className="field">
                <label>Document type</label>
                <select value={docType} onChange={(e) => setDocType(e.target.value)}>
                  <option value="COURSE_OUTLINE">Course outline</option>
                  <option value="SUPPORTING_DOC">Supporting document</option>
                  <option value="TIMETABLE">Timetable</option>
                  <option value="OTHER">Other</option>
                </select>
              </div>
              <div className="field">
                <label>Attach file</label>
                <input
                  type="file"
                  accept=".pdf,.doc,.docx,.txt,.png,.jpg,.jpeg"
                  disabled={busy}
                  onChange={(e) => {
                    const f = e.target.files?.[0] || null;
                    e.target.value = '';
                    void uploadFile(f);
                  }}
                />
              </div>
              {(detail?.attachments.length ?? 0) === 0 && (
                <p className="section-sub">No attachments yet.</p>
              )}
              <ul className="req-attach-list">
                {(detail?.attachments || []).map((a) => (
                  <li key={a.id}>
                    <div>
                      <div style={{ fontWeight: 600 }}>{a.fileName}</div>
                      <div className="field-hint" style={{ margin: 0 }}>
                        {a.docType.replace(/_/g, ' ')}
                        {a.departmentName ? ` · ${a.departmentName}` : ''}
                        {a.fileSizeBytes ? ` · ${formatBytes(a.fileSizeBytes)}` : ''}
                      </div>
                    </div>
                    <Button size="sm" onClick={() => void download(a)}>
                      Download
                    </Button>
                  </li>
                ))}
              </ul>
            </section>

            <section className="req-collab-panel">
              <h4 className="req-collab-title">Department exchange</h4>
              <p className="field-hint" style={{ marginTop: 0 }}>
                Both chairs can discuss eligibility, constraints, and notify academic authority.
              </p>
              <div className="req-msg-thread">
                {(detail?.messages || []).map((m: RequestMessage) => (
                  <div
                    key={m.id}
                    className={`req-msg ${m.mine ? 'req-msg-mine' : ''} req-msg-${m.messageType.toLowerCase()}`}
                  >
                    <div className="req-msg-meta">
                      <span className="badge badge-neutral">{messageTypeLabel(m.messageType)}</span>
                      <span>
                        {m.authorName || 'User'}
                        {m.authorDepartmentName ? ` · ${m.authorDepartmentName}` : ''}
                      </span>
                      <span className="mono">
                        {new Date(m.createdAt).toLocaleString('en-GB', {
                          day: '2-digit',
                          month: 'short',
                          hour: '2-digit',
                          minute: '2-digit',
                        })}
                      </span>
                    </div>
                    <div className="req-msg-body">{m.body}</div>
                    {m.relatedLecturerName && (
                      <div className="field-hint" style={{ marginTop: 4 }}>
                        Lecturer: <b>{m.relatedLecturerName}</b>
                      </div>
                    )}
                    {m.notifyAuthority && (
                      <div className="field-hint" style={{ marginTop: 4 }}>
                        Flagged for academic authority
                      </div>
                    )}
                  </div>
                ))}
                {(detail?.messages.length ?? 0) === 0 && (
                  <p className="section-sub">No messages yet — start the exchange below.</p>
                )}
              </div>

              <div className="field">
                <label>Message type</label>
                <select
                  value={msgType}
                  onChange={(e) => {
                    const v = e.target.value as typeof msgType;
                    setMsgType(v);
                    if (v === 'AUTHORITY_NOTICE') setNotifyAuthority(true);
                  }}
                >
                  <option value="COMMENT">General comment</option>
                  <option value="ELIGIBILITY_NOTE">Eligibility note (lecturer limited to this unit)</option>
                  <option value="AUTHORITY_NOTICE">Notify academic authority</option>
                </select>
              </div>
              {(msgType === 'ELIGIBILITY_NOTE' || msgType === 'COMMENT') && (
                <div className="field">
                  <label>Related lecturer (optional)</label>
                  <SuggestInput
                    id="req-msg-lecturer"
                    value={lecturerName}
                    onChange={setLecturerName}
                    options={lecturers.map((l) => ({ value: l.id, label: l.name }))}
                    placeholder="e.g. only eligible for this unit…"
                  />
                </div>
              )}
              <div className="field">
                <label>Message</label>
                <textarea
                  rows={3}
                  value={msgBody}
                  onChange={(e) => setMsgBody(e.target.value)}
                  placeholder={
                    msgType === 'ELIGIBILITY_NOTE'
                      ? 'e.g. Dr. Otieno is only eligible to teach this requested unit this semester.'
                      : msgType === 'AUTHORITY_NOTICE'
                        ? 'e.g. Please inform the Dean / academic authority before allocation proceeds.'
                        : 'Share constraints, timetable notes, or questions with the other department…'
                  }
                />
              </div>
              <label className="req-check">
                <input
                  type="checkbox"
                  checked={notifyAuthority || msgType === 'AUTHORITY_NOTICE'}
                  disabled={msgType === 'AUTHORITY_NOTICE'}
                  onChange={(e) => setNotifyAuthority(e.target.checked)}
                />
                Inform relevant academic authority
              </label>
              <div className="btn-row" style={{ marginTop: 10 }}>
                <Button variant="primary" disabled={busy} onClick={() => void sendMessage()}>
                  Send message
                </Button>
              </div>

              {canRespond && (
                <div className="field" style={{ marginTop: 16 }}>
                  <label>Response note (optional)</label>
                  <textarea
                    rows={2}
                    value={respondNote}
                    onChange={(e) => setRespondNote(e.target.value)}
                    placeholder="Shown on the thread when you accept or decline…"
                  />
                </div>
              )}
            </section>
          </div>
        </>
      )}
    </Modal>
  );
}

export function RequestsPage() {
  const [tab, setTab] = useState(0);
  const [tick, setTick] = useState(0);
  const { user } = useApp();
  const { error: notifyError, success, confirm } = useFeedback();
  const { data: requests, fromApi } = useAsyncData(
    () => requestService.list(),
    REQUESTS as unknown as ApiTeachingRequest[],
    [tick],
  );
  const { data: orgs } = useAsyncData(() => organizationService.list(), [], [tick]);
  const { data: units } = useAsyncData(() => unitService.list(), [], [tick]);
  const depts = orgs.filter((o) => o.type === 'Department');
  const [showAdd, setShowAdd] = useState(false);
  const [threadId, setThreadId] = useState<string | null>(null);
  const [busy, setBusy] = useState(false);
  const [busyId, setBusyId] = useState<string | null>(null);
  const [attachFile, setAttachFile] = useState<File | null>(null);
  const [attachDocType, setAttachDocType] = useState('COURSE_OUTLINE');
  const [form, setForm] = useState({
    requestingDepartment: user.departmentName && user.departmentName !== 'Institution' ? user.departmentName : '',
    preferredDepartment: '',
    academicUnit: '',
    studentCount: '',
    contactHours: '',
    requiredExpertise: '',
    briefingNote: '',
  });

  const counts = useMemo(() => {
    const my = requests.filter((r) => r.direction === 'outgoing' || r.createdBy === user.id).length;
    const incoming = requests.filter((r) => r.direction === 'incoming').length;
    const outgoing = requests.filter((r) => r.direction === 'outgoing').length;
    const awaiting = requests.filter((r) => isAwaiting(r.status)).length;
    const completed = requests.filter((r) => isCompleted(r.status)).length;
    return { all: requests.length, my, incoming, outgoing, awaiting, completed };
  }, [requests, user.id]);

  const filtered = requests.filter((r) => {
    if (tab === 1) return r.direction === 'outgoing' || r.createdBy === user.id;
    if (tab === 2) return r.direction === 'incoming';
    if (tab === 3) return r.direction === 'outgoing';
    if (tab === 4) return isAwaiting(r.status);
    if (tab === 5) return isCompleted(r.status);
    return true;
  });

  const respond = async (id: string, action: 'ACCEPT' | 'DECLINE', note?: string) => {
    const ok = await confirm({
      title: action === 'ACCEPT' ? 'Accept teaching request' : 'Decline teaching request',
      message:
        action === 'ACCEPT'
          ? 'Accepting allows the requesting department to match and allocate lecturers from your department.'
          : 'Decline this cross-department teaching request?',
      confirmLabel: action === 'ACCEPT' ? 'Accept' : 'Decline',
      danger: action === 'DECLINE',
    });
    if (!ok) return;
    setBusyId(id);
    try {
      await requestService.respond(id, action, note);
      setTick((t) => t + 1);
      if (threadId === id) setThreadId(null);
      success(action === 'ACCEPT' ? 'Request accepted' : 'Request declined');
    } catch (e) {
      notifyError(e instanceof Error ? e.message : 'Could not respond');
    } finally {
      setBusyId(null);
    }
  };

  const save = async () => {
    if (!form.requestingDepartment.trim()) {
      notifyError('Enter the requesting department');
      return;
    }
    if (!form.preferredDepartment.trim()) {
      notifyError('Enter the preferred / source department');
      return;
    }
    if (!form.academicUnit.trim()) {
      notifyError('Enter an academic unit (code or name)');
      return;
    }
    if (form.requestingDepartment.trim().toLowerCase() === form.preferredDepartment.trim().toLowerCase()) {
      notifyError('Requesting and preferred departments should be different for a cross-department request');
      return;
    }
    setBusy(true);
    try {
      const created = await requestService.create({
        requestingDepartment: form.requestingDepartment.trim(),
        preferredDepartment: form.preferredDepartment.trim(),
        academicUnit: form.academicUnit.trim(),
        studentCount: Number(form.studentCount) || 0,
        contactHours: Number(form.contactHours) || 3,
        requiredExpertise: form.requiredExpertise,
        briefingNote: form.briefingNote.trim() || undefined,
        createOffering: true,
      });
      if (attachFile) {
        try {
          await requestService.uploadAttachment(created.rawId, attachFile, attachDocType);
        } catch (e) {
          notifyError(
            e instanceof Error
              ? `Request created, but attachment failed: ${e.message}`
              : 'Request created, but attachment failed',
          );
        }
      }
      setShowAdd(false);
      setAttachFile(null);
      setForm({
        requestingDepartment:
          user.departmentName && user.departmentName !== 'Institution' ? user.departmentName : '',
        preferredDepartment: '',
        academicUnit: '',
        studentCount: '',
        contactHours: '',
        requiredExpertise: '',
        briefingNote: '',
      });
      setTick((t) => t + 1);
      success('Teaching request submitted — source department can review docs and reply from Incoming');
      setThreadId(created.rawId);
    } catch (e) {
      notifyError(e instanceof Error ? e.message : 'Failed');
    } finally {
      setBusy(false);
    }
  };

  return (
    <>
      <PageHead
        title="Teaching Requests"
        subtitle={
          fromApi
            ? 'Request teaching support from another department, attach course outlines, and exchange eligibility notes both ways.'
            : 'Local fallback requests.'
        }
        actions={
          <Button variant="primary" onClick={() => setShowAdd(true)}>
            + New request
          </Button>
        }
      />
      <Tabs
        tabs={[
          `All (${counts.all})`,
          `My Requests (${counts.my})`,
          `Incoming (${counts.incoming})`,
          `Outgoing (${counts.outgoing})`,
          `Awaiting Response (${counts.awaiting})`,
          `Completed (${counts.completed})`,
        ]}
        active={tab}
        onChange={setTab}
      />
      {filtered.length === 0 && (
        <p className="section-sub" style={{ marginTop: 12 }}>
          No requests in this view.
          {tab === 2
            ? ' When another department names yours as preferred source, requests appear here for accept/decline.'
            : ''}
        </p>
      )}
      {filtered.map((r) => (
        <RequestCard
          key={r.rawId}
          r={r as ApiTeachingRequest}
          onRespond={respond}
          onOpen={setThreadId}
          busyId={busyId}
        />
      ))}

      <Modal
        open={showAdd}
        title="New teaching request"
        onClose={() => setShowAdd(false)}
        footer={
          <FormActions
            onCancel={() => setShowAdd(false)}
            onSubmit={save}
            busy={busy}
            submitLabel="Submit request"
          />
        }
      >
        <p className="section-sub" style={{ marginBottom: 12 }}>
          Ask another department to supply a lecturer. Attach the course outline so they can assess fit, then keep
          talking in the request thread.
        </p>
        <div className="field">
          <label>Requesting department (yours)</label>
          <SuggestInput
            id="req-requesting-dept"
            value={form.requestingDepartment}
            onChange={(v) => setForm({ ...form, requestingDepartment: v })}
            options={depts.map((d) => ({ value: d.id, label: d.name }))}
            placeholder="Type department name…"
          />
        </div>
        <div className="field">
          <label>Preferred / source department</label>
          <SuggestInput
            id="req-preferred-dept"
            value={form.preferredDepartment}
            onChange={(v) => setForm({ ...form, preferredDepartment: v })}
            options={depts.map((d) => ({ value: d.id, label: d.name }))}
            placeholder="Department that should supply the lecturer…"
          />
        </div>
        <div className="field">
          <label>Academic unit</label>
          <SuggestInput
            id="req-academic-unit"
            value={form.academicUnit}
            onChange={(v) => setForm({ ...form, academicUnit: v })}
            options={units.map((u) => ({ value: u.id, label: `${u.code} — ${u.name}` }))}
            placeholder="Type unit code or name…"
          />
        </div>
        <div className="field">
          <label>Students</label>
          <input
            value={form.studentCount}
            onChange={(e) => setForm({ ...form, studentCount: e.target.value })}
            placeholder="e.g. 120"
          />
        </div>
        <div className="field">
          <label>Contact hours</label>
          <input
            value={form.contactHours}
            onChange={(e) => setForm({ ...form, contactHours: e.target.value })}
            placeholder="e.g. 4"
          />
        </div>
        <div className="field">
          <label>Required expertise</label>
          <input
            value={form.requiredExpertise}
            onChange={(e) => setForm({ ...form, requiredExpertise: e.target.value })}
            placeholder="e.g. Linear Algebra"
          />
        </div>
        <div className="field">
          <label>Briefing for source department</label>
          <textarea
            rows={3}
            value={form.briefingNote}
            onChange={(e) => setForm({ ...form, briefingNote: e.target.value })}
            placeholder="Context for the unit, preferred schedule, constraints, or what the outline covers…"
          />
        </div>
        <div className="field">
          <label>Attach course outline / documentation (optional)</label>
          <select
            value={attachDocType}
            onChange={(e) => setAttachDocType(e.target.value)}
            style={{ marginBottom: 8 }}
          >
            <option value="COURSE_OUTLINE">Course outline</option>
            <option value="SUPPORTING_DOC">Supporting document</option>
            <option value="TIMETABLE">Timetable</option>
            <option value="OTHER">Other</option>
          </select>
          <input
            type="file"
            accept=".pdf,.doc,.docx,.txt,.png,.jpg,.jpeg"
            onChange={(e) => setAttachFile(e.target.files?.[0] || null)}
          />
          {attachFile && (
            <p className="field-hint">Selected: {attachFile.name}</p>
          )}
        </div>
      </Modal>

      <RequestThreadModal
        open={!!threadId}
        requestId={threadId}
        onClose={() => setThreadId(null)}
        onChanged={() => setTick((t) => t + 1)}
        onRespond={respond}
      />
    </>
  );
}
