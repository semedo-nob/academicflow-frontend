import { useState } from 'react';
import { LECTURERS } from '../data/mockData';
import { useApp } from '../context/AppContext';
import { useFeedback } from '../context/FeedbackContext';
import { useAsyncData } from '../hooks/useAsyncData';
import { lecturerService, organizationService } from '../services';
import { Avatar, Badge } from '../components/ui/Badge';
import { Button } from '../components/ui/Button';
import { DrawerCloseButton, PageHead } from '../components/ui/Drawer';
import { FormActions, Modal } from '../components/ui/Modal';
import { IconSearch } from '../components/ui/Icons';
import type { Lecturer } from '../types';

function LecturerDrawer({ lecturer, onClose }: { lecturer: Lecturer; onClose: () => void }) {
  const levelClass: Record<string, string> = {
    Excellent: 'excellent',
    Strong: 'strong',
    Moderate: 'moderate',
  };
  return (
    <>
      <div className="drawer-head">
        <div>
          <div style={{ display: 'flex', alignItems: 'center', gap: 10, marginBottom: 6 }}>
            <div className="avatar-sm" style={{ width: 34, height: 34, fontSize: 13 }}>
              {lecturer.initials}
            </div>
            <div>
              <div style={{ fontWeight: 700, fontSize: 16 }}>{lecturer.name}</div>
              <div className="cell-sub mono">
                {lecturer.staffNumber} · {lecturer.department}
              </div>
            </div>
          </div>
          <Badge status={lecturer.status} />
        </div>
        <DrawerCloseButton onClose={onClose} />
      </div>
      <div className="drawer-body">
        <div className="stat-grid-3">
          <div className="stat-box">
            <div className="stat-box-value">{lecturer.currentWorkload} hrs</div>
            <div className="stat-box-label">Current workload</div>
          </div>
          <div className="stat-box">
            <div className="stat-box-value">{lecturer.maximumWorkload} hrs</div>
            <div className="stat-box-label">Maximum</div>
          </div>
          <div className="stat-box">
            <div className="stat-box-value">{lecturer.expertiseDetail.length}</div>
            <div className="stat-box-label">Expertise areas</div>
          </div>
        </div>
        <div className="section-title" style={{ marginTop: 18 }}>
          Qualification
        </div>
        <p className="section-sub" style={{ marginBottom: 14 }}>
          {lecturer.qualifications}
        </p>
        <div className="section-title">Expertise</div>
        {lecturer.expertiseDetail.map((e) => (
          <div className="expertise-row" key={e.subject}>
            <span className="expertise-name">{e.subject}</span>
            <span className={`expertise-level ${levelClass[e.level] || ''}`}>{e.level}</span>
          </div>
        ))}
        <div className="section-title" style={{ marginTop: 18 }}>
          Availability
        </div>
        <p className="section-sub">{lecturer.availability}</p>
      </div>
      <div className="drawer-foot">
        <Button onClick={onClose}>Close</Button>
      </div>
    </>
  );
}

