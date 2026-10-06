import { Navigate, Outlet, useLocation } from 'react-router-dom'
import { FullPageLoader } from '@/components/ui'
import { LOGIN_PATH, type RedirectState } from './redirect'
import { useAuth } from './useAuth'

/** Ruta privada: solo renderiza sus hijas con una sesión válida; si no, redirige a /login. */
export function ProtectedRoute() {
  const { status } = useAuth()
  const location = useLocation()

  if (status === 'loading') {
    return <FullPageLoader label="Verificando sesión…" />
  }

  if (status === 'unauthenticated') {
    const state: RedirectState = {
      from: { pathname: location.pathname, search: location.search, hash: location.hash },
    }
    return <Navigate to={LOGIN_PATH} replace state={state} />
  }

  return <Outlet />
}
