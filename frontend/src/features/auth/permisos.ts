import { useEmpresaSeleccionadaStore } from '@/features/empresa/empresaSeleccionadaStore'
import type { EmpresaSupervisada } from '@/features/empresa/types'
import { useAuthStore } from '@/store/authStore'
import type { Rol } from './types'

/** Nombre visible de cada rol, en su forma corta (panel lateral, tablas). */
export const ETIQUETA_ROL: Record<Rol, string> = {
  SUPER_ADMIN: 'Super administrador',
  ADMINISTRADOR_COMERCIAL: 'Administrador comercial',
  TRABAJADOR: 'Trabajador',
}

/** Nombre visible de cada rol donde hay espacio (perfil). */
export const ETIQUETA_ROL_COMPLETA: Record<Rol, string> = {
  SUPER_ADMIN: 'Super administrador de la plataforma',
  ADMINISTRADOR_COMERCIAL: 'Administrador comercial',
  TRABAJADOR: 'Trabajador',
}

/** Roles con Permisos.GESTION en el backend: ven catálogos y todas las solicitudes de la empresa. */
export const ROLES_GESTION: readonly Rol[] = ['SUPER_ADMIN', 'ADMINISTRADOR_COMERCIAL']
/** Personas de una empresa (Permisos.MIEMBRO_EMPRESA): las únicas que crean solicitudes. */
export const ROLES_EMPRESA: readonly Rol[] = ['ADMINISTRADOR_COMERCIAL', 'TRABAJADOR']
/** Administración de la plataforma: empresas y sus administradores. */
export const ROLES_PLATAFORMA: readonly Rol[] = ['SUPER_ADMIN']
/** Invitar trabajadores: solo el administrador de la empresa, a la suya. */
export const ROLES_INVITACION: readonly Rol[] = ['ADMINISTRADOR_COMERCIAL']

/**
 * Empresa sobre la que se trabaja:
 * - ADMINISTRADOR_COMERCIAL / TRABAJADOR: la suya, fija (viene de la sesión).
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
  /** Consulta órdenes de compra, recepciones y el kárdex (con costos). */
  verCompras: boolean
  /** Crea, edita, emite y cancela órdenes y registra recepciones (Permisos.ADMINISTRADOR_COMERCIAL en el backend). */
  gestionarCompras: boolean
  /** Ajustes manuales de inventario (Permisos.ADMINISTRADOR_COMERCIAL en el backend). */
  ajustarInventario: boolean
  /** Registra la mercancía recibida (Permisos.MIEMBRO_EMPRESA: trabajador y administrador). */
  registrarRecepciones: boolean
  /** Programa y reprograma entregas (Permisos.ADMINISTRADOR_COMERCIAL). */
  programarEntregas: boolean
  /** Supervisión de entregas: todas las vistas, resumen, discrepancias (Permisos.GESTION). */
  supervisarEntregas: boolean
  /** Calendario operativo: toda la empresa; el SUPER_ADMIN, solo supervisando una (en modo global no hay). */
  verCalendario: boolean
  /** Punto de venta: personas de la empresa (Permisos.MIEMBRO_EMPRESA). */
  vender: boolean
  /** Historial de ventas: el trabajador, las suyas; la gestión, todas (nunca en modo global). */
  verVentas: boolean
  /** Devoluciones y anulaciones (Permisos.ADMINISTRADOR_COMERCIAL). */
  gestionarVentas: boolean
  /** Gestión comercial y reportes con costos y rentabilidad (Permisos.GESTION, dentro de una empresa). */
  verComercial: boolean
  /** Cambia precios, IVA, política y emite autorizaciones (Permisos.ADMINISTRADOR_COMERCIAL). */
  gestionarPrecios: boolean
}

/**
 * Los cambios en los datos de una empresa los hace su administrador (ADMINISTRADOR_COMERCIAL). El SUPER_ADMIN la supervisa
 * en solo lectura: así una consulta de soporte nunca modifica datos por accidente. (La API
 * conserva el modo soporte con X-Tenant-ID para él; la interfaz no lo expone.)
 */
export function calcularPermisos({ rol, modoGlobal }: ContextoEmpresa): Permisos {
  const gestor = rol !== null && ROLES_GESTION.includes(rol)
  const adminEmpresa = rol === 'ADMINISTRADOR_COMERCIAL'
  return {
    registrarRecepciones: rol !== null && ROLES_EMPRESA.includes(rol),
    programarEntregas: adminEmpresa,
    supervisarEntregas: gestor,
    verCalendario: rol !== null && !modoGlobal,
    verTodasLasSolicitudes: gestor,
    revisarSolicitudes: adminEmpresa,
    crearSolicitudes: rol !== null && ROLES_EMPRESA.includes(rol),
    verCatalogos: gestor,
    gestionarCatalogos: adminEmpresa,
    gestionarInvitaciones: adminEmpresa,
    gestionarEmpresas: rol === 'SUPER_ADMIN',
    verCompras: gestor,
    gestionarCompras: adminEmpresa,
    ajustarInventario: adminEmpresa,
    vender: rol !== null && ROLES_EMPRESA.includes(rol),
    verVentas: rol !== null && !modoGlobal,
    gestionarVentas: adminEmpresa,
    verComercial: gestor && !modoGlobal,
    gestionarPrecios: adminEmpresa,
  }
}

/**
 * Permisos de interfaz del usuario actual. Solo deciden qué se pinta: la autorización real
 * la aplica el backend en cada petición (403 / 400).
 */
export function usePermisos(): Permisos {
  return calcularPermisos(useContextoEmpresa())
}
