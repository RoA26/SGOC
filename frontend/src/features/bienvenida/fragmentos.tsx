import { AlertTriangle, CalendarDays, CheckCircle2, ClipboardList, FileDown, Link2, MessageSquare, ScanBarcode, Ticket } from 'lucide-react'
import type { ReactNode } from 'react'
import { EstadoStockBadge } from '@/features/inventario/EstadoStockBadge'
import { EstadoOrdenBadge } from '@/features/ordenes/EstadoOrdenBadge'
import { EstadoBadge } from '@/features/solicitudes/EstadoBadge'
import { cn } from '@/lib/cn'
import { formatearCOP } from '@/lib/moneda'
import {
  ADMINISTRADOR_DEMO,
  CAMBIO_VENTA_DEMO,
  CODIGO_BARRAS_DEMO,
  EMPRESA_DEMO,
  FECHA_DEMO,
  INCIDENCIA_DEMO,
  LINEAS_DEMO,
  PROVEEDOR_DEMO,
  SOLICITANTE_DEMO,
  TOTAL_DEMO,
  TOTAL_VENTA_DEMO,
  UNIDADES_PEDIDAS,
  UNIDADES_RECIBIDAS,
  VENTA_DEMO,
  subtotal,
} from './datosDemo'

/*
 * Fragmentos de la interfaz de Nexora para la página pública: piezas a tamaño real, con los
 * componentes de la aplicación (insignias de estado, formato de moneda) y datos de ejemplo.
 * No son interactivos: ilustran lo que se ve dentro del sistema.
 */

/** Marco común de un fragmento: superficie de la aplicación con su borde (sin sombra en reposo). */
export function Pieza({ children, className }: { children: ReactNode; className?: string }) {
  return (
    <div data-ui className={cn('min-w-0 rounded-lg border border-border bg-surface text-left text-sm text-foreground', className)}>
      {children}
    </div>
  )
}

function Cabecera({ titulo, detalle, estado }: { titulo: ReactNode; detalle: ReactNode; estado: ReactNode }) {
  return (
    <div className="flex items-start justify-between gap-3 border-b border-border px-4 py-3">
      <div className="min-w-0">
        <p className="font-semibold tracking-tight">{titulo}</p>
        <p className="mt-0.5 truncate text-xs text-foreground-muted">{detalle}</p>
      </div>
      <span className="shrink-0">{estado}</span>
    </div>
  )
}

function Lineas({ conCosto = false, conRecibido = false }: { conCosto?: boolean; conRecibido?: boolean }) {
  return (
    <table className="w-full text-left">
      <thead className="text-xs text-foreground-muted">
        <tr>
          <th scope="col" className="px-4 pt-3 pb-1.5 font-medium">
            Producto
          </th>
          <th scope="col" className="px-2 pt-3 pb-1.5 text-right font-medium">
            {conRecibido ? 'Recibido' : 'Cant.'}
          </th>
          {conCosto && (
            <th scope="col" className="px-4 pt-3 pb-1.5 text-right font-medium">
              Subtotal
            </th>
          )}
        </tr>
      </thead>
      <tbody>
        {LINEAS_DEMO.map((linea) => (
          <tr key={linea.sku} className="border-t border-border/70">
            <td className="px-4 py-2">
              {linea.producto}
              <span className="block text-xs text-foreground-muted">{linea.sku}</span>
            </td>
            <td className="px-2 py-2 text-right tabular-nums">
              {conRecibido ? `${linea.recibido} / ${linea.cantidad}` : linea.cantidad}
            </td>
            {conCosto && <td className="px-4 py-2 text-right tabular-nums">{formatearCOP(subtotal(linea))}</td>}
          </tr>
        ))}
      </tbody>
    </table>
  )
}

function Pie({ children }: { children: ReactNode }) {
  return <div className="flex items-center justify-between gap-3 border-t border-border px-4 py-3 text-xs text-foreground-muted">{children}</div>
}

