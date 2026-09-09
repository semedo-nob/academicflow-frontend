import { useMemo, useRef, useState } from 'react';
import { useNavigate } from 'react-router-dom';
import { organizationService, userService } from '../services';
import { useAsyncData } from '../hooks/useAsyncData';
import { useApp } from '../context/AppContext';
import { useFeedback } from '../context/FeedbackContext';
import { canManageOrganization, isSuperAdmin, normalizeRole } from '../lib/access';
import { Button } from '../components/ui/Button';
import { Tabs, PageHead, Card } from '../components/ui/Drawer';
import { FormActions, Modal } from '../components/ui/Modal';
import { IconBuilding, IconFolder } from '../components/ui/Icons';
import { SuggestInput } from '../components/ui/SuggestInput';
import type { OrganizationNode } from '../types';

const NODE_TYPES = ['University', 'School', 'Faculty', 'College', 'Department', 'Division', 'Section'];

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
  const { error: notifyError, success, toast, confirm } = useFeedback();
  const canEdit = canManageOrganization(user.role);
  const isAdmin = normalizeRole(user.role) === 'INSTITUTION_ADMIN';
  const [tick, setTick] = useState(0);
  const { data: orgNodes, fromApi, loading: orgLoading } = useAsyncData(
    () => organizationService.list(),
    [],
    [tick],
  );
  const { data: memberships } = useAsyncData(() => userService.memberships(), [], [tick]);
  const { data: invitations } = useAsyncData(
    () => (isAdmin || canEdit ? userService.invitations() : Promise.resolve([])),
    [],
    [tick, isAdmin, canEdit],
  );
  const byParent = useMemo(() => buildTree(orgNodes), [orgNodes]);
  const defaultId = orgNodes.find((n) => n.name === 'Computer Science')?.id || orgNodes[0]?.id || '';
  const [selectedId, setSelectedId] = useState(defaultId);
  const [tab, setTab] = useState(0);
  const [showAdd, setShowAdd] = useState(false);
  const [showEdit, setShowEdit] = useState(false);
  const [showImport, setShowImport] = useState(false);
  const [showNodeMenu, setShowNodeMenu] = useState(false);
  const [busy, setBusy] = useState(false);
  const [form, setForm] = useState({ name: '', type: 'Department', parent: '' });
  const [editForm, setEditForm] = useState({ name: '', type: 'Department', parent: '' });
  const importRef = useRef<HTMLInputElement>(null);

  const effectiveId = orgNodes.some((n) => n.id === selectedId) ? selectedId : defaultId;
  const selected = orgNodes.find((n) => n.id === effectiveId) || orgNodes[0];
  const parent = orgNodes.find((n) => n.id === selected?.parentId);

  const chairForSelected = useMemo(() => {
    if (!selected) return null;
    return memberships.find(
      (m) => m.organizationNodeId === selected.id && m.role.toUpperCase() === 'DEPARTMENT_CHAIR',
    );
  }, [memberships, selected]);

  const pendingChairInvite = useMemo(() => {
    if (!selected) return null;
    return invitations.find(
      (i) =>
        i.status === 'PENDING' &&
        i.role.toUpperCase() === 'DEPARTMENT_CHAIR' &&
        i.organizationNodeId === selected.id,
    );
  }, [invitations, selected]);

  const deptsNeedingChair = useMemo(() => {
    const depts = orgNodes.filter((o) => o.type === 'Department');
    const chairIds = new Set(
      memberships.filter((m) => m.role.toUpperCase() === 'DEPARTMENT_CHAIR').map((m) => m.organizationNodeId),
    );
    const pendingIds = new Set(
      invitations
        .filter((i) => i.status === 'PENDING' && i.role.toUpperCase() === 'DEPARTMENT_CHAIR' && i.organizationNodeId)
        .map((i) => i.organizationNodeId as string),
    );
    return depts.filter((d) => !chairIds.has(d.id) && !pendingIds.has(d.id));
  }, [orgNodes, memberships, invitations]);

  if (!selected) {
    return (
      <>
        <PageHead
          title="Organization"
          subtitle={orgLoading ? 'Loading organization…' : 'No organization nodes yet.'}
        />
        {canEdit && !orgLoading && (
          <div className="btn-row">
            <Button variant="primary" onClick={() => setShowAdd(true)}>
              + Add node
            </Button>
            {isAdmin && (
              <Button onClick={() => navigate('/onboarding')}>Invite chairs</Button>
            )}
          </div>
        )}
      </>
    );
  }

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

  const openEdit = () => {
    setEditForm({
      name: selected.name,
      type: selected.type,
      parent: parent?.name || '',
    });
    setShowNodeMenu(false);
    setShowEdit(true);
  };

  const removeSelected = async (cascade: boolean) => {
    if (!selected) return;
    const ok = await confirm({
      title: cascade ? 'Delete with children' : 'Delete organization node',
      message: cascade
        ? `Delete “${selected.name}” and its empty child nodes? Nodes with lecturers or units cannot be removed.`
        : `Delete “${selected.name}”? Remove children first, or use cascade delete.`,
      confirmLabel: cascade ? 'Delete subtree' : 'Delete',
      danger: true,
    });
    if (!ok) return;
    setBusy(true);
    setShowNodeMenu(false);
    try {
      await organizationService.delete(selected.id, cascade);
      setTick((t) => t + 1);
      success(`${selected.name} deleted`);
    } catch (e) {
      notifyError(e instanceof Error ? e.message : 'Delete failed');
    } finally {
      setBusy(false);
    }
  };

  const saveEdit = async () => {
    if (!editForm.name.trim()) {
      notifyError('Enter a name');
      return;
    }
    setBusy(true);
    try {
      const clearParent = !editForm.parent.trim();
      await organizationService.update(selected.id, {
        name: editForm.name.trim(),
        type: editForm.type.trim() || selected.type,
        parentName: clearParent ? null : editForm.parent.trim(),
        clearParent,
      });
      setShowEdit(false);
      setTick((t) => t + 1);
      success('Organization node updated');
    } catch (e) {
      notifyError(e instanceof Error ? e.message : 'Update failed');
    } finally {
      setBusy(false);
    }
  };

  const runImport = async (file: File | null) => {
    if (!file) return;
    setBusy(true);
    try {
      const res = await organizationService.importCsv(file);
      setShowImport(false);
      setTick((t) => t + 1);
      success(`Org import: ${res.created} created · ${res.updated} updated · ${res.errors} errors`);
      if (res.details?.length) toast(res.details[0], res.errors ? 'warning' : 'info');
    } catch (e) {
      notifyError(e instanceof Error ? e.message : 'Import failed');
    } finally {
      setBusy(false);
      if (importRef.current) importRef.current.value = '';
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
              ? 'Create, edit, or import schools and departments. Invite chairs from Institution setup.'
              : 'Local fallback tree.'
        }
        actions={
          <div className="btn-row">
            <Button onClick={() => setTick((t) => t + 1)}>Refresh</Button>
            {isAdmin && (
              <Button onClick={() => navigate('/onboarding')}>Invite chairs</Button>
            )}
            {canEdit && (
              <>
                <Button onClick={() => setShowImport(true)}>Import CSV</Button>
                <Button variant="primary" onClick={() => setShowAdd(true)}>
                  + Add node
                </Button>
              </>
            )}
          </div>
        }
      />

      {isAdmin && deptsNeedingChair.length > 0 && (
        <div className="setup-banner">
          <p>
            {deptsNeedingChair.length} department{deptsNeedingChair.length === 1 ? '' : 's'} still need a chair
            account ({deptsNeedingChair
              .slice(0, 3)
              .map((d) => d.name)
              .join(', ')}
            {deptsNeedingChair.length > 3 ? '…' : ''}).
          </p>
          <Button variant="primary" onClick={() => navigate('/onboarding')}>
            Invite department chairs
          </Button>
        </div>
      )}

      <div className="split">
        <Card>
          <div className="tree">
            <TreeNodes
              parentId={null}
              byParent={byParent}
              selectedId={effectiveId}
              onSelect={(id) => {
                setSelectedId(id);
                setShowNodeMenu(true);
                setTab(0);
              }}
            />
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
            {canEdit && (
              <div className="onboard-node-actions">
                <Button size="sm" onClick={() => setShowNodeMenu((v) => !v)}>
                  Options
                </Button>
                {showNodeMenu && (
                  <div className="node-menu">
                    <button type="button" onClick={openEdit}>
                      Edit
                    </button>
                    {selected.type === 'Department' && isAdmin && (
                      <button
                        type="button"
                        onClick={() => {
                          setShowNodeMenu(false);
                          navigate('/onboarding');
                        }}
                      >
                        Invite chair
                      </button>
                    )}
                    <button type="button" disabled={busy} onClick={() => void removeSelected(false)}>
                      Delete
                    </button>
                    <button type="button" disabled={busy} onClick={() => void removeSelected(true)}>
                      Delete with empty children
                    </button>
                    <button type="button" onClick={() => setShowNodeMenu(false)}>
                      Cancel
                    </button>
                  </div>
                )}
              </div>
            )}
          </div>
          <Tabs
            tabs={['Overview', 'Lecturers', 'Units', 'Requests', 'Chair']}
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
                  <div className="stat-box-value">
                    {(byParent.get(selected.id) || []).length}
                  </div>
                  <div className="stat-box-label">Child nodes</div>
                </div>
              </div>
              <div className="def-list">
                <div className="def-row">
                  <span className="def-label">Node id</span>
                  <span className="def-value mono">{selected.id.slice(0, 8)}…</span>
                </div>
                <div className="def-row">
                  <span className="def-label">Type</span>
                  <span className="def-value">{selected.type}</span>
                </div>
                <div className="def-row">
                  <span className="def-label">Parent</span>
                  <span className="def-value">{parent?.name || '— Root —'}</span>
                </div>
              </div>
            </>
          )}
          {tab === 1 && (
            <>
              <p className="section-sub">
                {selected.lecturerCount ?? 0} lecturer(s) linked to this node. Open Lecturers for the directory
                {selected.type === 'Department' ? ' (scoped for chairs)' : ''}.
              </p>
              <Button size="sm" onClick={() => navigate('/lecturers')}>
                Open Lecturers →
              </Button>
            </>
          )}
          {tab === 2 && (
            <>
              <p className="section-sub">
                {selected.unitCount ?? 0} academic unit(s) are homed here.
              </p>
              <Button size="sm" onClick={() => navigate('/units')}>
                Open Academic Units →
              </Button>
            </>
          )}
          {tab === 3 && (
            <>
              <p className="section-sub">
                Teaching requests that name this department as requester or preferred source appear under Teaching
                Requests.
              </p>
              <Button size="sm" onClick={() => navigate('/requests')}>
                Open Requests →
              </Button>
            </>
          )}
          {tab === 4 && (
            <>
              {selected.type !== 'Department' ? (
                <p className="section-sub">Chairs are assigned to Department nodes.</p>
              ) : chairForSelected ? (
                <p className="section-sub">
                  Active chair membership is set for this department (role Department Chair).
                </p>
              ) : pendingChairInvite ? (
                <p className="section-sub">
                  Pending invite for <b>{pendingChairInvite.email}</b>. Copy the link from Institution setup or
                  Administration.
                </p>
              ) : (
                <p className="section-sub">No chair assigned yet for this department.</p>
              )}
              {isAdmin && selected.type === 'Department' && (
                <div className="btn-row" style={{ marginTop: 12 }}>
                  <Button variant="primary" onClick={() => navigate('/onboarding')}>
                    Invite / assign chairs
                  </Button>
                  <Button onClick={() => navigate('/admin')}>Open Administration</Button>
                </div>
              )}
            </>
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
            options={NODE_TYPES.map((t) => ({ value: t, label: t }))}
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
            placeholder={selected.name}
          />
        </div>
      </Modal>

      <Modal
        open={showEdit}
        title="Edit organization node"
        onClose={() => setShowEdit(false)}
        footer={
          <FormActions onCancel={() => setShowEdit(false)} onSubmit={saveEdit} busy={busy} submitLabel="Save changes" />
        }
      >
        <div className="field">
          <label>Name</label>
          <input value={editForm.name} onChange={(e) => setEditForm({ ...editForm, name: e.target.value })} />
        </div>
        <div className="field">
          <label>Type</label>
          <SuggestInput
            id="org-edit-type"
            value={editForm.type}
            onChange={(v) => setEditForm({ ...editForm, type: v })}
            options={NODE_TYPES.map((t) => ({ value: t, label: t }))}
            placeholder="Type…"
          />
        </div>
        <div className="field">
          <label>Parent (leave blank for root)</label>
          <SuggestInput
            id="org-edit-parent"
            value={editForm.parent}
            onChange={(v) => setEditForm({ ...editForm, parent: v })}
            options={orgNodes
              .filter((n) => n.id !== selected.id)
              .map((n) => ({ value: n.id, label: `${n.name} (${n.type})` }))}
            placeholder="Parent name or empty for root…"
          />
        </div>
      </Modal>

      <Modal
        open={showImport}
        title="Import organization nodes"
        onClose={() => setShowImport(false)}
        footer={
          <div className="btn-row" style={{ width: '100%', justifyContent: 'flex-end' }}>
            <Button onClick={() => setShowImport(false)}>Cancel</Button>
            <Button variant="primary" disabled={busy} onClick={() => importRef.current?.click()}>
              {busy ? 'Importing…' : 'Choose CSV file'}
            </Button>
          </div>
        }
      >
        <p className="section-sub">
          CSV columns: <span className="mono">name, type, parentName</span> (optional{' '}
          <span className="mono">id</span> to update an existing node). Parents should appear before children when
          creating new rows. Existing name+type matches are updated.
        </p>
        <pre className="mono" style={{ fontSize: 12, padding: 12, background: 'var(--bg)', borderRadius: 8 }}>
{`name,type,parentName
School of Computing,School,University of Nairobi
Computer Science,Department,School of Computing
Mathematics,Department,School of Computing`}
        </pre>
        <input
          ref={importRef}
          type="file"
          accept=".csv,.txt,text/csv,text/plain"
          hidden
          onChange={(e) => void runImport(e.target.files?.[0] || null)}
        />
      </Modal>
    </>
  );
}
