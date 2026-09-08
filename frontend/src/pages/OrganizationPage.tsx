import { useMemo, useState } from 'react';
import { useNavigate } from 'react-router-dom';
import { ORG_NODES } from '../data/mockData';
import { organizationService } from '../services';
import { useAsyncData } from '../hooks/useAsyncData';
import { useApp } from '../context/AppContext';
import { useFeedback } from '../context/FeedbackContext';
import { canManageOrganization, isSuperAdmin } from '../lib/access';
import { Button } from '../components/ui/Button';
import { Tabs, PageHead, Card } from '../components/ui/Drawer';
import { FormActions, Modal } from '../components/ui/Modal';
import { IconBuilding, IconFolder } from '../components/ui/Icons';
import { SuggestInput } from '../components/ui/SuggestInput';
import type { OrganizationNode } from '../types';

function buildTree(nodes: OrganizationNode[]) {
  const byParent = new Map<string | null, OrganizationNode[]>();
  nodes.forEach((n) => {
    const key = n.parentId;
    if (!byParent.has(key)) byParent.set(key, []);
    byParent.get(key)!.push(n);
  });
  return byParent;
}

function TreeNodes({
  parentId,
  byParent,
  selectedId,
  onSelect,
}: {
  parentId: string | null;
  byParent: Map<string | null, OrganizationNode[]>;
  selectedId: string;
  onSelect: (id: string) => void;
}) {
  const children = byParent.get(parentId) || [];
  return (
    <>
      {children.map((n) => {
        const kids = byParent.get(n.id) || [];
        const Icon = n.type === 'Department' || n.type === 'University' ? IconBuilding : IconFolder;
        return (
          <div key={n.id}>
            <div
              className={`tree-node ${selectedId === n.id ? 'selected' : ''}`}
              onClick={() => onSelect(n.id)}
            >
              <Icon />
              <span>{n.name}</span>
              {n.unitCount ? <span className="tree-count">{n.unitCount} units</span> : null}
            </div>
            {kids.length > 0 && (
              <div className="tree-children">
                <TreeNodes parentId={n.id} byParent={byParent} selectedId={selectedId} onSelect={onSelect} />
              </div>
            )}
          </div>
        );
      })}
    </>
  );
}

