import { cn } from '@/lib/cn'
import { cuadriculaMes, formatearDiaCorto, formatearDiaLargo, mismoMes, semana, type Dia } from '@/lib/dias'
import type { EventoCalendario } from '@/services/calendarioService'
import { numeroOrden } from '@/services/ordenCompraService'
import { estiloEvento } from './estilos'
import { ETIQUETA_ESTADO_CORTA, ETIQUETA_ESTADO_EVENTO, ETIQUETA_TIPO, horaDeEvento } from './filtros'

const DIAS_SEMANA = ['Lun', 'Mar', 'Mié', 'Jue', 'Vie', 'Sáb', 'Dom'] as const
const MAX_EN_CELDA = 3

interface VistaProps {
  fecha: Dia
  hoy: Dia
  porDia: Map<string, EventoCalendario[]>
  onAbrir: (evento: EventoCalendario) => void
  onIrADia: (dia: Dia) => void
}

function etiquetaCorta(evento: EventoCalendario): string {
  if (evento.ordenNumero !== undefined) return numeroOrden(evento.ordenNumero)
  if (evento.solicitudId !== undefined) return `Solicitud #${evento.solicitudId}`
  return evento.titulo
}

/** Botón de un evento: icono del tipo, hora (si la tiene) y título; el estado va en el nombre accesible. */
export function EventoChip({ evento, onAbrir, compacto = false }: { evento: EventoCalendario; onAbrir: (e: EventoCalendario) => void; compacto?: boolean }) {
  const { Icono, clase } = estiloEvento(evento)
  const hora = horaDeEvento(evento)
  const estado = ETIQUETA_ESTADO_EVENTO[evento.estado] ?? evento.estado
  return (
    <button
      type="button"
      onClick={() => onAbrir(evento)}
      aria-label={`${evento.titulo}, ${ETIQUETA_TIPO[evento.tipo].toLowerCase()}, ${estado}, ${hora ?? 'todo el día'}`}
      title={`${evento.titulo} · ${estado}`}
      className={cn(
        'flex w-full min-w-0 items-center gap-1.5 rounded-sm border border-l-4 border-border text-left text-xs text-foreground',
        'hover:border-border-strong focus-visible:outline-2 focus-visible:outline-focus',
        compacto ? 'px-1.5 py-0.5' : 'px-2 py-1.5',
        clase,
      )}
    >
      <Icono className="size-3.5 shrink-0" aria-hidden="true" />
      {hora && <span className="shrink-0 tabular-nums text-foreground-muted">{compacto ? hora.slice(0, 5) : hora}</span>}
      {/* En la celda del mes el icono ya dice el tipo: basta con el número de la orden o de la solicitud. */}
      <span className="truncate">{compacto ? etiquetaCorta(evento) : evento.titulo}</span>
    </button>
  )
}

/** Tarjeta de un evento en la semana y el día: con el detalle y el estado escritos. */
function EventoTarjeta({ evento, onAbrir }: { evento: EventoCalendario; onAbrir: (e: EventoCalendario) => void }) {
  const { Icono, clase } = estiloEvento(evento)
  const hora = horaDeEvento(evento)
  return (
    <button
      type="button"
      onClick={() => onAbrir(evento)}
      className={cn(
        'flex w-full items-start gap-2 rounded-md border border-l-4 border-border p-2.5 text-left text-sm',
        'hover:border-border-strong focus-visible:outline-2 focus-visible:outline-focus',
        clase,
      )}
    >
      <Icono className="mt-0.5 size-4 shrink-0" aria-hidden="true" />
      <span className="min-w-0">
        <span className="block font-medium text-foreground">{evento.titulo}</span>
        <span className="block text-xs text-foreground-muted">
          {hora ?? 'Todo el día'} · {ETIQUETA_ESTADO_CORTA[evento.estado] ?? evento.estado}
          {evento.incidencias > 0 && ` · ${evento.incidencias} incidencias`}
        </span>
        {evento.detalle && <span className="block truncate text-xs text-foreground-muted">{evento.detalle}</span>}
      </span>
    </button>
  )
}

