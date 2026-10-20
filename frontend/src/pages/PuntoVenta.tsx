import axios from 'axios'
import {
  BadgePercent,
  CheckCircle2,
  CircleAlert,
  FileDown,
  KeyRound,
  Loader2,
  Minus,
  Plus,
  ReceiptText,
  ScanBarcode,
  Search,
  ShoppingCart,
  TriangleAlert,
  X,
} from 'lucide-react'
import { useCallback, useEffect, useMemo, useReducer, useRef, useState, type FormEvent } from 'react'
import { Link } from 'react-router-dom'
import { PageHeader } from '@/components/layout/PageHeader'
import { EstadoStockBadge } from '@/features/inventario/EstadoStockBadge'
import { aPedido, carritoReducer, excedeExistencias, faltaPorPagar, validarDescuento, type LineaCarrito } from '@/features/ventas/carrito'
import { ETIQUETA_MEDIO, etiquetaIva } from '@/features/ventas/etiquetas'
import { useRecurso } from '@/hooks/useRecurso'
import { nuevaClaveIdempotencia } from '@/lib/claves'
import { cn } from '@/lib/cn'
import { getApiErrorMessage } from '@/lib/errors'
import { formatearImporte, formatearPorcentaje } from '@/lib/moneda'
import { reporteService } from '@/services/reporteService'
import {
  ventaService,
  type Cotizacion,
  type Descuento,
  type LineaPedido,
  type MedioPago,
  type ProductoVenta,
  type TipoDescuento,
  type VentaDetalle,
} from '@/services/ventaService'

const ESPERA_MS = 300
const MEDIOS: readonly MedioPago[] = ['EFECTIVO', 'TARJETA_DEBITO', 'TARJETA_CREDITO', 'TRANSFERENCIA', 'OTRO']

interface PagoEditable {
  medio: MedioPago
  valor: string
  recibido: string
  referencia: string
}

interface Problema {
  faltantes?: { productoId: number; nombre: string; solicitadas: number; disponibles: number }[]
  requiereAutorizacion?: boolean
}

function problemaDe(error: unknown): Problema {
  return axios.isAxiosError<Problema>(error) && typeof error.response?.data === 'object' && error.response.data !== null
    ? error.response.data
    : {}
}

function numero(texto: string): number {
  return Number(texto.replace(/\./g, '').replace(',', '.'))
}

/**
 * Caja: buscar o escanear productos, armar el carrito, aplicar descuentos autorizados, cobrar y
 * descargar el comprobante. Los importes que se muestran son siempre los que calcula el backend
 * (cotización); al confirmar, el backend vuelve a calcularlo todo con las existencias bloqueadas.
 */
