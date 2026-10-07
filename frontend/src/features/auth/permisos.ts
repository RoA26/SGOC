import { useAuthStore } from '@/store/authStore'
import type { Rol } from './types'

/** Nombre visible de cada rol. */
export const ETIQUETA_ROL: Record<Rol, string> = {
  ADMIN: 'Administrador',
  GERENTE: 'Gerente',
  USUARIO: 'Consulta',
}

/** Gestores: mantienen los catálogos y revisan las solicitudes (Permisos.GESTION en el backend). */
const ROLES_GESTION: readonly Rol[] = ['ADMIN', 'GERENTE']

export function puedeGestionarCatalogos(rol: Rol | null): boolean {
  return rol !== null && ROLES_GESTION.includes(rol)
}

/** Ve todas las solicitudes (no solo las propias) y puede aprobarlas o rechazarlas. */
export function puedeRevisarSolicitudes(rol: Rol | null): boolean {
  return rol !== null && ROLES_GESTION.includes(rol)
}

/**
 * `true` si el usuario actual puede modificar los catálogos. Solo decide qué se pinta: la
 * autorización real la aplica el backend en cada petición (403).
 */
export function usePuedeGestionarCatalogos(): boolean {
  return useAuthStore((state) => puedeGestionarCatalogos(state.rol))
}

export function usePuedeRevisarSolicitudes(): boolean {
  return useAuthStore((state) => puedeRevisarSolicitudes(state.rol))
}
