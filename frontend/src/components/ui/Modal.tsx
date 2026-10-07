import { X } from 'lucide-react'
import { useEffect, useId, useRef, type ReactNode } from 'react'
import { cn } from '@/lib/cn'

interface ModalProps {
  open: boolean
  onClose: () => void
  title: string
  description?: string
  children: ReactNode
  /** Botones de acción. Para enviar un formulario del cuerpo usa <button form="id-del-form">. */
  footer?: ReactNode
  size?: 'sm' | 'md' | 'lg' | 'xl'
  /** false mientras hay una operación en curso: bloquea Escape, el clic fuera y la X. */
  dismissible?: boolean
}

const SIZES = {
  sm: 'max-w-md',
  md: 'max-w-xl',
  lg: 'max-w-2xl',
  xl: 'max-w-3xl',
} as const

/**
 * Modal sobre el elemento nativo <dialog> (showModal): el navegador ya atrapa el foco,
 * vuelve inerte el resto de la página y gestiona Escape. El contenido solo se monta
 * mientras está abierto, así cada apertura empieza con un formulario limpio.
 */
export function Modal({
  open,
  onClose,
  title,
  description,
  children,
  footer,
  size = 'md',
  dismissible = true,
}: ModalProps) {
  const dialogRef = useRef<HTMLDialogElement>(null)
  const titleId = useId()
  const descriptionId = useId()

  useEffect(() => {
    const dialog = dialogRef.current
    if (!dialog) return
    if (open && !dialog.open) dialog.showModal()
    if (!open && dialog.open) dialog.close()
  }, [open])

  return (
    <dialog
      ref={dialogRef}
      aria-labelledby={titleId}
      aria-describedby={description ? descriptionId : undefined}
      // Escape: el cierre lo decide el componente padre (puede estar guardando).
      onCancel={(event) => {
        event.preventDefault()
        if (dismissible) onClose()
      }}
      // Si el navegador fuerza el cierre, se sincroniza el estado del padre.
      onClose={() => {
        if (open) onClose()
      }}
      // Clic en el fondo: el objetivo es el propio <dialog>, no su contenido.
      onClick={(event) => {
        if (event.target === event.currentTarget && dismissible) onClose()
      }}
      className={cn(
        'm-auto w-[calc(100%-2rem)] max-h-[calc(100dvh-2rem)] overflow-hidden p-0',
        'rounded-xl border border-border bg-surface text-foreground shadow-card',
        'backdrop:bg-black/50 backdrop:backdrop-blur-[2px]',
        SIZES[size],
      )}
    >
      {open && (
        <div className="flex max-h-[calc(100dvh-2rem)] flex-col">
          <header className="flex items-start justify-between gap-4 border-b border-border px-6 pt-5 pb-4">
            <div>
              <h2 id={titleId} className="font-serif text-2xl leading-tight">
                {title}
              </h2>
              {description && (
                <p id={descriptionId} className="mt-1 text-sm text-foreground-muted">
                  {description}
                </p>
              )}
            </div>
            <button
              type="button"
              onClick={onClose}
              disabled={!dismissible}
              aria-label="Cerrar"
              className="-mr-2 rounded-md p-1.5 text-foreground-muted hover:bg-surface-muted hover:text-foreground focus-visible:outline-2 focus-visible:outline-focus disabled:opacity-40"
            >
              <X className="size-5" aria-hidden="true" />
            </button>
          </header>

          <div className="overflow-y-auto px-6 py-5">{children}</div>

          {footer && (
            <footer className="flex flex-col-reverse gap-2 border-t border-border bg-surface-muted/40 px-6 py-4 sm:flex-row sm:justify-end sm:gap-3">
              {footer}
            </footer>
          )}
        </div>
      )}
    </dialog>
  )
}
