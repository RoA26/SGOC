import { Eye, Globe2 } from 'lucide-react'
import { Link } from 'react-router-dom'
import { useContextoEmpresa } from '@/features/auth/permisos'
import { useEmpresaSeleccionadaStore } from './empresaSeleccionadaStore'
import { EmpresaDemoBadge } from './EstadosBadges'

/**
 * Franja fija sobre el contenido mientras el SUPER_ADMIN supervisa una empresa: deja claro de qué
 * empresa son los datos, que es solo lectura y cómo volver al modo global.
 */
export function AvisoSupervision() {
  const { empresaSupervisada } = useContextoEmpresa()
  const salir = useEmpresaSeleccionadaStore((state) => state.salir)
  if (!empresaSupervisada) return null

  return (
    <div
      role="region"
      aria-label="Supervisión de empresa"
      className="mb-8 flex flex-col gap-3 rounded-lg border border-accent/40 bg-accent/10 px-4 py-3 text-sm sm:flex-row sm:items-center sm:justify-between"
    >
      <p className="flex items-start gap-2 text-foreground">
        <Eye className="mt-0.5 size-4 shrink-0 text-accent" aria-hidden="true" />
        <span>
          Supervisando <strong className="font-medium">{empresaSupervisada.nombre}</strong>{' '}
          <span className="font-mono text-xs text-foreground-muted">({empresaSupervisada.codigo})</span>
          {empresaSupervisada.demo && (
            <>
              {' '}
              <EmpresaDemoBadge />
            </>
          )}
          . Ves sus datos
          con tu cuenta de super administrador, en solo lectura.
        </span>
      </p>
      <div className="flex shrink-0 flex-wrap gap-2">
        <Link to={`/admin/empresas/${empresaSupervisada.id}`} className="u-btn u-btn--ghost h-9 px-3 text-sm">
          Ver ficha
        </Link>
        <button type="button" onClick={salir} className="u-btn u-btn--ghost h-9 px-3 text-sm">
          <Globe2 className="size-4" aria-hidden="true" />
          Volver al modo global
        </button>
      </div>
    </div>
  )
}
