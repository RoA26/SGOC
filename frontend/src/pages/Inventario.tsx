import { CheckCircle2, History, Search, SlidersHorizontal } from 'lucide-react'
import { useCallback, useEffect, useState } from 'react'
import { useSearchParams } from 'react-router-dom'
import { PageHeader } from '@/components/layout/PageHeader'
import { DataTable, type Column } from '@/components/ui/DataTable'
import { Modal } from '@/components/ui/Modal'
import { usePermisos, useContextoEmpresa } from '@/features/auth/permisos'
import { AvisoModoGlobal } from '@/features/empresa/AvisoModoGlobal'
import { AjusteModal } from '@/features/inventario/AjusteModal'
import { BuscadorCodigo } from '@/features/inventario/BuscadorCodigo'
import { EstadoStockBadge } from '@/features/inventario/EstadoStockBadge'
import { ETIQUETA_ESTADO_STOCK } from '@/features/inventario/estados'
import { TablaMovimientos } from '@/features/inventario/TablaMovimientos'
import { useAviso } from '@/hooks/useAviso'
import { usePaginatedResource } from '@/hooks/usePaginatedResource'
import { cn } from '@/lib/cn'
import { formatearFecha } from '@/lib/fechas'
import { formatearNumero } from '@/lib/numeros'
import { inventarioService, type EstadoStock, type Existencia } from '@/services/inventarioService'
import type { PageParams } from '@/services/types'

const TAMANO_PAGINA = 20
const ORDEN = 'producto.nombre,asc'
const ESTADOS: readonly EstadoStock[] = ['AGOTADO', 'CRITICO', 'NORMAL']
const ESPERA_BUSQUEDA_MS = 300

function estadoDeUrl(valor: string | null): EstadoStock | null {
  return ESTADOS.find((estado) => estado === valor) ?? null
}

/**
 * Existencias de los productos activos. Toda la empresa las consulta (sin costos); el
 * ADMIN_EMPRESA registra ajustes y la gestión ve el kárdex. Las cifras salen siempre del backend:
 * se mueven con recepciones y ajustes, nunca editándolas aquí.
 */
