import type { ReactNode } from 'react';
import { IconClose } from './Icons';

interface DrawerProps {
  open: boolean;
  onClose: () => void;
  children: ReactNode;
}

export function Drawer({ open, onClose, children }: DrawerProps) {
  return (
    <>
      <div className={`overlay ${open ? 'active' : ''}`} onClick={onClose} />
      <div className={`drawer ${open ? 'active' : ''}`}>{children}</div>
    </>
  );
}

export function DrawerCloseButton({ onClose }: { onClose: () => void }) {
  return (
    <button type="button" className="drawer-close" onClick={onClose} aria-label="Close">
      <IconClose />
    </button>
  );
}

interface TabsProps {
  tabs: string[];
  active: number;
  onChange: (index: number) => void;
}

export function Tabs({ tabs, active, onChange }: TabsProps) {
  return (
    <div className="tabs">
      {tabs.map((tab, i) => (
        <div
          key={tab}
          className={`tab ${i === active ? 'active' : ''}`}
          onClick={() => onChange(i)}
          role="tab"
          tabIndex={0}
          onKeyDown={(e) => {
            if (e.key === 'Enter' || e.key === ' ') onChange(i);
          }}
        >
          {tab}
        </div>
      ))}
    </div>
  );
}

export function PageHead({
  title,
  subtitle,
  eyebrow,
  actions,
}: {
  title: string;
  subtitle?: string;
  eyebrow?: ReactNode;
  actions?: ReactNode;
}) {
  return (
    <div className="page-head">
      <div>
        {eyebrow && <p className="section-sub" style={{ marginBottom: 5 }}>{eyebrow}</p>}
        <h1 className="page-title">{title}</h1>
        {subtitle && <p className="page-sub">{subtitle}</p>}
      </div>
      {actions}
    </div>
  );
}

export function Card({ children, className = '', pad = true }: { children: ReactNode; className?: string; pad?: boolean }) {
  return <div className={`card ${pad ? 'card-pad' : ''} ${className}`.trim()}>{children}</div>;
}

export function EmptyState({ title, subtitle, action }: { title: string; subtitle?: string; action?: ReactNode }) {
  return (
    <div className="empty-state">
      <div className="empty-state-title">{title}</div>
      {subtitle && <p className="empty-state-sub">{subtitle}</p>}
      {action}
    </div>
  );
}
