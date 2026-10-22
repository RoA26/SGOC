import { useState, type ReactNode } from 'react'
import { cn } from '@/lib/cn'
import { HojaComprobante, HojaHistorial, HojaOrden } from './fragmentos'

const DOCUMENTOS: readonly { nombre: string; formato: string; hoja: ReactNode }[] = [
  { nombre: 'Orden de compra', formato: 'PDF y Word, con versión y huella del documento', hoja: <HojaOrden /> },
  { nombre: 'Comprobante de venta', formato: 'PDF interno del punto de venta (no es factura electrónica)', hoja: <HojaComprobante /> },
  { nombre: 'Historial de la orden', formato: 'En pantalla: cada paso con su fecha y su autor', hoja: <HojaHistorial /> },
]

/** Índice de documentos a la izquierda y la hoja elegida, a tamaño real, sobre su escenario. */
export function Documentos({ tituloId }: { tituloId: string }) {
  const [elegido, setElegido] = useState(0)
  // El índice solo lo fijan los botones de la propia lista: siempre existe.
  const documento = DOCUMENTOS[elegido]!

  return (
    <div className="grid grid-cols-[minmax(0,1fr)] gap-8 lg:grid-cols-12 lg:gap-10">
      <div className="lg:col-span-5">
        <h2 id={tituloId} className="text-[1.75rem] leading-tight font-semibold tracking-tight text-balance">
          Lo que queda por escrito
        </h2>
        <p className="mt-3 max-w-md text-base leading-relaxed text-foreground">
          Cada compra deja documentos que se pueden descargar, enviar al proveedor o revisar después.
        </p>
        <ul className="mt-8 flex snap-x gap-4 overflow-x-auto pb-2 lg:block lg:space-y-4 lg:overflow-visible lg:pb-0">
          {DOCUMENTOS.map((item, i) => (
            <li key={item.nombre} className="shrink-0 snap-start">
              <button
                type="button"
                onClick={() => setElegido(i)}
                aria-pressed={i === elegido}
                className={cn(
                  'group rounded-sm text-left transition-colors duration-150',
                  i === elegido ? 'text-foreground' : 'text-foreground-muted hover:text-foreground',
                )}
              >
                <span className="block font-serif text-[clamp(1.75rem,3vw,2.75rem)] leading-[1.1]">{item.nombre}</span>
                <span className={cn('mt-1 hidden text-sm lg:block', i === elegido ? 'text-foreground-muted' : 'invisible')}>
                  {item.formato}
                </span>
              </button>
            </li>
          ))}
        </ul>
        <p className="mt-2 text-sm text-foreground-muted lg:hidden">{documento.formato}</p>
      </div>
      <div className="relative h-[26rem] overflow-hidden rounded-lg bg-surface-muted sm:h-[32rem] lg:col-span-7 lg:h-[36rem]">
        <div key={documento.nombre} className="u-cambio absolute top-6 left-4 sm:top-12 sm:left-10 lg:top-16 lg:left-14">
          <div className="rounded-sm border border-border">{documento.hoja}</div>
        </div>
      </div>
    </div>
  )
}