export function OrganizationPage() {
  const navigate = useNavigate();
  const { user } = useApp();
  const { error: notifyError, success } = useFeedback();
  const canEdit = canManageOrganization(user.role);
  const [tick, setTick] = useState(0);
  const { data: orgNodes, fromApi } = useAsyncData(() => organizationService.list(), ORG_NODES, [tick]);
  const byParent = useMemo(() => buildTree(orgNodes), [orgNodes]);
  const defaultId = orgNodes.find((n) => n.name === 'Computer Science')?.id || orgNodes[0]?.id || '';
  const [selectedId, setSelectedId] = useState(defaultId);
  const [tab, setTab] = useState(0);
  const [showAdd, setShowAdd] = useState(false);
  const [busy, setBusy] = useState(false);
  const [form, setForm] = useState({ name: '', type: 'Department', parent: '' });

  const effectiveId = orgNodes.some((n) => n.id === selectedId) ? selectedId : defaultId;
  const selected = orgNodes.find((n) => n.id === effectiveId) || orgNodes[0];
  const parent = orgNodes.find((n) => n.id === selected?.parentId);
  if (!selected) return null;

  const save = async () => {
    if (!form.name.trim()) {
      notifyError('Enter a name');
      return;
    }
    setBusy(true);
    try {
      await organizationService.create({
        name: form.name.trim(),
        type: form.type.trim() || 'Department',
        parentName: form.parent.trim() || selected.name,
      });
      setShowAdd(false);
      setForm({ name: '', type: 'Department', parent: '' });
      setTick((t) => t + 1);
      success('Organization node created');
    } catch (e) {
      notifyError(e instanceof Error ? e.message : 'Failed');
    } finally {
      setBusy(false);
    }
  };

  return (
    <>
      <PageHead
        title="Organization"
        subtitle={
          isSuperAdmin(user.role)
            ? 'Institution tree — add schools and departments. Create new universities under Administration → Institutions.'
            : fromApi
              ? 'Flexible org tree from the API (OrganizationNode).'
              : 'Local fallback tree.'
        }
        actions={
          <div className="btn-row">
            <Button onClick={() => setTick((t) => t + 1)}>Refresh</Button>
            {canEdit && (
              <Button variant="primary" onClick={() => setShowAdd(true)}>
                + Add node
              </Button>
            )}
          </div>
        }
      />
      <div className="split">
        <Card>
          <div className="tree">
            <TreeNodes parentId={null} byParent={byParent} selectedId={effectiveId} onSelect={setSelectedId} />
          </div>
        </Card>
        <Card>
          <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'flex-start', marginBottom: 4 }}>
            <div>
              <div className="section-title" style={{ fontSize: 17 }}>
                {selected.name}
              </div>
              <p className="section-sub" style={{ marginBottom: 16 }}>
                {selected.type}
                {parent ? ` · Parent: ${parent.name}` : ''}
              </p>
            </div>
            <Button size="sm" disabled={!canEdit}>
              Edit
            </Button>
          </div>
          <Tabs
            tabs={['Overview', 'Lecturers', 'Units', 'Requests', 'Statistics', 'Settings']}
            active={tab}
            onChange={setTab}
          />
          {tab === 0 && (
            <>
              <div className="stat-grid-3">
                <div className="stat-box">
                  <div className="stat-box-value">{selected.lecturerCount ?? 0}</div>
                  <div className="stat-box-label">Lecturers</div>
                </div>
                <div className="stat-box">
                  <div className="stat-box-value">{selected.unitCount ?? 0}</div>
                  <div className="stat-box-label">Academic units</div>
                </div>
                <div className="stat-box">
                  <div className="stat-box-value">8</div>
                  <div className="stat-box-label">Active requests</div>
                </div>
              </div>
              <div className="def-list">
                <div className="def-row">
                  <span className="def-label">Allocated units</span>
                  <span className="def-value">54 / 68</span>
                </div>
                <div className="def-row">
                  <span className="def-label">Cross-department requests sent</span>
                  <span className="def-value">3</span>
                </div>
                <div className="def-row">
                  <span className="def-label">Cross-department requests received</span>
                  <span className="def-value">5</span>
                </div>
                <div className="def-row">
                  <span className="def-label">Chairperson</span>
                  <span className="def-value">Dr. Jane Wanjiku</span>
                </div>
              </div>
            </>
          )}
          {tab === 1 && (
            <>
              <p className="section-sub">42 lecturers belong to this department. Open the Lecturers screen for the full directory.</p>
              <Button size="sm" onClick={() => navigate('/lecturers')}>
                Open Lecturers →
              </Button>
            </>
          )}
          {tab === 2 && (
            <>
              <p className="section-sub">68 academic units are homed in this department, 14 of which remain unallocated this semester.</p>
              <Button size="sm" onClick={() => navigate('/units')}>
                Open Academic Units →
              </Button>
            </>
          )}
          {tab === 3 && (
            <>
              <p className="section-sub">8 teaching requests currently involve this department, either sent or received.</p>
              <Button size="sm" onClick={() => navigate('/requests')}>
                Open Requests →
              </Button>
            </>
          )}
          {tab === 4 && (
            <p className="section-sub">Allocation completion has moved from 61% to 79% over the last three weeks.</p>
          )}
          {tab === 5 && (
            <div className="def-list">
              <div className="def-row">
                <span className="def-label">Cross-department teaching</span>
                <span className="def-value">Allowed</span>
              </div>
              <div className="def-row">
                <span className="def-label">Cross-school teaching</span>
                <span className="def-value">Requires approval</span>
              </div>
              <div className="def-row">
                <span className="def-label">Maximum lecturer workload</span>
                <span className="def-value">12 hrs / week</span>
              </div>
            </div>
          )}
        </Card>
      </div>

      <Modal
        open={showAdd}
        title="Add organization node"
        onClose={() => setShowAdd(false)}
        footer={<FormActions onCancel={() => setShowAdd(false)} onSubmit={save} busy={busy} submitLabel="Create node" />}
      >
        <div className="field">
          <label>Name</label>
          <input value={form.name} onChange={(e) => setForm({ ...form, name: e.target.value })} />
        </div>
        <div className="field">
          <label>Type</label>
          <SuggestInput
            id="org-type"
            value={form.type}
            onChange={(v) => setForm({ ...form, type: v })}
            options={['University', 'School', 'Faculty', 'College', 'Department', 'Division', 'Section'].map((t) => ({
              value: t,
              label: t,
            }))}
            placeholder="Type node type…"
          />
        </div>
        <div className="field">
          <label>Parent</label>
          <SuggestInput
            id="org-parent"
            value={form.parent}
            onChange={(v) => setForm({ ...form, parent: v })}
            options={orgNodes.map((n) => ({ value: n.id, label: `${n.name} (${n.type})` }))}
            placeholder={`Under ${selected.name} — or type another parent`}
          />
        </div>
      </Modal>
    </>
  );
}
