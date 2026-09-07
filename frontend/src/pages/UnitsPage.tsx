import { useState } from 'react';
import { useNavigate } from 'react-router-dom';
import { UNITS } from '../data/mockData';
import { useApp } from '../context/AppContext';
import { useFeedback } from '../context/FeedbackContext';
import { useAsyncData } from '../hooks/useAsyncData';
import { organizationService, unitService } from '../services';
import { Badge } from '../components/ui/Badge';
import { Button } from '../components/ui/Button';
import { DrawerCloseButton, PageHead } from '../components/ui/Drawer';
import { FormActions, Modal } from '../components/ui/Modal';
import { IconExchange, IconSearch } from '../components/ui/Icons';
import type { AcademicUnit } from '../types';

function UnitDrawer({ unit, onClose }: { unit: AcademicUnit; onClose: () => void }) {
  const navigate = useNavigate();
  return (
    <>
      <div className="drawer-head">
        <div>
          <div className="mono cell-sub" style={{ marginBottom: 3 }}>{unit.code}</div>
          <div style={{ fontWeight: 700, fontSize: 16, marginBottom: 6 }}>{unit.name}</div>
          <Badge status={unit.status} />
        </div>
        <DrawerCloseButton onClose={onClose} />
      </div>
      <div className="drawer-body">
        {unit.requestingDepartment && (
          <div style={{ display: 'flex', alignItems: 'center', gap: 9, background: 'var(--info-tint)', color: 'var(--primary)', borderRadius: 8, padding: '10px 13px', marginBottom: 16, fontSize: 12.5, fontWeight: 600 }}>
            <IconExchange /> Requested by {unit.requestingDepartment}
          </div>
        )}
        <div className="stat-grid-3">
          <div className="stat-box"><div className="stat-box-value">{unit.studentCount}</div><div className="stat-box-label">Students</div></div>
          <div className="stat-box"><div className="stat-box-value">{unit.contactHours} hrs</div><div className="stat-box-label">Contact hours</div></div>
          <div className="stat-box"><div className="stat-box-value">{unit.semester.replace('Semester ', 'S')}</div><div className="stat-box-label">{unit.semester}</div></div>
        </div>
        <div className="def-list">
          <div className="def-row"><span className="def-label">Source department</span><span className="def-value">{unit.sourceDepartment}</span></div>
          <div className="def-row"><span className="def-label">Lecturer</span><span className="def-value">{unit.lecturerName || 'Unassigned'}</span></div>
        </div>
        <div className="section-title" style={{ marginTop: 18 }}>Required expertise</div>
        <div className="btn-row" style={{ marginTop: 8 }}>
          {unit.requiredExpertise.map((e) => <span className="badge badge-neutral" key={e}>{e}</span>)}
        </div>
      </div>
      <div className="drawer-foot">
        <Button onClick={onClose}>Close</Button>
        <Button variant="primary" style={{ marginLeft: 'auto' }} onClick={() => { onClose(); navigate('/recommendations'); }}>Find candidates</Button>
      </div>
    </>
  );
}

