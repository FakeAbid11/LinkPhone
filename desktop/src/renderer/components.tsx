import {
  type ButtonHTMLAttributes,
  type InputHTMLAttributes,
  type ReactNode,
  type SelectHTMLAttributes,
  type TextareaHTMLAttributes,
  useEffect,
  useId,
  useRef,
} from 'react';
import logoUrl from './assets/logo.png';
import { ICONS, type IconName } from './icons';

export type { IconName };

export function Icon({
  name,
  size = 18,
  className,
}: {
  name: IconName;
  size?: number;
  className?: string;
}) {
  return (
    <svg
      width={size}
      height={size}
      viewBox="0 -960 960 960"
      fill="currentColor"
      aria-hidden="true"
      focusable="false"
      className={`icon ${className ?? ''}`}
    >
      <path d={ICONS[name]} />
    </svg>
  );
}

export function Logo({ size = 34 }: { size?: number }) {
  return (
    <img
      src={logoUrl}
      width={size}
      height={size}
      alt=""
      aria-hidden="true"
      draggable={false}
      className="logo-mark"
      style={{ borderRadius: Math.round(size * 0.24) }}
    />
  );
}

interface CardProps {
  title?: string;
  subtitle?: string;
  icon?: IconName;
  actions?: ReactNode;
  children: ReactNode;
  className?: string;
  id?: string;
}

export function Card({ title, subtitle, icon, actions, children, className, id }: CardProps) {
  return (
    <section className={`card ${className ?? ''}`} id={id}>
      {(title || actions) && (
        <header className="card-head">
          <div className="card-head-copy">
            {icon && (
              <span className="card-head-icon">
                <Icon name={icon} size={17} />
              </span>
            )}
            <div>
              {title && <h2>{title}</h2>}
              {subtitle && <p>{subtitle}</p>}
            </div>
          </div>
          {actions && <div className="card-actions">{actions}</div>}
        </header>
      )}
      {children}
    </section>
  );
}

type ButtonVariant = 'primary' | 'secondary' | 'ghost' | 'danger';

interface ButtonProps extends ButtonHTMLAttributes<HTMLButtonElement> {
  variant?: ButtonVariant;
  small?: boolean;
  loading?: boolean;
  icon?: IconName;
}

export function Button({
  variant = 'secondary',
  small = false,
  loading = false,
  icon,
  className,
  type = 'button',
  disabled,
  children,
  ...props
}: ButtonProps) {
  return (
    <button
      type={type}
      className={`button button-${variant} ${small ? 'button-small' : ''} ${className ?? ''}`}
      disabled={disabled || loading}
      aria-busy={loading || undefined}
      {...props}
    >
      {loading ? (
        <span className="spinner spinner-inline" aria-hidden="true" />
      ) : (
        icon && <Icon name={icon} size={small ? 15 : 16} />
      )}
      {children}
    </button>
  );
}

export function Badge({
  children,
  tone = 'neutral',
}: {
  children: ReactNode;
  tone?: 'neutral' | 'good' | 'warn' | 'bad' | 'accent';
}) {
  return <span className={`badge badge-${tone}`}>{children}</span>;
}

export function Toggle({
  checked,
  onChange,
  label,
  description,
  disabled = false,
}: {
  checked: boolean;
  onChange: (value: boolean) => void;
  label: string;
  description?: string;
  disabled?: boolean;
}) {
  const id = useId();
  return (
    <div className={`toggle-row ${disabled ? 'is-disabled' : ''}`}>
      <span className="toggle-copy">
        <span className="toggle-label" id={id}>
          {label}
        </span>
        {description && <span className="toggle-description">{description}</span>}
      </span>
      <button
        type="button"
        role="switch"
        aria-checked={checked}
        aria-labelledby={id}
        disabled={disabled}
        className={`switch ${checked ? 'is-on' : ''}`}
        onClick={() => onChange(!checked)}
      >
        <span />
      </button>
    </div>
  );
}

interface FieldProps {
  id: string;
  label: string;
  hint?: string;
  error?: string;
  children: ReactNode;
  className?: string;
}

