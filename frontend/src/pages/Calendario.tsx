import { CheckCircle2, ChevronLeft, ChevronRight, Loader2, RotateCw } from 'lucide-react'
import { useCallback, useMemo, useState } from 'react'
import { Link, useSearchParams } from 'react-router-dom'
import { PageHeader } from '@/components/layout/PageHeader'
import { usePermisos, useContextoEmpresa } from '@/features/auth/permisos'
import { DetalleEventoModal } from '@/features/calendario/DetalleEventoModal'
import { ESTILO_TIPO } from '@/features/calendario/estilos'
import {
  ETIQUETA_FILTRO_ESTADO,
  ETIQUETA_TIPO,
  TIPOS_EVENTO,
  agruparPorDia,
  filtrarEventos,
  type FiltroEstado,
} from '@/features/calendario/filtros'
import { VistaDia, VistaMes, VistaSemana } from '@/features/calendario/Vistas'
import { AvisoModoGlobal } from '@/features/empresa/AvisoModoGlobal'
import { useAviso } from '@/hooks/useAviso'
import { useRecurso } from '@/hooks/useRecurso'
import { cn } from '@/lib/cn'
import { VISTAS, esDia, rangoDeVista, type Vista } from '@/features/calendario/rango'
import {
  formatearDia,
  formatearDiaLargo,
  formatearMes,
  hoyEnBogota,
  semana,
  sumarDias,
  sumarMeses,
  type Dia,
} from '@/lib/dias'
import { calendarioService, type EventoCalendario, type TipoEventoCalendario } from '@/services/calendarioService'

/**
 * Calendario operativo de la empresa. Los eventos salen de los datos reales (el backend los
 * deriva de órdenes, programaciones, recepciones y solicitudes) y se pintan en el día y la hora
 * de la zona del negocio. Las entregas sin franja son de todo el día: no se inventa una hora.
 * La URL guarda la vista y la fecha (?vista=semana&fecha=2026-11-03).
 */
