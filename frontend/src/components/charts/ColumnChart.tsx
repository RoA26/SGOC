import { useId, useState, type KeyboardEvent } from 'react'
import { formatearCompacto } from '@/lib/numeros'
import { etiquetaCorta, etiquetaLarga } from '@/lib/periodo'
import type { Granularidad, Punto } from '@/services/analiticaService'
import { escalaLimpia } from './escala'
import { useAnchoContenedor } from './useAnchoContenedor'

interface ColumnChartProps {
  puntos: Punto[]
  granularidad: Granularidad
  /** Unidad para el tooltip y el resumen accesible ("órdenes", "recepciones"). */
  unidad: (valor: number) => string
  /** Descripción para lectores de pantalla. */
  descripcion: string
}

const ALTO_GRAFICA = 168
const BANDA_EJE_X = 26
const MARGEN_SUPERIOR = 18
const MARGEN_IZQUIERDO = 40
const MARGEN_DERECHO = 8
const GROSOR_MAXIMO = 24
const SEPARACION = 2
const RADIO = 4
/** Hueco mínimo por etiqueta del eje X («22 de sept»): con menos, se omiten etiquetas intermedias. */
const ANCHO_POR_ETIQUETA = 72

/**
 * Columnas de una serie temporal (una sola serie: un color, sin leyenda; el título la nombra).
 * Barras finas (≤ 24 px) que crecen desde la línea base con el extremo redondeado, cuadrícula
 * de líneas finas y solo el máximo etiquetado. Puntero y teclado (flechas) muestran el valor de
 * cada día; la vista de tabla del ChartCard da el resto sin depender de ellos.
 */
