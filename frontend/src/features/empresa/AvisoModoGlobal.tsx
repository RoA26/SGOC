import { Globe2 } from 'lucide-react'
import type { ReactNode } from 'react'
import { useContextoEmpresa } from '@/features/auth/permisos'

/**
 * Para el SUPER_ADMIN en modo global: explica por qué no puede crear ni modificar. El backend
 * responde 400 ("Empresa no seleccionada") a cualquier escritura sin X-Tenant-ID. Con `children`
 * se sustituye el texto por defecto.
 */
export function AvisoModoGlobal({ que, children }: { que?: string; children?: ReactNode }) {
  const { modoGlobal } = useContextoEmpresa()
  if (!modoGlobal) return null
  return (
    <p role="note" className="mb-6 flex items-start gap-2 rounded-md border border-border bg-surface-muted/60 px-4 py-3 text-sm text-foreground-muted">
      <Globe2 className="mt-0.5 size-4 shrink-0 text-accent" aria-hidden="true" />
      <span>
        <strong className="font-medium text-foreground">Modo global:</strong>{' '}
        {children ?? (
          <>
            ves {que} de todas las empresas. Para crear o modificar, entra en una empresa desde el panel «Trabajar en
            una empresa».
          </>
        )}
      </span>
    </p>
  )
}