/** Paso 1 y 2: la solicitud interna, pendiente o ya revisada. */
export function FragmentoSolicitud({ aprobada = false }: { aprobada?: boolean }) {
  return (
    <Pieza>
      <Cabecera
        titulo="Solicitud #1"
        detalle={`${SOLICITANTE_DEMO} · ${EMPRESA_DEMO}`}
        estado={<EstadoBadge estado={aprobada ? 'APROBADA' : 'PENDIENTE'} />}
      />
      <p className="px-4 pt-3 text-xs text-foreground-muted">Justificación</p>
      <p className="px-4 pt-0.5">Reposición de leche y huevos para el fin de semana.</p>
      <Lineas />
      {aprobada ? (
        <p className="mx-4 mb-3 flex items-start gap-2 rounded-md bg-success-surface px-3 py-2 text-xs text-success">
          <MessageSquare className="mt-px size-3.5 shrink-0" aria-hidden="true" />
          {ADMINISTRADOR_DEMO}: «Aprobada: generar la orden hoy».
        </p>
      ) : null}
      <Pie>
        <span>Total estimado</span>
        <span className="text-sm font-semibold text-foreground tabular-nums">{formatearCOP(TOTAL_DEMO)}</span>
      </Pie>
    </Pieza>
  )
}

/** Paso 3: la orden emitida al proveedor. */
export function FragmentoOrden() {
  return (
    <Pieza>
      <Cabecera
        titulo="Orden de compra OC-00003"
        detalle={`${PROVEEDOR_DEMO.razonSocial} · NIT ${PROVEEDOR_DEMO.nit}`}
        estado={<EstadoOrdenBadge estado="EMITIDA" />}
      />
      <Lineas conCosto />
      <Pie>
        <span className="inline-flex items-center gap-1.5">
          <FileDown className="size-3.5" aria-hidden="true" />
          PDF y Word · versión 1
        </span>
        <span className="text-sm font-semibold text-foreground tabular-nums">{formatearCOP(TOTAL_DEMO)}</span>
      </Pie>
    </Pieza>
  )
}

/** Paso 4: la recepción, con la diferencia anotada. */
export function FragmentoRecepcion() {
  return (
    <Pieza>
      <Cabecera
        titulo="Recepción de OC-00003"
        detalle={`Recibió ${SOLICITANTE_DEMO} · ${UNIDADES_RECIBIDAS} de ${UNIDADES_PEDIDAS} unidades`}
        estado={<EstadoOrdenBadge estado="RECIBIDA_PARCIALMENTE" />}
      />
      <Lineas conRecibido />
      <p className="mx-4 mb-3 flex items-start gap-2 rounded-md border border-warning-border bg-warning-surface px-3 py-2 text-xs text-warning">
        <AlertTriangle className="mt-px size-3.5 shrink-0" aria-hidden="true" />
        {INCIDENCIA_DEMO.tipo} · {INCIDENCIA_DEMO.producto} · {INCIDENCIA_DEMO.cantidad} u. — {INCIDENCIA_DEMO.nota}.
      </p>
    </Pieza>
  )
}

/** Paso 5: las existencias, ya actualizadas por la recepción. */
export function FragmentoExistencias() {
  return (
    <Pieza>
      <Cabecera titulo="Existencias" detalle="Actualizadas con la recepción de OC-00003" estado={null} />
      <ul>
        {LINEAS_DEMO.map((linea) => (
          <li key={linea.sku} className="flex items-center justify-between gap-3 border-t border-border/70 px-4 py-2.5 first:border-t-0">
            <span className="min-w-0">
              {linea.producto}
              <span className="block text-xs text-foreground-muted tabular-nums">
                +{linea.recibido} → saldo {linea.saldo} · mín. {linea.minimo}
              </span>
            </span>
            <EstadoStockBadge estado={linea.saldo <= linea.minimo ? 'CRITICO' : 'NORMAL'} />
          </li>
        ))}
      </ul>
    </Pieza>
  )
}

