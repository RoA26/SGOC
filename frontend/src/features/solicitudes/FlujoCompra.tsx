import { Check, Clock, X } from 'lucide-react'
import { cn } from '@/lib/cn'
import type { EstadoSolicitud } from '@/services/solicitudService'

type EstadoPaso = 'hecho' | 'actual' | 'rechazado' | 'pendiente' | 'proximamente'

interface Paso {
  titulo: string
  descripcion: string
  /** false: el backend aún no tiene este módulo. */
  disponible: boolean
}

const PASOS: Paso[] = [
  { titulo: 'Necesidad', descripcion: 'Alguien del equipo necesita un producto o servicio.', disponible: true },
  { titulo: 'Solicitud', descripcion: 'La registra con productos, cantidades y motivo.', disponible: true },
  { titulo: 'Aprobación', descripcion: 'Un gerente la aprueba o la rechaza.', disponible: true },
  { titulo: 'Orden de compra', descripcion: 'Se emite al proveedor elegido.', disponible: false },
  { titulo: 'Recepción', descripcion: 'Se controla lo recibido frente a lo pedido.', disponible: false },
]

/** Estado de cada paso para una solicitud concreta (sin solicitud: el flujo general). */
function estadosDePasos(estado?: EstadoSolicitud): EstadoPaso[] {
  return PASOS.map((paso, indice) => {
    if (!paso.disponible) return 'proximamente'
    if (!estado) return 'pendiente'
    if (indice < 2) return 'hecho'
    if (estado === 'PENDIENTE') return 'actual'
    return estado === 'APROBADA' ? 'hecho' : 'rechazado'
  })
}

const ICONOS: Partial<Record<EstadoPaso, typeof Check>> = { hecho: Check, actual: Clock, rechazado: X }

const TEXTO_ESTADO: Record<EstadoPaso, string> = {
  hecho: 'completado',
  actual: 'en curso',
  rechazado: 'rechazada',
  pendiente: 'disponible',
  proximamente: 'próximamente',
}

/** En pantallas estrechas el flujo compacto solo muestra los círculos: esta línea lo resume. */
const RESUMEN: Record<EstadoSolicitud, string> = {
  PENDIENTE: 'En aprobación: un gerente debe revisarla.',
  APROBADA: 'Aprobada. Siguiente paso: orden de compra (próximamente).',
  RECHAZADA: 'Rechazada en la aprobación.',
}

/**
 * Flujo del SGOC: Necesidad → Solicitud → Aprobación → Orden de compra → Recepción. Con
 * `estado`, marca en qué punto está una solicitud. Orden de compra y recepción aparecen como
 * "Próximamente": el backend aún no las soporta.
 */
export function FlujoCompra({ estado, compacto = false }: { estado?: EstadoSolicitud; compacto?: boolean }) {
  const estados = estadosDePasos(estado)
  return (
    <div>
      <ol className={cn('grid gap-3', compacto ? 'grid-cols-5' : 'sm:grid-cols-5')} aria-label="Flujo de compra">
        {PASOS.map((paso, indice) => {
          const estadoPaso = estados[indice] ?? 'pendiente'
          const Icono = ICONOS[estadoPaso]
          return (
            <li key={paso.titulo} className="relative flex flex-col gap-1.5">
              <div className="flex items-center gap-2">
                <span
                  className={cn(
                    'flex size-6 shrink-0 items-center justify-center rounded-full border text-xs font-semibold',
                    estadoPaso === 'hecho' && 'border-success-border bg-success-surface text-success',
                    estadoPaso === 'actual' && 'border-accent bg-accent/15 text-accent',
                    estadoPaso === 'rechazado' && 'border-danger-border bg-danger-surface text-danger',
                    estadoPaso === 'pendiente' && 'border-border-strong text-foreground',
                    estadoPaso === 'proximamente' && 'border-dashed border-border-strong text-foreground-muted',
                  )}
                  aria-hidden="true"
                >
                  {Icono ? <Icono className="size-3.5" /> : indice + 1}
                </span>
                <span className={cn('h-px flex-1 bg-border', indice === PASOS.length - 1 && 'invisible')} aria-hidden="true" />
              </div>
              <p
                className={cn(
                  'text-sm font-medium',
                  paso.disponible ? 'text-foreground' : 'text-foreground-muted',
                  compacto && 'sr-only sm:not-sr-only',
                )}
              >
                {paso.titulo}
                <span className="sr-only">: {TEXTO_ESTADO[estadoPaso]}</span>
              </p>
              {!compacto && <p className="text-xs text-foreground-muted">{paso.descripcion}</p>}
              {estadoPaso === 'proximamente' && (
                <p
                  className={cn(
                    'text-[0.65rem] font-medium tracking-wide text-foreground-muted uppercase',
                    compacto && 'hidden sm:block',
                  )}
                >
                  Próximamente
                </p>
              )}
            </li>
          )
        })}
      </ol>
      {compacto && estado && (
        // Los lectores de pantalla ya leen el estado de cada paso en la lista.
        <p className="mt-2 text-xs text-foreground-muted sm:hidden" aria-hidden="true">
          {RESUMEN[estado]}
        </p>
      )}
    </div>
  )
}
