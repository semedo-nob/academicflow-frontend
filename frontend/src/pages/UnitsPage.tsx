import { useState } from 'react';
import { useNavigate, useSearchParams } from 'react-router-dom';
import { UNITS } from '../data/mockData';
import { useApp } from '../context/AppContext';
import { useFeedback } from '../context/FeedbackContext';
import { useAsyncData } from '../hooks/useAsyncData';
import { courseOfferingService, organizationService, unitService } from '../services';
import { Badge } from '../components/ui/Badge';
import { Button } from '../components/ui/Button';
import { DrawerCloseButton, PageHead } from '../components/ui/Drawer';
import { FormActions, Modal } from '../components/ui/Modal';
import { IconExchange, IconSearch } from '../components/ui/Icons';
import { SuggestInput } from '../components/ui/SuggestInput';
import type { AcademicUnit } from '../types';

function UnitDrawer({
  unit,
  onClose,
}: {
  unit: AcademicUnit;
  onClose: () => void;
}) {
  const navigate = useNavigate();
  const { setSelectedOfferingId } = useApp();
  const { error: notifyError, success } = useFeedback();
  const [busy, setBusy] = useState(false);
  const unallocated = unit.status.toLowerCase().includes('unallocat');

  const openAllocate = async () => {
    setBusy(true);
    try {
      const offering = await courseOfferingService.ensureForUnit({
        id: unit.id,
        name: unit.name,
        sourceDepartmentId: unit.sourceDepartmentId,
        requiredExpertise: unit.requiredExpertise,
      });
      setSelectedOfferingId(offering.id);
      onClose();
      success(`Opened allocate-by-context for ${unit.code}`);
      navigate('/allocate-context');
    } catch (e) {
      notifyError(e instanceof Error ? e.message : 'Could not open allocate-by-context');
    } finally {
      setBusy(false);
    }
  };

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
        {unallocated && (
          <p className="field-hint" style={{ marginTop: 14 }}>
            Allocate by context ranks lecturers using expertise and current workload.
          </p>
        )}
      </div>
      <div className="drawer-foot">
        <Button onClick={onClose}>Close</Button>
        <Button
          variant="primary"
          style={{ marginLeft: 'auto' }}
          disabled={busy}
          onClick={() => void openAllocate()}
        >
          {busy ? 'Opening…' : unallocated ? 'Allocate by context' : 'Find candidates'}
        </Button>
      </div>
    </>
  );
}

export function UnitsPage() {
  const { openDrawer, closeDrawer } = useApp();
  const { error: notifyError, success } = useFeedback();
  const [searchParams] = useSearchParams();
  const statusParam = searchParams.get('status') || '';
  const [tick, setTick] = useState(0);
  const { data: units, fromApi } = useAsyncData(() => unitService.list(), UNITS, [tick]);
  const { data: orgs } = useAsyncData(() => organizationService.list(), [], []);
  const depts = orgs.filter((o) => o.type === 'Department');
  const [showAdd, setShowAdd] = useState(false);
  const [busy, setBusy] = useState(false);
  const [query, setQuery] = useState('');
  const [deptFilter, setDeptFilter] = useState('');
  const [statusFilter, setStatusFilter] = useState(statusParam);
  const [form, setForm] = useState({ code: '', name: '', sourceDepartment: '', contactHours: '3', studentCount: '100', requiredExpertise: '' });

  const save = async () => {
    if (!form.sourceDepartment.trim()) {
      notifyError('Enter a source department');
      return;
    }
    setBusy(true);
    try {
      await unitService.create({
        code: form.code,
        name: form.name,
        sourceDepartment: form.sourceDepartment.trim(),
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
    const deptQ = deptFilter.trim().toLowerCase();
    const matchesDept = !deptQ || u.sourceDepartment.toLowerCase().includes(deptQ);
    const statusQ = statusFilter.trim().toLowerCase();
    const matchesStatus = !statusQ || u.status.toLowerCase().includes(statusQ.toLowerCase());
    return matchesQuery && matchesDept && matchesStatus;
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
        <SuggestInput
          id="unit-dept-filter"
          value={deptFilter}
          onChange={setDeptFilter}
          options={depts.map((d) => ({ value: d.id, label: d.name }))}
          placeholder="Filter by department…"
          hint=""
        />
        <SuggestInput
          id="unit-status-filter"
          value={statusFilter}
          onChange={setStatusFilter}
          options={[
            { value: 'Unallocated', label: 'Unallocated' },
            { value: 'Pending', label: 'Pending' },
            { value: 'Allocated', label: 'Allocated' },
          ]}
          placeholder="Filter by status…"
          hint=""
        />
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
        <div className="field">
          <label>Source department</label>
          <SuggestInput
            id="unit-source-dept"
            value={form.sourceDepartment}
            onChange={(v) => setForm({ ...form, sourceDepartment: v })}
            options={depts.map((d) => ({ value: d.id, label: d.name }))}
            placeholder="Type department name…"
          />
        </div>
        <div className="field"><label>Contact hours</label><input value={form.contactHours} onChange={(e) => setForm({ ...form, contactHours: e.target.value })} /></div>
        <div className="field"><label>Students</label><input value={form.studentCount} onChange={(e) => setForm({ ...form, studentCount: e.target.value })} /></div>
        <div className="field"><label>Required expertise (comma-separated)</label><input value={form.requiredExpertise} onChange={(e) => setForm({ ...form, requiredExpertise: e.target.value })} placeholder="Linear Algebra, Mathematics" /></div>
      </Modal>
    </>
  );
}
