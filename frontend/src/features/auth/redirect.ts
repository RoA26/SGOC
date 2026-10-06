import type { Location } from 'react-router-dom'

export const LOGIN_PATH = '/login'
export const DEFAULT_AUTHENTICATED_PATH = '/dashboard'

/** Estado que <ProtectedRoute> adjunta al redirigir a /login. */
export interface RedirectState {
  from: Pick<Location, 'pathname' | 'search' | 'hash'>
}

/**
 * Ruta a la que volver tras iniciar sesión. `location.state` es `unknown` en tiempo
 * de ejecución (puede venir manipulado del historial), así que se valida antes de usarlo.
 */
export function getPostLoginPath(state: unknown): string {
  if (typeof state !== 'object' || state === null || !('from' in state)) {
    return DEFAULT_AUTHENTICATED_PATH
  }
  const { from } = state
  if (
    typeof from !== 'object' ||
    from === null ||
    !('pathname' in from) ||
    typeof from.pathname !== 'string' ||
    !from.pathname.startsWith('/') ||
    from.pathname.startsWith('//') ||
    from.pathname === LOGIN_PATH
  ) {
    return DEFAULT_AUTHENTICATED_PATH
  }
  const search = 'search' in from && typeof from.search === 'string' ? from.search : ''
  const hash = 'hash' in from && typeof from.hash === 'string' ? from.hash : ''
  return `${from.pathname}${search}${hash}`
}
