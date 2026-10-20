import { History, KeyRound, Loader2, Pencil, Search, Settings2, TriangleAlert } from 'lucide-react'
import { useCallback, useEffect, useState, type FormEvent } from 'react'
import { PageHeader } from '@/components/layout/PageHeader'
import { DataTable, type Column } from '@/components/ui/DataTable'
import { Modal } from '@/components/ui/Modal'
import { usePermisos } from '@/features/auth/permisos'
import { EstadoStockBadge } from '@/features/inventario/EstadoStockBadge'
import { ETIQUETA_TRATAMIENTO, etiquetaIva } from '@/features/ventas/etiquetas'
import { useAviso } from '@/hooks/useAviso'
import { usePaginatedResource } from '@/hooks/usePaginatedResource'
import { useRecurso } from '@/hooks/useRecurso'
import { cn } from '@/lib/cn'
import { getApiErrorMessage, getFieldErrors } from '@/lib/errors'
import { formatearFechaHora } from '@/lib/fechas'
import { formatearImporte, formatearPorcentaje } from '@/lib/moneda'
import {
  comercialService,
  type Autorizacion,
  type CambioPrecio,
  type FiltroComercial,
  type ProductoComercial,
  type TratamientoIva,
  type VentaBajoCosto,
} from '@/services/comercialService'
import type { PageParams } from '@/services/types'

type Pestana = 'precios' | 'historial' | 'politica'

const FILTROS: { valor: FiltroComercial; etiqueta: string }[] = [
  { valor: 'TODOS', etiqueta: 'Todos' },
  { valor: 'A_LA_VENTA', etiqueta: 'A la venta' },
  { valor: 'SIN_PRECIO', etiqueta: 'Sin precio de venta' },
  { valor: 'BAJO_COSTO', etiqueta: 'Precio bajo el costo' },
  { valor: 'SIN_COSTO', etiqueta: 'Costo desconocido' },
]

/**
 * Gestión comercial de la empresa: precios de venta (sin IVA), tratamiento del IVA, costo promedio
 * y rentabilidad unitaria, historial de cambios, política de descuentos y autorizaciones. Solo el
 * administrador comercial cambia algo (el backend lo impone); el SUPER_ADMIN supervisando consulta.
 */
export default function GestionComercial() {
  const permisos = usePermisos()
  const [pestana, setPestana] = useState<Pestana>('precios')
  const { aviso, mostrarAviso } = useAviso()

  return (
    <>
      <PageHeader
        eyebrow="Ventas"
        title="Gestión comercial"
        description="Precios de venta, IVA, costos, rentabilidad y política comercial de tu empresa."
      />
      <div role="tablist" aria-label="Secciones de la gestión comercial" className="mb-6 flex flex-wrap gap-2">
        {([
          ['precios', 'Precios y rentabilidad'],
          ['historial', 'Historial de precios'],
          ['politica', 'Política y autorizaciones'],
        ] as const).map(([id, etiqueta]) => (
          <button
            key={id}
            role="tab"
            type="button"
            aria-selected={pestana === id}
            className={cn('u-btn', pestana === id ? 'u-btn--primary' : 'u-btn--ghost')}
            onClick={() => setPestana(id)}
          >
            {etiqueta}
          </button>
        ))}
      </div>
      <div role="status" aria-live="polite">{aviso && <p className="u-alert u-alert--success mb-6">{aviso}</p>}</div>
      {pestana === 'precios' && <Precios editable={permisos.gestionarPrecios} onAviso={mostrarAviso} />}
      {pestana === 'historial' && <HistorialPrecios />}
      {pestana === 'politica' && <PoliticaYAutorizaciones editable={permisos.gestionarPrecios} onAviso={mostrarAviso} />}
    </>
  )
}

