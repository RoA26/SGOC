/**
 * Persistencia del JWT en localStorage.
 *
 * Todos los accesos van protegidos: localStorage puede lanzar excepciones
 * (modo privado, cuota excedida, almacenamiento bloqueado por el navegador).
 */
const TOKEN_KEY = 'sgoc.access_token'

export const tokenStorage = {
  key: TOKEN_KEY,

  get(): string | null {
    try {
      return window.localStorage.getItem(TOKEN_KEY)
    } catch {
      return null
    }
  },

  set(token: string): void {
    try {
      window.localStorage.setItem(TOKEN_KEY, token)
    } catch {
      // Sin persistencia: la sesión durará solo mientras la pestaña esté abierta.
    }
  },

  clear(): void {
    try {
      window.localStorage.removeItem(TOKEN_KEY)
    } catch {
      // Nada que limpiar si el almacenamiento no está disponible.
    }
  },
}