export function VistaMes({ fecha, hoy, porDia, onAbrir, onIrADia }: VistaProps) {
  const dias = cuadriculaMes(fecha)
  return (
    <div className="u-card overflow-hidden">
      <div className="grid grid-cols-7 border-b border-border bg-surface-muted/60 text-center text-xs font-medium text-foreground-muted" aria-hidden="true">
        {DIAS_SEMANA.map((d) => (
          <div key={d} className="py-2">
            {d}
          </div>
        ))}
      </div>
      <ol className="grid grid-cols-7" aria-label="Días del mes">
        {dias.map((dia) => {
          const eventos = porDia.get(dia) ?? []
          const fuera = !mismoMes(dia, fecha)
          const esHoy = dia === hoy
          return (
            <li
              key={dia}
              className={cn(
                'min-h-16 border-r border-b border-border p-1 sm:min-h-28 sm:p-1.5 [&:nth-child(7n)]:border-r-0',
                fuera && 'bg-surface-muted/40',
              )}
            >
              <button
                type="button"
                onClick={() => onIrADia(dia)}
                aria-label={`${formatearDiaLargo(dia)}: ${eventos.length === 0 ? 'sin eventos' : `${eventos.length} eventos`}`}
                aria-current={esHoy ? 'date' : undefined}
                className={cn(
                  'flex size-7 items-center justify-center rounded-full text-xs tabular-nums focus-visible:outline-2 focus-visible:outline-focus',
                  esHoy
                    ? 'bg-foreground font-semibold text-background'
                    : cn('hover:bg-surface-muted', fuera ? 'text-foreground-muted' : 'text-foreground'),
                )}
              >
                {Number(dia.slice(8))}
              </button>
              {/* Móvil: solo marcas (el detalle se ve al tocar el día). Escritorio: los primeros eventos. */}
              {eventos.length > 0 && (
                <div className="mt-1 flex flex-wrap gap-0.5 sm:hidden" aria-hidden="true">
                  {eventos.slice(0, 4).map((e) => (
                    <span key={e.id} className={cn('size-1.5 rounded-full', estiloEvento(e).marca)} />
                  ))}
                </div>
              )}
              <ul className="mt-1 hidden space-y-0.5 sm:block">
                {eventos.slice(0, MAX_EN_CELDA).map((evento) => (
                  <li key={evento.id}>
                    <EventoChip evento={evento} onAbrir={onAbrir} compacto />
                  </li>
                ))}
                {eventos.length > MAX_EN_CELDA && (
                  <li>
                    <button
                      type="button"
                      onClick={() => onIrADia(dia)}
                      className="px-1 text-xs font-medium text-foreground-muted hover:text-foreground hover:underline"
                    >
                      +{eventos.length - MAX_EN_CELDA} más
                    </button>
                  </li>
                )}
              </ul>
            </li>
          )
        })}
      </ol>
    </div>
  )
}

export function VistaSemana({ fecha, hoy, porDia, onAbrir, onIrADia }: VistaProps) {
  return (
    <ol className="grid gap-3 md:grid-cols-7 md:gap-2">
      {semana(fecha).map((dia) => {
        const eventos = porDia.get(dia) ?? []
        return (
          <li key={dia} className={cn('u-card min-w-0 p-2', dia === hoy && 'border-foreground')}>
            <button
              type="button"
              onClick={() => onIrADia(dia)}
              aria-current={dia === hoy ? 'date' : undefined}
              className="mb-2 w-full rounded-sm px-1 text-left text-sm font-medium text-foreground first-letter:uppercase hover:underline"
            >
              {formatearDiaCorto(dia)}
              {dia === hoy && <span className="ml-1 text-xs font-normal text-foreground-muted">(hoy)</span>}
            </button>
            {eventos.length === 0 ? (
              <p className="px-1 text-xs text-foreground-muted">Sin eventos</p>
            ) : (
              <ul className="space-y-1.5">
                {eventos.map((evento) => (
                  <li key={evento.id}>
                    <EventoTarjeta evento={evento} onAbrir={onAbrir} />
                  </li>
                ))}
              </ul>
            )}
          </li>
        )
      })}
    </ol>
  )
}

export function VistaDia({ fecha, porDia, onAbrir }: Omit<VistaProps, 'onIrADia' | 'hoy'>) {
  const eventos = porDia.get(fecha) ?? []
  const todoElDia = eventos.filter((e) => horaDeEvento(e) === null)
  const conHora = eventos.filter((e) => horaDeEvento(e) !== null)
  if (eventos.length === 0) {
    return (
      <section className="u-card px-6 py-12 text-center">
        <p className="font-medium text-foreground">Sin eventos este día</p>
        <p className="mt-1 text-sm text-foreground-muted">Revisa los filtros o cambia de día.</p>
      </section>
    )
  }
  return (
    <div className="space-y-6">
      {todoElDia.length > 0 && (
        <section aria-labelledby="todo-el-dia">
          <h2 id="todo-el-dia" className="mb-2 text-xs font-medium tracking-wide text-foreground-muted uppercase">
            Todo el día
          </h2>
          <ul className="grid gap-2 sm:grid-cols-2">
            {todoElDia.map((evento) => (
              <li key={evento.id}>
                <EventoTarjeta evento={evento} onAbrir={onAbrir} />
              </li>
            ))}
          </ul>
        </section>
      )}
      {conHora.length > 0 && (
        <section aria-labelledby="con-hora">
          <h2 id="con-hora" className="mb-2 text-xs font-medium tracking-wide text-foreground-muted uppercase">
            Con hora
          </h2>
          <ol className="space-y-2 border-l border-border pl-4">
            {conHora.map((evento) => (
              <li key={evento.id} className="relative">
                <span className="absolute top-3 -left-[1.3rem] size-2 rounded-full bg-border-strong" aria-hidden="true" />
                <EventoTarjeta evento={evento} onAbrir={onAbrir} />
              </li>
            ))}
          </ol>
        </section>
      )}
    </div>
  )
}
