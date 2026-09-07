import { useState } from 'react';
import { useNavigate } from 'react-router-dom';
import { REQUESTS } from '../data/mockData';
import { useApp } from '../context/AppContext';
import { useAsyncData } from '../hooks/useAsyncData';
import { organizationService, requestService, unitService, type ApiTeachingRequest } from '../services';
import { Badge } from '../components/ui/Badge';
import { Button } from '../components/ui/Button';
import { PageHead, Tabs } from '../components/ui/Drawer';
import { FormActions, Modal } from '../components/ui/Modal';
import { IconChevRight } from '../components/ui/Icons';

function RequestCard({ r }: { r: ApiTeachingRequest | (typeof REQUESTS)[0] }) {
  const navigate = useNavigate();
  const { setSelectedRequestId } = useApp();
  const rawId = 'rawId' in r ? r.rawId : r.id;
  return (
    <div className="card card-pad" style={{ marginBottom: 12 }}>
      <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'flex-start', gap: 16 }}>
        <div style={{ minWidth: 0 }}>
          <div style={{ display: 'flex', alignItems: 'center', gap: 10, marginBottom: 6 }}>
            <span className="mono cell-primary">{r.id}</span>
            <span className="flow-chip"><b>{r.requestingDepartment}</b> <IconChevRight /> <b>{r.sourceDepartment}</b></span>
          </div>
          <div style={{ fontWeight: 700, fontSize: 14, marginBottom: 8 }}>{r.academicUnit}</div>
          <div className="rec-meta-line" style={{ marginBottom: 0 }}>
            <span><b>{r.studentCount}</b> students</span>
            <span><b>{r.contactHours}</b> contact hours</span>
            <span>Required expertise: <b>{r.requiredExpertise}</b></span>
            <span>Requested <b>{r.createdAt}</b></span>
          </div>
        </div>
        <div style={{ textAlign: 'right', flexShrink: 0 }}>
          <Badge status={r.status} />
          <div className="btn-row" style={{ marginTop: 12, justifyContent: 'flex-end' }}>
            <Button size="sm" onClick={() => { setSelectedRequestId(rawId); navigate('/recommendations'); }}>View</Button>
            {(r.status.includes('Awaiting') || r.status.includes('PENDING') || r.status.includes('RESPONSE')) && (
              <Button size="sm" variant="primary" onClick={() => { setSelectedRequestId(rawId); navigate('/recommendations'); }}>Find candidates</Button>
            )}
          </div>
        </div>
      </div>
    </div>
  );
}

export function RequestsPage() {
  const [tab, setTab] = useState(0);
  const [tick, setTick] = useState(0);
  const { data: requests, fromApi } = useAsyncData(() => requestService.list(), REQUESTS as unknown as ApiTeachingRequest[], [tick]);
  const { data: orgs } = useAsyncData(() => organizationService.list(), [], []);
  const { data: units } = useAsyncData(() => unitService.list(), [], []);
  const depts = orgs.filter((o) => o.type === 'Department');
  const [showAdd, setShowAdd] = useState(false);
  const [busy, setBusy] = useState(false);
  const cs = depts.find((d) => d.name === 'Computer Science');
  const mat = depts.find((d) => d.name === 'Mathematics');
  const [form, setForm] = useState({
    requestingDepartmentId: '',
    preferredDepartmentId: '',
    academicUnitId: '',
    studentCount: '180',
    contactHours: '4',
    requiredExpertise: 'Linear Algebra',
  });

  const filtered = requests.filter((r) => {
    if (tab === 4) return r.status.toLowerCase().includes('await');
    if (tab === 5) return r.status.toLowerCase().includes('complet') || r.status.toLowerCase().includes('publish');
    return true;
  });

  const save = async () => {
    setBusy(true);
    try {
      await requestService.create({
        requestingDepartmentId: form.requestingDepartmentId || cs?.id || depts[0]?.id,
        preferredDepartmentId: form.preferredDepartmentId || mat?.id || null,
        academicUnitId: form.academicUnitId || units[0]?.id,
        studentCount: Number(form.studentCount) || 0,
        contactHours: Number(form.contactHours) || 3,
        requiredExpertise: form.requiredExpertise,
      });
      setShowAdd(false);
      setTick((t) => t + 1);
    } catch (e) {
      window.alert(e instanceof Error ? e.message : 'Failed');
    } finally {
      setBusy(false);
    }
  };

  return (
    <>
      <PageHead
        title="Teaching Requests"
        subtitle={fromApi ? 'Cross-department requests from the API.' : 'Local fallback requests.'}
        actions={<Button variant="primary" onClick={() => setShowAdd(true)}>+ New request</Button>}
      />
      <Tabs tabs={[`All (${requests.length})`, 'My Requests', 'Incoming', 'Outgoing', 'Awaiting Response', 'Completed']} active={tab} onChange={setTab} />
      {filtered.map((r) => <RequestCard key={r.rawId} r={r as ApiTeachingRequest} />)}

      <Modal open={showAdd} title="New teaching request" onClose={() => setShowAdd(false)} footer={<FormActions onCancel={() => setShowAdd(false)} onSubmit={save} busy={busy} submitLabel="Submit request" />}>
        <div className="field"><label>Requesting department</label>
          <select value={form.requestingDepartmentId} onChange={(e) => setForm({ ...form, requestingDepartmentId: e.target.value })}>
            <option value="">Computer Science (default)</option>
            {depts.map((d) => <option key={d.id} value={d.id}>{d.name}</option>)}
          </select>
        </div>
        <div className="field"><label>Preferred / source department</label>
          <select value={form.preferredDepartmentId} onChange={(e) => setForm({ ...form, preferredDepartmentId: e.target.value })}>
            <option value="">Mathematics (default)</option>
            {depts.map((d) => <option key={d.id} value={d.id}>{d.name}</option>)}
          </select>
        </div>
        <div className="field"><label>Academic unit</label>
          <select value={form.academicUnitId} onChange={(e) => setForm({ ...form, academicUnitId: e.target.value })}>
            <option value="">Select unit</option>
            {units.map((u) => <option key={u.id} value={u.id}>{u.code} — {u.name}</option>)}
          </select>
        </div>
        <div className="field"><label>Students</label><input value={form.studentCount} onChange={(e) => setForm({ ...form, studentCount: e.target.value })} /></div>
        <div className="field"><label>Contact hours</label><input value={form.contactHours} onChange={(e) => setForm({ ...form, contactHours: e.target.value })} /></div>
        <div className="field"><label>Required expertise</label><input value={form.requiredExpertise} onChange={(e) => setForm({ ...form, requiredExpertise: e.target.value })} /></div>
      </Modal>
    </>
  );
}
