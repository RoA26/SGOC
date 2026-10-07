import { CheckCircle2, Pencil, Plus, Trash2 } from 'lucide-react'
import { useState } from 'react'
import { PageHeader } from '@/components/layout/PageHeader'
import { ConfirmDialog } from '@/components/ui/ConfirmDialog'
import { DataTable, type Column } from '@/components/ui/DataTable'
import { usePuedeGestionarCatalogos } from '@/features/auth/permisos'
import { ProveedorFormModal } from '@/features/proveedores/ProveedorFormModal'
import { useAviso } from '@/hooks/useAviso'
import { usePaginatedResource } from '@/hooks/usePaginatedResource'
import { getApiErrorMessage } from '@/lib/errors'
import { proveedorService, type Proveedor } from '@/services/proveedorService'
import { useProveedoresStore } from '@/store/proveedoresStore'

const TAMANO_PAGINA = 10
const ORDEN = 'razonSocial,asc'

/** null = cerrado; { proveedor: undefined } = alta; { proveedor } = edición. */
type Editor = { proveedor?: Proveedor } | null

export default function Proveedores() {
  const puedeGestionar = usePuedeGestionarCatalogos()
  // Crear o eliminar proveedores cambia lo que ve el catálogo de productos.
  const invalidarProveedores = useProveedoresStore((state) => state.invalidar)
  const [pagina, setPagina] = useState(0)
  const { data, loading, error, reload } = usePaginatedResource(proveedorService.listar, pagina, TAMANO_PAGINA, ORDEN)
  const filas = data?.content ?? []

  const [editor, setEditor] = useState<Editor>(null)
  const [porEliminar, setPorEliminar] = useState<Proveedor | null>(null)
  const [eliminando, setEliminando] = useState(false)
  const [errorEliminar, setErrorEliminar] = useState<string | null>(null)
  const { aviso, mostrarAviso } = useAviso()

  function alGuardar(proveedor: Proveedor, modo: 'creado' | 'actualizado') {
    setEditor(null)
    mostrarAviso(`Proveedor «${proveedor.razonSocial}» ${modo}.`)
    invalidarProveedores()
    reload()
  }

  function pedirEliminacion(proveedor: Proveedor) {
    setErrorEliminar(null)
    setPorEliminar(proveedor)
  }

  async function confirmarEliminacion() {
    if (!porEliminar) return
    setEliminando(true)
    setErrorEliminar(null)
    try {
      await proveedorService.eliminar(porEliminar.id)
      mostrarAviso(`Proveedor «${porEliminar.razonSocial}» eliminado.`)
      invalidarProveedores()
      setPorEliminar(null)
      // Si era el último de la página, retrocede una; si no, recarga la actual.
      if (filas.length === 1 && pagina > 0) setPagina(pagina - 1)
      else reload()
    } catch (err) {
      // 409: el proveedor tiene productos activos.
      setErrorEliminar(getApiErrorMessage(err))
    } finally {
      setEliminando(false)
    }
  }

  const columnas: Column<Proveedor>[] = [
    {
      id: 'nit',
      header: 'NIT',
      cell: (proveedor) => <span className="font-mono text-xs whitespace-nowrap">{proveedor.nit}</span>,
    },
    {
      id: 'razonSocial',
      header: 'Razón social',
      cell: (proveedor) => (
        <div className="min-w-48">
          <p className="font-medium text-foreground">{proveedor.razonSocial}</p>
          {proveedor.direccion && <p className="text-xs text-foreground-muted">{proveedor.direccion}</p>}
        </div>
      ),
    },
    {
      id: 'email',
      header: 'Correo',
      cell: (proveedor) => (
        <a className="text-foreground-muted hover:text-foreground hover:underline" href={`mailto:${proveedor.email}`}>
          {proveedor.email}
        </a>
      ),
    },
    {
      id: 'telefono',
      header: 'Teléfono',
      className: 'hidden md:table-cell',
      cell: (proveedor) => <span className="whitespace-nowrap">{proveedor.telefono ?? '—'}</span>,
    },
  ]

  // Sin permiso, la columna de acciones ni siquiera existe en el DOM.
  if (puedeGestionar) {
    columnas.push({
      id: 'acciones',
      header: <span className="sr-only">Acciones</span>,
      align: 'right',
      cell: (proveedor) => (
        <div className="flex justify-end gap-1">
          <button
            type="button"
            className="rounded-md p-2 text-foreground-muted hover:bg-surface-muted hover:text-foreground"
            onClick={() => setEditor({ proveedor })}
            aria-label={`Editar ${proveedor.razonSocial}`}
            title="Editar"
          >
            <Pencil className="size-4" aria-hidden="true" />
          </button>
          <button
            type="button"
            className="rounded-md p-2 text-foreground-muted hover:bg-danger-surface hover:text-danger"
            onClick={() => pedirEliminacion(proveedor)}
            aria-label={`Eliminar ${proveedor.razonSocial}`}
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
        title="Proveedores"
        description="Empresas a las que Unisen compra bienes y servicios."
        actions={
          puedeGestionar && (
            <button type="button" className="u-btn u-btn--primary" onClick={() => setEditor({})}>
              <Plus className="size-4" aria-hidden="true" />
              Nuevo proveedor
            </button>
          )
        }
      />

      <p role="status" aria-live="polite" className="min-h-0">
        {aviso && (
          <span className="mb-4 flex items-center gap-2 rounded-md border border-success/30 bg-success/10 px-4 py-2.5 text-sm text-success">
            <CheckCircle2 className="size-4" aria-hidden="true" />
            {aviso}
          </span>
        )}
      </p>

      <DataTable
        caption="Listado de proveedores"
        columns={columnas}
        rows={filas}
        rowKey={(proveedor) => proveedor.id}
        loading={loading}
        error={error}
        onRetry={reload}
        emptyMessage={puedeGestionar ? 'Aún no hay proveedores. Crea el primero con «Nuevo proveedor».' : 'Aún no hay proveedores.'}
        page={data?.page}
        onPageChange={setPagina}
      />

      {puedeGestionar && editor && (
        <ProveedorFormModal proveedor={editor.proveedor} onClose={() => setEditor(null)} onSaved={alGuardar} />
      )}

      {puedeGestionar && (
        <ConfirmDialog
          open={porEliminar !== null}
          title="Eliminar proveedor"
          confirmLabel="Eliminar"
          busy={eliminando}
          error={errorEliminar}
          onConfirm={confirmarEliminacion}
          onCancel={() => setPorEliminar(null)}
        >
          <p>
            ¿Eliminar a <strong className="text-foreground">{porEliminar?.razonSocial}</strong>? Dejará de aparecer en
            los catálogos, pero se conserva su histórico.
          </p>
        </ConfirmDialog>
      )}
    </>
  )
}
