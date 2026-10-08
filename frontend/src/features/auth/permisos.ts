import { useEmpresaSeleccionadaStore } from '@/features/empresa/empresaSeleccionadaStore'
import { useAuthStore } from '@/store/authStore'
import type { Rol } from './types'

/** Nombre visible de cada rol. */
export const ETIQUETA_ROL: Record<Rol, string> = {
  SUPER_ADMIN: 'Super administrador',
  GERENTE: 'Gerente',
  USUARIO: 'Usuario',
}

/** Roles con Permisos.GESTION en el backend: catálogos, invitaciones y revisión de solicitudes. */
export const ROLES_GESTION: readonly Rol[] = ['SUPER_ADMIN', 'GERENTE']
/** Personas de una empresa (Permisos.MIEMBRO_EMPRESA): las únicas que crean solicitudes. */
export const ROLES_EMPRESA: readonly Rol[] = ['GERENTE', 'USUARIO']

/**
 * Empresa sobre la que se trabaja:
 * - GERENTE / USUARIO: la suya, fija (viene de la sesión).
 * - SUPER_ADMIN: la que haya elegido, o ninguna (modo global, solo lectura).
 */
export interface ContextoEmpresa {
  rol: Rol | null
  empresaId: number | null
  esSuperAdmin: boolean
  /** SUPER_ADMIN sin empresa elegida: ve todas las empresas pero no puede escribir. */
  modoGlobal: boolean
}

export function useContextoEmpresa(): ContextoEmpresa {
  const rol = useAuthStore((state) => state.rol)
  const empresaSesion = useAuthStore((state) => state.empresaId)
  const empresaElegida = useEmpresaSeleccionadaStore((state) => state.empresaId)
  const esSuperAdmin = rol === 'SUPER_ADMIN'
  const empresaId = esSuperAdmin ? empresaElegida : empresaSesion
  return { rol, empresaId, esSuperAdmin, modoGlobal: esSuperAdmin && empresaId === null }
}

/** Qué puede hacer el usuario actual en la interfaz, según su rol y su contexto de empresa. */
export interface Permisos {
  /** Ve las solicitudes de toda la empresa (o de todas, en modo global), no solo las suyas. */
  verTodasLasSolicitudes: boolean
  /** Aprueba o rechaza: escribir exige una empresa en el contexto. */
  revisarSolicitudes: boolean
  /** Crea solicitudes: solo personas de una empresa (el backend responde 403 al SUPER_ADMIN). */
  crearSolicitudes: boolean
  verCatalogos: boolean
  gestionarCatalogos: boolean
  gestionarInvitaciones: boolean
}

export function calcularPermisos({ rol, empresaId }: ContextoEmpresa): Permisos {
  const gestor = rol !== null && ROLES_GESTION.includes(rol)
  // Toda escritura de datos de negocio necesita empresa (el backend: "Empresa no seleccionada").
  const gestorConEmpresa = gestor && empresaId !== null
  return {
    verTodasLasSolicitudes: gestor,
    revisarSolicitudes: gestorConEmpresa,
    crearSolicitudes: rol !== null && ROLES_EMPRESA.includes(rol),
    verCatalogos: gestor,
    gestionarCatalogos: gestorConEmpresa,
    gestionarInvitaciones: gestorConEmpresa,
  }
}

/**
 * Permisos de interfaz del usuario actual. Solo deciden qué se pinta: la autorización real
 * la aplica el backend en cada petición (403 / 400).
 */
export function usePermisos(): Permisos {
  return calcularPermisos(useContextoEmpresa())
}
