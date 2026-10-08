import { ArrowRight, ClipboardList, FileText, Plus, SearchCheck } from 'lucide-react'
import { Link } from 'react-router-dom'
import { ETIQUETA_ROL, useContextoEmpresa, usePermisos } from '@/features/auth/permisos'
import { AvisoModoGlobal } from '@/features/empresa/AvisoModoGlobal'
import { etiquetaEmpresa } from '@/features/empresa/etiquetas'
import { EstadoBadge } from '@/features/solicitudes/EstadoBadge'
import { FlujoCompra } from '@/features/solicitudes/FlujoCompra'
import { useResumenSolicitudes } from '@/features/solicitudes/useResumenSolicitudes'
import { cn } from '@/lib/cn'
import { formatearFecha } from '@/lib/fechas'
import { formatearCOP } from '@/lib/moneda'
import type { EstadoSolicitud } from '@/services/solicitudService'
import { useAuthStore } from '@/store/authStore'

/** Panel de inicio del SGOC: en qué punto del proceso de compra está la empresa (o el usuario). */
export default function Inicio() {
  const nombre = useAuthStore((state) => state.nombre)
  const { rol, empresaId, modoGlobal } = useContextoEmpresa()
  const permisos = usePermisos()
  const resumen = useResumenSolicitudes()
  const propias = !permisos.verTodasLasSolicitudes

  const contexto = modoGlobal
    ? 'Modo global · todas las empresas'
    : [empresaId !== null ? etiquetaEmpresa(empresaId) : null, rol ? ETIQUETA_ROL[rol] : null].filter(Boolean).join(' · ')

  return (
    <>
      <p className="text-xs font-medium tracking-widest text-foreground-muted uppercase">Panel de compras</p>
      <h1 className="mt-3 font-serif text-5xl text-foreground">
        Hola, <em className="text-accent">{nombre}</em>
      </h1>
      <p className="mt-2 text-sm text-foreground-muted">{contexto}</p>

      <div className="mt-6 flex flex-wrap gap-3">
        {permisos.crearSolicitudes && (
          <Link to="/solicitudes?nueva=1" className="u-btn u-btn--primary">
            <Plus className="size-4" aria-hidden="true" />
            Nueva solicitud
          </Link>
        )}
        {permisos.verTodasLasSolicitudes && (
          <Link to="/solicitudes?estado=PENDIENTE" className="u-btn u-btn--ghost">
            <SearchCheck className="size-4" aria-hidden="true" />
            Revisar pendientes
          </Link>
        )}
      </div>

      <div className="mt-8">
        <AvisoModoGlobal que="las solicitudes y los catálogos" />
      </div>

      <section aria-labelledby="indicadores" className="mt-2">
        <h2 id="indicadores" className="sr-only">
          Indicadores
        </h2>
        {resumen.estado === 'error' ? (
          <p role="alert" className="u-alert u-alert--error">
            No se pudo cargar el resumen: {resumen.mensaje}
          </p>
        ) : (
          <div className="grid gap-4 sm:grid-cols-3">
            <Indicador
              estado="PENDIENTE"
              titulo={propias ? 'Mis solicitudes pendientes' : 'Pendientes de aprobación'}
              detalle="Esperan la revisión de un gerente."
              valor={resumen.estado === 'listo' ? resumen.conteos.PENDIENTE : null}
            />
            <Indicador
              estado="APROBADA"
              titulo={propias ? 'Mis solicitudes aprobadas' : 'Aprobadas'}
              detalle="Listas para convertirse en orden de compra."
              valor={resumen.estado === 'listo' ? resumen.conteos.APROBADA : null}
            />
            <Indicador
              estado="RECHAZADA"
              titulo={propias ? 'Mis solicitudes rechazadas' : 'Rechazadas'}
              detalle="Con el motivo indicado por el gerente."
              valor={resumen.estado === 'listo' ? resumen.conteos.RECHAZADA : null}
            />
          </div>
        )}
      </section>

      <section aria-labelledby="ordenes" className="u-card mt-4 p-5">
        <div className="flex items-start gap-3">
          <FileText className="mt-0.5 size-5 shrink-0 text-foreground-muted" aria-hidden="true" />
          <div>
            <h2 id="ordenes" className="font-medium text-foreground">
              Órdenes de compra y recepción
            </h2>
            <p className="mt-1 text-sm text-foreground-muted">
              Órdenes activas, pendientes de recepción y completadas aparecerán aquí cuando el sistema incorpore el
              módulo de órdenes de compra (próximamente).
            </p>
          </div>
        </div>
      </section>

      <section aria-labelledby="flujo" className="u-card mt-4 p-5 sm:p-6">
        <h2 id="flujo" className="font-medium text-foreground">
          Cómo funciona una compra
        </h2>
        <div className="mt-4">
          <FlujoCompra />
        </div>
      </section>

      <section aria-labelledby="recientes" className="mt-8">
        <div className="flex items-end justify-between gap-4">
          <h2 id="recientes" className="font-medium text-foreground">
            {propias ? 'Mis solicitudes recientes' : 'Actividad reciente'}
          </h2>
          <Link to="/solicitudes" className="inline-flex items-center gap-1 text-sm font-medium text-foreground hover:underline">
            Ver todas
            <ArrowRight className="size-4" aria-hidden="true" />
          </Link>
        </div>
        <ActividadReciente resumen={resumen} puedeCrear={permisos.crearSolicitudes} mostrarSolicitante={!propias} />
      </section>
    </>
  )
}

