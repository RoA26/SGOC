import { forwardRef, useId, type ButtonHTMLAttributes, type InputHTMLAttributes, type ReactNode, type SelectHTMLAttributes } from "react";
import { Icon, type IconName } from "./Icon";

type Variant = "primary" | "secondary" | "ghost" | "danger";
const VARIANT: Record<Variant, string> = {
  primary: "bg-ink text-on-ink hover:bg-ink-3 disabled:bg-ink/50",
  secondary: "bg-surface text-text border border-line-strong hover:bg-surface-2 disabled:opacity-60",
  ghost: "text-text-2 hover:bg-surface-2 hover:text-text",
  danger: "bg-danger text-white hover:bg-danger/90 disabled:opacity-60",
};

type ButtonProps = ButtonHTMLAttributes<HTMLButtonElement> & { variant?: Variant; icon?: IconName; loading?: boolean };

export function Button({ variant = "primary", icon, loading, children, className = "", disabled, ...rest }: ButtonProps) {
  return (
    <button
      {...rest}
      disabled={disabled || loading}
      aria-busy={loading || undefined}
      className={`inline-flex min-h-10 items-center justify-center gap-2 rounded-b px-4 text-sm font-semibold transition-colors disabled:cursor-not-allowed ${VARIANT[variant]} ${className}`}
    >
      {loading ? <Spinner /> : icon && <Icon name={icon} className="h-4 w-4" />}
      {children}
    </button>
  );
}

export function Spinner({ className = "h-4 w-4" }: { className?: string }) {
  return (
    <svg viewBox="0 0 24 24" className={`animate-spin ${className}`} aria-hidden="true">
      <circle cx="12" cy="12" r="9" fill="none" stroke="currentColor" strokeOpacity=".25" strokeWidth="3" />
      <path d="M21 12a9 9 0 0 0-9-9" fill="none" stroke="currentColor" strokeWidth="3" strokeLinecap="round" />
    </svg>
  );
}

const control =
  "block w-full min-h-10 rounded-b border bg-surface px-3 text-[15px] text-text placeholder:text-text-2/70 transition-colors focus:border-accent-strong focus:outline-none focus:ring-2 focus:ring-accent-strong/20 aria-[invalid=true]:border-danger";

type FieldProps = { label: string; error?: string | undefined; hint?: string | undefined; children: (id: string, describedBy?: string) => ReactNode };

export function Field({ label, error, hint, children }: FieldProps) {
  const id = useId();
  const msgId = `${id}-msg`;
  return (
    <div className="space-y-1.5">
      <label htmlFor={id} className="block text-sm font-semibold text-text">
        {label}
      </label>
      {children(id, error || hint ? msgId : undefined)}
      {error ? (
        <p id={msgId} className="text-sm text-danger">
          {error}
        </p>
      ) : hint ? (
        <p id={msgId} className="text-sm text-text-2">
          {hint}
        </p>
      ) : null}
    </div>
  );
}

export const Input = forwardRef<HTMLInputElement, InputHTMLAttributes<HTMLInputElement> & { invalid?: boolean }>(
  function Input({ invalid, className = "", ...rest }, ref) {
    return <input ref={ref} {...rest} aria-invalid={invalid || undefined} className={`${control} border-line-strong ${className}`} />;
  },
);

export function Select({ invalid, className = "", ...rest }: SelectHTMLAttributes<HTMLSelectElement> & { invalid?: boolean }) {
  return <select {...rest} aria-invalid={invalid || undefined} className={`${control} border-line-strong ${className}`} />;
}

type Tone = "error" | "success" | "info" | "warn";
const TONE: Record<Tone, { box: string; icon: IconName }> = {
  error: { box: "bg-danger-soft text-danger border-danger/25", icon: "alert" },
  success: { box: "bg-ok-soft text-ok border-ok/25", icon: "check" },
  info: { box: "bg-surface-2 text-text border-line", icon: "info" },
  warn: { box: "bg-warn-soft text-warn border-warn/25", icon: "alert" },
};

