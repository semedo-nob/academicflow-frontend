import { useState } from 'react';
import { useApp } from '../context/AppContext';
import { useFeedback } from '../context/FeedbackContext';
import { roleLabel } from '../lib/access';
import { userService } from '../services';
import { Avatar } from '../components/ui/Badge';
import { Button } from '../components/ui/Button';
import { Card, PageHead } from '../components/ui/Drawer';

export function ProfilePage() {
  const { user, logout } = useApp();
  const { error: notifyError, success } = useFeedback();
  const [name, setName] = useState(user.name);
  const [busy, setBusy] = useState(false);

  const save = async () => {
    if (!name.trim()) {
      notifyError('Name is required');
      return;
    }
    setBusy(true);
    try {
      if (user.id && user.id !== 'u-local') {
        await userService.update(user.id, { name: name.trim() });
      }
      const raw = localStorage.getItem('af_user');
      if (raw) {
        const parsed = JSON.parse(raw) as Record<string, unknown>;
        parsed.name = name.trim();
        localStorage.setItem('af_user', JSON.stringify(parsed));
      }
      success('Profile updated');
      // Keep UI in sync without full reload where possible
      window.dispatchEvent(new Event('af-user-updated'));
    } catch (e) {
      notifyError(e instanceof Error ? e.message : 'Could not update profile');
    } finally {
      setBusy(false);
    }
  };

  return (
    <>
      <PageHead
        title="My profile"
        subtitle="Manage your AcademicFlow account details."
      />
      <div className="split" style={{ maxWidth: 880 }}>
        <Card>
          <div style={{ display: 'flex', gap: 16, alignItems: 'center', marginBottom: 20 }}>
            <Avatar initials={user.initials} size="md" />
            <div>
              <div style={{ fontWeight: 750, fontSize: 16 }}>{user.name}</div>
              <div className="cell-sub">{user.email}</div>
            </div>
          </div>
          <div className="field">
            <label>Display name</label>
            <input value={name} onChange={(e) => setName(e.target.value)} />
          </div>
          <div className="field">
            <label>Email</label>
            <input value={user.email} disabled />
          </div>
          <div className="field">
            <label>Role</label>
            <input value={roleLabel(user.role)} disabled />
          </div>
          <div className="field">
            <label>Context</label>
            <input value={user.departmentName || 'Institution'} disabled />
          </div>
          <div className="btn-row" style={{ marginTop: 8 }}>
            <Button variant="primary" disabled={busy} onClick={() => void save()}>
              {busy ? 'Saving…' : 'Save profile'}
            </Button>
            <Button
              onClick={() => {
                logout();
                window.location.href = '/login';
              }}
            >
              Sign out
            </Button>
          </div>
        </Card>
        <Card>
          <div className="section-title">Profile management</div>
          <p className="section-sub">
            Use search (⌘K) and type <b>profile</b>, <b>account</b>, or <b>me</b> to open this page from anywhere.
          </p>
          <p className="section-sub">
            Institution admins can invite other users from Administration → Users & invitations.
          </p>
          <div className="def-list" style={{ marginTop: 16 }}>
            <div className="def-row">
              <span className="def-label">Role</span>
              <span className="def-value">{roleLabel(user.role)}</span>
            </div>
            <div className="def-row">
              <span className="def-label">Email</span>
              <span className="def-value">{user.email}</span>
            </div>
            <div className="def-row">
              <span className="def-label">Workspace</span>
              <span className="def-value">{user.departmentName || '—'}</span>
            </div>
          </div>
        </Card>
      </div>
    </>
  );
}