function Indicador({
  estado,
  titulo,
  detalle,
  valor,
}: {
  estado: EstadoSolicitud
  titulo: string
  detalle: string
  valor: number | null
}) {
  return (
    <Link
      to={`/solicitudes?estado=${estado}`}
      className="u-card group block p-5 transition-colors hover:border-border-strong"
      aria-busy={valor === null}
    >
      <EstadoBadge estado={estado} />
      <p className="mt-3 font-serif text-4xl text-foreground tabular-nums">{valor ?? '—'}</p>
      <p className="mt-1 text-sm font-medium text-foreground group-hover:underline">{titulo}</p>
      <p className="text-xs text-foreground-muted">{detalle}</p>
    </Link>
  )
}

function ActividadReciente({
  resumen,
  puedeCrear,
  mostrarSolicitante,
}: {
  resumen: ReturnType<typeof useResumenSolicitudes>
  puedeCrear: boolean
  mostrarSolicitante: boolean
}) {
  if (resumen.estado === 'cargando') {
    return <p className="mt-3 text-sm text-foreground-muted">Cargando…</p>
  }
  if (resumen.estado === 'error') {
    return null
  }
  if (resumen.recientes.length === 0) {
    return (
      <div className="u-card mt-3 flex flex-col items-center px-6 py-10 text-center">
        <ClipboardList className="size-6 text-accent" aria-hidden="true" />
        <p className="mt-3 font-medium text-foreground">Aún no hay solicitudes</p>
        <p className="mt-1 max-w-md text-sm text-foreground-muted">
          Todo empieza con una necesidad: «Necesitamos 10 teclados para renovar los equipos de la oficina».
        </p>
        {puedeCrear && (
          <Link to="/solicitudes?nueva=1" className="u-btn u-btn--primary mt-4">
            <Plus className="size-4" aria-hidden="true" />
            Crear la primera
          </Link>
        )}
      </div>
    )
  }
  return (
    <ul className="u-card mt-3 divide-y divide-border">
      {resumen.recientes.map((solicitud) => {
        const [primero, ...resto] = solicitud.detalles
        return (
          <li key={solicitud.id}>
            <Link
              to={`/solicitudes?ver=${solicitud.id}`}
              className="flex flex-wrap items-center gap-x-4 gap-y-1 px-5 py-3 hover:bg-surface-muted/50"
            >
              <span className="font-mono text-xs text-foreground-muted">#{solicitud.id}</span>
              <span className="min-w-0 flex-1 truncate text-sm text-foreground">
                {primero?.producto.nombre}
                {resto.length > 0 && ` y ${resto.length} más`}
                {mostrarSolicitante && (
                  <span className="text-foreground-muted"> · {solicitud.solicitante.nombre}</span>
                )}
              </span>
              <span className={cn('text-sm font-medium tabular-nums text-foreground')}>
                {formatearCOP(solicitud.totalEstimado)}
              </span>
              <EstadoBadge estado={solicitud.estado} />
              <span className="w-24 text-right text-xs text-foreground-muted">{formatearFecha(solicitud.fecha)}</span>
            </Link>
          </li>
        )
      })}
    </ul>
  )
}
