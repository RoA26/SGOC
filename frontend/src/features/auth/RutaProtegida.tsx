import { useEffect, type ReactNode } from 'react'
import { Navigate, Outlet, useLocation } from 'react-router-dom'
import { isSessionActive, useAuthStore } from '@/store/authStore'

interface RutaProtegidaProps {
  /**
   * Lo que ve un visitante sin sesión en la raíz (`/`): la página pública. Cualquier otra ruta
   * privada sigue redirigiendo a /login, recordando la ruta solicitada.
   */
  publicaEnRaiz?: ReactNode
}

/** Rutas privadas: sin sesión vigente redirige a /login recordando la ruta solicitada. */
export function RutaProtegida({ publicaEnRaiz }: RutaProtegidaProps) {
  const location = useLocation()
  const active = useAuthStore(isSessionActive)
  const expiresAt = useAuthStore((state) => state.expiresAt)
  const clearSession = useAuthStore((state) => state.clearSession)

  // Cierra la sesión justo cuando el token caduca, aunque el usuario no haga nada.
  useEffect(() => {
    if (expiresAt === null) return
    const timeoutId = window.setTimeout(clearSession, Math.max(expiresAt - Date.now(), 0))
    return () => window.clearTimeout(timeoutId)
  }, [expiresAt, clearSession])

  if (!active) {
    if (publicaEnRaiz !== undefined && location.pathname === '/') return publicaEnRaiz
    return <Navigate to="/login" replace state={{ from: location.pathname }} />
  }
  return <Outlet />
}