export function Alert({ tone, children }: { tone: Tone; children: ReactNode }) {
  const t = TONE[tone];
  return (
    <div role={tone === "error" ? "alert" : "status"} className={`flex gap-2.5 rounded-b border px-3.5 py-3 text-sm ${t.box}`}>
      <Icon name={t.icon} className="mt-0.5 h-4 w-4 shrink-0" />
      <div>{children}</div>
    </div>
  );
}

export function EmptyState({ icon, title, children, action }: { icon: IconName; title: string; children?: ReactNode; action?: ReactNode }) {
  return (
    <div className="flex flex-col items-center px-6 py-14 text-center">
      <span className="mb-4 grid h-12 w-12 place-items-center rounded-full bg-surface-2 text-text-2">
        <Icon name={icon} className="h-6 w-6" />
      </span>
      <h3 className="text-base font-bold text-text">{title}</h3>
      {children && <p className="mt-1.5 max-w-sm text-sm text-text-2">{children}</p>}
      {action && <div className="mt-5">{action}</div>}
    </div>
  );
}

export function LoadingState({ label = "Cargando…" }: { label?: string }) {
  return (
    <div role="status" className="flex items-center justify-center gap-3 py-14 text-sm text-text-2">
      <Spinner className="h-5 w-5" />
      {label}
    </div>
  );
}

export function ErrorState({ message, onRetry }: { message: string; onRetry?: () => void }) {
  return (
    <EmptyState icon="alert" title="No se pudo cargar la información" action={onRetry && <Button variant="secondary" onClick={onRetry}>Reintentar</Button>}>
      {message}
    </EmptyState>
  );
}

export function PageHeader({ title, description, actions }: { title: string; description?: string | undefined; actions?: ReactNode }) {
  return (
    <div className="mb-6 flex flex-col gap-4 sm:flex-row sm:items-end sm:justify-between">
      <div>
        <h1 className="text-2xl font-extrabold tracking-[-0.02em] text-text">{title}</h1>
        {description && <p className="mt-1 text-sm text-text-2">{description}</p>}
      </div>
      {actions && <div className="flex flex-wrap gap-2">{actions}</div>}
    </div>
  );
}

export function Panel({ children, className = "" }: { children: ReactNode; className?: string }) {
  return <section className={`rounded-xl border border-line bg-surface ${className}`}>{children}</section>;
}

export function Badge({ tone = "neutral", children }: { tone?: "neutral" | "ok" | "warn" | "danger"; children: ReactNode }) {
  const cls = { neutral: "bg-surface-2 text-text-2", ok: "bg-ok-soft text-ok", warn: "bg-warn-soft text-warn", danger: "bg-danger-soft text-danger" }[tone];
  return <span className={`inline-flex items-center rounded-full px-2 py-0.5 text-xs font-semibold ${cls}`}>{children}</span>;
}

/** Diálogo de confirmación modal (role="alertdialog"). */
export function ConfirmDialog({ open, title, children, confirmLabel, onConfirm, onCancel, busy, tone = "primary" }: {
  open: boolean; title: string; children: ReactNode; confirmLabel: string; onConfirm: () => void; onCancel: () => void; busy?: boolean; tone?: "primary" | "danger";
}) {
  if (!open) return null;
  return (
    <div className="fixed inset-0 z-50 grid place-items-center bg-ink/50 p-4" onClick={onCancel}>
      <div role="alertdialog" aria-modal="true" aria-labelledby="confirm-title" className="w-full max-w-md rounded-xl bg-surface p-6 shadow-xl" onClick={(e) => e.stopPropagation()}>
        <h2 id="confirm-title" className="text-lg font-bold">{title}</h2>
        <div className="mt-2 text-sm text-text-2">{children}</div>
        <div className="mt-6 flex justify-end gap-2">
          <Button variant="secondary" onClick={onCancel} disabled={busy}>Cancelar</Button>
          <Button variant={tone} onClick={onConfirm} loading={busy ?? false} autoFocus>{confirmLabel}</Button>
        </div>
      </div>
    </div>
  );
}
