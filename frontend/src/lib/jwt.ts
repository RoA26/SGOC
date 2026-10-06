/**
 * Lectura (sin verificación) de los claims de un JWT.
 *
 * La firma solo la valida el backend; aquí únicamente se lee `exp` para
 * descartar tokens caducados y programar el cierre de sesión automático.
 */

function decodeBase64Url(segment: string): string {
  const base64 = segment.replace(/-/g, '+').replace(/_/g, '/')
  const padded = base64.padEnd(base64.length + ((4 - (base64.length % 4)) % 4), '=')
  const binary = atob(padded)
  const bytes = Uint8Array.from(binary, (char) => char.charCodeAt(0))
  return new TextDecoder().decode(bytes)
}

/** Devuelve la expiración del token en milisegundos epoch, o `null` si no es legible. */
export function getTokenExpiration(token: string): number | null {
  const payload = token.split('.')[1]
  if (!payload) return null

  try {
    const claims: unknown = JSON.parse(decodeBase64Url(payload))
    if (
      typeof claims === 'object' &&
      claims !== null &&
      'exp' in claims &&
      typeof claims.exp === 'number'
    ) {
      return claims.exp * 1000
    }
    return null
  } catch {
    return null
  }
}

/** Un token sin `exp` legible se considera caducado. */
export function isTokenExpired(token: string, now: number = Date.now()): boolean {
  const expiration = getTokenExpiration(token)
  return expiration === null || expiration <= now
}