export default function PuntoVenta() {
  const [lineas, despachar] = useReducer(carritoReducer, [])
  const [general, setGeneral] = useState<{ tipo: TipoDescuento; valor: string }>({ tipo: 'PORCENTAJE', valor: '' })
  const [codigoAutorizacion, setCodigoAutorizacion] = useState('')
  // Cada cotización y cada rechazo por existencias quedan ligados al carrito (su «firma») que los
  // produjo: si el carrito cambia, dejan de mostrarse sin necesidad de borrarlos.
  const [resultado, setResultado] = useState<{ firma: string; cotizacion?: Cotizacion; error?: string } | null>(null)
  const [pagos, setPagos] = useState<PagoEditable[]>([{ medio: 'EFECTIVO', valor: '', recibido: '', referencia: '' }])
  const [cliente, setCliente] = useState({ nombre: '', documento: '' })
  const [confirmando, setConfirmando] = useState(false)
  const [errorVenta, setErrorVenta] = useState<string | null>(null)
  const [rechazo, setRechazo] = useState<{ firma: string; faltantes: NonNullable<Problema['faltantes']> } | null>(null)
  const [venta, setVenta] = useState<VentaDetalle | null>(null)
  const [recargaHoy, setRecargaHoy] = useState(0)
  // Una clave por intento de cobro: un reintento tras un error de red no duplica la venta. Si el
  // carrito cambia, es otra venta y lleva otra clave.
  const clave = useRef<{ firma: string; valor: string } | null>(null)

  const errorGeneral = validarDescuento(general.tipo, general.valor)
  const descuentoGeneral: Descuento | undefined =
    general.valor.trim() && !errorGeneral ? { tipo: general.tipo, valor: numero(general.valor) } : undefined
  const pedido = useMemo(() => aPedido(lineas), [lineas])
  const firma = JSON.stringify([pedido, descuentoGeneral, codigoAutorizacion.trim()])

  useEffect(() => {
    const [lineasPedido, descuento, codigo] = JSON.parse(firma) as [LineaPedido[], Descuento | undefined, string]
    if (lineasPedido.length === 0) return
    const controller = new AbortController()
    const id = window.setTimeout(() => {
      ventaService
        .cotizar({ lineas: lineasPedido, descuentoGeneral: descuento ?? undefined, codigoAutorizacion: codigo || undefined }, controller.signal)
        .then((c) => setResultado({ firma, cotizacion: c }))
        .catch((error: unknown) => {
          if (!axios.isCancel(error)) setResultado({ firma, error: getApiErrorMessage(error) })
        })
    }, ESPERA_MS)
    return () => {
      window.clearTimeout(id)
      controller.abort()
    }
  }, [firma])

  const vigente = pedido.length > 0 && resultado?.firma === firma ? resultado : null
  // Mientras llega la cotización del carrito actual se conserva la anterior, atenuada.
  const cotizacion = pedido.length > 0 ? (vigente?.cotizacion ?? resultado?.cotizacion ?? null) : null
  const cotizando = pedido.length > 0 && vigente === null
  const errorCotizacion = vigente?.error ?? null
  const faltantes = rechazo?.firma === firma ? rechazo.faltantes : []

  const hoy = useRecurso(useCallback((signal: AbortSignal) => reporteService.misVentas(signal), []), String(recargaHoy))

  // Con un solo pago, su valor es el total calculado por el backend.
  const total = cotizacion?.total ?? 0
  const pagosVigentes = pagos.length === 1 ? [{ ...pagos[0]!, valor: total ? String(total) : '' }] : pagos
  const pagosNumericos = pagosVigentes.map((p) => ({ ...p, valorNumero: numero(p.valor), recibidoNumero: p.recibido ? numero(p.recibido) : undefined }))
  const falta = cotizacion ? faltaPorPagar(cotizacion.total, pagosNumericos.map((p) => ({ valor: p.valorNumero }))) : 0
  const efectivo = pagosNumericos.find((p) => p.medio === 'EFECTIVO' && p.recibidoNumero !== undefined)
  const cambio = efectivo && efectivo.recibidoNumero! >= efectivo.valorNumero ? efectivo.recibidoNumero! - efectivo.valorNumero : null
  const recibidoInsuficiente = pagosNumericos.some((p) => p.medio === 'EFECTIVO' && p.recibidoNumero !== undefined && p.recibidoNumero < p.valorNumero)

  const listo =
    lineas.length > 0 &&
    cotizacion !== null &&
    !cotizando &&
    !errorCotizacion &&
    !errorGeneral &&
    cotizacion.existenciasSuficientes &&
    (!cotizacion.requiereAutorizacion || cotizacion.autorizacionValida === true) &&
    falta === 0 &&
    !recibidoInsuficiente

  async function cobrar(evento: FormEvent) {
    evento.preventDefault()
    if (!listo || !cotizacion) return
    if (clave.current?.firma !== firma) clave.current = { firma, valor: nuevaClaveIdempotencia() }
    setConfirmando(true)
    setErrorVenta(null)
    setRechazo(null)
    try {
      const registrada = await ventaService.confirmar({
        claveIdempotencia: clave.current.valor,
        lineas: pedido,
        descuentoGeneral,
        codigoAutorizacion: codigoAutorizacion.trim() || undefined,
        pagos: pagosNumericos.map((p) => ({
          medio: p.medio,
          valor: p.valorNumero,
          ...(p.medio === 'EFECTIVO' && p.recibidoNumero !== undefined ? { recibido: p.recibidoNumero } : {}),
          ...(p.referencia.trim() ? { referencia: p.referencia.trim() } : {}),
        })),
        clienteNombre: cliente.nombre.trim() || undefined,
        clienteDocumento: cliente.documento.trim() || undefined,
      })
      setVenta(registrada)
      setRecargaHoy((n) => n + 1)
    } catch (error) {
      const problema = problemaDe(error)
      if (problema.faltantes?.length) {
        setRechazo({ firma, faltantes: problema.faltantes })
        setErrorVenta('Existencias insuficientes: ajusta las cantidades marcadas. No se registró la venta.')
      } else if (axios.isAxiosError(error) && !error.response) {
        // Puede que la venta sí se registrara: el reintento usa la misma clave y no la duplica.
        setErrorVenta('No hubo respuesta del servidor. Reintenta el cobro: si la venta ya se registró, no se duplicará.')
      } else {
        setErrorVenta(getApiErrorMessage(error))
      }
    } finally {
      setConfirmando(false)
    }
  }

  function nuevaVenta() {
    despachar({ tipo: 'vaciar' })
    setGeneral({ tipo: 'PORCENTAJE', valor: '' })
    setCodigoAutorizacion('')
    setPagos([{ medio: 'EFECTIVO', valor: '', recibido: '', referencia: '' }])
    setCliente({ nombre: '', documento: '' })
    setVenta(null)
    setErrorVenta(null)
    clave.current = null
    setRechazo(null)
  }

  return (
    <>
      <PageHeader
        eyebrow="Ventas"
        title="Punto de venta"
        description="Busca o escanea productos, cobra y entrega el comprobante. Los precios, el IVA y los totales los calcula el sistema."
        actions={
          <p className="text-sm text-foreground-muted" aria-live="polite">
            Tus ventas de hoy:{' '}
            <strong className="text-foreground">
              {hoy.datos ? `${hoy.datos.ventas} · ${formatearImporte(hoy.datos.total)}` : '—'}
            </strong>
          </p>
        }
      />

      {venta ? (
        <ResultadoVenta venta={venta} onNueva={nuevaVenta} />
      ) : (
        <div className="grid gap-6 lg:grid-cols-[minmax(0,1fr)_minmax(0,28rem)]">
          <Catalogo onAgregar={(producto) => despachar({ tipo: 'agregar', producto })} />

          <form onSubmit={(e) => void cobrar(e)} className="u-card flex flex-col gap-4 p-4 sm:p-5" aria-label="Venta en curso">
            <h2 className="flex items-center gap-2 text-lg font-semibold text-foreground">
              <ShoppingCart className="size-5" aria-hidden="true" />
              Carrito
              {lineas.length > 0 && <span className="text-sm font-normal text-foreground-muted">({lineas.length})</span>}
            </h2>

            {lineas.length === 0 ? (
              <p className="rounded-md border border-dashed border-border p-6 text-center text-sm text-foreground-muted">
                El carrito está vacío. Escanea un código o elige un producto del catálogo.
              </p>
            ) : (
              <ul className="divide-y divide-border" aria-label="Productos del carrito">
                {lineas.map((linea) => (
                  <LineaDelCarrito
                    key={linea.producto.id}
                    linea={linea}
                    cotizada={cotizacion?.lineas.find((l) => l.productoId === linea.producto.id)}
                    faltante={faltantes.find((f) => f.productoId === linea.producto.id)}
                    onCantidad={(cantidad) => despachar({ tipo: 'cantidad', productoId: linea.producto.id, cantidad })}
                    onDescuento={(descuento) => despachar({ tipo: 'descuento', productoId: linea.producto.id, descuento })}
                    onQuitar={() => despachar({ tipo: 'quitar', productoId: linea.producto.id })}
                  />
                ))}
              </ul>
            )}

            {lineas.length > 0 && (
              <>
                <fieldset className="grid gap-2">
                  <legend className="mb-1 flex items-center gap-1.5 text-sm font-medium text-foreground">
                    <BadgePercent className="size-4" aria-hidden="true" /> Descuento general (opcional)
                  </legend>
                  <div className="flex gap-2">
                    <select
                      className="u-input w-28"
                      aria-label="Tipo de descuento general"
                      value={general.tipo}
                      onChange={(e) => setGeneral({ ...general, tipo: e.target.value as TipoDescuento })}
                    >
                      <option value="PORCENTAJE">%</option>
                      <option value="VALOR">$</option>
                    </select>
                    <input
                      className="u-input flex-1"
                      inputMode="decimal"
                      aria-label="Valor del descuento general"
                      placeholder={general.tipo === 'PORCENTAJE' ? 'p. ej. 5' : 'p. ej. 2000'}
                      value={general.valor}
                      onChange={(e) => setGeneral({ ...general, valor: e.target.value })}
                      aria-invalid={errorGeneral !== null}
                    />
                  </div>
                  {errorGeneral && <p className="text-xs text-danger">{errorGeneral}</p>}
                  {cotizacion && (
                    <p className="text-xs text-foreground-muted">
                      Tu límite de descuento por línea: {formatearPorcentaje(cotizacion.limiteDescuento)}.
                    </p>
                  )}
                </fieldset>

                {cotizacion?.requiereAutorizacion && (
                  <div className="u-alert u-alert--warning grid gap-2" role="alert">
                    <p className="flex items-start gap-2 text-sm">
                      <TriangleAlert className="mt-0.5 size-4 shrink-0" aria-hidden="true" />
                      <span>{cotizacion.motivosAutorizacion.join(' ')} Pide un código al administrador comercial.</span>
                    </p>
                    <label className="flex items-center gap-2 text-sm">
                      <KeyRound className="size-4" aria-hidden="true" />
                      <span className="sr-only">Código de autorización</span>
                      <input
                        className="u-input flex-1 font-mono uppercase"
                        placeholder="ABCD-2345"
                        aria-label="Código de autorización"
                        value={codigoAutorizacion}
                        onChange={(e) => setCodigoAutorizacion(e.target.value)}
                      />
                    </label>
                    {codigoAutorizacion.trim() && cotizacion.autorizacionValida === false && (
                      <p className="text-xs text-danger">El código no existe, ya se usó, venció o no cubre esta venta.</p>
                    )}
                    {cotizacion.autorizacionValida && <p className="text-xs text-success">Código válido para esta venta.</p>}
                  </div>
                )}

                <Totales cotizacion={cotizacion} cotizando={cotizando} error={errorCotizacion} />

                <fieldset className="grid gap-2">
                  <legend className="mb-1 text-sm font-medium text-foreground">Pago</legend>
                  {pagosVigentes.map((pago, i) => (
                    <div key={i} className="grid grid-cols-[minmax(0,1fr)_minmax(0,1fr)_auto] gap-2">
                      <select
                        className="u-input"
                        aria-label={`Medio de pago ${i + 1}`}
                        value={pago.medio}
                        onChange={(e) => setPagos(pagos.map((p, j) => (j === i ? { ...p, medio: e.target.value as MedioPago } : p)))}
                      >
                        {MEDIOS.map((m) => (
                          <option key={m} value={m}>
                            {ETIQUETA_MEDIO[m]}
                          </option>
                        ))}
                      </select>
                      <input
                        className="u-input"
                        inputMode="decimal"
                        aria-label={`Valor del pago ${i + 1}`}
                        value={pago.valor}
                        readOnly={pagos.length === 1}
                        onChange={(e) => setPagos(pagos.map((p, j) => (j === i ? { ...p, valor: e.target.value } : p)))}
                      />
                      {pagos.length > 1 ? (
                        <button type="button" className="u-btn u-btn--ghost" aria-label={`Quitar pago ${i + 1}`} onClick={() => setPagos(pagos.filter((_, j) => j !== i))}>
                          <X className="size-4" aria-hidden="true" />
                        </button>
                      ) : (
                        <span />
                      )}
                      {pago.medio === 'EFECTIVO' ? (
                        <input
                          className="u-input col-span-2"
                          inputMode="decimal"
                          aria-label={`Efectivo recibido ${i + 1}`}
                          placeholder="Recibido (opcional, para el cambio)"
                          value={pago.recibido}
                          onChange={(e) => setPagos(pagos.map((p, j) => (j === i ? { ...p, recibido: e.target.value } : p)))}
                        />
                      ) : (
                        <input
                          className="u-input col-span-2"
                          aria-label={`Referencia del pago ${i + 1}`}
                          placeholder="Referencia o autorización (opcional)"
                          maxLength={60}
                          value={pago.referencia}
                          onChange={(e) => setPagos(pagos.map((p, j) => (j === i ? { ...p, referencia: e.target.value } : p)))}
                        />
                      )}
                    </div>
                  ))}
                  <div className="flex flex-wrap items-center justify-between gap-2 text-sm">
                    <button
                      type="button"
                      className="u-btn u-btn--ghost"
                      disabled={pagos.length >= 5}
                      onClick={() => setPagos([...pagos, { medio: 'TARJETA_DEBITO', valor: falta > 0 ? String(falta) : '', recibido: '', referencia: '' }])}
                    >
                      <Plus className="size-4" aria-hidden="true" /> Otro medio
                    </button>
                    <span aria-live="polite" className={cn(falta !== 0 ? 'text-danger' : 'text-foreground-muted')}>
                      {falta > 0 ? `Falta por pagar ${formatearImporte(falta)}` : falta < 0 ? `Pagos exceden el total en ${formatearImporte(-falta)}` : cambio !== null ? `Cambio: ${formatearImporte(cambio)}` : 'Pagos completos'}
                    </span>
                  </div>
                  {recibidoInsuficiente && <p className="text-xs text-danger">Lo recibido en efectivo es menor que el valor del pago.</p>}
                </fieldset>

                <details className="text-sm">
                  <summary className="cursor-pointer text-foreground-muted">Datos del cliente (opcional)</summary>
                  <div className="mt-2 grid gap-2 sm:grid-cols-2">
                    <input className="u-input" aria-label="Nombre del cliente" placeholder="Nombre" maxLength={150} value={cliente.nombre} onChange={(e) => setCliente({ ...cliente, nombre: e.target.value })} />
                    <input className="u-input" aria-label="Documento del cliente" placeholder="Documento" maxLength={20} value={cliente.documento} onChange={(e) => setCliente({ ...cliente, documento: e.target.value })} />
                  </div>
                </details>
              </>
            )}

            <div role="status" aria-live="assertive">
              {errorVenta && (
                <p role="alert" className="u-alert u-alert--error flex items-start gap-2 text-sm">
                  <CircleAlert className="mt-0.5 size-4 shrink-0" aria-hidden="true" />
                  {errorVenta}
                </p>
              )}
            </div>

            <button type="submit" className="u-btn u-btn--primary h-12 text-base" disabled={!listo || confirmando} aria-busy={confirmando}>
              {confirmando ? <Loader2 className="size-5 animate-spin" aria-hidden="true" /> : <ReceiptText className="size-5" aria-hidden="true" />}
              {confirmando ? 'Registrando…' : cotizacion ? `Cobrar ${formatearImporte(cotizacion.total)}` : 'Cobrar'}
            </button>
          </form>
        </div>
      )}
    </>
  )
}

