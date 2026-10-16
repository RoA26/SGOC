import axios from 'axios'
import { Loader2, ScanBarcode, Search } from 'lucide-react'
import { useId, useRef, useState, type FormEvent } from 'react'
import { getApiErrorMessage } from '@/lib/errors'
import { formatearCOP } from '@/lib/moneda'
import { formatearNumero } from '@/lib/numeros'
import { inventarioService, type ProductoEncontrado } from '@/services/inventarioService'
import { EstadoStockBadge } from './EstadoStockBadge'

type Resultado =
  | { tipo: 'vacio' }
  | { tipo: 'buscando'; codigo: string }
  | { tipo: 'encontrado'; codigo: string; datos: ProductoEncontrado }
  | { tipo: 'error'; codigo: string; mensaje: string; inactivo: boolean }

/**
 * Búsqueda por escáner o teclado. Los lectores de código de barras USB/Bluetooth funcionan como
 * un teclado: escriben el código y pulsan Enter, que lanza la búsqueda. El campo queda
 * seleccionado para que la siguiente lectura sustituya a la anterior. La empresa la fija el
 * backend; nunca se envía.
 */
export function BuscadorCodigo() {
  const [codigo, setCodigo] = useState('')
  const [resultado, setResultado] = useState<Resultado>({ tipo: 'vacio' })
  const inputRef = useRef<HTMLInputElement>(null)
  const peticion = useRef<AbortController | null>(null)
  const id = useId()

  async function buscar(event: FormEvent) {
    event.preventDefault()
    const limpio = codigo.trim()
    if (!limpio) return
    peticion.current?.abort()
    const controller = new AbortController()
    peticion.current = controller
    setResultado({ tipo: 'buscando', codigo: limpio })
    try {
      const datos = await inventarioService.buscarPorCodigo(limpio, controller.signal)
      setResultado({ tipo: 'encontrado', codigo: limpio, datos })
    } catch (error) {
      if (axios.isCancel(error)) return
      const inactivo = axios.isAxiosError(error) && error.response?.status === 409
      setResultado({ tipo: 'error', codigo: limpio, mensaje: getApiErrorMessage(error), inactivo })
    } finally {
      inputRef.current?.select()
    }
  }

  return (
    <section aria-labelledby={`${id}-titulo`} className="u-card mb-6 p-5">
      <h2 id={`${id}-titulo`} className="flex items-center gap-2 font-medium text-foreground">
        <ScanBarcode className="size-4 text-accent" aria-hidden="true" />
        Buscar por código
      </h2>
      <p className="mt-0.5 text-xs text-foreground-muted">
        Escanea el código de barras o escribe el SKU y pulsa Enter. Se respetan los ceros a la izquierda.
      </p>
      <form onSubmit={buscar} className="mt-3 flex gap-2" role="search">
        <label htmlFor={id} className="sr-only">
          Código de barras o SKU
        </label>
        <input
          ref={inputRef}
          id={id}
          value={codigo}
          onChange={(event) => setCodigo(event.target.value)}
          className="u-input min-w-0 flex-1 font-mono"
          placeholder="7702004003508 o TOR-M8-100"
          autoComplete="off"
          autoCapitalize="none"
          spellCheck={false}
          maxLength={64}
          inputMode="text"
          autoFocus
        />
        <button type="submit" className="u-btn u-btn--primary shrink-0" disabled={!codigo.trim() || resultado.tipo === 'buscando'}>
          {resultado.tipo === 'buscando' ? <Loader2 className="size-4 animate-spin" aria-hidden="true" /> : <Search className="size-4" aria-hidden="true" />}
          Buscar
        </button>
      </form>

      <div role="status" aria-live="polite" className="mt-3">
        {resultado.tipo === 'encontrado' && (
          <div className="flex flex-wrap items-center justify-between gap-3 rounded-md border border-border bg-surface-muted/50 px-4 py-3">
            <div className="min-w-0">
              <p className="font-medium text-foreground">{resultado.datos.producto.nombre}</p>
              <p className="font-mono text-xs text-foreground-muted">
                SKU {resultado.datos.producto.sku}
                {resultado.datos.producto.codigoBarras && ` · ${resultado.datos.producto.codigoBarras}`} ·{' '}
                {resultado.datos.coincidePor === 'CODIGO_BARRAS' ? 'por código de barras' : 'por SKU'}
              </p>
              <p className="mt-0.5 text-xs text-foreground-muted">
                {resultado.datos.producto.proveedor.razonSocial} · precio de referencia {formatearCOP(resultado.datos.producto.precio)}
              </p>
            </div>
            <div className="text-right">
              <p className="text-2xl font-semibold text-foreground">{formatearNumero(resultado.datos.existencias)}</p>
              <EstadoStockBadge estado={resultado.datos.estadoStock} />
            </div>
          </div>
        )}
        {resultado.tipo === 'error' && (
          <p role="alert" className="u-alert u-alert--error">
            {resultado.mensaje}
            {resultado.inactivo && ' No se puede usar en compras ni en inventario.'}
          </p>
        )}
      </div>
    </section>
  )
}
