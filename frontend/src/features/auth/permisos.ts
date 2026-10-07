import { useAuthStore } from '@/store/authStore'
import type { Rol } from './types'

/** Nombre visible de cada rol. */
export const ETIQUETA_ROL: Record<Rol, string> = {
  ADMIN: 'Administrador',
  GERENTE: 'Gerente',
  USUARIO: 'Consulta',
}

/** Quién da de alta, edita y elimina proveedores y productos. */
const ROLES_GESTION_CATALOGOS: readonly Rol[] = ['ADMIN', 'GERENTE']

export function puedeGestionarCatalogos(rol: Rol | null): boolean {
  return rol !== null && ROLES_GESTION_CATALOGOS.includes(rol)
}

/**
 * `true` si el usuario actual puede modificar los catálogos. Solo decide qué se pinta: la
 * autorización real la aplica el backend en cada petición (403).
 */
export function usePuedeGestionarCatalogos(): boolean {
  return useAuthStore((state) => puedeGestionarCatalogos(state.rol))
}