function Catalogo({ onAgregar }: { onAgregar: (producto: ProductoVenta) => void }) {
  const [texto, setTexto] = useState('')
  const [busqueda, setBusqueda] = useState('')
  const [codigo, setCodigo] = useState('')
  const [errorCodigo, setErrorCodigo] = useState<string | null>(null)
  const [buscandoCodigo, setBuscandoCodigo] = useState(false)

  useEffect(() => {
    const id = window.setTimeout(() => setBusqueda(texto.trim()), ESPERA_MS)
    return () => window.clearTimeout(id)
  }, [texto])

  const pedir = useCallback(
    (signal: AbortSignal) => ventaService.catalogo({ page: 0, size: 24, sort: 'producto.nombre,asc', q: busqueda || undefined }, signal),
    [busqueda],
  )
  const { datos, error, cargando, recargar } = useRecurso(pedir, busqueda)

  async function escanear(evento: FormEvent) {
    evento.preventDefault()
    const valor = codigo.trim()
    if (!valor) return
    setBuscandoCodigo(true)
    setErrorCodigo(null)
    try {
      onAgregar(await ventaService.porCodigo(valor))
      setCodigo('')
    } catch (err) {
      setErrorCodigo(axios.isAxiosError(err) && err.response?.status === 404 ? `Ningún producto tiene el código «${valor}».` : getApiErrorMessage(err))
    } finally {
      setBuscandoCodigo(false)
    }
  }

  return (
    <section className="flex min-w-0 flex-col gap-4" aria-label="Catálogo">
      <form onSubmit={(e) => void escanear(e)} className="u-card flex flex-col gap-2 p-4">
        <label htmlFor="pos-codigo" className="flex items-center gap-2 text-sm font-medium text-foreground">
          <ScanBarcode className="size-4" aria-hidden="true" /> Código de barras o SKU
        </label>
        <div className="flex gap-2">
          <input id="pos-codigo" className="u-input flex-1 font-mono" autoComplete="off" value={codigo} onChange={(e) => setCodigo(e.target.value)} placeholder="Escanea o escribe y pulsa Enter" />
          <button type="submit" className="u-btn u-btn--ghost" disabled={buscandoCodigo || !codigo.trim()}>
            {buscandoCodigo ? <Loader2 className="size-4 animate-spin" aria-hidden="true" /> : <Plus className="size-4" aria-hidden="true" />}
            Agregar
          </button>
        </div>
        {errorCodigo && <p role="alert" className="text-sm text-danger">{errorCodigo}</p>}
      </form>

      <div className="relative">
        <Search className="pointer-events-none absolute top-1/2 left-3 size-4 -translate-y-1/2 text-foreground-muted" aria-hidden="true" />
        <input className="u-input w-full pl-9" type="search" aria-label="Buscar productos" placeholder="Buscar por nombre, SKU o código" value={texto} onChange={(e) => setTexto(e.target.value)} />
      </div>

      {error ? (
        <p role="alert" className="u-alert u-alert--error flex items-center justify-between gap-2 text-sm">
          No se pudo cargar el catálogo: {error}
          <button type="button" className="u-btn u-btn--ghost" onClick={recargar}>Reintentar</button>
        </p>
      ) : !datos ? (
        <p className="flex items-center gap-2 text-sm text-foreground-muted"><Loader2 className="size-4 animate-spin" aria-hidden="true" /> Cargando productos…</p>
      ) : datos.content.length === 0 ? (
        <p className="rounded-md border border-dashed border-border p-6 text-center text-sm text-foreground-muted">
          {busqueda ? `Ningún producto a la venta coincide con «${busqueda}».` : 'Aún no hay productos con precio de venta. El administrador comercial los define en Gestión comercial.'}
        </p>
      ) : (
        <ul className={cn('grid gap-3 sm:grid-cols-2 xl:grid-cols-3', cargando && 'opacity-60')} aria-label="Productos a la venta">
          {datos.content.map((p) => (
            <li key={p.id}>
              <button
                type="button"
                onClick={() => onAgregar(p)}
                disabled={p.existencias <= 0}
                className="u-card flex h-full w-full flex-col gap-1 p-3 text-left transition-colors hover:border-border-strong disabled:cursor-not-allowed disabled:opacity-60"
                aria-label={`Agregar ${p.nombre}`}
              >
                <span className="font-medium text-foreground">{p.nombre}</span>
                <span className="font-mono text-xs text-foreground-muted">{p.sku}</span>
                <span className="mt-auto pt-2">
                  <span className="block text-base font-semibold text-foreground">{formatearImporte(p.precioConIva)}</span>
                  <span className="block text-xs text-foreground-muted">{formatearImporte(p.precioVenta)} + {etiquetaIva(p.tratamientoIva, p.tarifaIva)}</span>
                </span>
                <span className="flex flex-wrap items-center justify-between gap-x-2 gap-y-1 text-xs text-foreground-muted">
                  <EstadoStockBadge estado={p.estadoStock} />
                  {p.existencias} disp.
                </span>
              </button>
            </li>
          ))}
        </ul>
      )}
    </section>
  )
}

