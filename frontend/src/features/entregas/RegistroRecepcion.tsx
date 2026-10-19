import { ArrowLeft, CheckCheck, ClipboardCheck, Loader2, PackageCheck, Plus, Trash2 } from 'lucide-react'
import { useId, useState, type FormEvent } from 'react'
import { nuevaClaveIdempotencia } from '@/lib/claves'
import { cn } from '@/lib/cn'
import { getApiErrorMessage } from '@/lib/errors'
import { formatearNumero } from '@/lib/numeros'
import { ordenCompraService, type Recepcion } from '@/services/ordenCompraService'
import { ETIQUETA_INCIDENCIA } from './etiquetas'
import { construirRecepcion, entero, type Conteo, type LineaARecibir, type Otra, type TipoOtra } from './recepcion'

const SIN_FLECHAS =
  '[appearance:textfield] [&::-webkit-inner-spin-button]:appearance-none [&::-webkit-outer-spin-button]:appearance-none'

/**
 * Registro de una entrega: por producto, lo recibido en buen estado (entra al inventario), lo
 * dañado y lo que faltó; aparte, excedentes, productos incorrectos y otras novedades. Antes de
 * enviarlo se revisa un resumen. Quién recibe, la empresa y la hora los pone el servidor.
 *
 * La clave de idempotencia se genera al abrir y se reutiliza si hay que reintentar lo mismo; al
 * volver a editar se genera otra (el contenido puede cambiar).
 */
