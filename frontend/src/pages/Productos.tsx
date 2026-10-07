import { CheckCircle2, Info, Pencil, Plus, Trash2 } from 'lucide-react'
import { useEffect, useState } from 'react'
import { Link } from 'react-router-dom'
import { PageHeader } from '@/components/layout/PageHeader'
import { ConfirmDialog } from '@/components/ui/ConfirmDialog'
import { DataTable, type Column } from '@/components/ui/DataTable'
import { usePuedeGestionarCatalogos } from '@/features/auth/permisos'
import { ProductoFormModal } from '@/features/productos/ProductoFormModal'
import { useAviso } from '@/hooks/useAviso'
import { usePaginatedResource } from '@/hooks/usePaginatedResource'
import { getApiErrorMessage } from '@/lib/errors'
import { formatearCOP } from '@/lib/moneda'
import { productoService, type Producto } from '@/services/productoService'
import { useProductosStore } from '@/store/productosStore'
import { useProveedoresStore } from '@/store/proveedoresStore'

const TAMANO_PAGINA = 10
const ORDEN = 'nombre,asc'
const SIN_PROVEEDORES = 'Debes registrar al menos un proveedor primero'
const ID_AYUDA_SIN_PROVEEDORES = 'productos-sin-proveedores'

type Editor = { producto?: Producto } | null