function LineaDelCarrito({
  linea,
  cotizada,
  faltante,
  onCantidad,
  onDescuento,
  onQuitar,
}: {
  linea: LineaCarrito
  cotizada?: Cotizacion['lineas'][number]
  faltante?: { disponibles: number }
  onCantidad: (cantidad: number) => void
  onDescuento: (descuento?: Descuento) => void
  onQuitar: () => void
}) {
  const [editando, setEditando] = useState(Boolean(linea.descuento))
  const [tipo, setTipo] = useState<TipoDescuento>(linea.descuento?.tipo ?? 'PORCENTAJE')
  const [valor, setValor] = useState(linea.descuento ? String(linea.descuento.valor) : '')
  const error = validarDescuento(tipo, valor)
  const sinExistencias = faltante !== undefined || (cotizada ? !cotizada.suficiente : excedeExistencias(linea))
  const disponibles = faltante?.disponibles ?? cotizada?.disponibles ?? linea.producto.existencias

  function cambiarDescuento(nuevoTipo: TipoDescuento, nuevoValor: string) {
    setTipo(nuevoTipo)
    setValor(nuevoValor)
    const invalido = validarDescuento(nuevoTipo, nuevoValor)
    onDescuento(nuevoValor.trim() && !invalido ? { tipo: nuevoTipo, valor: numero(nuevoValor) } : undefined)
  }

  return (
    <li className="grid gap-2 py-3">
      <div className="flex items-start justify-between gap-2">
        <div className="min-w-0">
          <p className="truncate font-medium text-foreground">{linea.producto.nombre}</p>
          <p className="text-xs text-foreground-muted">
            {formatearImporte(linea.producto.precioVenta)} c/u sin IVA · {etiquetaIva(linea.producto.tratamientoIva, linea.producto.tarifaIva)}
          </p>
        </div>
        <button type="button" className="u-btn u-btn--ghost" aria-label={`Quitar ${linea.producto.nombre}`} onClick={onQuitar}>
          <X className="size-4" aria-hidden="true" />
        </button>
      </div>
      <div className="flex flex-wrap items-center justify-between gap-2">
        <div className="flex items-center gap-1">
          <button type="button" className="u-btn u-btn--ghost px-2" aria-label="Restar una unidad" onClick={() => onCantidad(linea.cantidad - 1)}>
            <Minus className="size-4" aria-hidden="true" />
          </button>
          <input
            className="u-input w-16 text-center"
            inputMode="numeric"
            aria-label={`Cantidad de ${linea.producto.nombre}`}
            value={linea.cantidad}
            onChange={(e) => {
              const n = Number(e.target.value)
              if (Number.isInteger(n) && n > 0) onCantidad(n)
            }}
          />
          <button type="button" className="u-btn u-btn--ghost px-2" aria-label="Sumar una unidad" onClick={() => onCantidad(linea.cantidad + 1)}>
            <Plus className="size-4" aria-hidden="true" />
          </button>
        </div>
        <p className="text-right font-semibold text-foreground">{cotizada ? formatearImporte(cotizada.total) : '…'}</p>
      </div>
      {sinExistencias && (
        <p role="alert" className="text-xs text-danger">Existencias insuficientes: hay {disponibles} disponibles.</p>
      )}
      {cotizada && cotizada.descuentoLinea + cotizada.descuentoGeneral > 0 && (
        <p className="text-xs text-foreground-muted">
          Descuento {formatearImporte(cotizada.descuentoLinea + cotizada.descuentoGeneral)} ({formatearPorcentaje(cotizada.porcentajeDescuento)})
        </p>
      )}
      {editando ? (
        <div className="flex items-center gap-2">
          <select className="u-input w-20" aria-label={`Tipo de descuento de ${linea.producto.nombre}`} value={tipo} onChange={(e) => cambiarDescuento(e.target.value as TipoDescuento, valor)}>
            <option value="PORCENTAJE">%</option>
            <option value="VALOR">$</option>
          </select>
          <input className="u-input flex-1" inputMode="decimal" aria-label={`Descuento de ${linea.producto.nombre}`} value={valor} onChange={(e) => cambiarDescuento(tipo, e.target.value)} aria-invalid={error !== null} />
          {error && <span className="text-xs text-danger">{error}</span>}
        </div>
      ) : (
        <button type="button" className="justify-self-start text-xs text-primary underline-offset-2 hover:underline" onClick={() => setEditando(true)}>
          Aplicar descuento
        </button>
      )}
    </li>
  )
}

