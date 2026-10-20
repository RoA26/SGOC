import { Building, Building2, Eye, Globe2 } from 'lucide-react'
import { Link } from 'react-router-dom'
import { ETIQUETA_ROL, useContextoEmpresa } from '@/features/auth/permisos'
import { useAuthStore } from '@/store/authStore'
import { useEmpresaSeleccionadaStore } from './empresaSeleccionadaStore'
import { etiquetaEmpresa } from './etiquetas'
import type { EmpresaSupervisada } from './types'

/**
 * Empresa sobre la que se trabaja. ADMINISTRADOR_COMERCIAL y TRABAJADOR ven la suya, con el nombre comercial
 * que devuelve el backend (GET /api/auth/me). El SUPER_ADMIN
 * está en modo global o supervisando una empresa que eligió en «Empresas»; desde aquí vuelve al
 * modo global.
 */
export function ContextoEmpresaPanel() {
  const { rol, empresaId, esSuperAdmin, empresaSupervisada } = useContextoEmpresa()
  const empresaNombre = useAuthStore((state) => state.empresaNombre)
  const empresaCodigo = useAuthStore((state) => state.empresaCodigo)

  if (!esSuperAdmin) {
    return (
      <div className="rounded-lg border border-border bg-surface-muted/50 px-3 py-2.5">
        <p className="text-xs text-foreground-muted">Empresa</p>
        <p className="flex items-center gap-2 text-sm font-medium text-foreground">
          <Building className="size-4 shrink-0 text-accent" aria-hidden="true" />
          {/* Mientras llega el perfil de una sesión antigua, el id; nunca un nombre que no venga del backend. */}
          <span className="truncate">{empresaNombre ?? (empresaId !== null ? etiquetaEmpresa(empresaId) : 'Sin empresa')}</span>
        </p>
        <p className="mt-0.5 text-xs text-foreground-muted">
          {empresaCodigo && <span className="font-mono">{empresaCodigo} · </span>}
          {rol && ETIQUETA_ROL[rol]}
        </p>
      </div>
    )
  }
  return empresaSupervisada ? <PanelSupervision empresa={empresaSupervisada} /> : <PanelModoGlobal />
}

function PanelModoGlobal() {
  return (
    <div className="rounded-lg border border-border bg-surface-muted/50 px-3 py-2.5">
      <p className="text-xs text-foreground-muted">Super administrador</p>
      <p className="flex items-center gap-2 text-sm font-medium text-foreground">
        <Globe2 className="size-4 shrink-0 text-accent" aria-hidden="true" />
        Modo global
      </p>
      <p className="mt-0.5 text-xs text-foreground-muted">Ves los datos de todas las empresas.</p>
      <Link
        to="/admin/empresas"
        className="mt-2 inline-flex items-center gap-1.5 text-xs font-medium text-foreground underline-offset-4 hover:underline"
      >
        <Building2 className="size-3.5" aria-hidden="true" />
        Gestionar empresas
      </Link>
    </div>
  )
}

function PanelSupervision({ empresa }: { empresa: EmpresaSupervisada }) {
  const salir = useEmpresaSeleccionadaStore((state) => state.salir)
  return (
    <div className="rounded-lg border border-accent/40 bg-accent/10 px-3 py-2.5">
      <p className="flex items-center gap-1.5 text-xs text-foreground-muted">
        <Eye className="size-3.5" aria-hidden="true" />
        Supervisando · solo lectura
      </p>
      <p className="flex items-center gap-2 text-sm font-medium text-foreground">
        <Building className="size-4 shrink-0 text-accent" aria-hidden="true" />
        <span className="truncate">{empresa.nombre}</span>
      </p>
      <p className="mt-0.5 font-mono text-xs text-foreground-muted">{empresa.codigo}</p>
      <div className="mt-2 flex flex-wrap gap-x-3 gap-y-1">
        <Link
          to={`/admin/empresas/${empresa.id}`}
          className="text-xs font-medium text-foreground underline-offset-4 hover:underline"
        >
          Ver ficha
        </Link>
        <button
          type="button"
          onClick={salir}
          className="inline-flex items-center gap-1.5 text-xs font-medium text-foreground underline-offset-4 hover:underline"
        >
          <Globe2 className="size-3.5" aria-hidden="true" />
          Volver al modo global
        </button>
      </div>
    </div>
  )
}
