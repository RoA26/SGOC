import { create } from 'zustand'
import { createJSONStorage, persist } from 'zustand/middleware'
import type { LoginResponse, Rol, UsuarioResponse } from '@/features/auth/types'

interface SessionData {
  accessToken: string | null
  username: string | null
  /** Nombre visible (nombres + apellidos, o el nombre completo de una cuenta anterior). */
  nombre: string | null
  nombres: string | null
  apellidos: string | null
  datosPersonalesCompletos: boolean
  email: string | null
  rol: Rol | null
  /** Empresa (tenant) del usuario; null para SUPER_ADMIN, que la elige aparte. */
  empresaId: number | null
  /** Nombre comercial y código de su empresa, refrescados desde GET /api/auth/me. */
  empresaNombre: string | null
  empresaCodigo: string | null
  /** Instante de expiración del token (epoch en ms). */
  expiresAt: number | null
}

interface AuthState extends SessionData {
  setSession: (response: LoginResponse) => void
  /** Actualiza los datos del perfil (no el token) con la respuesta de /api/auth/me. */
  actualizarPerfil: (usuario: UsuarioResponse) => void
  clearSession: () => void
}

const EMPTY_SESSION: SessionData = {
  accessToken: null,
  username: null,
  nombre: null,
  nombres: null,
  apellidos: null,
  datosPersonalesCompletos: true,
  email: null,
  rol: null,
  empresaId: null,
  empresaNombre: null,
  empresaCodigo: null,
  expiresAt: null,
}

function datosDePerfil(usuario: UsuarioResponse) {
  return {
    username: usuario.username,
    nombre: usuario.nombre,
    nombres: usuario.nombres ?? null,
    apellidos: usuario.apellidos ?? null,
    datosPersonalesCompletos: usuario.datosPersonalesCompletos,
    email: usuario.email,
    rol: usuario.rol,
    empresaId: usuario.empresaId ?? null,
    empresaNombre: usuario.empresaNombre ?? null,
    empresaCodigo: usuario.empresaCodigo ?? null,
  }
}

/**
 * Versión del formato guardado.
 * - 1: SUPER_ADMIN y empresaId (las sesiones anteriores, con el rol ADMIN, se descartaron).
 * - 2: roles ADMIN_EMPRESA y TRABAJADOR (antes GERENTE y USUARIO), nombres, apellidos y empresa.
 *   Una sesión de la versión 1 se traduce sin cerrarla: el token sigue siendo válido porque el
 *   backend lee el rol de la BD, y el primer GET /api/auth/me completa el resto de datos.
 */
const VERSION_SESION = 2

const ROL_ANTERIOR: Record<string, Rol> = { GERENTE: 'ADMIN_EMPRESA', USUARIO: 'TRABAJADOR' }

function migrarSesion(guardada: unknown, version: number): SessionData {
  if (version < 1 || typeof guardada !== 'object' || guardada === null) return EMPTY_SESSION
  const anterior = guardada as Partial<SessionData> & { rol?: string | null }
  const rol = anterior.rol ? (ROL_ANTERIOR[anterior.rol] ?? (anterior.rol as Rol)) : null
  return { ...EMPTY_SESSION, ...anterior, rol }
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
        set({ accessToken, ...datosDePerfil(usuario), expiresAt: Date.now() + expiresIn * 1000 }),
      actualizarPerfil: (usuario) => set(datosDePerfil(usuario)),
      clearSession: () => set(EMPTY_SESSION),
    }),
    {
      name: 'sgp.session',
      storage: createJSONStorage(() => localStorage),
      version: VERSION_SESION,
      migrate: migrarSesion,
      partialize: (state): SessionData => ({
        accessToken: state.accessToken,
        username: state.username,
        nombre: state.nombre,
        nombres: state.nombres,
        apellidos: state.apellidos,
        datosPersonalesCompletos: state.datosPersonalesCompletos,
        email: state.email,
        rol: state.rol,
        empresaId: state.empresaId,
        empresaNombre: state.empresaNombre,
        empresaCodigo: state.empresaCodigo,
        expiresAt: state.expiresAt,
      }),
    },
  ),
)

/** `true` si hay un token y aún no ha expirado. */
export function isSessionActive(state: SessionData, now: number = Date.now()): boolean {
  return state.accessToken !== null && state.expiresAt !== null && state.expiresAt > now
}

/** Nombre para saludos breves ("Hola, Light"): los nombres, o el nombre visible si faltan. */
export function nombreCorto(state: Pick<SessionData, 'nombres' | 'nombre' | 'username'>): string {
  return state.nombres ?? state.nombre ?? state.username ?? ''
}