function Totales({ cotizacion, cotizando, error }: { cotizacion: Cotizacion | null; cotizando: boolean; error: string | null }) {
  if (error) {
    return <p role="alert" className="u-alert u-alert--error text-sm">No se pudo calcular el total: {error}</p>
  }
  if (!cotizacion) {
    return <p className="flex items-center gap-2 text-sm text-foreground-muted"><Loader2 className="size-4 animate-spin" aria-hidden="true" /> Calculando…</p>
  }
  const descuentos = cotizacion.descuentoLineas + cotizacion.descuentoGeneral
  return (
    <dl className={cn('grid grid-cols-[1fr_auto] gap-x-4 gap-y-1 rounded-md bg-surface-muted/60 p-3 text-sm', cotizando && 'opacity-60')} aria-live="polite" aria-busy={cotizando}>
      <dt className="text-foreground-muted">Subtotal (sin IVA)</dt>
      <dd className="text-right">{formatearImporte(cotizacion.subtotal)}</dd>
      {descuentos > 0 && (
        <>
          <dt className="text-foreground-muted">Descuentos</dt>
          <dd className="text-right">− {formatearImporte(descuentos)}</dd>
        </>
      )}
      <dt className="text-foreground-muted">Base gravable</dt>
      <dd className="text-right">{formatearImporte(cotizacion.baseGravable)}</dd>
      {cotizacion.impuestosPorTarifa.map((t) => (
        <div key={`${t.tratamiento}-${t.tarifa}`} className="contents">
          <dt className="text-foreground-muted">{t.tratamiento === 'GRAVADO' ? `IVA ${formatearPorcentaje(t.tarifa)}` : etiquetaIva(t.tratamiento, t.tarifa)} sobre {formatearImporte(t.base)}</dt>
          <dd className="text-right">{formatearImporte(t.impuesto)}</dd>
        </div>
      ))}
      <dt className="mt-1 border-t border-border pt-2 text-base font-semibold text-foreground">Total</dt>
      <dd className="mt-1 border-t border-border pt-2 text-right text-2xl font-semibold text-foreground" data-testid="total-venta">
        {formatearImporte(cotizacion.total)}
      </dd>
    </dl>
  )
}

