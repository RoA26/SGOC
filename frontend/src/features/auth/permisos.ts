import { useEmpresaSeleccionadaStore } from '@/features/empresa/empresaSeleccionadaStore'
import type { EmpresaSupervisada } from '@/features/empresa/types'
import { useAuthStore } from '@/store/authStore'
import type { Rol } from './types'

/** Nombre visible de cada rol. */
export const ETIQUETA_ROL: Record<Rol, string> = {
  SUPER_ADMIN: 'Super administrador',
  GERENTE: 'Gerente',
  USUARIO: 'Usuario',
}

/** Roles con Permisos.GESTION en el backend: ven catálogos y todas las solicitudes de la empresa. */
export const ROLES_GESTION: readonly Rol[] = ['SUPER_ADMIN', 'GERENTE']
/** Personas de una empresa (Permisos.MIEMBRO_EMPRESA): las únicas que crean solicitudes. */
export const ROLES_EMPRESA: readonly Rol[] = ['GERENTE', 'USUARIO']
/** Administración de la plataforma: empresas y sus administradores. */
export const ROLES_PLATAFORMA: readonly Rol[] = ['SUPER_ADMIN']
/** Invitar trabajadores: solo el gerente, a su propia empresa. */
export const ROLES_INVITACION: readonly Rol[] = ['GERENTE']

/**
 * Empresa sobre la que se trabaja:
 * - GERENTE / USUARIO: la suya, fija (viene de la sesión).
 * - SUPER_ADMIN: la que supervisa, o ninguna (modo global). En ambos casos, solo lectura.
 */
export interface ContextoEmpresa {
  rol: Rol | null
  empresaId: number | null
  esSuperAdmin: boolean
  /** SUPER_ADMIN sin empresa elegida: ve todas las empresas. */
  modoGlobal: boolean
  /** Empresa que supervisa el SUPER_ADMIN (nombre y código), o null. */
  empresaSupervisada: EmpresaSupervisada | null
}

export function useContextoEmpresa(): ContextoEmpresa {
  const rol = useAuthStore((state) => state.rol)
  const empresaSesion = useAuthStore((state) => state.empresaId)
  const empresaElegida = useEmpresaSeleccionadaStore((state) => state.empresa)
  const esSuperAdmin = rol === 'SUPER_ADMIN'
  const empresaSupervisada = esSuperAdmin ? empresaElegida : null
  const empresaId = esSuperAdmin ? (empresaSupervisada?.id ?? null) : empresaSesion
  return { rol, empresaId, esSuperAdmin, modoGlobal: esSuperAdmin && empresaId === null, empresaSupervisada }
}

/** Qué puede hacer el usuario actual en la interfaz, según su rol y su contexto de empresa. */
export interface Permisos {
  /** Ve las solicitudes de toda la empresa (o de todas, en modo global), no solo las suyas. */
  verTodasLasSolicitudes: boolean
  /** Aprueba o rechaza solicitudes de su empresa. */
  revisarSolicitudes: boolean
  /** Crea solicitudes: solo personas de una empresa (el backend responde 403 al SUPER_ADMIN). */
  crearSolicitudes: boolean
  verCatalogos: boolean
  gestionarCatalogos: boolean
  gestionarInvitaciones: boolean
  /** Consulta, crea, supervisa y activa o desactiva empresas (cuenta raíz). */
  gestionarEmpresas: boolean
}

/**
 * Los cambios en los datos de una empresa los hace su GERENTE. El SUPER_ADMIN la supervisa
 * en solo lectura: así una consulta de soporte nunca modifica datos por accidente. (La API
 * conserva el modo soporte con X-Tenant-ID para él; la interfaz no lo expone.)
 */
export function calcularPermisos({ rol }: ContextoEmpresa): Permisos {
  const gestor = rol !== null && ROLES_GESTION.includes(rol)
  const gerente = rol === 'GERENTE'
  return {
    verTodasLasSolicitudes: gestor,
    revisarSolicitudes: gerente,
    crearSolicitudes: rol !== null && ROLES_EMPRESA.includes(rol),
    verCatalogos: gestor,
    gestionarCatalogos: gerente,
    gestionarInvitaciones: gerente,
    gestionarEmpresas: rol === 'SUPER_ADMIN',
  }
}

/**
 * Permisos de interfaz del usuario actual. Solo deciden qué se pinta: la autorización real
 * la aplica el backend en cada petición (403 / 400).
 */
export function usePermisos(): Permisos {
  return calcularPermisos(useContextoEmpresa())
}
