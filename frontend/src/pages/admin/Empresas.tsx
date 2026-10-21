import { Building2, CheckCircle2, Eye, FileText, Plus, X } from 'lucide-react'
import { useState } from 'react'
import { Link, useNavigate } from 'react-router-dom'
import { PageHeader } from '@/components/layout/PageHeader'
import { BotonCopiar } from '@/components/ui/BotonCopiar'
import { DataTable, type Column } from '@/components/ui/DataTable'
import { CrearEmpresaModal } from '@/features/empresa/CrearEmpresaModal'
import { empresaService } from '@/features/empresa/empresaApi'
import { useEmpresaSeleccionadaStore } from '@/features/empresa/empresaSeleccionadaStore'
import { EmpresaDemoBadge, EstadoEmpresaBadge } from '@/features/empresa/EstadosBadges'
import type { Empresa } from '@/features/empresa/types'
import { usePaginatedResource } from '@/hooks/usePaginatedResource'
import { formatearFecha } from '@/lib/fechas'

const TAMANO_PAGINA = 20
const ORDEN = 'nombre,asc'

/**
 * Gestión de empresas del SUPER_ADMIN: todas las organizaciones registradas con su código
 * empresarial y su administrador principal; desde aquí se crean, se abre su ficha o se
 * supervisan (solo lectura, con su propia cuenta).
 */
export default function Empresas() {
  const navigate = useNavigate()
  const supervisar = useEmpresaSeleccionadaStore((state) => state.seleccionar)
  const [pagina, setPagina] = useState(0)
  const { data, loading, error, reload } = usePaginatedResource(empresaService.listar, pagina, TAMANO_PAGINA, ORDEN)
  const filas = data?.content ?? []
  const [creando, setCreando] = useState(false)
  const [creada, setCreada] = useState<Empresa | null>(null)

  function alCrear(empresa: Empresa) {
    setCreando(false)
    setCreada(empresa)
    reload()
  }

  function entrarEnSupervision(empresa: Empresa) {
    supervisar({ id: empresa.id, nombre: empresa.nombre, codigo: empresa.codigo, demo: empresa.demo })
    navigate('/')
  }

  const columnas: Column<Empresa>[] = [
    {
      id: 'empresa',
      header: 'Empresa',
      cell: (empresa) => (
        <div className="min-w-44">
          <span className="flex flex-wrap items-center gap-x-2 gap-y-1">
            <Link to={`/admin/empresas/${empresa.id}`} className="font-medium text-foreground hover:underline">
              {empresa.nombre}
            </Link>
            {empresa.demo && <EmpresaDemoBadge />}
          </span>
          <p className="font-mono text-xs text-foreground-muted">NIT {empresa.nit}</p>
        </div>
      ),
    },
    {
      id: 'codigo',
      header: 'Código',
      cell: (empresa) => (
        <span className="rounded-md border border-border bg-surface-muted px-2 py-0.5 font-mono text-xs font-medium tracking-wider whitespace-nowrap text-foreground">
          {empresa.codigo}
        </span>
      ),
    },
    {
      id: 'administrador',
      header: 'Administrador principal',
      cell: (empresa) =>
        empresa.administrador ? (
          <div className="min-w-44">
            <p className="text-foreground">{empresa.administrador.nombre}</p>
            <p className="text-xs text-foreground-muted">{empresa.administrador.email}</p>
          </div>
        ) : (
          <span className="text-foreground-muted">Sin administrador</span>
        ),
    },
    {
      id: 'estado',
      header: 'Estado',
      cell: (empresa) => <EstadoEmpresaBadge activa={empresa.activa} />,
    },
    {
      id: 'creada',
      header: 'Alta',
      className: 'hidden lg:table-cell',
      cell: (empresa) => <span className="whitespace-nowrap text-foreground-muted">{formatearFecha(empresa.creadoEn)}</span>,
    },
    {
      id: 'acciones',
      header: <span className="sr-only">Acciones</span>,
      align: 'right',
      cell: (empresa) => (
        <div className="flex justify-end gap-1">
          <Link
            to={`/admin/empresas/${empresa.id}`}
            className="inline-flex items-center gap-1.5 rounded-md px-2.5 py-1.5 text-sm text-foreground-muted hover:bg-surface-muted hover:text-foreground"
            aria-label={`Ver detalles de ${empresa.nombre}`}
          >
            <FileText className="size-4" aria-hidden="true" />
            <span className="hidden xl:inline">Detalles</span>
          </Link>
          <button
            type="button"
            onClick={() => entrarEnSupervision(empresa)}
            className="inline-flex items-center gap-1.5 rounded-md px-2.5 py-1.5 text-sm text-foreground-muted hover:bg-surface-muted hover:text-foreground"
            aria-label={`Supervisar ${empresa.nombre}`}
          >
            <Eye className="size-4" aria-hidden="true" />
            <span className="hidden xl:inline">Supervisar</span>
          </button>
        </div>
      ),
    },
  ]

  const sinEmpresas = !loading && !error && data !== null && data.page.totalElements === 0

  return (
    <>
      <PageHeader
        eyebrow="Administración"
        title="Empresas"
        description="Organizaciones registradas en Unisen, con su código empresarial y su administrador principal."
        actions={
          <button type="button" className="u-btn u-btn--primary" onClick={() => setCreando(true)}>
            <Plus className="size-4" aria-hidden="true" />
            Crear empresa
          </button>
        }
      />

      <div role="status" aria-live="polite">
        {creada && <EmpresaCreada empresa={creada} onClose={() => setCreada(null)} />}
      </div>

      {sinEmpresas ? (
        <section className="u-card flex flex-col items-center px-6 py-14 text-center">
          <span className="flex size-12 items-center justify-center rounded-full bg-surface-muted">
            <Building2 className="size-6 text-accent" aria-hidden="true" />
          </span>
          <h2 className="mt-4 font-medium text-foreground">Aún no hay empresas registradas</h2>
          <p className="mt-1 max-w-md text-sm text-foreground-muted">
            Registra la primera junto con su administrador principal, que podrá entrar de inmediato y dar de alta a su equipo.
          </p>
          <button type="button" className="u-btn u-btn--primary mt-5" onClick={() => setCreando(true)}>
            <Plus className="size-4" aria-hidden="true" />
            Registrar la primera empresa
          </button>
        </section>
      ) : (
        <DataTable
          caption="Empresas registradas"
          columns={columnas}
          rows={filas}
          rowKey={(empresa) => empresa.id}
          loading={loading}
          error={error}
          onRetry={reload}
          emptyMessage="Aún no hay empresas registradas."
          page={data?.page}
          onPageChange={setPagina}
        />
      )}

      {creando && <CrearEmpresaModal onClose={() => setCreando(false)} onCreated={alCrear} />}
    </>
  )
}

