import { Loader2 } from 'lucide-react'
import type { ReactNode } from 'react'
import { Modal } from './Modal'

interface ConfirmDialogProps {
  open: boolean
  title: string
  children: ReactNode
  confirmLabel: string
  /** Mensaje de error de la última operación (p. ej. 409 de la API). */
  error?: string | null
  busy?: boolean
  onConfirm: () => void
  onCancel: () => void
}

/** Confirmación de acciones destructivas, sobre el Modal base. */
export function ConfirmDialog({
  open,
  title,
  children,
  confirmLabel,
  error,
  busy = false,
  onConfirm,
  onCancel,
}: ConfirmDialogProps) {
  return (
    <Modal
      open={open}
      onClose={onCancel}
      title={title}
      size="sm"
      dismissible={!busy}
      footer={
        <>
          <button type="button" className="u-btn u-btn--ghost" onClick={onCancel} disabled={busy}>
            Cancelar
          </button>
          <button
            type="button"
            className="u-btn bg-danger text-danger-surface hover:opacity-90"
            onClick={onConfirm}
            disabled={busy}
            aria-busy={busy}
          >
            {busy && <Loader2 className="size-4 animate-spin" aria-hidden="true" />}
            {confirmLabel}
          </button>
        </>
      }
    >
      <div className="space-y-4 text-sm text-foreground-muted">
        {children}
        {error && (
          <p role="alert" className="u-alert u-alert--error">
            {error}
          </p>
        )}
      </div>
    </Modal>
  )
}
