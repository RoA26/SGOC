import { useId, useState, type CSSProperties, type ReactNode } from 'react'
import { cn } from '@/lib/cn'
import { FragmentoExistencias, FragmentoOrden, FragmentoRecepcion, FragmentoSolicitud } from './fragmentos'

interface Etapa {
  nombre: string
  /** Frase grande de la etapa: hace de titular mientras está seleccionada. */
  frase: string
  detalle: string
  fragmento: ReactNode
}

const ETAPAS: readonly Etapa[] = [
  {
    nombre: 'Solicitud',
    frase: 'Alguien pide lo que hace falta.',
    detalle: 'Un trabajador registra los productos, las cantidades y el motivo. El costo estimado sale del catálogo.',
    fragmento: <FragmentoSolicitud />,
  },
  {
    nombre: 'Aprobación',
    frase: 'Quien administra decide.',
    detalle: 'El administrador comercial aprueba o rechaza con un comentario. Quien pidió ve la respuesta en su panel.',
    fragmento: <FragmentoSolicitud aprobada />,
  },
  {
    nombre: 'Orden',
    frase: 'La orden sale al proveedor.',
    detalle: 'Desde la solicitud aprobada se crea la orden en borrador y se emite. El documento queda en PDF y Word.',
    fragmento: <FragmentoOrden />,
  },
  {
    nombre: 'Recepción',
    frase: 'Se recibe lo que llegó.',
    detalle: 'Quien recibe anota cantidades e incidencias: faltantes, daños, excedentes o un producto equivocado. Una orden puede llegar en varias entregas.',
    fragmento: <FragmentoRecepcion />,
  },
  {
    nombre: 'Existencias',
    frase: 'El inventario se pone al día.',
    detalle: 'Cada recepción suma al kárdex del producto y avisa cuando algo queda en su mínimo o sin existencias.',
    fragmento: <FragmentoExistencias />,
  },
]

/**
 * Recorrido de una compra: un control deslizante (teclado: flechas, Inicio, Fin) o los nombres de
 * las etapas cambian a la vez la frase y el fragmento de la aplicación. No depende del scroll.
 */
export function FlujoInteractivo() {
  const [indice, setIndice] = useState(0)
  const idRango = useId()
  // El índice lo limitan el rango (0…último) y los botones: siempre existe.
  const etapa = ETAPAS[indice]!
  const ultimo = ETAPAS.length - 1
  const avance = (indice / ultimo) * 100

  return (
    <div className="grid grid-cols-[minmax(0,1fr)] overflow-hidden rounded-lg lg:grid-cols-2">
      {/* Campo de la frase (oscuro, de marca) */}
      <div className="order-2 flex flex-col justify-between gap-10 bg-brand-panel p-6 text-brand-panel-foreground sm:p-10 lg:order-1 lg:min-h-[30rem]">
        <div key={`frase-${indice}`} className="u-cambio" aria-live="polite">
          <p className="font-serif text-[clamp(2.25rem,4vw,3.75rem)] leading-[1.02] text-balance">{etapa.frase}</p>
          <p className="mt-5 max-w-md text-base leading-relaxed text-brand-panel-muted">{etapa.detalle}</p>
        </div>
        <div>
          <label htmlFor={idRango} className="sr-only">
            Etapa de la compra
          </label>
          <input
            id={idRango}
            type="range"
            min={0}
            max={ultimo}
            step={1}
            value={indice}
            onChange={(event) => setIndice(Number(event.target.value))}
            aria-valuetext={`${etapa.nombre} (${indice + 1} de ${ETAPAS.length})`}
            className="u-rango w-full"
            style={{ '--avance': `${avance}%` } as CSSProperties}
          />
          <ol className="mt-3 grid grid-cols-2 gap-x-4 gap-y-1 sm:grid-cols-5">
            {ETAPAS.map((item, i) => (
              <li key={item.nombre}>
                <button
                  type="button"
                  onClick={() => setIndice(i)}
                  aria-pressed={i === indice}
                  className={cn(
                    'rounded-sm py-1 text-left text-[0.9375rem] transition-colors duration-150',
                    i === indice ? 'text-brand-panel-foreground' : 'text-brand-panel-muted hover:text-brand-panel-foreground',
                  )}
                >
                  {item.nombre}
                </button>
              </li>
            ))}
          </ol>
        </div>
      </div>

      {/* Campo del fragmento, a tamaño real */}
      <div className="order-1 grid grid-cols-[minmax(0,1fr)] place-items-center bg-surface-muted p-5 sm:p-10 lg:order-2">
        <div key={`fragmento-${indice}`} className="u-cambio w-full max-w-[27.5rem]">
          {etapa.fragmento}
        </div>
      </div>
    </div>
  )
}
