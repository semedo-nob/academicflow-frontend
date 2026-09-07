import type { ButtonHTMLAttributes, ReactNode } from 'react';

type Variant = 'default' | 'primary' | 'ghost' | 'danger-text' | 'danger';
type Size = 'md' | 'sm';

interface ButtonProps extends ButtonHTMLAttributes<HTMLButtonElement> {
  variant?: Variant;
  size?: Size;
  fullWidth?: boolean;
  children: ReactNode;
}

export function Button({
  variant = 'default',
  size = 'md',
  fullWidth,
  className = '',
  children,
  ...rest
}: ButtonProps) {
  const classes = [
    'btn',
    variant === 'primary' && 'btn-primary',
    variant === 'ghost' && 'btn-ghost',
    variant === 'danger-text' && 'btn-danger-text',
    variant === 'danger' && 'btn-danger',
    size === 'sm' && 'btn-sm',
    fullWidth && 'btn-full',
    className,
  ]
    .filter(Boolean)
    .join(' ');

  return (
    <button type="button" className={classes} {...rest}>
      {children}
    </button>
  );
}
