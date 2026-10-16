import axios from 'axios'
import { ArrowLeft, Eye, Loader2, Power, RotateCw, UserRound } from 'lucide-react'
import { useCallback, useEffect, useState, type ReactNode } from 'react'
import { Link, useNavigate, useParams } from 'react-router-dom'
import { PageHeader } from '@/components/layout/PageHeader'
import { BotonCopiar } from '@/components/ui/BotonCopiar'
import { ConfirmDialog } from '@/components/ui/ConfirmDialog'
import { DataTable, type Column } from '@/components/ui/DataTable'
import { ETIQUETA_ROL, useContextoEmpresa } from '@/features/auth/permisos'
import { empresaService } from '@/features/empresa/empresaApi'
import { useEmpresaSeleccionadaStore } from '@/features/empresa/empresaSeleccionadaStore'
import { EstadoEmpresaBadge, EstadoUsuarioBadge } from '@/features/empresa/EstadosBadges'
import type { Empresa, UsuarioEmpresa } from '@/features/empresa/types'
import { useAviso } from '@/hooks/useAviso'
import { usePaginatedResource } from '@/hooks/usePaginatedResource'
import { getApiErrorMessage } from '@/lib/errors'
import { formatearFecha } from '@/lib/fechas'
import type { PageParams } from '@/services/types'

const TAMANO_PAGINA = 10
/** Gerentes primero (GERENTE < USUARIO) y después por nombre. */
const ORDEN_USUARIOS = ['rol,asc', 'nombre,asc']

type Carga<T> = { estado: 'cargando' } | { estado: 'error'; mensaje: string } | { estado: 'listo'; datos: T }

/** Acción pendiente de confirmar: desactivar la empresa o la cuenta de su administrador. */
type Confirmacion = 'empresa' | 'gerente' | null

/**
 * Ficha de una empresa para el SUPER_ADMIN: datos, administrador principal, indicadores reales
 * (consultados dentro de esa empresa con X-Tenant-ID) y usuarios con su estado. Desde aquí se
 * supervisa la empresa y se activa o desactiva, a ella o a la cuenta de su administrador.
 */
export default function EmpresaDetalle() {
  const id = Number(useParams().id)
  const valido = Number.isInteger(id) && id > 0
  const [carga, setCarga] = useState<Carga<Empresa>>({ estado: 'cargando' })
  const [recarga, setRecarga] = useState(0)

  useEffect(() => {
    if (!valido) return
    const controller = new AbortController()
    empresaService
      .obtener(id, controller.signal)
      .then((empresa) => setCarga({ estado: 'listo', datos: empresa }))
      .catch((error: unknown) => {
        if (!axios.isCancel(error)) setCarga({ estado: 'error', mensaje: getApiErrorMessage(error) })
      })
    return () => controller.abort()
  }, [id, valido, recarga])

  if (!valido) {
    return <ErrorFicha mensaje="La dirección no corresponde a ninguna empresa." />
  }
  if (carga.estado === 'cargando') {
    return (
      <p className="flex items-center gap-2 text-sm text-foreground-muted">
        <Loader2 className="size-4 animate-spin" aria-hidden="true" />
        Cargando la empresa…
      </p>
    )
  }
  if (carga.estado === 'error') {
    return <ErrorFicha mensaje={carga.mensaje} onRetry={() => setRecarga((n) => n + 1)} />
  }
  return <Ficha empresa={carga.datos} onActualizada={(datos) => setCarga({ estado: 'listo', datos })} />
}

function ErrorFicha({ mensaje, onRetry }: { mensaje: string; onRetry?: () => void }) {
  return (
    <>
      <VolverAEmpresas />
      <div role="alert" className="u-alert u-alert--error flex flex-wrap items-center justify-between gap-3">
        <span>{mensaje}</span>
        {onRetry && (
          <button type="button" className="u-btn u-btn--ghost h-9 px-3" onClick={onRetry}>
            <RotateCw className="size-4" aria-hidden="true" />
            Reintentar
          </button>
        )}
      </div>
    </>
  )
}

function VolverAEmpresas() {
  return (
    <Link
      to="/admin/empresas"
      className="mb-6 inline-flex items-center gap-1.5 text-sm font-medium text-foreground-muted hover:text-foreground"
    >
      <ArrowLeft className="size-4" aria-hidden="true" />
      Empresas
    </Link>
  )
}

