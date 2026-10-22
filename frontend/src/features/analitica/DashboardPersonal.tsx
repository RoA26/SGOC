import { AlertTriangle, PackageCheck, XCircle } from 'lucide-react'
import { useCallback } from 'react'
import { Link } from 'react-router-dom'
import { StatTile } from '@/components/ui/StatTile'
import { EstadoBadge } from '@/features/solicitudes/EstadoBadge'
import { useRecurso } from '@/hooks/useRecurso'
import { formatearNumero } from '@/lib/numeros'
import { analiticaService } from '@/services/analiticaService'
import type { EstadoSolicitud } from '@/services/solicitudService'
import { ErrorPanel, EsqueletoPanel } from './EstadoCarga'

const TEXTOS: Record<EstadoSolicitud, { titulo: string; detalle: string }> = {
  PENDIENTE: { titulo: 'Mis solicitudes pendientes', detalle: 'Esperan la revisión de un administrador.' },
  APROBADA: { titulo: 'Mis solicitudes aprobadas', detalle: 'Compras emitirá la orden al proveedor.' },
  RECHAZADA: { titulo: 'Mis solicitudes rechazadas', detalle: 'Con el motivo indicado por el administrador.' },
}

/**
 * Dashboard del trabajador: disponibilidad de productos en su empresa y sus solicitudes. Sin
 * indicadores de ventas: no existen ventas reales hasta que se implemente el punto de venta.
 */
export function DashboardPersonal() {
  const pedir = useCallback((signal: AbortSignal) => analiticaService.personal(signal), [])
  const { datos, error, recargar } = useRecurso(pedir, 'personal')

  return (
    <section aria-labelledby="dashboard-personal" className="space-y-4">
      <h2 id="dashboard-personal" className="sr-only">
        Mis indicadores
      </h2>
      {error && <ErrorPanel mensaje={error} onReintentar={recargar} />}
      {!datos ? (
        !error && <EsqueletoPanel tarjetas={3} />
      ) : (
        <>
          <div className="grid gap-4 sm:grid-cols-3">
            <StatTile
              etiqueta="Productos disponibles"
              valor={formatearNumero(datos.productosDisponibles)}
              detalle="Con existencias en la empresa."
              icono={PackageCheck}
              to="/inventario"
            />
            <StatTile
              etiqueta="Stock crítico"
              valor={formatearNumero(datos.productosCriticos)}
              detalle="En su stock mínimo o por debajo."
              icono={AlertTriangle}
              claseIcono="text-warning"
              to="/inventario?estado=CRITICO"
            />
            <StatTile
              etiqueta="Sin existencias"
              valor={formatearNumero(datos.productosAgotados)}
              detalle="Si los necesitas, crea una solicitud."
              icono={XCircle}
              claseIcono="text-danger"
              to="/inventario?estado=AGOTADO"
            />
          </div>
          <div className="grid gap-4 sm:grid-cols-3">
            {(Object.keys(TEXTOS) as EstadoSolicitud[]).map((estado) => (
              <Link
                key={estado}
                to={`/solicitudes?estado=${estado}`}
                className="u-card u-card-interactiva group block p-5"
              >
                <EstadoBadge estado={estado} />
                <p className="mt-3 text-3xl font-semibold tracking-tight text-foreground">
                  {formatearNumero(datos.misSolicitudes[estado])}
                </p>
                <p className="mt-1 text-sm font-medium text-foreground group-hover:underline">{TEXTOS[estado].titulo}</p>
                <p className="text-xs text-foreground-muted">{TEXTOS[estado].detalle}</p>
              </Link>
            ))}
          </div>
        </>
      )}
    </section>
  )
}
