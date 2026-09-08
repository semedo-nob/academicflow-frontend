import type { Membership } from '../types';

const SETUP_DONE_KEY = 'af_setup_done';
const SETUP_SKIP_KEY = 'af_setup_skipped';

function tenantKey(suffix: string): string {
  const tenant = localStorage.getItem('af_tenant') || 'default';
  return `${suffix}:${tenant}`;
}

export function isSetupComplete(): boolean {
  return localStorage.getItem(tenantKey(SETUP_DONE_KEY)) === '1';
}

export function isSetupSkipped(): boolean {
  return localStorage.getItem(tenantKey(SETUP_SKIP_KEY)) === '1';
}

export function markSetupComplete() {
  localStorage.setItem(tenantKey(SETUP_DONE_KEY), '1');
  localStorage.removeItem(tenantKey(SETUP_SKIP_KEY));
}

export function markSetupSkipped() {
  localStorage.setItem(tenantKey(SETUP_SKIP_KEY), '1');
}

export function clearSetupFlags() {
  localStorage.removeItem(tenantKey(SETUP_DONE_KEY));
  localStorage.removeItem(tenantKey(SETUP_SKIP_KEY));
}

type OrgLike = { type: string; id: string };
type MembershipLike = Pick<Membership, 'organizationNodeId' | 'role'> & { role: string };

/** True when institution admin should be guided through department + chair setup. */
export function needsInstitutionSetup(
  orgs: OrgLike[],
  memberships: MembershipLike[],
): boolean {
  if (isSetupComplete() || isSetupSkipped()) return false;
  const depts = orgs.filter((o) => o.type === 'Department');
  if (depts.length === 0) return true;
  const chairDepts = new Set(
    memberships
      .filter((m) => m.role.toUpperCase() === 'DEPARTMENT_CHAIR')
      .map((m) => m.organizationNodeId),
  );
  return chairDepts.size === 0;
}