function Ficha({ empresa, onActualizada }: { empresa: Empresa; onActualizada: (empresa: Empresa) => void }) {
  const navigate = useNavigate()
  const { empresaSupervisada } = useContextoEmpresa()
  const supervisar = useEmpresaSeleccionadaStore((state) => state.seleccionar)
  const supervisandoEsta = empresaSupervisada?.id === empresa.id
  const { aviso, mostrarAviso } = useAviso()

  const [confirmacion, setConfirmacion] = useState<Confirmacion>(null)
  const [enCurso, setEnCurso] = useState(false)
  const [errorAccion, setErrorAccion] = useState<string | null>(null)
  const [recargaUsuarios, setRecargaUsuarios] = useState(0)

  function entrarEnSupervision() {
    supervisar({ id: empresa.id, nombre: empresa.nombre, codigo: empresa.codigo })
    navigate('/')
  }

  async function ejecutar(accion: () => Promise<void>, mensaje: string) {
    setEnCurso(true)
    setErrorAccion(null)
    try {
      await accion()
      setConfirmacion(null)
      mostrarAviso(mensaje)
    } catch (error) {
      setErrorAccion(getApiErrorMessage(error))
    } finally {
      setEnCurso(false)
    }
  }

  const cambiarEstadoEmpresa = (activa: boolean) =>
    ejecutar(async () => {
      onActualizada(await empresaService.cambiarEstado(empresa.id, activa))
    }, activa ? `«${empresa.nombre}» vuelve a estar activa.` : `«${empresa.nombre}» desactivada: sus usuarios ya no pueden operar.`)

  const gerente = empresa.gerente
  const cambiarEstadoGerente = (estado: 'ACTIVO' | 'INACTIVO') =>
    ejecutar(async () => {
      if (!gerente) return
      const actualizado = await empresaService.cambiarEstadoGerente(empresa.id, gerente.id, estado)
      onActualizada({ ...empresa, gerente: { ...gerente, estado: actualizado.estado } })
      setRecargaUsuarios((n) => n + 1)
    }, estado === 'ACTIVO' ? 'Cuenta del administrador reactivada.' : 'Cuenta del administrador desactivada.')

  return (
    <>
      <VolverAEmpresas />
      <PageHeader
        eyebrow="Ficha de empresa"
        title={empresa.nombre}
        description={`Código ${empresa.codigo} · NIT ${empresa.nit} · Alta el ${formatearFecha(empresa.creadoEn)}`}
        actions={
          <>
            {empresa.activa ? (
              <button
                type="button"
                className="u-btn u-btn--ghost"
                onClick={() => {
                  setErrorAccion(null)
                  setConfirmacion('empresa')
                }}
              >
                <Power className="size-4" aria-hidden="true" />
                Desactivar
              </button>
            ) : (
              <button type="button" className="u-btn u-btn--ghost" onClick={() => void cambiarEstadoEmpresa(true)} disabled={enCurso}>
                <Power className="size-4" aria-hidden="true" />
                Reactivar
              </button>
            )}
            {supervisandoEsta ? (
              <Link to="/" className="u-btn u-btn--primary">
                <Eye className="size-4" aria-hidden="true" />
                Ir al panel supervisado
              </Link>
            ) : (
              <button type="button" className="u-btn u-btn--primary" onClick={entrarEnSupervision}>
                <Eye className="size-4" aria-hidden="true" />
                Supervisar
              </button>
            )}
          </>
        }
      />

      <div role="status" aria-live="polite">
        {aviso && <p className="u-alert u-alert--success mb-6">{aviso}</p>}
      </div>
      {errorAccion && confirmacion === null && (
        <p role="alert" className="u-alert u-alert--error mb-6">
          {errorAccion}
        </p>
      )}

      <div className="grid gap-4 lg:grid-cols-2">
        <section aria-labelledby="datos-empresa" className="u-card p-5 sm:p-6">
          <h2 id="datos-empresa" className="font-medium text-foreground">
            Datos de la empresa
          </h2>
          <dl className="mt-4 grid grid-cols-[auto_minmax(0,1fr)] gap-x-6 gap-y-3 text-sm">
            <Dato termino="Código empresarial">
              <span className="flex flex-wrap items-center gap-x-2">
                <span className="font-mono font-medium tracking-wider text-foreground">{empresa.codigo}</span>
                <BotonCopiar texto={empresa.codigo} etiqueta="Copiar" ariaLabel="Copiar código empresarial" />
              </span>
            </Dato>
            <Dato termino="NIT">
              <span className="font-mono">{empresa.nit}</span>
            </Dato>
            <Dato termino="Estado">
              <EstadoEmpresaBadge activa={empresa.activa} />
            </Dato>
            <Dato termino="Alta">{formatearFecha(empresa.creadoEn)}</Dato>
          </dl>
          {!empresa.activa && (
            <p className="mt-4 text-xs text-foreground-muted">
              Desactivada: ninguno de sus usuarios puede iniciar sesión ni operar. Sus datos se conservan.
            </p>
          )}
        </section>

        <section aria-labelledby="administrador" className="u-card p-5 sm:p-6">
          <h2 id="administrador" className="font-medium text-foreground">
            Administrador principal
          </h2>
          {gerente ? (
            <>
              <div className="mt-4 flex items-start gap-3">
                <span className="flex size-10 shrink-0 items-center justify-center rounded-full bg-surface-muted">
                  <UserRound className="size-5 text-accent" aria-hidden="true" />
                </span>
                <div className="min-w-0 text-sm">
                  <p className="font-medium text-foreground">{gerente.nombre}</p>
                  <p className="text-foreground-muted">
                    <span className="font-mono">@{gerente.username}</span> · {ETIQUETA_ROL[gerente.rol]}
                  </p>
                  <a href={`mailto:${gerente.email}`} className="break-all text-foreground-muted hover:text-foreground hover:underline">
                    {gerente.email}
                  </a>
                  {gerente.estado && (
                    <p className="mt-2">
                      <EstadoUsuarioBadge estado={gerente.estado} />
                    </p>
                  )}
                </div>
              </div>
              <div className="mt-4 border-t border-border pt-4">
                {gerente.estado === 'INACTIVO' ? (
                  <button type="button" className="u-btn u-btn--ghost h-9 px-3 text-sm" onClick={() => void cambiarEstadoGerente('ACTIVO')} disabled={enCurso}>
                    Reactivar cuenta
                  </button>
                ) : (
                  <button
                    type="button"
                    className="u-btn u-btn--ghost h-9 px-3 text-sm"
                    onClick={() => {
                      setErrorAccion(null)
                      setConfirmacion('gerente')
                    }}
                  >
                    Desactivar cuenta
                  </button>
                )}
              </div>
            </>
          ) : (
            <p className="mt-4 text-sm text-foreground-muted">Esta empresa aún no tiene ningún gerente registrado.</p>
          )}
        </section>
      </div>

      <Indicadores empresaId={empresa.id} />

      <UsuariosEmpresa empresaId={empresa.id} recarga={recargaUsuarios} />

      <ConfirmDialog
        open={confirmacion === 'empresa'}
        title="Desactivar empresa"
        confirmLabel="Desactivar empresa"
        busy={enCurso}
        error={errorAccion}
        onConfirm={() => void cambiarEstadoEmpresa(false)}
        onCancel={() => setConfirmacion(null)}
      >
        <p>
          ¿Desactivar <strong className="text-foreground">{empresa.nombre}</strong>? Ninguno de sus usuarios, tampoco su
          administrador, podrá iniciar sesión ni operar hasta que la reactives. No se borra ningún dato.
        </p>
      </ConfirmDialog>

      <ConfirmDialog
        open={confirmacion === 'gerente'}
        title="Desactivar cuenta del administrador"
        confirmLabel="Desactivar cuenta"
        busy={enCurso}
        error={errorAccion}
        onConfirm={() => void cambiarEstadoGerente('INACTIVO')}
        onCancel={() => setConfirmacion(null)}
      >
        <p>
          ¿Desactivar la cuenta de <strong className="text-foreground">{gerente?.nombre}</strong>? Perderá el acceso
          desde su siguiente acción, aunque tenga la sesión abierta. Podrás reactivarla después.
        </p>
      </ConfirmDialog>
    </>
  )
}