export function LecturersPage() {
  const { openDrawer, closeDrawer } = useApp();
  const { error: notifyError, success } = useFeedback();
  const [tick, setTick] = useState(0);
  const { data: lecturers, fromApi } = useAsyncData(() => lecturerService.list(), LECTURERS, [tick]);
  const { data: orgs } = useAsyncData(() => organizationService.list(), [], []);
  const depts = orgs.filter((o) => o.type === 'Department');
  const [showAdd, setShowAdd] = useState(false);
  const [busy, setBusy] = useState(false);
  const [query, setQuery] = useState('');
  const [deptFilter, setDeptFilter] = useState('');
  const [form, setForm] = useState({
    staffNumber: '',
    name: '',
    email: '',
    departmentId: '',
    qualifications: '',
    maximumWorkload: '12',
    availability: 'Monday–Friday',
    expertiseSubject: '',
    expertiseLevel: 'Strong',
  });

  const open = (id: string) => {
    const l = lecturers.find((x) => x.id === id)!;
    openDrawer(<LecturerDrawer lecturer={l} onClose={closeDrawer} />);
  };

  const save = async () => {
    setBusy(true);
    try {
      await lecturerService.create({
        staffNumber: form.staffNumber,
        name: form.name,
        email: form.email,
        departmentId: form.departmentId || depts[0]?.id,
        qualifications: form.qualifications,
        maximumWorkload: Number(form.maximumWorkload) || 12,
        availability: form.availability,
        expertise: form.expertiseSubject
          ? [{ subject: form.expertiseSubject, level: form.expertiseLevel }]
          : [],
      });
      setShowAdd(false);
      setTick((t) => t + 1);
      success('Lecturer created');
    } catch (e) {
      notifyError(e instanceof Error ? e.message : 'Failed to create lecturer');
    } finally {
      setBusy(false);
    }
  };

  const filtered = lecturers.filter((l) => {
    const q = query.trim().toLowerCase();
    const matchesQuery =
      !q ||
      l.name.toLowerCase().includes(q) ||
      l.staffNumber.toLowerCase().includes(q) ||
      l.email.toLowerCase().includes(q) ||
      l.expertise.some((e) => e.toLowerCase().includes(q));
    const matchesDept = !deptFilter || l.departmentId === deptFilter;
    return matchesQuery && matchesDept;
  });

  return (
    <>
      <PageHead
        title="Lecturers"
        subtitle={fromApi ? 'Loaded from PostgreSQL via REST API.' : 'Showing local fallback data.'}
        actions={
          <div className="btn-row">
            <Button onClick={() => setTick((t) => t + 1)}>Refresh</Button>
            <Button variant="primary" onClick={() => setShowAdd(true)}>
              + Add Lecturer
            </Button>
          </div>
        }
      />
      <div className="filters-bar">
        <div className="search-inline">
          <IconSearch />
          <input value={query} onChange={(e) => setQuery(e.target.value)} placeholder="Search lecturers…" />
        </div>
        <select
          className="filter-chip"
          value={deptFilter}
          onChange={(e) => setDeptFilter(e.target.value)}
          style={{ appearance: 'auto' }}
        >
          <option value="">All departments</option>
          {depts.map((d) => (
            <option key={d.id} value={d.id}>
              {d.name}
            </option>
          ))}
        </select>
      </div>
      <div className="table-wrap">
        <table>
          <thead>
            <tr>
              <th>Staff no.</th>
              <th>Lecturer</th>
              <th>Department</th>
              <th>Expertise</th>
              <th>Current load</th>
              <th>Max load</th>
              <th>Availability</th>
              <th>Status</th>
            </tr>
          </thead>
          <tbody>
            {filtered.map((l) => (
              <tr key={l.id} onClick={() => open(l.id)}>
                <td className="mono">{l.staffNumber}</td>
                <td>
                  <div className="name-cell">
                    <Avatar initials={l.initials} />
                    <div>
                      <div className="cell-primary">{l.name}</div>
                      <div className="cell-sub">{l.qualifications.split(',')[0]}</div>
                    </div>
                  </div>
                </td>
                <td>{l.department}</td>
                <td>{l.expertise.join(', ')}</td>
                <td>
                  <span className="progress-text">
                    {l.currentWorkload}/{l.maximumWorkload} hrs
                  </span>
                </td>
                <td>{l.maximumWorkload} hrs</td>
                <td>
                  <Badge status={l.availabilityLabel} />
                </td>
                <td>
                  <Badge status={l.status} />
                </td>
              </tr>
            ))}
          </tbody>
        </table>
      </div>

      <Modal
        open={showAdd}
        title="Add lecturer"
        onClose={() => setShowAdd(false)}
        footer={<FormActions onCancel={() => setShowAdd(false)} onSubmit={save} busy={busy} submitLabel="Create lecturer" />}
      >
        <div className="field">
          <label>Staff number</label>
          <input value={form.staffNumber} onChange={(e) => setForm({ ...form, staffNumber: e.target.value })} />
        </div>
        <div className="field">
          <label>Full name</label>
          <input value={form.name} onChange={(e) => setForm({ ...form, name: e.target.value })} />
        </div>
        <div className="field">
          <label>Email</label>
          <input value={form.email} onChange={(e) => setForm({ ...form, email: e.target.value })} />
        </div>
        <div className="field">
          <label>Department</label>
          <select value={form.departmentId} onChange={(e) => setForm({ ...form, departmentId: e.target.value })}>
            <option value="">Select department</option>
            {depts.map((d) => (
              <option key={d.id} value={d.id}>
                {d.name}
              </option>
            ))}
          </select>
        </div>
        <div className="field">
          <label>Qualifications</label>
          <input value={form.qualifications} onChange={(e) => setForm({ ...form, qualifications: e.target.value })} />
        </div>
        <div className="field">
          <label>Max workload (hrs)</label>
          <input value={form.maximumWorkload} onChange={(e) => setForm({ ...form, maximumWorkload: e.target.value })} />
        </div>
        <div className="field">
          <label>Availability</label>
          <input value={form.availability} onChange={(e) => setForm({ ...form, availability: e.target.value })} />
        </div>
        <div className="field">
          <label>Primary expertise</label>
          <input
            value={form.expertiseSubject}
            onChange={(e) => setForm({ ...form, expertiseSubject: e.target.value })}
            placeholder="e.g. Linear Algebra"
          />
        </div>
        <div className="field">
          <label>Expertise level</label>
          <select value={form.expertiseLevel} onChange={(e) => setForm({ ...form, expertiseLevel: e.target.value })}>
            <option>Excellent</option>
            <option>Strong</option>
            <option>Moderate</option>
            <option>Basic</option>
          </select>
        </div>
      </Modal>
    </>
  );
}