function Precios({ editable, onAviso }: { editable: boolean; onAviso: (m: string) => void }) {
  const [texto, setTexto] = useState('')
  const [q, setQ] = useState('')
  const [filtro, setFiltro] = useState<FiltroComercial>('TODOS')
  const [pagina, setPagina] = useState(0)
  const [editando, setEditando] = useState<ProductoComercial | null>(null)

  useEffect(() => {
    const id = window.setTimeout(() => {
      setQ(texto.trim())
      setPagina(0)
    }, 300)
    return () => window.clearTimeout(id)
  }, [texto])

  const listar = useCallback(
    (params: PageParams, signal: AbortSignal) => comercialService.productos({ ...params, q: q || undefined, filtro }, signal),
    [q, filtro],
  )
  const { data, error, loading, reload } = usePaginatedResource(listar, pagina, 20, 'producto.nombre,asc', `${q}|${filtro}`)

  const columnas: Column<ProductoComercial>[] = [
    {
      id: 'producto',
      header: 'Producto',
      cell: (p) => (
        <>
          <p className="font-medium text-foreground">{p.nombre}</p>
          <p className="font-mono text-xs text-foreground-muted">{p.sku}</p>
        </>
      ),
    },
    {
      id: 'precio',
      header: 'Precio de venta',
      align: 'right',
      cell: (p) =>
        p.precioVenta === undefined ? (
          <span className="text-foreground-muted">Sin precio</span>
        ) : (
          <>
            <p className="font-medium">{formatearImporte(p.precioVenta)}</p>
            <p className="text-xs text-foreground-muted">{formatearImporte(p.precioConIva)} con {etiquetaIva(p.tratamientoIva, p.tarifaIva)}</p>
          </>
        ),
    },
    {
      id: 'costo',
      header: 'Costo promedio',
      align: 'right',
      className: 'hidden md:table-cell',
      cell: (p) => (p.costoPromedio === undefined ? <span className="text-foreground-muted">Desconocido</span> : formatearImporte(p.costoPromedio)),
    },
    {
      id: 'rentabilidad',
      header: 'Utilidad · margen · recargo',
      align: 'right',
      cell: (p) =>
        p.utilidadUnitaria === undefined ? (
          <span className="text-foreground-muted">No disponible</span>
        ) : (
          <span className={cn(p.bajoCosto && 'text-danger')}>
            {p.bajoCosto && <TriangleAlert className="mr-1 inline size-3.5" aria-label="Precio bajo el costo" />}
            {formatearImporte(p.utilidadUnitaria)}
            <span className="block text-xs text-foreground-muted">{formatearPorcentaje(p.margen)} · {formatearPorcentaje(p.recargo)}</span>
          </span>
        ),
    },
    {
      id: 'existencias',
      header: 'Existencias',
      className: 'hidden lg:table-cell',
      cell: (p) => (
        <span className="flex items-center gap-2">{p.existencias} <EstadoStockBadge estado={p.estadoStock} /></span>
      ),
    },
    ...(editable
      ? [{
          id: 'acciones',
          header: <span className="sr-only">Acciones</span>,
          align: 'right' as const,
          cell: (p: ProductoComercial) => (
            <button type="button" className="u-btn u-btn--ghost" onClick={() => setEditando(p)} aria-label={`Cambiar precio de ${p.nombre}`}>
              <Pencil className="size-4" aria-hidden="true" /> Precio
            </button>
          ),
        }]
      : []),
  ]

  return (
    <>
      <div className="mb-4 flex flex-wrap items-end gap-3">
        <div className="relative min-w-60 flex-1">
          <Search className="pointer-events-none absolute top-1/2 left-3 size-4 -translate-y-1/2 text-foreground-muted" aria-hidden="true" />
          <input type="search" className="u-input w-full pl-9" aria-label="Buscar productos" placeholder="Nombre, SKU o código de barras" value={texto} onChange={(e) => setTexto(e.target.value)} />
        </div>
        <label className="grid gap-1 text-sm">
          <span className="text-foreground-muted">Mostrar</span>
          <select className="u-input" value={filtro} onChange={(e) => { setFiltro(e.target.value as FiltroComercial); setPagina(0) }}>
            {FILTROS.map((f) => <option key={f.valor} value={f.valor}>{f.etiqueta}</option>)}
          </select>
        </label>
      </div>
      <p className="mb-3 text-xs text-foreground-muted">
        Precios sin IVA. Costo: promedio ponderado de las entradas (método provisional, pendiente de validación contable). La utilidad es bruta: no descuenta gastos operativos.
      </p>
      <DataTable
        caption="Productos con sus condiciones de venta"
        columns={columnas}
        rows={data?.content ?? []}
        rowKey={(p) => p.id}
        loading={loading}
        error={error}
        onRetry={reload}
        emptyMessage={q || filtro !== 'TODOS' ? 'Ningún producto coincide.' : 'Aún no hay productos en el catálogo.'}
        page={data?.page}
        onPageChange={setPagina}
      />
      {editando && (
        <PrecioModal
          producto={editando}
          onCerrar={() => setEditando(null)}
          onGuardado={(p) => {
            setEditando(null)
            reload()
            onAviso(p.bajoCosto ? `Precio de ${p.nombre} guardado. Atención: queda por debajo del costo.` : `Precio de ${p.nombre} guardado.`)
          }}
        />
      )}
    </>
  )
}