function ResultadoVenta({ venta, onNueva }: { venta: VentaDetalle; onNueva: () => void }) {
  const [descargando, setDescargando] = useState(false)
  const [error, setError] = useState<string | null>(null)
  const [descargado, setDescargado] = useState<string | null>(null)
  const efectivo = venta.pagos.find((p) => p.medio === 'EFECTIVO' && p.cambio !== undefined)

  async function descargar() {
    setDescargando(true)
    setError(null)
    try {
      setDescargado(await ventaService.descargarComprobante(venta.id))
    } catch (err) {
      setError(getApiErrorMessage(err))
    } finally {
      setDescargando(false)
    }
  }

  return (
    <section className="u-card mx-auto grid max-w-xl gap-4 p-6 text-center" aria-label="Venta confirmada">
      <CheckCircle2 className="mx-auto size-10 text-success" aria-hidden="true" />
      <div>
        <h2 className="text-xl font-semibold text-foreground">Venta {venta.numeroVisible} registrada</h2>
        <p className="mt-1 text-sm text-foreground-muted">
          {venta.lineas.reduce((s, l) => s + l.cantidad, 0)} unidades · IVA {formatearImporte(venta.impuestos)}
        </p>
      </div>
      <p className="text-4xl font-semibold text-foreground">{formatearImporte(venta.total)}</p>
      {efectivo && <p className="text-lg text-foreground">Cambio: <strong>{formatearImporte(efectivo.cambio)}</strong></p>}
      <p className="text-xs text-foreground-muted">El comprobante es un documento interno: no es factura electrónica.</p>
      <div className="flex flex-wrap justify-center gap-2">
        <button type="button" className="u-btn u-btn--ghost" onClick={() => void descargar()} disabled={descargando} aria-busy={descargando}>
          {descargando ? <Loader2 className="size-4 animate-spin" aria-hidden="true" /> : <FileDown className="size-4" aria-hidden="true" />}
          Descargar comprobante
        </button>
        <Link to={`/ventas/${venta.id}`} className="u-btn u-btn--ghost">Ver detalle</Link>
        <button type="button" className="u-btn u-btn--primary" onClick={onNueva}>
          <Plus className="size-4" aria-hidden="true" /> Nueva venta
        </button>
      </div>
      <div role="status" aria-live="polite">
        {descargado && <p className="text-sm text-success">Comprobante {descargado} descargado.</p>}
        {error && <p role="alert" className="text-sm text-danger">No se pudo descargar el comprobante: {error}</p>}
      </div>
    </section>
  )
}
