import type { ReactNode } from 'react';
import { Button } from './Button';
import { IconClose } from './Icons';

interface ModalProps {
  open: boolean;
  title: string;
  onClose: () => void;
  children: ReactNode;
  footer?: ReactNode;
  /** Wider dialog for collaboration / multi-column content. */
  wide?: boolean;
}

export function Modal({ open, title, onClose, children, footer, wide }: ModalProps) {
  if (!open) return null;
  return (
    <>
      <div className="overlay active" onClick={onClose} />
      <div
        className="drawer active"
        style={{
          width: wide ? 'min(920px, 94vw)' : 480,
          left: '50%',
          right: 'auto',
          top: '6vh',
          bottom: 'auto',
          maxHeight: '88vh',
          transform: 'translateX(-50%)',
          borderRadius: 14,
        }}
      >
        <div className="drawer-head">
          <div style={{ fontWeight: 700, fontSize: 16 }}>{title}</div>
          <button type="button" className="drawer-close" onClick={onClose} aria-label="Close">
            <IconClose />
          </button>
        </div>
        <div className="drawer-body">{children}</div>
        {footer && <div className="drawer-foot">{footer}</div>}
      </div>
    </>
  );
}

export function FormActions({
  onCancel,
  onSubmit,
  submitLabel = 'Save',
  busy,
}: {
  onCancel: () => void;
  onSubmit: () => void;
  submitLabel?: string;
  busy?: boolean;
}) {
  return (
    <>
      <Button onClick={onCancel}>Cancel</Button>
      <Button variant="primary" style={{ marginLeft: 'auto' }} onClick={onSubmit} disabled={busy}>
        {busy ? 'Saving…' : submitLabel}
      </Button>
    </>
  );
}
