import { Navigate, Outlet, useLocation } from 'react-router-dom'
import type { Rol } from '@/features/auth/types'
import { isSessionActive, useAuthStore } from '@/store/authStore'

interface RutaConRolProps {
  /** Roles que pueden ver las rutas hijas. */
  roles: readonly Rol[]
}

/**
 * Rutas restringidas por rol (catálogos, administración). Sin sesión lleva a /login; con
 * sesión pero sin uno de los roles, al inicio.
 *
 * Es una guarda de interfaz: el rol sale de la sesión guardada en el navegador y podría
 * manipularse. La protección real está en el backend (`@PreAuthorize`), que responde 403.
 */
export function RutaConRol({ roles }: RutaConRolProps) {
  const location = useLocation()
  const active = useAuthStore(isSessionActive)
  const rolActual = useAuthStore((state) => state.rol)

  if (!active) {
    return <Navigate to="/login" replace state={{ from: location.pathname }} />
  }
  if (rolActual === null || !roles.includes(rolActual)) {
    return <Navigate to="/" replace />
  }
  return <Outlet />
}