/** El documento de la orden tal como lo genera el backend (PDF y Word), en su versión de muestra. */
export function HojaOrden() {
  return (
    <div data-ui className="w-[34rem] max-w-none bg-surface p-8 text-left text-[0.8125rem] text-foreground">
      <div className="flex items-start justify-between gap-6">
        <div>
          <p className="text-xl font-semibold tracking-tight">ORDEN DE COMPRA</p>
          <p className="text-foreground-muted">OC-00003 · Emitida el {FECHA_DEMO}</p>
        </div>
        <p className="text-right text-foreground-muted">
          {EMPRESA_DEMO}
          <br />
          Código empresarial MVSA01
        </p>
      </div>
      <div className="mt-6 grid grid-cols-2 gap-6">
        <div>
          <p className="text-xs font-semibold">PROVEEDOR</p>
          <p className="mt-1">{PROVEEDOR_DEMO.razonSocial}</p>
          <p className="text-foreground-muted">NIT {PROVEEDOR_DEMO.nit}</p>
        </div>
        <div>
          <p className="text-xs font-semibold">ENTREGA</p>
          <p className="mt-1">Fecha estimada: por programar</p>
          <p className="text-foreground-muted">Origen: solicitud interna de compra #1</p>
        </div>
      </div>
      <table className="mt-6 w-full">
        <thead className="text-xs">
          <tr className="border-b border-border-strong">
            <th scope="col" className="py-1.5 text-left font-semibold">
              Producto
            </th>
            <th scope="col" className="py-1.5 text-right font-semibold">
              Cantidad
            </th>
            <th scope="col" className="py-1.5 text-right font-semibold">
              Precio unitario
            </th>
            <th scope="col" className="py-1.5 text-right font-semibold">
              Subtotal
            </th>
          </tr>
        </thead>
        <tbody>
          {LINEAS_DEMO.map((linea) => (
            <tr key={linea.sku} className="border-b border-border">
              <td className="py-2">{linea.producto}</td>
              <td className="py-2 text-right tabular-nums">{linea.cantidad}</td>
              <td className="py-2 text-right tabular-nums">{formatearCOP(linea.costo)}</td>
              <td className="py-2 text-right tabular-nums">{formatearCOP(subtotal(linea))}</td>
            </tr>
          ))}
        </tbody>
        <tfoot>
          <tr>
            <td colSpan={3} className="pt-3 text-right font-semibold">
              TOTAL COP
            </td>
            <td className="pt-3 text-right font-semibold tabular-nums">{formatearCOP(TOTAL_DEMO)}</td>
          </tr>
        </tfoot>
      </table>
      <p className="mt-6 text-xs text-foreground-muted">
        Valores en pesos colombianos (COP), sin impuestos ni descuentos. Documento versión 1 · Huella SHA-256 2b534d61…
      </p>
    </div>
  )
}

/** Comprobante interno de venta (PDF). No es factura electrónica. */
export function HojaComprobante() {
  return (
    <div data-ui className="w-[22rem] max-w-none bg-surface p-7 text-left text-[0.8125rem] text-foreground">
      <p className="text-base font-semibold tracking-tight">COMPROBANTE INTERNO DE VENTA</p>
      <p className="text-foreground-muted">
        {VENTA_DEMO.numero} · {FECHA_DEMO}
      </p>
      <p className="mt-1 text-foreground-muted">{EMPRESA_DEMO}</p>
      <table className="mt-5 w-full">
        <tbody>
          {VENTA_DEMO.lineas.map((linea) => (
            <tr key={linea.producto} className="border-b border-border">
              <td className="py-2">
                {linea.producto}
                <span className="block text-xs text-foreground-muted tabular-nums">
                  {linea.cantidad} × {formatearCOP(linea.precio)}
                </span>
              </td>
              <td className="py-2 text-right align-top tabular-nums">{formatearCOP(linea.cantidad * linea.precio)}</td>
            </tr>
          ))}
        </tbody>
      </table>
      <dl className="mt-3 space-y-1 tabular-nums">
        <div className="flex justify-between font-semibold">
          <dt>Total</dt>
          <dd>{formatearCOP(TOTAL_VENTA_DEMO)}</dd>
        </div>
        <div className="flex justify-between text-foreground-muted">
          <dt>Efectivo</dt>
          <dd>{formatearCOP(VENTA_DEMO.recibido)}</dd>
        </div>
        <div className="flex justify-between text-foreground-muted">
          <dt>Cambio</dt>
          <dd>{formatearCOP(CAMBIO_VENTA_DEMO)}</dd>
        </div>
      </dl>
      <p className="mt-5 text-xs text-foreground-muted">Comprobante interno: no tiene validez ante la DIAN.</p>
    </div>
  )
}

const HISTORIAL = [
  { evento: 'Solicitud creada', quien: SOLICITANTE_DEMO, icono: ClipboardList },
  { evento: 'Solicitud aprobada', quien: ADMINISTRADOR_DEMO, icono: CheckCircle2 },
  { evento: 'Orden emitida al proveedor', quien: ADMINISTRADOR_DEMO, icono: FileDown },
  { evento: `Recepción parcial: ${UNIDADES_RECIBIDAS} unidades, 1 incidencia`, quien: SOLICITANTE_DEMO, icono: AlertTriangle },
] as const

