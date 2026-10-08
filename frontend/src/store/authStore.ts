import { create } from 'zustand'
import { createJSONStorage, persist } from 'zustand/middleware'
import type { LoginResponse, Rol } from '@/features/auth/types'

interface SessionData {
  accessToken: string | null
  username: string | null
  nombre: string | null
  email: string | null
  rol: Rol | null
  /** Empresa (tenant) del usuario; null para SUPER_ADMIN, que la elige aparte. */
  empresaId: number | null
  /** Instante de expiración del token (epoch en ms). */
  expiresAt: number | null
}

interface AuthState extends SessionData {
  setSession: (response: LoginResponse) => void
  clearSession: () => void
}

const EMPTY_SESSION: SessionData = {
  accessToken: null,
  username: null,
  nombre: null,
  email: null,
  rol: null,
  empresaId: null,
  expiresAt: null,
}

/**
 * Versión del formato guardado. La 1 introduce SUPER_ADMIN y empresaId: las sesiones
 * anteriores (con el rol ADMIN, que ya no existe) se descartan y se pide iniciar sesión.
 */
const VERSION_SESION = 1

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
          username: usuario.username,
          nombre: usuario.nombre,
          email: usuario.email,
          rol: usuario.rol,
          empresaId: usuario.empresaId ?? null,
          expiresAt: Date.now() + expiresIn * 1000,
        }),
      clearSession: () => set(EMPTY_SESSION),
    }),
    {
      name: 'sgp.session',
      storage: createJSONStorage(() => localStorage),
      version: VERSION_SESION,
      migrate: (guardada, version) => (version < VERSION_SESION ? EMPTY_SESSION : (guardada as SessionData)),
      partialize: ({ accessToken, username, nombre, email, rol, empresaId, expiresAt }): SessionData => ({
        accessToken,
        username,
        nombre,
        email,
        rol,
        empresaId,
        expiresAt,
      }),
    },
  ),
)

/** `true` si hay un token y aún no ha expirado. */
export function isSessionActive(state: SessionData, now: number = Date.now()): boolean {
  return state.accessToken !== null && state.expiresAt !== null && state.expiresAt > now
}
