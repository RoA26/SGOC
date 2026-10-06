import { useEffect } from 'react'
import { Navigate, Outlet, useLocation } from 'react-router-dom'
import { isSessionActive, useAuthStore } from '@/store/authStore'

/** Rutas privadas: sin sesión vigente redirige a /login recordando la ruta solicitada. */
export function RutaProtegida() {
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
    return <Navigate to="/login" replace state={{ from: location.pathname }} />
  }
  return <Outlet />
}