function PrecioModal({ producto, onCerrar, onGuardado }: { producto: ProductoComercial; onCerrar: () => void; onGuardado: (p: ProductoComercial) => void }) {
  const [precio, setPrecio] = useState(producto.precioVenta !== undefined ? String(producto.precioVenta) : '')
  const [tratamiento, setTratamiento] = useState<TratamientoIva>(producto.tratamientoIva ?? 'GRAVADO')
  const [tarifa, setTarifa] = useState(producto.tarifaIva !== undefined && producto.tratamientoIva === 'GRAVADO' ? String(producto.tarifaIva) : '19')
  const [motivo, setMotivo] = useState('')
  const [confirmando, setConfirmando] = useState(false)
  const [enviando, setEnviando] = useState(false)
  const [error, setError] = useState<string | null>(null)
  const [errores, setErrores] = useState<Record<string, string>>({})
  const precioNumero = Number(precio.replace(',', '.'))
  const tarifaNumero = tratamiento === 'GRAVADO' ? Number(tarifa.replace(',', '.')) : 0
  const valido = Number.isFinite(precioNumero) && precioNumero > 0 && (tratamiento !== 'GRAVADO' || (tarifaNumero > 0 && tarifaNumero < 100))
  // Vista previa orientativa; el backend recalcula con el costo vigente al guardar.
  const bajoCosto = valido && producto.costoPromedio !== undefined && precioNumero < producto.costoPromedio
  const margen = valido && producto.costoPromedio !== undefined ? ((precioNumero - producto.costoPromedio) / precioNumero) * 100 : undefined

  async function guardar(e: FormEvent) {
    e.preventDefault()
    if (!valido) {
      setError('Revisa el precio y la tarifa.')
      return
    }
    if (!confirmando) {
      setConfirmando(true)
      return
    }
    setEnviando(true)
    setError(null)
    try {
      onGuardado(await comercialService.cambiarCondiciones(producto.id, {
        precioVenta: precioNumero,
        tratamientoIva: tratamiento,
        tarifaIva: tarifaNumero,
        motivo: motivo.trim() || undefined,
      }))
    } catch (err) {
      setErrores(getFieldErrors(err))
      setError(getApiErrorMessage(err))
      setConfirmando(false)
    } finally {
      setEnviando(false)
    }
  }

  return (
    <Modal
      open
      onClose={onCerrar}
      dismissible={!enviando}
      title={`Precio de ${producto.nombre}`}
      description="El cambio queda en el historial con el valor anterior, quién y cuándo. Las ventas ya registradas no cambian."
      footer={
        <>
          <button type="button" className="u-btn u-btn--ghost" onClick={onCerrar} disabled={enviando}>Cancelar</button>
          <button type="submit" form="form-precio" className="u-btn u-btn--primary" disabled={enviando} aria-busy={enviando}>
            {enviando && <Loader2 className="size-4 animate-spin" aria-hidden="true" />}
            {confirmando ? 'Confirmar cambio' : 'Guardar'}
          </button>
        </>
      }
    >
      <form id="form-precio" onSubmit={(e) => void guardar(e)} className="grid gap-4">
        <div className="grid gap-4 sm:grid-cols-2">
          <label className="grid gap-1 text-sm">
            Precio de venta sin IVA (COP)
            <input className="u-input" inputMode="decimal" value={precio} onChange={(e) => { setPrecio(e.target.value); setConfirmando(false) }} aria-invalid={Boolean(errores.precioVenta)} />
            {errores.precioVenta && <span className="text-xs text-danger">{errores.precioVenta}</span>}
          </label>
          <label className="grid gap-1 text-sm">
            Tratamiento del IVA
            <select className="u-input" value={tratamiento} onChange={(e) => { setTratamiento(e.target.value as TratamientoIva); setConfirmando(false) }}>
              {(Object.keys(ETIQUETA_TRATAMIENTO) as TratamientoIva[]).map((t) => <option key={t} value={t}>{ETIQUETA_TRATAMIENTO[t]}</option>)}
            </select>
          </label>
          {tratamiento === 'GRAVADO' && (
            <label className="grid gap-1 text-sm">
              Tarifa del IVA (%)
              <input className="u-input" inputMode="decimal" value={tarifa} onChange={(e) => { setTarifa(e.target.value); setConfirmando(false) }} aria-invalid={Boolean(errores.tarifaIva)} />
              {errores.tarifaIva && <span className="text-xs text-danger">{errores.tarifaIva}</span>}
            </label>
          )}
          <label className="grid gap-1 text-sm sm:col-span-2">
            Motivo (opcional)
            <input className="u-input" maxLength={300} value={motivo} onChange={(e) => setMotivo(e.target.value)} />
          </label>
        </div>
        <dl className="grid grid-cols-[1fr_auto] gap-x-4 gap-y-1 rounded-md bg-surface-muted/60 p-3 text-sm">
          <dt className="text-foreground-muted">Precio actual</dt>
          <dd className="text-right">{producto.precioVenta === undefined ? 'Sin precio' : formatearImporte(producto.precioVenta)}</dd>
          <dt className="text-foreground-muted">Costo promedio</dt>
          <dd className="text-right">{producto.costoPromedio === undefined ? 'Desconocido' : formatearImporte(producto.costoPromedio)}</dd>
          <dt className="text-foreground-muted">Margen bruto estimado</dt>
          <dd className="text-right">{formatearPorcentaje(margen === undefined ? undefined : Math.round(margen * 100) / 100)}</dd>
          {producto.precioSugerido !== undefined && (
            <>
              <dt className="text-foreground-muted">Precio sugerido (margen objetivo)</dt>
              <dd className="text-right">
                <button type="button" className="text-primary hover:underline" onClick={() => { setPrecio(String(producto.precioSugerido)); setConfirmando(false) }}>
                  {formatearImporte(producto.precioSugerido)}
                </button>
              </dd>
            </>
          )}
        </dl>
        {tratamiento !== 'GRAVADO' && (
          <p className="text-xs text-foreground-muted">
            {tratamiento === 'EXENTO' ? 'Exento: gravado a tarifa 0 %.' : 'Excluido: la venta no causa IVA.'} Verifica la clasificación con asesoría tributaria.
          </p>
        )}
        {bajoCosto && (
          <p role="alert" className="u-alert u-alert--warning flex items-start gap-2 text-sm">
            <TriangleAlert className="mt-0.5 size-4 shrink-0" aria-hidden="true" />
            Este precio queda por debajo del costo promedio: cada venta generará pérdida bruta.
          </p>
        )}
        {confirmando && (
          <p className="u-alert u-alert--warning text-sm">
            Vas a cambiar el precio de {producto.precioVenta === undefined ? 'nada (sin precio)' : formatearImporte(producto.precioVenta)} a {formatearImporte(precioNumero)} ({etiquetaIva(tratamiento, tarifaNumero)}). Pulsa «Confirmar cambio».
          </p>
        )}
        {error && <p role="alert" className="u-alert u-alert--error text-sm">{error}</p>}
      </form>
    </Modal>
  )
}

