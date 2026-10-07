import { Navigate, Outlet, useLocation } from 'react-router-dom'
import type { Rol } from '@/features/auth/types'
import { isSessionActive, useAuthStore } from '@/store/authStore'

interface RutaConRolProps {
  /** Rol exigido para ver las rutas hijas. */
  rol: Rol
}

/**
 * Rutas restringidas por rol (p. ej. el panel de administración). Sin sesión lleva a /login;
 * con sesión pero sin el rol, al inicio.
 *
 * Es una guarda de interfaz: el rol sale de la sesión guardada en el navegador y podría
 * manipularse. La protección real está en el backend (`@PreAuthorize`), que responde 403.
 */
export function RutaConRol({ rol }: RutaConRolProps) {
  const location = useLocation()
  const active = useAuthStore(isSessionActive)
  const rolActual = useAuthStore((state) => state.rol)

  if (!active) {
    return <Navigate to="/login" replace state={{ from: location.pathname }} />
  }
  if (rolActual !== rol) {
    return <Navigate to="/" replace />
  }
  return <Outlet />
}