/** Historial de la orden: cada paso queda con su autor. */
export function HojaHistorial() {
  return (
    <div data-ui className="w-[28rem] max-w-none bg-surface p-7 text-left text-[0.8125rem] text-foreground">
      <p className="text-base font-semibold tracking-tight">Historial de OC-00003</p>
      <ol className="mt-4 space-y-4 border-l border-border pl-5">
        {HISTORIAL.map(({ evento, quien, icono: Icono }) => (
          <li key={evento} className="relative">
            <span className="absolute top-0.5 -left-[1.6rem] grid size-5 place-items-center rounded-full border border-border bg-surface">
              <Icono className="size-3 text-foreground-muted" aria-hidden="true" />
            </span>
            <p className="font-medium">{evento}</p>
            <p className="text-xs text-foreground-muted">
              {FECHA_DEMO} · {quien}
            </p>
          </li>
        ))}
      </ol>
    </div>
  )
}

/* --- Demostraciones pequeñas de los módulos --- */

export function PanelPuntoVenta() {
  return (
    <Pieza className="w-full">
      <div className="flex items-center gap-2 border-b border-border px-3 py-2 text-xs text-foreground-muted">
        <ScanBarcode className="size-4" aria-hidden="true" />
        <span className="tabular-nums">{CODIGO_BARRAS_DEMO}</span>
        <span className="ml-auto text-success">Agregado</span>
      </div>
      <ul className="px-3 py-1.5">
        {VENTA_DEMO.lineas.map((linea) => (
          <li key={linea.producto} className="flex justify-between py-1 tabular-nums">
            <span>
              {linea.cantidad} × {linea.producto}
            </span>
            <span>{formatearCOP(linea.cantidad * linea.precio)}</span>
          </li>
        ))}
      </ul>
      <div className="flex justify-between border-t border-border px-3 py-2 font-semibold tabular-nums">
        <span>Total</span>
        <span>{formatearCOP(TOTAL_VENTA_DEMO)}</span>
      </div>
    </Pieza>
  )
}

export function PanelCalendario() {
  const filas = [
    { dia: 'jue 22', hora: '9:00 – 11:00', orden: 'OC-00003', proveedor: PROVEEDOR_DEMO.razonSocial, confirmada: true },
    { dia: 'vie 23', hora: 'Todo el día', orden: 'OC-00002', proveedor: 'Bebidas del Río S.A.S. (demo)', confirmada: false },
  ]
  return (
    <Pieza className="w-full">
      <p className="flex items-center gap-1.5 border-b border-border px-3 py-2 text-xs text-foreground-muted">
        <CalendarDays className="size-3.5" aria-hidden="true" />
        Entregas de la semana
      </p>
      <ul className="px-3 py-1">
        {filas.map((fila) => (
          <li key={fila.orden} className="grid grid-cols-[3.25rem_minmax(0,1fr)] gap-2 py-1.5">
            <span className="text-xs leading-5 text-foreground-muted">{fila.dia}</span>
            <span className="min-w-0">
              <span className="block truncate">
                {fila.orden} · {fila.hora}
              </span>
              <span className="block truncate text-xs text-foreground-muted">
                {fila.proveedor} · {fila.confirmada ? 'confirmada' : 'estimada'}
              </span>
            </span>
          </li>
        ))}
      </ul>
    </Pieza>
  )
}

export function PanelExistencias() {
  return (
    <Pieza className="w-full">
      <p className="flex items-center gap-1.5 border-b border-border px-3 py-2 text-xs text-foreground-muted">
        <ScanBarcode className="size-3.5" aria-hidden="true" />
        Kárdex de Leche entera 1 L
      </p>
      <ul className="px-3 py-1.5 text-xs tabular-nums">
        <li className="flex justify-between py-1">
          <span>Recepción OC-00003</span>
          <span>+6 → 66</span>
        </li>
        <li className="flex justify-between py-1">
          <span>Venta {VENTA_DEMO.numero}</span>
          <span>−2 → 64</span>
        </li>
        <li className="flex justify-between py-1 text-foreground-muted">
          <span>Ajuste: conteo físico</span>
          <span>−1 → 63</span>
        </li>
      </ul>
    </Pieza>
  )
}

export function PanelInvitacion() {
  return (
    <Pieza className="w-full p-3">
      <p className="flex items-center gap-1.5 text-xs text-foreground-muted">
        <Ticket className="size-3.5" aria-hidden="true" />
        Invitación de un solo uso · caduca en 72 horas
      </p>
      <p className="mt-2 rounded-md bg-surface-muted px-3 py-2 font-mono text-[0.9375rem] tracking-wider">7KQ2-M9XA-4HPR-T3VW</p>
      <p className="mt-2 inline-flex items-center gap-1.5 text-xs text-foreground-muted">
        <Link2 className="size-3.5" aria-hidden="true" />
        /registro?codigo=7KQ2-M9XA-4HPR-T3VW
      </p>
    </Pieza>
  )
}