function HistorialPrecios() {
  const [pagina, setPagina] = useState(0)
  const listar = useCallback((params: PageParams, signal: AbortSignal) => comercialService.historial(params, signal), [])
  const { data, error, loading, reload } = usePaginatedResource(listar, pagina, 20, 'fecha,desc')
  const columnas: Column<CambioPrecio>[] = [
    { id: 'fecha', header: 'Fecha', cell: (c) => formatearFechaHora(c.fecha), className: 'whitespace-nowrap' },
    { id: 'producto', header: 'Producto', cell: (c) => <>{c.nombre} <span className="font-mono text-xs text-foreground-muted">{c.sku}</span></> },
    {
      id: 'cambio',
      header: 'Precio sin IVA',
      align: 'right',
      cell: (c) => <>{c.precioAnterior === undefined ? 'Sin precio' : formatearImporte(c.precioAnterior)} → <strong>{formatearImporte(c.precioNuevo)}</strong></>,
    },
    {
      id: 'iva',
      header: 'IVA',
      className: 'hidden md:table-cell',
      cell: (c) =>
        c.tratamientoAnterior && (c.tratamientoAnterior !== c.tratamientoNuevo || c.tarifaAnterior !== c.tarifaNueva)
          ? `${etiquetaIva(c.tratamientoAnterior, c.tarifaAnterior)} → ${etiquetaIva(c.tratamientoNuevo, c.tarifaNueva)}`
          : etiquetaIva(c.tratamientoNuevo, c.tarifaNueva),
    },
    { id: 'usuario', header: 'Responsable', cell: (c) => c.usuario.nombre, className: 'hidden lg:table-cell' },
    { id: 'motivo', header: 'Motivo', cell: (c) => c.motivo ?? '—', className: 'hidden lg:table-cell' },
  ]
  return (
    <DataTable
      caption="Historial de cambios de precio"
      columns={columnas}
      rows={data?.content ?? []}
      rowKey={(c) => c.id}
      loading={loading}
      error={error}
      onRetry={reload}
      emptyMessage={<span className="inline-flex items-center gap-2"><History className="size-4" aria-hidden="true" /> Aún no hay cambios de precio.</span>}
      page={data?.page}
      onPageChange={setPagina}
    />
  )
}

