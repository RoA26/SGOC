import { create } from 'zustand'
import { createJSONStorage, persist } from 'zustand/middleware'
import type { LoginResponse, Rol } from '@/features/auth/types'

interface SessionData {
  accessToken: string | null
  nombre: string | null
  email: string | null
  rol: Rol | null
  /** Instante de expiración del token (epoch en ms). */
  expiresAt: number | null
}

interface AuthState extends SessionData {
  setSession: (response: LoginResponse) => void
  clearSession: () => void
}

const EMPTY_SESSION: SessionData = {
  accessToken: null,
  nombre: null,
  email: null,
  rol: null,
  expiresAt: null,
}

/**
 * Sesión del usuario: token JWT y datos básicos para la interfaz.
 * Se persiste en localStorage para sobrevivir a recargas de la página.
 */
export const useAuthStore = create<AuthState>()(
  persist(
    (set) => ({
      ...EMPTY_SESSION,
      setSession: ({ accessToken, expiresIn, usuario }) =>
        set({
          accessToken,
          nombre: usuario.nombre,
          email: usuario.email,
          rol: usuario.rol,
          expiresAt: Date.now() + expiresIn * 1000,
        }),
      clearSession: () => set(EMPTY_SESSION),
    }),
    {
      name: 'sgp.session',
      storage: createJSONStorage(() => localStorage),
      partialize: ({ accessToken, nombre, email, rol, expiresAt }): SessionData => ({
        accessToken,
        nombre,
        email,
        rol,
        expiresAt,
      }),
    },
  ),
)

/** `true` si hay un token y aún no ha expirado. */
export function isSessionActive(state: SessionData, now: number = Date.now()): boolean {
  return state.accessToken !== null && state.expiresAt !== null && state.expiresAt > now
}
