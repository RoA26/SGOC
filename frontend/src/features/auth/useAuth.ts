import { useContext } from 'react'
import { AuthContext } from './AuthContext'
import type { AuthContextValue, User } from './types'

export function useAuth(): AuthContextValue {
  const context = useContext(AuthContext)
  if (!context) {
    throw new Error('useAuth debe usarse dentro de <AuthProvider>.')
  }
  return context
}

/** Usuario autenticado, garantizado no nulo. Solo para vistas bajo <ProtectedRoute>. */
export function useCurrentUser(): User {
  const auth = useAuth()
  if (auth.status !== 'authenticated') {
    throw new Error('useCurrentUser requiere una sesión activa (úsalo bajo <ProtectedRoute>).')
  }
  return auth.user
}