export function Field({ id, label, hint, error, children, className }: FieldProps) {
  return (
    <div className={`field ${error ? 'has-error' : ''} ${className ?? ''}`}>
      <label htmlFor={id}>{label}</label>
      {children}
      {error ? (
        <span className="field-error" id={`${id}-error`}>
          {error}
        </span>
      ) : hint ? (
        <span className="field-hint" id={`${id}-hint`}>
          {hint}
        </span>
      ) : null}
    </div>
  );
}

export function TextInput({
  invalid,
  className,
  ...props
}: InputHTMLAttributes<HTMLInputElement> & { invalid?: boolean }) {
  return (
    <input
      className={`text-input ${className ?? ''}`}
      aria-invalid={invalid || undefined}
      {...props}
    />
  );
}

export function TextArea({
  invalid,
  className,
  ...props
}: TextareaHTMLAttributes<HTMLTextAreaElement> & { invalid?: boolean }) {
  return (
    <textarea
      className={`text-area ${className ?? ''}`}
      aria-invalid={invalid || undefined}
      {...props}
    />
  );
}

export function Select({ className, ...props }: SelectHTMLAttributes<HTMLSelectElement>) {
  return <select className={`select ${className ?? ''}`} {...props} />;
}

export function EmptyState({
  title,
  body,
  action,
  icon,
}: {
  title: string;
  body: string;
  action?: ReactNode;
  icon?: IconName;
}) {
  return (
    <div className="empty">
      <div className="empty-mark">
        {icon ? (
          <span className="empty-icon">
            <Icon name={icon} size={24} />
          </span>
        ) : (
          <Logo size={40} />
        )}
      </div>
      <h3>{title}</h3>
      <p>{body}</p>
      {action && <div className="empty-action">{action}</div>}
    </div>
  );
}

export function Notice({
  tone = 'neutral',
  title,
  children,
}: {
  tone?: 'neutral' | 'warn' | 'bad' | 'good';
  title?: string;
  children: ReactNode;
}) {
  const icon: IconName =
    tone === 'bad' || tone === 'warn' ? 'alert' : tone === 'good' ? 'check' : 'shield';
  return (
    <div className={`notice notice-${tone}`}>
      <span className="notice-icon">
        <Icon name={icon} size={16} />
      </span>
      <div className="notice-copy">
        {title && <strong>{title}</strong>}
        <span>{children}</span>
      </div>
    </div>
  );
}

export function Spinner({ label }: { label?: string }) {
  return (
    <span className="spinner-wrap" role="status">
      <span className="spinner" aria-hidden="true" />
      {label && <span>{label}</span>}
    </span>
  );
}

export function PageHeader({
  title,
  subtitle,
  actions,
}: {
  title: string;
  subtitle?: string;
  actions?: ReactNode;
}) {
  return (
    <header className="page-head">
      <div>
        <h1>{title}</h1>
        {subtitle && <p>{subtitle}</p>}
      </div>
      {actions && <div className="page-actions">{actions}</div>}
    </header>
  );
}

