import { Globe2 } from 'lucide-react'
import type { ReactNode } from 'react'
import { Link } from 'react-router-dom'
import { useContextoEmpresa } from '@/features/auth/permisos'

/**
 * Para el SUPER_ADMIN en modo global: indica que ve datos de todas las empresas y cómo revisar
 * los de una sola. Con `children` se sustituye el texto por defecto.
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
            ves {que} de todas las empresas. Para revisar los de una sola, supervísala desde{' '}
            <Link to="/admin/empresas" className="font-medium text-foreground underline-offset-4 hover:underline">
              Empresas
            </Link>
            .
          </>
        )}
      </span>
    </p>
  )
}