function Dato({ termino, children }: { termino: string; children: ReactNode }) {
  return (
    <>
      <dt className="text-foreground-muted">{termino}</dt>
      <dd className="min-w-0 text-foreground">{children}</dd>
    </>
  )
}

interface ConteosEmpresa {
  productos: number
  proveedores: number
  pendientes: number
  aprobadas: number
  rechazadas: number
}

/** Indicadores reales de la empresa: totales de sus listados, pedidos con X-Tenant-ID de ESTA empresa. */
function Indicadores({ empresaId }: { empresaId: number }) {
  const [carga, setCarga] = useState<Carga<ConteosEmpresa>>({ estado: 'cargando' })

  useEffect(() => {
    const controller = new AbortController()
    const contar = (ruta: string, params?: Record<string, string>) =>
      empresaService.contarEn(empresaId, ruta, params, controller.signal)
    Promise.all([
      contar('/v1/productos'),
      contar('/v1/proveedores'),
      contar('/v1/solicitudes', { estado: 'PENDIENTE' }),
      contar('/v1/solicitudes', { estado: 'APROBADA' }),
      contar('/v1/solicitudes', { estado: 'RECHAZADA' }),
    ])
      .then(([productos, proveedores, pendientes, aprobadas, rechazadas]) =>
        setCarga({ estado: 'listo', datos: { productos, proveedores, pendientes, aprobadas, rechazadas } }),
      )
      .catch((error: unknown) => {
        if (!axios.isCancel(error)) setCarga({ estado: 'error', mensaje: getApiErrorMessage(error) })
      })
    return () => controller.abort()
  }, [empresaId])

  const valor = (clave: keyof ConteosEmpresa) => (carga.estado === 'listo' ? carga.datos[clave] : null)

  return (
    <section aria-labelledby="indicadores-empresa" className="mt-8">
      <h2 id="indicadores-empresa" className="font-medium text-foreground">
        Actividad
      </h2>
      {carga.estado === 'error' ? (
        <p role="alert" className="u-alert u-alert--error mt-3">
          No se pudieron cargar los indicadores: {carga.mensaje}
        </p>
      ) : (
        <div className="mt-3 grid grid-cols-2 gap-4 sm:grid-cols-3 lg:grid-cols-5">
          <Indicador titulo="Productos activos" valor={valor('productos')} />
          <Indicador titulo="Proveedores activos" valor={valor('proveedores')} />
          <Indicador titulo="Solicitudes pendientes" valor={valor('pendientes')} />
          <Indicador titulo="Solicitudes aprobadas" valor={valor('aprobadas')} />
          <Indicador titulo="Solicitudes rechazadas" valor={valor('rechazadas')} />
        </div>
      )}
    </section>
  )
}

