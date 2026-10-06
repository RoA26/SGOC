import { Navigate, Outlet, useLocation } from 'react-router-dom'
import { isSessionActive, useAuthStore } from '@/store/authStore'

function redirectTarget(state: unknown): string {
  if (typeof state === 'object' && state !== null && 'from' in state) {
    const { from } = state
    // Solo rutas internas: evita redirecciones abiertas a otros dominios.
    if (typeof from === 'string' && from.startsWith('/') && !from.startsWith('//') && from !== '/login') {
      return from
    }
  }
  return '/'
}

/**
 * Rutas solo para invitados (/login). Al iniciar sesión el store cambia y esta ruta
 * redirige automáticamente a la página que el usuario intentaba abrir.
 */
export function RutaInvitado() {
  const location = useLocation()
  const active = useAuthStore(isSessionActive)

  if (active) {
    return <Navigate to={redirectTarget(location.state)} replace />
  }
  return <Outlet />
}