export default function Calendario() {
  const permisos = usePermisos()
  const { modoGlobal } = useContextoEmpresa()
  const [searchParams, setSearchParams] = useSearchParams()
  const vistaUrl = searchParams.get('vista')
  const vista: Vista = vistaUrl === 'semana' || vistaUrl === 'dia' ? vistaUrl : 'mes'
  const fechaUrl = searchParams.get('fecha')
  const hoyLocal = hoyEnBogota()
  const fecha: Dia = esDia(fechaUrl) ? fechaUrl : hoyLocal
  const desde = rangoDeVista(vista, fecha).desde
  const hasta = rangoDeVista(vista, fecha).hasta

  const tiposDisponibles = TIPOS_EVENTO.filter((tipo) => tipo !== 'ORDEN_EMITIDA' || permisos.verCompras)
  const [tipos, setTipos] = useState<ReadonlySet<TipoEventoCalendario>>(() => new Set(TIPOS_EVENTO))
  const [estado, setEstado] = useState<FiltroEstado>('TODOS')
  const [abierto, setAbierto] = useState<EventoCalendario | null>(null)
  const { aviso, mostrarAviso } = useAviso()

  // La clave (texto) identifica el período pedido; en el modo global no hay calendario que pedir.
  const clave = `${desde}|${hasta}|${modoGlobal ? 'global' : 'empresa'}`
  const pedir = useCallback((signal: AbortSignal) => {
    const [d = '', h = '', contexto] = clave.split('|')
    return contexto === 'global' ? Promise.resolve(null) : calendarioService.eventos(d, h, signal)
  }, [clave])
  const { datos, error, cargando, recargar } = useRecurso(pedir, clave)
  // Mientras llega el período nuevo se conservan los eventos anteriores atenuados; solo se pintan
  // si corresponden al período visible.
  const vigentes = datos && datos.desde === desde && datos.hasta === hasta ? datos : null
  const hoy = datos?.hoy ?? hoyLocal

  const eventos = useMemo(() => filtrarEventos(vigentes?.eventos ?? [], tipos, estado), [vigentes, tipos, estado])
  const porDia = useMemo(() => agruparPorDia(eventos), [eventos])

  function navegar(cambios: { vista?: Vista; fecha?: Dia }) {
    const siguiente = new URLSearchParams(searchParams)
    siguiente.set('vista', cambios.vista ?? vista)
    siguiente.set('fecha', cambios.fecha ?? fecha)
    setSearchParams(siguiente)
  }

  function mover(sentido: 1 | -1) {
    const fechaNueva = vista === 'mes' ? sumarMeses(fecha, sentido) : sumarDias(fecha, sentido * (vista === 'semana' ? 7 : 1))
    navegar({ fecha: fechaNueva })
  }

  function alternarTipo(tipo: TipoEventoCalendario) {
    setTipos((actual) => {
      const siguiente = new Set(actual)
      if (siguiente.has(tipo)) siguiente.delete(tipo)
      else siguiente.add(tipo)
      return siguiente
    })
  }

  const titulo =
    vista === 'mes'
      ? formatearMes(fecha)
      : vista === 'semana'
        ? `${formatearDia(semana(fecha)[0]!)} – ${formatearDia(semana(fecha)[6]!)}`
        : formatearDiaLargo(fecha)
  const totalPeriodo = vigentes?.eventos.length ?? 0

  return (
    <>
      <PageHeader
        eyebrow="Operación"
        title="Calendario"
        description="Entregas programadas, recepciones, órdenes emitidas y solicitudes por aprobar de tu empresa."
      />
      {modoGlobal ? (
        <AvisoModoGlobal>
          el calendario es de una empresa. Elige una en{' '}
          <Link to="/admin/empresas" className="font-medium text-foreground underline-offset-4 hover:underline">
            Empresas
          </Link>{' '}
          y pulsa «Supervisar».
        </AvisoModoGlobal>
      ) : (
        <>
          <div role="status" aria-live="polite">
            {aviso && (
              <p className="u-alert u-alert--success mb-4 flex items-center gap-2">
                <CheckCircle2 className="size-4 shrink-0" aria-hidden="true" />
                {aviso}
              </p>
            )}
          </div>

          <div className="mb-4 flex flex-col gap-3 lg:flex-row lg:items-center lg:justify-between">
            <div className="flex flex-wrap items-center gap-2">
              <button type="button" className="u-btn u-btn--ghost h-9" onClick={() => navegar({ fecha: hoy })}>
                Hoy
              </button>
              <div className="flex">
                <button type="button" className="u-btn u-btn--ghost h-9 px-2" onClick={() => mover(-1)} aria-label="Período anterior">
                  <ChevronLeft className="size-4" aria-hidden="true" />
                </button>
                <button type="button" className="u-btn u-btn--ghost h-9 px-2" onClick={() => mover(1)} aria-label="Período siguiente">
                  <ChevronRight className="size-4" aria-hidden="true" />
                </button>
              </div>
              <h2 className="text-lg font-medium text-foreground first-letter:uppercase" aria-live="polite">
                {titulo}
              </h2>
              {cargando && <Loader2 className="size-4 animate-spin text-foreground-muted" aria-label="Cargando" />}
            </div>
            <div role="radiogroup" aria-label="Vista del calendario" className="inline-flex self-start rounded-md border border-border p-0.5">
              {VISTAS.map(({ valor, etiqueta }) => (
                <button
                  key={valor}
                  type="button"
                  role="radio"
                  aria-checked={vista === valor}
                  onClick={() => navegar({ vista: valor })}
                  className={cn(
                    'h-8 rounded-sm px-3 text-sm',
                    vista === valor ? 'bg-foreground font-medium text-background' : 'text-foreground-muted hover:text-foreground',
                  )}
                >
                  {etiqueta}
                </button>
              ))}
            </div>
          </div>

          <div className="mb-4 flex flex-col gap-3 md:flex-row md:items-center md:justify-between">
            <div className="flex flex-wrap gap-2" role="group" aria-label="Tipos de evento">
              {tiposDisponibles.map((tipo) => {
                const { Icono, marca } = ESTILO_TIPO[tipo]
                const activo = tipos.has(tipo)
                return (
                  <button
                    key={tipo}
                    type="button"
                    aria-pressed={activo}
                    onClick={() => alternarTipo(tipo)}
                    className={cn(
                      'inline-flex h-8 items-center gap-1.5 rounded-full border px-3 text-sm',
                      activo ? 'border-border-strong bg-surface text-foreground' : 'border-dashed border-border text-foreground-muted line-through',
                    )}
                  >
                    <span className={cn('size-2 rounded-full', marca)} aria-hidden="true" />
                    <Icono className="size-3.5" aria-hidden="true" />
                    {ETIQUETA_TIPO[tipo]}
                  </button>
                )
              })}
            </div>
            <label className="flex min-w-0 items-center gap-2 text-sm">
              <span className="text-foreground-muted">Estado</span>
              <select value={estado} onChange={(e) => setEstado(e.target.value as FiltroEstado)} className="u-input h-9 min-w-0 flex-1 sm:w-64 sm:flex-none">
                {(Object.keys(ETIQUETA_FILTRO_ESTADO) as FiltroEstado[]).map((valor) => (
                  <option key={valor} value={valor}>
                    {ETIQUETA_FILTRO_ESTADO[valor]}
                  </option>
                ))}
              </select>
            </label>
          </div>

          {error && (
            <div role="alert" className="u-alert u-alert--error mb-4 flex flex-wrap items-center justify-between gap-3">
              No se pudo cargar el calendario: {error}
              <button type="button" className="u-btn u-btn--ghost h-8 text-sm" onClick={recargar}>
                <RotateCw className="size-4" aria-hidden="true" />
                Reintentar
              </button>
            </div>
          )}
          {vigentes?.truncado && (
            <p className="u-alert u-alert--warning mb-4">Hay demasiados eventos en este período: se muestran los primeros. Usa la vista semanal.</p>
          )}
          {vigentes && totalPeriodo === 0 && (
            <p className="mb-4 text-sm text-foreground-muted">No hay eventos en este período.</p>
          )}
          {vigentes && totalPeriodo > 0 && eventos.length === 0 && (
            <p className="mb-4 text-sm text-foreground-muted">Ningún evento coincide con los filtros.</p>
          )}

          <div className={cn(cargando && 'opacity-60 transition-opacity')} aria-busy={cargando}>
            {vista === 'mes' && (
              <VistaMes fecha={fecha} hoy={hoy} porDia={porDia} onAbrir={setAbierto} onIrADia={(dia) => navegar({ vista: 'dia', fecha: dia })} />
            )}
            {vista === 'semana' && (
              <VistaSemana fecha={fecha} hoy={hoy} porDia={porDia} onAbrir={setAbierto} onIrADia={(dia) => navegar({ vista: 'dia', fecha: dia })} />
            )}
            {vista === 'dia' && <VistaDia fecha={fecha} porDia={porDia} onAbrir={setAbierto} />}
          </div>
          <p className="mt-3 text-xs text-foreground-muted">
            Días y horas en hora de Colombia ({datos?.zonaHoraria ?? 'America/Bogota'}). Las entregas son fechas estimadas; las
            recepciones, la hora real en que se registraron.
          </p>
        </>
      )}

      {abierto && (
        <DetalleEventoModal
          evento={abierto}
          onClose={() => setAbierto(null)}
          onCambio={(mensaje) => {
            setAbierto(null)
            recargar()
            mostrarAviso(mensaje)
          }}
        />
      )}
    </>
  )
}