function Indicador({ titulo, valor }: { titulo: string; valor: number | null }) {
  return (
    <div className="u-card p-4" aria-busy={valor === null}>
      <p className="font-serif text-3xl text-foreground tabular-nums">{valor ?? '—'}</p>
      <p className="mt-1 text-xs text-foreground-muted">{titulo}</p>
    </div>
  )
}

/** Usuarios de la empresa (gerentes primero) con su estado. Solo lectura. */
function UsuariosEmpresa({ empresaId, recarga }: { empresaId: number; recarga: number }) {
  const [pagina, setPagina] = useState(0)
  // El hook solo admite un criterio de orden: aquí se envían los dos que entiende Spring.
  const listar = useCallback(
    (params: PageParams, signal: AbortSignal) =>
      empresaService.listarUsuarios(empresaId, { ...params, sort: ORDEN_USUARIOS }, signal),
    [empresaId],
  )
  const { data, loading, error, reload } = usePaginatedResource(listar, pagina, TAMANO_PAGINA, 'rol,nombre', String(recarga))

  const columnas: Column<UsuarioEmpresa>[] = [
    {
      id: 'nombre',
      header: 'Nombre',
      cell: (usuario) => (
        <div className="min-w-40">
          <p className="font-medium text-foreground">{usuario.nombre}</p>
          <p className="font-mono text-xs text-foreground-muted">@{usuario.username}</p>
        </div>
      ),
    },
    { id: 'email', header: 'Correo', className: 'hidden md:table-cell', cell: (usuario) => usuario.email },
    { id: 'rol', header: 'Rol', cell: (usuario) => ETIQUETA_ROL[usuario.rol] },
    { id: 'estado', header: 'Estado', cell: (usuario) => <EstadoUsuarioBadge estado={usuario.estado} /> },
    {
      id: 'alta',
      header: 'Alta',
      className: 'hidden lg:table-cell',
      cell: (usuario) => <span className="whitespace-nowrap text-foreground-muted">{formatearFecha(usuario.creadoEn)}</span>,
    },
  ]

  return (
    <section aria-labelledby="usuarios-empresa" className="mt-8">
      <h2 id="usuarios-empresa" className="mb-3 font-medium text-foreground">
        Usuarios{data ? ` (${data.page.totalElements})` : ''}
      </h2>
      <DataTable
        caption="Usuarios de la empresa"
        columns={columnas}
        rows={data?.content ?? []}
        rowKey={(usuario) => usuario.id}
        loading={loading}
        error={error}
        onRetry={reload}
        emptyMessage="La empresa aún no tiene usuarios."
        page={data?.page}
        onPageChange={setPagina}
      />
    </section>
  )
}