function PoliticaYAutorizaciones({ editable, onAviso }: { editable: boolean; onAviso: (m: string) => void }) {
  const [version, setVersion] = useState(0)
  const politica = useRecurso(useCallback((signal: AbortSignal) => comercialService.politica(signal), []), String(version))
  const [form, setForm] = useState<{ trabajador: string; administrador: string; bajoCosto: VentaBajoCosto; margen: string } | null>(null)
  const [guardando, setGuardando] = useState(false)
  const [error, setError] = useState<string | null>(null)
  const datos = politica.datos
  const valores = form ?? (datos ? {
    trabajador: String(datos.descuentoMaxTrabajador),
    administrador: String(datos.descuentoMaxAdministrador),
    bajoCosto: datos.ventaBajoCosto,
    margen: datos.margenObjetivo === undefined ? '' : String(datos.margenObjetivo),
  } : null)

  async function guardar(e: FormEvent) {
    e.preventDefault()
    if (!valores) return
    setGuardando(true)
    setError(null)
    try {
      await comercialService.guardarPolitica({
        descuentoMaxTrabajador: Number(valores.trabajador),
        descuentoMaxAdministrador: Number(valores.administrador),
        ventaBajoCosto: valores.bajoCosto,
        margenObjetivo: valores.margen.trim() ? Number(valores.margen) : null,
      })
      setForm(null)
      setVersion((v) => v + 1)
      onAviso('Política comercial guardada.')
    } catch (err) {
      setError(getApiErrorMessage(err))
    } finally {
      setGuardando(false)
    }
  }

  return (
    <div className="grid gap-6 lg:grid-cols-2">
      <form onSubmit={(e) => void guardar(e)} className="u-card grid content-start gap-4 p-5">
        <h2 className="flex items-center gap-2 text-lg font-semibold text-foreground"><Settings2 className="size-5" aria-hidden="true" /> Política comercial</h2>
        {politica.error && <p role="alert" className="u-alert u-alert--error text-sm">{politica.error}</p>}
        {!valores ? (
          <p className="flex items-center gap-2 text-sm text-foreground-muted"><Loader2 className="size-4 animate-spin" aria-hidden="true" /> Cargando…</p>
        ) : (
          <>
            {datos?.porDefecto && (
              <p className="u-alert u-alert--warning text-sm">
                Tu empresa aún no ha definido su política: rigen los valores por defecto (el trabajador no puede descontar; las ventas bajo el costo se permiten con advertencia).
              </p>
            )}
            <fieldset disabled={!editable || guardando} className="grid gap-3 sm:grid-cols-2">
              <label className="grid gap-1 text-sm">
                Descuento máximo del trabajador (%)
                <input className="u-input" inputMode="decimal" value={valores.trabajador} onChange={(e) => setForm({ ...valores, trabajador: e.target.value })} />
              </label>
              <label className="grid gap-1 text-sm">
                Descuento máximo del administrador (%)
                <input className="u-input" inputMode="decimal" value={valores.administrador} onChange={(e) => setForm({ ...valores, administrador: e.target.value })} />
              </label>
              <label className="grid gap-1 text-sm">
                Ventas bajo el costo
                <select className="u-input" value={valores.bajoCosto} onChange={(e) => setForm({ ...valores, bajoCosto: e.target.value as VentaBajoCosto })}>
                  <option value="PERMITIR">Permitir con advertencia</option>
                  <option value="AUTORIZACION">Exigir autorización</option>
                </select>
              </label>
              <label className="grid gap-1 text-sm">
                Margen bruto objetivo (%) — opcional
                <input className="u-input" inputMode="decimal" value={valores.margen} onChange={(e) => setForm({ ...valores, margen: e.target.value })} />
              </label>
            </fieldset>
            <p className="text-xs text-foreground-muted">Los límites se aplican al descuento efectivo de cada línea (el de la línea más su parte del descuento general).</p>
            {datos?.actualizadaPor && <p className="text-xs text-foreground-muted">Última actualización: {datos.actualizadaPor.nombre}, {formatearFechaHora(datos.actualizadaEn!)}.</p>}
            {error && <p role="alert" className="u-alert u-alert--error text-sm">{error}</p>}
            {editable && (
              <button type="submit" className="u-btn u-btn--primary justify-self-start" disabled={guardando || form === null} aria-busy={guardando}>
                {guardando && <Loader2 className="size-4 animate-spin" aria-hidden="true" />} Guardar política
              </button>
            )}
          </>
        )}
      </form>
      <Autorizaciones editable={editable} />
    </div>
  )
}