export function Modal({
  open,
  title,
  children,
  footer,
  onClose,
  wide = false,
}: {
  open: boolean;
  title: string;
  children: ReactNode;
  footer?: ReactNode;
  onClose?: () => void;
  wide?: boolean;
}) {
  const dialogRef = useRef<HTMLDivElement>(null);
  const backdropRef = useRef<HTMLDivElement>(null);
  const onCloseRef = useRef(onClose);
  const titleId = useId();
  // Callers pass inline arrow functions, so an effect keyed on `onClose` would
  // re-run on every parent render and its cleanup would pull focus back to the
  // trigger in the middle of typing.
  onCloseRef.current = onClose;

  useEffect(() => {
    if (!open) return;
    const previous = document.activeElement instanceof HTMLElement ? document.activeElement : null;
    const node = dialogRef.current;
    if (!node) return;

    const focusables = () =>
      Array.from(
        node.querySelectorAll<HTMLElement>(
          'a[href], button:not([disabled]), textarea:not([disabled]), input:not([disabled]), select:not([disabled]), [tabindex]:not([tabindex="-1"])',
        ),
      ).filter((element) => element.offsetParent !== null);

    // Move focus before hiding anything: Chromium warns when an aria-hidden
    // subtree still contains the focused element.
    if (!node.contains(document.activeElement)) {
      const preferred = node.querySelector<HTMLElement>('[data-autofocus]') ?? focusables()[0];
      preferred?.focus();
    }

    // aria-modal is advisory. Hide the dialog's siblings at every level up to
    // <body> so the app shell leaves the accessibility tree and the tab order.
    // Only siblings are hidden, never an ancestor of the dialog itself.
    const hidden: Array<{ element: HTMLElement; ariaHidden: string | null; inert: boolean }> = [];
    let level: HTMLElement | null = backdropRef.current;
    while (level && level !== document.body) {
      const parent: HTMLElement | null = level.parentElement;
      if (!parent) break;
      for (const sibling of Array.from(parent.children)) {
        if (sibling === level || !(sibling instanceof HTMLElement)) continue;
        hidden.push({
          element: sibling,
          ariaHidden: sibling.getAttribute('aria-hidden'),
          inert: sibling.hasAttribute('inert'),
        });
        sibling.setAttribute('aria-hidden', 'true');
        // React 18 drops an `inert` prop, so it is set as an attribute.
        sibling.setAttribute('inert', '');
      }
      level = parent;
    }

    const onKey = (event: KeyboardEvent) => {
      const close = onCloseRef.current;
      if (event.key === 'Escape' && close) {
        event.stopPropagation();
        close();
        return;
      }
      if (event.key !== 'Tab') return;
      const items = focusables();
      if (items.length === 0) {
        event.preventDefault();
        return;
      }
      const first = items[0];
      const last = items[items.length - 1];
      if (event.shiftKey && document.activeElement === first) {
        event.preventDefault();
        last.focus();
      } else if (!event.shiftKey && document.activeElement === last) {
        event.preventDefault();
        first.focus();
      }
    };

    document.addEventListener('keydown', onKey);
    return () => {
      document.removeEventListener('keydown', onKey);
      for (const { element, ariaHidden, inert } of hidden) {
        if (ariaHidden === null) element.removeAttribute('aria-hidden');
        else element.setAttribute('aria-hidden', ariaHidden);
        if (!inert) element.removeAttribute('inert');
      }
      previous?.focus?.();
    };
  }, [open]);

  if (!open) return null;
  return (
    <div className="modal-backdrop" ref={backdropRef} role="presentation" onMouseDown={onClose}>
      <div
        ref={dialogRef}
        className={`modal ${wide ? 'modal-wide' : ''}`}
        role="dialog"
        aria-modal="true"
        aria-labelledby={titleId}
        tabIndex={-1}
        onMouseDown={(event) => event.stopPropagation()}
      >
        <header className="modal-head">
          <h2 id={titleId}>{title}</h2>
          {onClose && (
            <button type="button" className="icon-button" onClick={onClose} aria-label="Close">
              <Icon name="x" size={17} />
            </button>
          )}
        </header>
        <div className="modal-body">{children}</div>
        {footer && <footer className="modal-foot">{footer}</footer>}
      </div>
    </div>
  );
}

export interface ToastItem {
  id: number;
  kind: 'info' | 'error' | 'success';
  message: string;
}

export function ToastStack({
  toasts,
  onDismiss,
}: {
  toasts: ToastItem[];
  onDismiss: (id: number) => void;
}) {
  return (
    <div className="toast-stack" role="status" aria-live="polite">
      {toasts.map((toast) => (
        <div key={toast.id} className={`toast toast-${toast.kind}`}>
          <span className="toast-icon">
            <Icon
              name={toast.kind === 'error' ? 'alert' : toast.kind === 'success' ? 'check' : 'shield'}
              size={16}
            />
          </span>
          <span>{toast.message}</span>
          <button
            type="button"
            className="icon-button"
            onClick={() => onDismiss(toast.id)}
            aria-label="Dismiss notification"
          >
            <Icon name="x" size={15} />
          </button>
        </div>
      ))}
    </div>
  );
}
