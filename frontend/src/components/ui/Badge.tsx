import type { CSSProperties } from 'react';

const STATUS_MAP: Record<string, string> = {
  Unallocated: 'neutral',
  Draft: 'neutral',
  Pending: 'warning',
  Searching: 'info',
  Recommended: 'info',
  'Awaiting Response': 'warning',
  'Awaiting Mathematics Response': 'warning',
  Assigned: 'info',
  Conflict: 'danger',
  'Awaiting Approval': 'warning',
  'Pending Approval': 'warning',
  Approved: 'success',
  Published: 'success',
  Rejected: 'danger',
  Cancelled: 'neutral',
  Matched: 'success',
  Completed: 'success',
  Active: 'success',
  Limited: 'warning',
  'At limit': 'danger',
  Available: 'success',
  Overloaded: 'danger',
  'Near limit': 'warning',
  Underloaded: 'neutral',
  Optimal: 'success',
  High: 'danger',
  "Allocation cycle: In progress": 'info',
};

interface BadgeProps {
  status: string;
  className?: string;
  style?: CSSProperties;
}

export function Badge({ status, className = '', style }: BadgeProps) {
  const tone = STATUS_MAP[status] || 'neutral';
  return (
    <span className={`badge badge-${tone} ${className}`.trim()} style={style}>
      {status}
    </span>
  );
}

export function initialsAvatar(name: string): string {
  const parts = name.replace(/^(Dr\.|Prof\.|Mr\.|Ms\.)\s*/, '').split(' ');
  return ((parts[0]?.[0] || '') + (parts[1]?.[0] || '')).toUpperCase();
}

export function Avatar({ initials, size = 'sm' }: { initials: string; size?: 'sm' | 'md' }) {
  if (size === 'md') {
    return <div className="avatar">{initials}</div>;
  }
  return <div className="avatar-sm">{initials}</div>;
}