export function ColumnChart({ puntos, granularidad, unidad, descripcion }: ColumnChartProps) {
  const { ref, ancho } = useAnchoContenedor<HTMLDivElement>()
  const [activo, setActivo] = useState<number | null>(null)
  const tooltipId = useId()

  const n = puntos.length
  const anchoGrafica = Math.max(1, ancho - MARGEN_IZQUIERDO - MARGEN_DERECHO)
  const banda = anchoGrafica / Math.max(1, n)
  const grosor = Math.max(1, Math.min(GROSOR_MAXIMO, banda - SEPARACION))
  const maximo = puntos.reduce((max, punto) => Math.max(max, punto.valor), 0)
  const { tope, marcas } = escalaLimpia(maximo)
  const y = (valor: number) => MARGEN_SUPERIOR + ALTO_GRAFICA - (valor / tope) * ALTO_GRAFICA
  const xCentro = (indice: number) => MARGEN_IZQUIERDO + banda * indice + banda / 2
  const indiceMaximo = maximo > 0 ? puntos.findIndex((punto) => punto.valor === maximo) : -1

  const maxEtiquetas = Math.max(2, Math.floor(anchoGrafica / ANCHO_POR_ETIQUETA))
  const paso = n <= maxEtiquetas ? 1 : Math.ceil(n / maxEtiquetas)
  // Cada `paso` puntos y siempre el último (hoy); si el último queda pegado al anterior, lo sustituye.
  const ultimoMultiplo = Math.floor((n - 1) / paso) * paso
  const sustituido = n - 1 - ultimoMultiplo < paso / 2 ? ultimoMultiplo : -1
  const conEtiqueta = (indice: number) => indice === n - 1 || (indice % paso === 0 && indice !== sustituido)

  function teclado(event: KeyboardEvent<SVGSVGElement>) {
    if (n === 0) return
    const actual = activo ?? n - 1
    const siguiente =
      event.key === 'ArrowRight' ? Math.min(n - 1, actual + 1)
      : event.key === 'ArrowLeft' ? Math.max(0, actual - 1)
      : event.key === 'Home' ? 0
      : event.key === 'End' ? n - 1
      : null
    if (siguiente === null) return
    event.preventDefault()
    setActivo(siguiente)
  }

  const puntoActivo = activo !== null ? puntos[activo] : undefined
  const altoTotal = MARGEN_SUPERIOR + ALTO_GRAFICA + BANDA_EJE_X

  return (
    <div ref={ref} className="relative w-full min-w-0 overflow-hidden" onPointerLeave={() => setActivo(null)}>
      <svg
        width={ancho}
        height={altoTotal}
        role="img"
        aria-label={`${descripcion}. Usa las flechas para recorrer los valores.`}
        aria-describedby={puntoActivo ? tooltipId : undefined}
        tabIndex={0}
        onKeyDown={teclado}
        onFocus={() => setActivo((actual) => actual ?? (n > 0 ? n - 1 : null))}
        onBlur={() => setActivo(null)}
        className="block rounded-sm focus-visible:outline-2 focus-visible:outline-offset-4 focus-visible:outline-focus"
      >
        {/* Cuadrícula: líneas finas y continuas, un paso por encima de la superficie. */}
        {marcas.map((marca) => (
          <g key={marca}>
            <line
              x1={MARGEN_IZQUIERDO}
              x2={ancho - MARGEN_DERECHO}
              y1={y(marca)}
              y2={y(marca)}
              stroke="var(--nexora-color-border)"
              strokeWidth={1}
              shapeRendering="crispEdges"
            />
            <text
              x={MARGEN_IZQUIERDO - 8}
              y={y(marca)}
              dy="0.32em"
              textAnchor="end"
              className="fill-foreground-muted text-[11px] tabular-nums"
            >
              {formatearCompacto(marca)}
            </text>
          </g>
        ))}

        {puntos.map((punto, indice) => {
          const alto = (punto.valor / tope) * ALTO_GRAFICA
          const x = xCentro(indice) - grosor / 2
          const base = MARGEN_SUPERIOR + ALTO_GRAFICA
          const r = Math.min(RADIO, grosor / 2, alto)
          return (
            <g key={punto.fecha}>
              {punto.valor > 0 && (
                <path
                  d={`M${x},${base} V${base - alto + r} Q${x},${base - alto} ${x + r},${base - alto} H${x + grosor - r} Q${x + grosor},${base - alto} ${x + grosor},${base - alto + r} V${base} Z`}
                  fill="var(--nexora-viz-series-1)"
                  fillOpacity={activo === indice ? 0.75 : 1}
                />
              )}
              {conEtiqueta(indice) && (
                <text
                  x={xCentro(indice)}
                  y={base + 17}
                  textAnchor={indice === 0 && n > 1 ? 'start' : indice === n - 1 && n > 1 ? 'end' : 'middle'}
                  className="fill-foreground-muted text-[11px]"
                >
                  {etiquetaCorta(punto.fecha, granularidad)}
                </text>
              )}
              {/* Zona sensible: toda la banda, mucho mayor que la barra. */}
              <rect
                x={MARGEN_IZQUIERDO + banda * indice}
                y={MARGEN_SUPERIOR}
                width={banda}
                height={ALTO_GRAFICA}
                fill="transparent"
                onPointerEnter={() => setActivo(indice)}
              />
            </g>
          )
        })}

        {/* Línea base */}
        <line
          x1={MARGEN_IZQUIERDO}
          x2={ancho - MARGEN_DERECHO}
          y1={MARGEN_SUPERIOR + ALTO_GRAFICA}
          y2={MARGEN_SUPERIOR + ALTO_GRAFICA}
          stroke="var(--nexora-color-border-strong)"
          strokeWidth={1}
          shapeRendering="crispEdges"
        />

        {/* Solo el máximo lleva etiqueta directa. */}
        {indiceMaximo >= 0 && activo === null && (
          <text
            x={xCentro(indiceMaximo)}
            y={y(maximo) - 6}
            textAnchor={indiceMaximo === 0 ? 'start' : indiceMaximo === n - 1 ? 'end' : 'middle'}
            className="fill-foreground text-[11px] font-medium tabular-nums"
          >
            {formatearCompacto(maximo)}
          </text>
        )}
      </svg>

      {puntoActivo && activo !== null && (
        <div
          id={tooltipId}
          role="status"
          className="pointer-events-none absolute top-0 z-10 -translate-x-1/2 rounded-md border border-border bg-surface px-2.5 py-1.5 text-xs whitespace-nowrap shadow-card"
          style={{ left: Math.min(Math.max(xCentro(activo), 70), ancho - 70) }}
        >
          <p className="font-semibold text-foreground tabular-nums">{unidad(puntoActivo.valor)}</p>
          <p className="text-foreground-muted">{etiquetaLarga(puntoActivo.fecha, granularidad)}</p>
        </div>
      )}
    </div>
  )
}