export default function Productos() {
  const puedeGestionar = usePuedeGestionarCatalogos()
  const [pagina, setPagina] = useState(0)
  const { data, loading, error, reload } = usePaginatedResource(productoService.listar, pagina, TAMANO_PAGINA, ORDEN)
  const filas = data?.content ?? []

  const [editor, setEditor] = useState<Editor>(null)
  const [porEliminar, setPorEliminar] = useState<Producto | null>(null)
  const [eliminando, setEliminando] = useState(false)
  const [errorEliminar, setErrorEliminar] = useState<string | null>(null)
  const { aviso, mostrarAviso } = useAviso()

  // Un producto siempre pertenece a un proveedor: sin proveedores no se puede crear ninguno.
  const proveedores = useProveedoresStore((state) => state.items)
  const estadoProveedores = useProveedoresStore((state) => state.estado)
  const cargarProveedores = useProveedoresStore((state) => state.cargar)
  // Altas, cambios y bajas cambian las opciones del formulario de solicitudes.
  const invalidarProductos = useProductosStore((state) => state.invalidar)
  useEffect(() => {
    if (puedeGestionar) void cargarProveedores({ forzar: true })
  }, [puedeGestionar, cargarProveedores])

  const sinProveedores = estadoProveedores === 'listo' && proveedores.length === 0
  // Mientras carga también se bloquea; si la carga falla, el formulario muestra el error.
  const altaBloqueada = sinProveedores || estadoProveedores === 'inicial' || estadoProveedores === 'cargando'

  function alGuardar(producto: Producto, modo: 'creado' | 'actualizado') {
    setEditor(null)
    mostrarAviso(`Producto «${producto.nombre}» ${modo}.`)
    invalidarProductos()
    reload()
  }

  async function confirmarEliminacion() {
    if (!porEliminar) return
    setEliminando(true)
    setErrorEliminar(null)
    try {
      await productoService.eliminar(porEliminar.id)
      mostrarAviso(`Producto «${porEliminar.nombre}» eliminado.`)
      invalidarProductos()
      setPorEliminar(null)
      if (filas.length === 1 && pagina > 0) setPagina(pagina - 1)
      else reload()
    } catch (err) {
      setErrorEliminar(getApiErrorMessage(err))
    } finally {
      setEliminando(false)
    }
  }

  const columnas: Column<Producto>[] = [
    {
      id: 'sku',
      header: 'SKU',
      cell: (producto) => <span className="font-mono text-xs whitespace-nowrap">{producto.sku}</span>,
    },
    {
      id: 'nombre',
      header: 'Producto',
      cell: (producto) => (
        <div className="min-w-48">
          <p className="font-medium text-foreground">{producto.nombre}</p>
          {producto.descripcion && (
            <p className="line-clamp-1 max-w-md text-xs text-foreground-muted">{producto.descripcion}</p>
          )}
        </div>
      ),
    },
    {
      id: 'proveedor',
      header: 'Proveedor',
      className: 'hidden md:table-cell',
      cell: (producto) => <span className="text-foreground-muted">{producto.proveedor.razonSocial}</span>,
    },
    {
      id: 'precio',
      header: 'Precio',
      align: 'right',
      cell: (producto) => (
        <span className="font-medium whitespace-nowrap tabular-nums">{formatearCOP(producto.precio)}</span>
      ),
    },
  ]

  // Sin permiso, la columna de acciones ni siquiera existe en el DOM.
  if (puedeGestionar) {
    columnas.push({
      id: 'acciones',
      header: <span className="sr-only">Acciones</span>,
      align: 'right',
      cell: (producto) => (
        <div className="flex justify-end gap-1">
          <button
            type="button"
            className="rounded-md p-2 text-foreground-muted hover:bg-surface-muted hover:text-foreground"
            onClick={() => setEditor({ producto })}
            aria-label={`Editar ${producto.nombre}`}
            title="Editar"
          >
            <Pencil className="size-4" aria-hidden="true" />
          </button>
          <button
            type="button"
            className="rounded-md p-2 text-foreground-muted hover:bg-danger-surface hover:text-danger"
            onClick={() => {
              setErrorEliminar(null)
              setPorEliminar(producto)
            }}
            aria-label={`Eliminar ${producto.nombre}`}
            title="Eliminar"
          >
            <Trash2 className="size-4" aria-hidden="true" />
          </button>
        </div>
      ),
    })
  }

  return (
    <>
      <PageHeader
        eyebrow="Catálogos"
        title="Productos"
        description="Bienes y servicios que se pueden incluir en una orden de compra."
        actions={
          puedeGestionar && (
            <button
              type="button"
              className="u-btn u-btn--primary disabled:cursor-not-allowed disabled:opacity-50"
              onClick={() => setEditor({})}
              disabled={altaBloqueada}
              aria-busy={estadoProveedores === 'cargando'}
              title={sinProveedores ? SIN_PROVEEDORES : undefined}
              aria-describedby={sinProveedores ? ID_AYUDA_SIN_PROVEEDORES : undefined}
            >
              <Plus className="size-4" aria-hidden="true" />
              Nuevo producto
            </button>
          )
        }
      />

      {/* El title no se ve en pantallas táctiles ni lo leen todos los lectores: se explica también aquí. */}
      {puedeGestionar && sinProveedores && (
        <p id={ID_AYUDA_SIN_PROVEEDORES} className="mb-6 flex items-start gap-2 text-sm text-foreground-muted">
          <Info className="mt-0.5 size-4 shrink-0" aria-hidden="true" />
          <span>
            {SIN_PROVEEDORES}: cada producto pertenece a un proveedor.{' '}
            <Link to="/proveedores" className="font-medium text-foreground underline-offset-4 hover:underline">
              Ir a Proveedores
            </Link>
          </span>
        </p>
      )}

      <p role="status" aria-live="polite">
        {aviso && (
          <span className="mb-4 flex items-center gap-2 rounded-md border border-success/30 bg-success/10 px-4 py-2.5 text-sm text-success">
            <CheckCircle2 className="size-4" aria-hidden="true" />
            {aviso}
          </span>
        )}
      </p>

      <DataTable
        caption="Listado de productos"
        columns={columnas}
        rows={filas}
        rowKey={(producto) => producto.id}
        loading={loading}
        error={error}
        onRetry={reload}
        emptyMessage={
          puedeGestionar && !sinProveedores
            ? 'Aún no hay productos. Crea el primero con «Nuevo producto».'
            : 'Aún no hay productos.'
        }
        page={data?.page}
        onPageChange={setPagina}
      />

      {puedeGestionar && editor && <ProductoFormModal producto={editor.producto} onClose={() => setEditor(null)} onSaved={alGuardar} />}

      {puedeGestionar && (
        <ConfirmDialog
          open={porEliminar !== null}
          title="Eliminar producto"
          confirmLabel="Eliminar"
          busy={eliminando}
          error={errorEliminar}
          onConfirm={confirmarEliminacion}
          onCancel={() => setPorEliminar(null)}
        >
          <p>
            ¿Eliminar <strong className="text-foreground">{porEliminar?.nombre}</strong> ({porEliminar?.sku})? Dejará de
            aparecer en el catálogo, pero se conserva su histórico.
          </p>
        </ConfirmDialog>
      )}
    </>
  )
}