export function RegistroRecepcion({
  ordenId,
  lineas,
  onRegistrada,
  onCancelar,
}: {
  ordenId: number
  lineas: LineaARecibir[]
  onRegistrada: (recepcion: Recepcion) => void
  onCancelar?: () => void
}) {
  const formId = useId()
  const pendientes = lineas.filter((linea) => linea.pendiente > 0)
  const [conteos, setConteos] = useState<Record<number, Conteo>>({})
  const [otras, setOtras] = useState<Otra[]>([])
  const [observaciones, setObservaciones] = useState('')
  const [paso, setPaso] = useState<'editar' | 'revisar'>('editar')
  const [clave, setClave] = useState(nuevaClaveIdempotencia)
  const [enviando, setEnviando] = useState(false)
  const [error, setError] = useState<string | null>(null)

  const { erroresLinea, erroresOtra, detalles, incidencias } = construirRecepcion(pendientes, conteos, otras)
  const hayErrores = Object.keys(erroresLinea).length > 0 || Object.keys(erroresOtra).length > 0
  const vacia = detalles.length === 0 && incidencias.length === 0
  const unidades = detalles.reduce((suma, d) => suma + d.cantidad, 0)

  function cambiar(lineaId: number, campo: keyof Conteo, valor: string) {
    setConteos((actual) => ({
      ...actual,
      [lineaId]: { ...(actual[lineaId] ?? { recibidas: '', danadas: '', faltantes: '' }), [campo]: valor },
    }))
  }

  function llegoTodo() {
    setConteos(Object.fromEntries(pendientes.map((l) => [l.id, { recibidas: String(l.pendiente), danadas: '', faltantes: '' }])))
  }

  function agregarOtra(tipo: TipoOtra) {
    setOtras((actual) => [...actual, { clave: Date.now() + actual.length, tipo, lineaId: '', cantidad: '', descripcion: '' }])
  }

  function revisar(event: FormEvent) {
    event.preventDefault()
    if (hayErrores || vacia) return
    setError(null)
    setPaso('revisar')
  }

  function volverAEditar() {
    setPaso('editar')
    setClave(nuevaClaveIdempotencia())
    setError(null)
  }

  async function confirmar() {
    setEnviando(true)
    setError(null)
    try {
      onRegistrada(
        await ordenCompraService.registrarRecepcion(ordenId, {
          claveIdempotencia: clave,
          observaciones: observaciones.trim() || null,
          detalles,
          incidencias,
        }),
      )
    } catch (err) {
      // 400: supera lo pendiente (p. ej. otra persona registró antes). 409: la orden ya no admite recepciones.
      setError(getApiErrorMessage(err))
      setEnviando(false)
    }
  }

  const nombreLinea = (id: number) => pendientes.find((l) => l.id === id)?.producto.nombre ?? `Línea ${id}`

  if (pendientes.length === 0) {
    return <p className="text-sm text-foreground-muted">No quedan unidades pendientes en esta orden.</p>
  }

  if (paso === 'revisar') {
    return (
      <section aria-labelledby={`${formId}-resumen`} className="space-y-5">
        <h3 id={`${formId}-resumen`} className="flex items-center gap-2 font-medium text-foreground">
          <ClipboardCheck className="size-5" aria-hidden="true" />
          Revisa antes de confirmar
        </h3>
        {error && (
          <p role="alert" className="u-alert u-alert--error">
            {error}
          </p>
        )}
        <dl className="grid gap-3 sm:grid-cols-3">
          <Cifra titulo="Entran al inventario" valor={`${formatearNumero(unidades)} unidades`} />
          <Cifra titulo="Productos con unidades" valor={formatearNumero(detalles.length)} />
          <Cifra titulo="Novedades" valor={formatearNumero(incidencias.length)} />
        </dl>
        <ul className="divide-y divide-border rounded-md border border-border text-sm">
          {pendientes.map((linea) => {
            const conteo = conteos[linea.id]
            const recibidas = entero(conteo?.recibidas ?? '') ?? 0
            const danadas = entero(conteo?.danadas ?? '') ?? 0
            const faltantes = entero(conteo?.faltantes ?? '') ?? 0
            return (
              <li key={linea.id} className="flex flex-wrap items-baseline justify-between gap-2 px-3 py-2">
                <span className="text-foreground">{linea.producto.nombre}</span>
                <span className="text-foreground-muted tabular-nums">
                  {formatearNumero(recibidas)} recibidas
                  {danadas > 0 && ` · ${formatearNumero(danadas)} dañadas`}
                  {faltantes > 0 && ` · ${formatearNumero(faltantes)} faltan`}
                  {' · '}quedarán {formatearNumero(linea.pendiente - recibidas)} pendientes
                </span>
              </li>
            )
          })}
        </ul>
        {incidencias.filter((i) => i.tipo !== 'DANADO' && i.tipo !== 'FALTANTE').length > 0 && (
          <ul className="space-y-1 text-sm">
            {incidencias
              .filter((i) => i.tipo !== 'DANADO' && i.tipo !== 'FALTANTE')
              .map((i, indice) => (
                <li key={indice} className="text-foreground">
                  <span className="font-medium">{ETIQUETA_INCIDENCIA[i.tipo]}</span>
                  {i.detalleOrdenId !== undefined && ` · ${nombreLinea(i.detalleOrdenId)}`}
                  {i.cantidad !== undefined && ` · ${formatearNumero(i.cantidad)} u.`}
                  {i.descripcion && ` · ${i.descripcion}`}
                </li>
              ))}
          </ul>
        )}
        {observaciones.trim() && <p className="text-sm text-foreground-muted">Observaciones: {observaciones.trim()}</p>}
        <p className="text-xs text-foreground-muted">
          Al confirmar, el sistema registra tu usuario y la hora. Una recepción confirmada no se edita: un error se corrige con
          un ajuste de inventario.
        </p>
        <div className="flex flex-col-reverse gap-2 sm:flex-row sm:justify-end sm:gap-3">
          <button type="button" className="u-btn u-btn--ghost" onClick={volverAEditar} disabled={enviando}>
            <ArrowLeft className="size-4" aria-hidden="true" />
            Corregir
          </button>
          <button type="button" className="u-btn u-btn--primary" onClick={() => void confirmar()} disabled={enviando} aria-busy={enviando}>
            {enviando ? <Loader2 className="size-4 animate-spin" aria-hidden="true" /> : <PackageCheck className="size-4" aria-hidden="true" />}
            {error ? 'Reintentar' : 'Confirmar recepción'}
          </button>
        </div>
      </section>
    )
  }

  return (
    <form id={formId} onSubmit={revisar} noValidate className="space-y-6">
      <div className="flex flex-wrap items-center justify-between gap-3">
        <p className="text-sm text-foreground-muted">Cuenta lo que llegó. Lo dañado y lo que falta no entra al inventario.</p>
        <button type="button" className="u-btn u-btn--ghost h-9 text-sm" onClick={llegoTodo}>
          <CheckCheck className="size-4" aria-hidden="true" />
          Llegó todo lo pendiente
        </button>
      </div>

      <ul className="divide-y divide-border rounded-md border border-border">
        {pendientes.map((linea) => {
          const conteo = conteos[linea.id] ?? { recibidas: '', danadas: '', faltantes: '' }
          const errorLinea = erroresLinea[linea.id]
          const idError = `${formId}-error-${linea.id}`
          return (
            <li key={linea.id} className="grid gap-3 p-3 sm:grid-cols-[minmax(0,1fr)_auto] sm:items-start">
              <div className="min-w-0">
                <p className="text-foreground">{linea.producto.nombre}</p>
                <p className="font-mono text-xs text-foreground-muted">
                  {linea.producto.sku}
                  {linea.producto.codigoBarras && ` · ${linea.producto.codigoBarras}`}
                </p>
                <p className="mt-1 text-xs text-foreground-muted tabular-nums">
                  Pendientes: <span className="font-medium text-foreground">{formatearNumero(linea.pendiente)}</span> de{' '}
                  {formatearNumero(linea.cantidad)}
                </p>
              </div>
              <div className="grid grid-cols-3 gap-2 sm:w-80">
                {(
                  [
                    ['recibidas', 'Recibidas'],
                    ['danadas', 'Dañadas'],
                    ['faltantes', 'Faltan'],
                  ] as const
                ).map(([campo, etiqueta]) => (
                  <label key={campo} className="space-y-1">
                    <span className="block text-xs text-foreground-muted">{etiqueta}</span>
                    <input
                      type="number"
                      inputMode="numeric"
                      min={0}
                      max={linea.pendiente}
                      value={conteo[campo]}
                      placeholder="0"
                      onChange={(event) => cambiar(linea.id, campo, event.target.value)}
                      onWheel={(event) => event.currentTarget.blur()}
                      aria-label={`${etiqueta} de ${linea.producto.nombre}`}
                      aria-invalid={Boolean(errorLinea) || undefined}
                      aria-describedby={errorLinea ? idError : undefined}
                      className={cn('u-input text-right tabular-nums', SIN_FLECHAS)}
                    />
                  </label>
                ))}
              </div>
              {errorLinea && (
                <p id={idError} className="u-field-error sm:col-span-2">
                  {errorLinea}
                </p>
              )}
            </li>
          )
        })}
      </ul>

      <fieldset className="space-y-3">
        <legend className="u-label">Otras novedades</legend>
        {otras.length === 0 && <p className="text-sm text-foreground-muted">Ninguna. Añade una si llegó algo de más o distinto.</p>}
        {otras.map((otra) => {
          const errorOtra = erroresOtra[otra.clave]
          const actualizar = (cambios: Partial<Otra>) =>
            setOtras((actual) => actual.map((o) => (o.clave === otra.clave ? { ...o, ...cambios } : o)))
          return (
            <div key={otra.clave} className="space-y-2 rounded-md border border-border p-3">
              <div className="flex items-center justify-between gap-2">
                <p className="text-sm font-medium text-foreground">{ETIQUETA_INCIDENCIA[otra.tipo]}</p>
                <button
                  type="button"
                  onClick={() => setOtras((actual) => actual.filter((o) => o.clave !== otra.clave))}
                  className="rounded-md p-1.5 text-foreground-muted hover:bg-surface-muted hover:text-foreground"
                  aria-label={`Quitar ${ETIQUETA_INCIDENCIA[otra.tipo].toLowerCase()}`}
                >
                  <Trash2 className="size-4" aria-hidden="true" />
                </button>
              </div>
              <div className="grid gap-2 sm:grid-cols-[minmax(0,1fr)_7rem]">
                {otra.tipo === 'EXCEDENTE' ? (
                  <label className="space-y-1">
                    <span className="block text-xs text-foreground-muted">Producto</span>
                    <select value={otra.lineaId} onChange={(e) => actualizar({ lineaId: e.target.value })} className="u-input">
                      <option value="">Elige un producto…</option>
                      {pendientes.map((l) => (
                        <option key={l.id} value={l.id}>
                          {l.producto.nombre}
                        </option>
                      ))}
                    </select>
                  </label>
                ) : (
                  <label className="space-y-1">
                    <span className="block text-xs text-foreground-muted">Descripción</span>
                    <input
                      value={otra.descripcion}
                      onChange={(e) => actualizar({ descripcion: e.target.value })}
                      maxLength={500}
                      placeholder={otra.tipo === 'PRODUCTO_INCORRECTO' ? 'Qué llegó en lugar de lo pedido' : 'Qué pasó'}
                      className="u-input"
                    />
                  </label>
                )}
                <label className="space-y-1">
                  <span className="block text-xs text-foreground-muted">Unidades{otra.tipo !== 'EXCEDENTE' && ' (opcional)'}</span>
                  <input
                    type="number"
                    inputMode="numeric"
                    min={1}
                    value={otra.cantidad}
                    onChange={(e) => actualizar({ cantidad: e.target.value })}
                    onWheel={(event) => event.currentTarget.blur()}
                    className={cn('u-input text-right tabular-nums', SIN_FLECHAS)}
                  />
                </label>
              </div>
              {otra.tipo === 'EXCEDENTE' && (
                <p className="text-xs text-foreground-muted">Lo que llega de más no entra al inventario: queda informado.</p>
              )}
              {errorOtra && <p className="u-field-error">{errorOtra}</p>}
            </div>
          )
        })}
        <div className="flex flex-wrap gap-2">
          {(['EXCEDENTE', 'PRODUCTO_INCORRECTO', 'OTRO'] as const).map((tipo) => (
            <button key={tipo} type="button" className="u-btn u-btn--ghost h-9 text-sm" onClick={() => agregarOtra(tipo)}>
              <Plus className="size-4" aria-hidden="true" />
              {ETIQUETA_INCIDENCIA[tipo]}
            </button>
          ))}
        </div>
      </fieldset>

      <div className="space-y-1.5">
        <label htmlFor={`${formId}-obs`} className="u-label">
          Observaciones <span className="font-normal text-foreground-muted">(opcional)</span>
        </label>
        <textarea
          id={`${formId}-obs`}
          value={observaciones}
          onChange={(event) => setObservaciones(event.target.value)}
          maxLength={500}
          rows={2}
          className="u-input h-auto py-2.5"
          placeholder="Remisión del proveedor, estado de la entrega…"
        />
      </div>

      <div className="flex flex-col-reverse gap-2 sm:flex-row sm:items-center sm:justify-end sm:gap-3">
        {vacia && !hayErrores && <p className="text-sm text-foreground-muted sm:mr-auto">Indica al menos una cantidad o una novedad.</p>}
        {onCancelar && (
          <button type="button" className="u-btn u-btn--ghost" onClick={onCancelar}>
            Cancelar
          </button>
        )}
        <button type="submit" className="u-btn u-btn--primary" disabled={hayErrores || vacia}>
          <ClipboardCheck className="size-4" aria-hidden="true" />
          Revisar recepción
        </button>
      </div>
    </form>
  )
}

function Cifra({ titulo, valor }: { titulo: string; valor: string }) {
  return (
    <div className="rounded-md border border-border px-3 py-2">
      <dt className="text-xs text-foreground-muted">{titulo}</dt>
      <dd className="mt-0.5 font-medium text-foreground tabular-nums">{valor}</dd>
    </div>
  )
}