function Autorizaciones({ editable }: { editable: boolean }) {
  const [version, setVersion] = useState(0)
  const [descuento, setDescuento] = useState('20')
  const [bajoCosto, setBajoCosto] = useState(false)
  const [motivo, setMotivo] = useState('')
  const [minutos, setMinutos] = useState('30')
  const [creada, setCreada] = useState<Autorizacion | null>(null)
  const [enviando, setEnviando] = useState(false)
  const [error, setError] = useState<string | null>(null)
  const listado = useRecurso(
    useCallback((signal: AbortSignal) => comercialService.autorizaciones({ page: 0, size: 10, sort: 'creadaEn,desc' }, signal), []),
    String(version),
  )

  async function crear(e: FormEvent) {
    e.preventDefault()
    if (!motivo.trim()) {
      setError('Indica el motivo.')
      return
    }
    setEnviando(true)
    setError(null)
    try {
      setCreada(await comercialService.crearAutorizacion({
        descuentoMaximo: Number(descuento), permiteBajoCosto: bajoCosto, motivo: motivo.trim(), minutosVigencia: Number(minutos),
      }))
      setMotivo('')
      setVersion((v) => v + 1)
    } catch (err) {
      setError(getApiErrorMessage(err))
    } finally {
      setEnviando(false)
    }
  }

  return (
    <section className="u-card grid content-start gap-4 p-5" aria-label="Autorizaciones de excepción">
      <h2 className="flex items-center gap-2 text-lg font-semibold text-foreground"><KeyRound className="size-5" aria-hidden="true" /> Autorizaciones de excepción</h2>
      <p className="text-sm text-foreground-muted">
        Un código de un solo uso, con vencimiento, para una venta que supera el límite de descuento de quien vende o queda bajo el costo. Queda registrado quién lo autorizó y en qué venta se usó.
      </p>
      {editable && (
        <form onSubmit={(e) => void crear(e)} className="grid gap-3 sm:grid-cols-2">
          <label className="grid gap-1 text-sm">Descuento máximo (%)<input className="u-input" inputMode="decimal" value={descuento} onChange={(e) => setDescuento(e.target.value)} /></label>
          <label className="grid gap-1 text-sm">Vigencia (minutos)<input className="u-input" inputMode="numeric" value={minutos} onChange={(e) => setMinutos(e.target.value)} /></label>
          <label className="grid gap-1 text-sm sm:col-span-2">Motivo<input className="u-input" maxLength={300} value={motivo} onChange={(e) => setMotivo(e.target.value)} /></label>
          <label className="flex items-center gap-2 text-sm sm:col-span-2">
            <input type="checkbox" checked={bajoCosto} onChange={(e) => setBajoCosto(e.target.checked)} /> Permite vender por debajo del costo
          </label>
          {error && <p role="alert" className="u-alert u-alert--error text-sm sm:col-span-2">{error}</p>}
          <button type="submit" className="u-btn u-btn--primary justify-self-start" disabled={enviando} aria-busy={enviando}>
            {enviando && <Loader2 className="size-4 animate-spin" aria-hidden="true" />} Generar código
          </button>
        </form>
      )}
      {creada && (
        <p className="u-alert u-alert--success text-sm" role="status">
          Código generado: <strong className="font-mono text-base">{creada.codigo}</strong> (vence {formatearFechaHora(creada.expiraEn)}). Solo se muestra ahora: entrégalo a quien vende.
        </p>
      )}
      {listado.datos && listado.datos.content.length > 0 && (
        <ul className="grid gap-2 text-sm">
          {listado.datos.content.map((a) => (
            <li key={a.id} className="flex flex-wrap justify-between gap-2 border-t border-border pt-2">
              <span>
                <span className="font-mono">{a.codigo}</span> · hasta {formatearPorcentaje(a.descuentoMaximo)}{a.permiteBajoCosto && ' · bajo costo'} · {a.motivo}
              </span>
              <span className="text-foreground-muted">
                {a.estado === 'USADA' ? `Usada por ${a.usadaPor?.nombre}` : a.estado === 'VENCIDA' ? 'Vencida' : 'Vigente'}
              </span>
            </li>
          ))}
        </ul>
      )}
    </section>
  )
}