export function UnitsPage() {
  const { openDrawer, closeDrawer } = useApp();
  const { error: notifyError, success } = useFeedback();
  const [tick, setTick] = useState(0);
  const { data: units, fromApi } = useAsyncData(() => unitService.list(), UNITS, [tick]);
  const { data: orgs } = useAsyncData(() => organizationService.list(), [], []);
  const depts = orgs.filter((o) => o.type === 'Department');
  const [showAdd, setShowAdd] = useState(false);
  const [busy, setBusy] = useState(false);
  const [query, setQuery] = useState('');
  const [deptFilter, setDeptFilter] = useState('');
  const [form, setForm] = useState({ code: '', name: '', sourceDepartmentId: '', contactHours: '3', studentCount: '100', requiredExpertise: '' });

  const save = async () => {
    setBusy(true);
    try {
      await unitService.create({
        code: form.code,
        name: form.name,
        sourceDepartmentId: form.sourceDepartmentId || depts[0]?.id,
        contactHours: Number(form.contactHours) || 3,
        studentCount: Number(form.studentCount) || 0,
        requiredExpertise: form.requiredExpertise.split(',').map((s) => s.trim()).filter(Boolean),
      });
      setShowAdd(false);
      setTick((t) => t + 1);
      success('Academic unit created');
    } catch (e) {
      notifyError(e instanceof Error ? e.message : 'Failed');
    } finally {
      setBusy(false);
    }
  };

  const filtered = units.filter((u) => {
    const q = query.trim().toLowerCase();
    const matchesQuery =
      !q ||
      u.code.toLowerCase().includes(q) ||
      u.name.toLowerCase().includes(q) ||
      (u.lecturerName || '').toLowerCase().includes(q);
    const matchesDept = !deptFilter || u.sourceDepartmentId === deptFilter;
    return matchesQuery && matchesDept;
  });

  return (
    <>
      <PageHead
        title="Academic Units"
        subtitle={fromApi ? 'Loaded from PostgreSQL via REST API.' : 'Showing local fallback data.'}
        actions={<div className="btn-row"><Button onClick={() => setTick((t) => t + 1)}>Refresh</Button><Button variant="primary" onClick={() => setShowAdd(true)}>+ Add unit</Button></div>}
      />
      <div className="filters-bar">
        <div className="search-inline">
          <IconSearch />
          <input value={query} onChange={(e) => setQuery(e.target.value)} placeholder="Search units…" />
        </div>
        <select
          className="filter-chip"
          value={deptFilter}
          onChange={(e) => setDeptFilter(e.target.value)}
          style={{ appearance: 'auto' }}
        >
          <option value="">All departments</option>
          {depts.map((d) => (
            <option key={d.id} value={d.id}>{d.name}</option>
          ))}
        </select>
      </div>
      <div className="table-wrap">
        <table>
          <thead><tr><th>Code</th><th>Unit</th><th>Source dept.</th><th>Contact hrs</th><th>Students</th><th>Semester</th><th>Lecturer</th><th>Status</th></tr></thead>
          <tbody>
            {filtered.map((u) => (
              <tr key={u.id} onClick={() => openDrawer(<UnitDrawer unit={u} onClose={closeDrawer} />)}>
                <td className="mono cell-primary">{u.code}</td>
                <td>{u.name}</td>
                <td>{u.sourceDepartment}</td>
                <td>{u.contactHours} hrs</td>
                <td>{u.studentCount}</td>
                <td>{u.semester}</td>
                <td>{u.lecturerName || <span className="cell-sub">— Unassigned —</span>}</td>
                <td><Badge status={u.status} /></td>
              </tr>
            ))}
          </tbody>
        </table>
      </div>
      <Modal open={showAdd} title="Add academic unit" onClose={() => setShowAdd(false)} footer={<FormActions onCancel={() => setShowAdd(false)} onSubmit={save} busy={busy} submitLabel="Create unit" />}>
        <div className="field"><label>Code</label><input value={form.code} onChange={(e) => setForm({ ...form, code: e.target.value })} placeholder="MAT 204" /></div>
        <div className="field"><label>Name</label><input value={form.name} onChange={(e) => setForm({ ...form, name: e.target.value })} /></div>
        <div className="field"><label>Source department</label>
          <select value={form.sourceDepartmentId} onChange={(e) => setForm({ ...form, sourceDepartmentId: e.target.value })}>
            <option value="">Select</option>
            {depts.map((d) => <option key={d.id} value={d.id}>{d.name}</option>)}
          </select>
        </div>
        <div className="field"><label>Contact hours</label><input value={form.contactHours} onChange={(e) => setForm({ ...form, contactHours: e.target.value })} /></div>
        <div className="field"><label>Students</label><input value={form.studentCount} onChange={(e) => setForm({ ...form, studentCount: e.target.value })} /></div>
        <div className="field"><label>Required expertise (comma-separated)</label><input value={form.requiredExpertise} onChange={(e) => setForm({ ...form, requiredExpertise: e.target.value })} placeholder="Linear Algebra, Mathematics" /></div>
      </Modal>
    </>
  );
}