/** Confirmación del alta con lo que hay que comunicar al nuevo administrador. */
function EmpresaCreada({ empresa, onClose }: { empresa: Empresa; onClose: () => void }) {
  return (
    <section className="u-alert u-alert--success relative mb-6 pr-12">
      <button
        type="button"
        onClick={onClose}
        className="absolute top-2.5 right-2.5 rounded-md p-1 hover:bg-success/10"
        aria-label="Cerrar confirmación"
      >
        <X className="size-4" aria-hidden="true" />
      </button>
      <p className="flex items-center gap-2 font-medium">
        <CheckCircle2 className="size-4 shrink-0" aria-hidden="true" />
        Empresa «{empresa.nombre}» creada con el código {empresa.codigo}.
      </p>
      {empresa.administrador && (
        <p className="mt-1 text-foreground">
          Su administrador, {empresa.administrador.nombre}, ya puede iniciar sesión con el usuario{' '}
          <strong className="font-mono font-medium">{empresa.administrador.username}</strong> y la contraseña inicial que
          definiste.
        </p>
      )}
      <div className="mt-3 flex flex-wrap items-center gap-3">
        <BotonCopiar texto={empresa.codigo} etiqueta="Copiar código" />
        {empresa.administrador && <BotonCopiar texto={empresa.administrador.username} etiqueta="Copiar usuario" />}
        <Link to={`/admin/empresas/${empresa.id}`} className="u-btn u-btn--ghost h-9 px-3">
          Ver ficha
        </Link>
      </div>
    </section>
  )
}