export default function Inventario() {
  const permisos = usePermisos()
  const { modoGlobal } = useContextoEmpresa()
  const [searchParams, setSearchParams] = useSearchParams()
  const estado = estadoDeUrl(searchParams.get('estado'))
  const [texto, setTexto] = useState('')
  const [busqueda, setBusqueda] = useState('')
  const [pagina, setPagina] = useState(0)
  const [ajustando, setAjustando] = useState<Existencia | null>(null)
  const [kardex, setKardex] = useState<Existencia | null>(null)
  const [recargaMovimientos, setRecargaMovimientos] = useState(0)
  const { aviso, mostrarAviso } = useAviso()

  useEffect(() => {
    const id = window.setTimeout(() => {
      setBusqueda(texto.trim())
      setPagina(0)
    }, ESPERA_BUSQUEDA_MS)
    return () => window.clearTimeout(id)
  }, [texto])

  const listar = useCallback(
    (params: PageParams, signal: AbortSignal) =>
      inventarioService.listar({ ...params, estado: estado ?? undefined, q: busqueda || undefined }, signal),
    [estado, busqueda],
  )
  const { data, loading, error, reload } = usePaginatedResource(listar, pagina, TAMANO_PAGINA, ORDEN, `${estado ?? ''}|${busqueda}`)

  function filtrar(valor: EstadoStock | null) {
    setSearchParams((actuales) => {
      const siguientes = new URLSearchParams(actuales)
      if (valor) siguientes.set('estado', valor)
      else siguientes.delete('estado')
      return siguientes
    })
    setPagina(0)
  }

  const columnas: Column<Existencia>[] = [
    {
      id: 'producto',
      header: 'Producto',
      cell: (fila) => (
        <div className="min-w-44">
          <p className="font-medium text-foreground">{fila.nombre}</p>
          <p className="font-mono text-xs text-foreground-muted">
            {fila.sku}
            {fila.codigoBarras && ` · ${fila.codigoBarras}`}
          </p>
        </div>
      ),
    },
    {
      id: 'cantidad',
      header: 'Existencias',
      align: 'right',
      cell: (fila) => <span className="font-medium tabular-nums">{formatearNumero(fila.cantidad)}</span>,
    },
    {
      id: 'minimo',
      header: 'Mínimo',
      align: 'right',
      className: 'hidden sm:table-cell',
      cell: (fila) => <span className="tabular-nums text-foreground-muted">{fila.stockMinimo > 0 ? formatearNumero(fila.stockMinimo) : '—'}</span>,
    },
    { id: 'estado', header: 'Estado', cell: (fila) => <EstadoStockBadge estado={fila.estado} /> },
    {
      id: 'actualizado',
      header: 'Último movimiento',
      className: 'hidden lg:table-cell',
      cell: (fila) => <span className="whitespace-nowrap text-foreground-muted">{formatearFecha(fila.actualizadoEn)}</span>,
    },
  ]
  if (permisos.verCompras || permisos.ajustarInventario) {
    columnas.push({
      id: 'acciones',
      header: <span className="sr-only">Acciones</span>,
      align: 'right',
      cell: (fila) => (
        <div className="flex justify-end gap-1">
          {permisos.verCompras && (
            <button
              type="button"
              className="rounded-md p-2 text-foreground-muted hover:bg-surface-muted hover:text-foreground"
              onClick={() => setKardex(fila)}
              aria-label={`Ver el kárdex de ${fila.nombre}`}
              title="Kárdex"
            >
              <History className="size-4" aria-hidden="true" />
            </button>
          )}
          {permisos.ajustarInventario && (
            <button
              type="button"
              className="rounded-md p-2 text-foreground-muted hover:bg-surface-muted hover:text-foreground"
              onClick={() => setAjustando(fila)}
              aria-label={`Ajustar las existencias de ${fila.nombre}`}
              title="Ajustar"
            >
              <SlidersHorizontal className="size-4" aria-hidden="true" />
            </button>
          )}
        </div>
      ),
    })
  }

  return (
    <>
      <PageHeader
        eyebrow="Inventario"
        title="Existencias"
        description="Unidades disponibles de cada producto activo. Suben con las recepciones y cambian con los ajustes registrados."
      />
      <AvisoModoGlobal que="las existencias" />
      <div role="status" aria-live="polite">
        {aviso && (
          <p className="u-alert u-alert--success mb-6 flex items-center gap-2">
            <CheckCircle2 className="size-4 shrink-0" aria-hidden="true" />
            {aviso}
          </p>
        )}
      </div>

      {/* Sin empresa (modo global) la búsqueda por código no aplica: el backend la rechaza. */}
      {!modoGlobal && <BuscadorCodigo />}

      <div className="mb-4 flex flex-wrap items-center gap-2">
        <div role="group" aria-label="Filtrar por estado" className="flex flex-wrap gap-2">
          {[null, ...ESTADOS].map((valor) => (
            <button
              key={valor ?? 'todos'}
              type="button"
              aria-pressed={estado === valor}
              onClick={() => filtrar(valor)}
              className={cn(
                'h-8 rounded-full border px-3 text-sm',
                estado === valor
                  ? 'border-foreground bg-foreground font-medium text-background'
                  : 'border-border text-foreground-muted hover:bg-surface-muted hover:text-foreground',
              )}
            >
              {valor ? ETIQUETA_ESTADO_STOCK[valor] : 'Todos'}
            </button>
          ))}
        </div>
        <label className="relative ml-auto w-full sm:w-64">
          <span className="sr-only">Buscar en el inventario</span>
          <Search className="pointer-events-none absolute top-1/2 left-3 size-4 -translate-y-1/2 text-foreground-muted" aria-hidden="true" />
          <input
            type="search"
            value={texto}
            onChange={(event) => setTexto(event.target.value)}
            placeholder="Nombre, SKU o código"
            className="u-input h-9 pl-9"
          />
        </label>
      </div>

      <DataTable
        caption="Existencias por producto"
        columns={columnas}
        rows={data?.content ?? []}
        rowKey={(fila) => fila.productoId}
        loading={loading}
        error={error}
        onRetry={reload}
        emptyMessage={estado || busqueda ? 'Ningún producto coincide con el filtro.' : 'Aún no hay productos activos en el catálogo.'}
        page={data?.page}
        onPageChange={setPagina}
      />

      {permisos.verCompras && (
        <section aria-labelledby="movimientos" className="mt-10">
          <h2 id="movimientos" className="mb-3 font-medium text-foreground">
            Movimientos recientes
          </h2>
          <TablaMovimientos recarga={recargaMovimientos} />
        </section>
      )}

      {ajustando && (
        <AjusteModal
          existencia={ajustando}
          onClose={() => setAjustando(null)}
          onAjustado={(movimiento) => {
            setAjustando(null)
            mostrarAviso(
              `Ajuste registrado en «${movimiento.producto.nombre}»: ahora hay ${formatearNumero(movimiento.saldoResultante)} unidades.`,
            )
            reload()
            setRecargaMovimientos((n) => n + 1)
          }}
        />
      )}
      {kardex && (
        <Modal open onClose={() => setKardex(null)} title={`Kárdex · ${kardex.nombre}`} description={`${kardex.sku} · ${formatearNumero(kardex.cantidad)} unidades`} size="xl">
          <TablaMovimientos productoId={kardex.productoId} />
        </Modal>
      )}
    </>
  )
}
