import { Navigate, Outlet, useLocation } from 'react-router-dom'
import { FullPageLoader } from '@/components/ui'
import { getPostLoginPath } from './redirect'
import { useAuth } from './useAuth'

/**
 * Ruta solo para invitados (p. ej. /login). Con sesión activa redirige a la ruta
 * que el usuario intentaba visitar o al dashboard. Es también quien navega tras un
 * login correcto, ya que reacciona al cambio de estado a "authenticated".
 */
export function GuestRoute() {
  const { status } = useAuth()
  const location = useLocation()

  if (status === 'loading') {
    return <FullPageLoader label="Verificando sesión…" />
  }

  if (status === 'authenticated') {
    return <Navigate to={getPostLoginPath(location.state)} replace />
  }

  return <Outlet />
}
