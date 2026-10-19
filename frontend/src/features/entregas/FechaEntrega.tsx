import { BadgeCheck, CircleDashed } from 'lucide-react'
import { formatearDia, formatearDiaCorto } from '@/lib/dias'
import type { ProgramacionEntrega } from '@/services/ordenCompraService'
import { franja } from './etiquetas'

/**
 * Fecha estimada vigente con su franja y si el proveedor la confirmó. Es una planificación, no
 * la recepción real.
 */
export function FechaEntrega({ programacion, compacta = false }: { programacion?: ProgramacionEntrega; compacta?: boolean }) {
  if (!programacion) {
    return <span className="text-foreground-muted">Sin fecha estimada</span>
  }
  return (
    <span className="inline-flex flex-col">
      <span className="whitespace-nowrap text-foreground">
        {compacta ? formatearDiaCorto(programacion.fechaEstimada) : formatearDia(programacion.fechaEstimada)}
        <span className="text-foreground-muted"> · {franja(programacion)}</span>
      </span>
      <span className="inline-flex items-center gap-1 text-xs text-foreground-muted">
        {programacion.confirmadaProveedor ? (
          <>
            <BadgeCheck className="size-3.5 text-success" aria-hidden="true" />
            Confirmada por el proveedor
          </>
        ) : (
          <>
            <CircleDashed className="size-3.5" aria-hidden="true" />
            Estimada, sin confirmar
          </>
        )}
      </span>
    </span>
  )
}
