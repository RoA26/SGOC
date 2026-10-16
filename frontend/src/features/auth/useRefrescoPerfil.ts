import axios from 'axios'
import { useEffect } from 'react'
import { useAuthStore } from '@/store/authStore'
import { fetchCurrentUser } from './authApi'

/** Como mucho una consulta por minuto al volver a la pestaña. */
const INTERVALO_MINIMO_MS = 60_000

/**
 * Mantiene al día los datos de la sesión que pueden cambiar sin volver a entrar (nombres, rol,
 * nombre comercial de la empresa): los pide a GET /api/auth/me al cargar la aplicación y al
 * volver a la pestaña. El backend los lee de la BD; el JWT solo identifica a la persona.
 * Si la cuenta ya no está autorizada (401, o 403 con motivo), el interceptor de Axios cierra la sesión.
 */
export function useRefrescoPerfil() {
  const accessToken = useAuthStore((state) => state.accessToken)
  const actualizarPerfil = useAuthStore((state) => state.actualizarPerfil)

  useEffect(() => {
    if (!accessToken) return
    let ultimaConsulta = 0
    let controller: AbortController | null = null

    const refrescar = () => {
      const ahora = Date.now()
      if (ahora - ultimaConsulta < INTERVALO_MINIMO_MS) return
      ultimaConsulta = ahora
      controller?.abort()
      controller = new AbortController()
      fetchCurrentUser(controller.signal)
        .then(actualizarPerfil)
        .catch((error: unknown) => {
          // Sin red no se pierde la sesión: se reintenta en la próxima ocasión.
          if (!axios.isCancel(error)) ultimaConsulta = 0
        })
    }
    const alVolver = () => {
      if (document.visibilityState === 'visible') refrescar()
    }

    refrescar()
    document.addEventListener('visibilitychange', alVolver)
    return () => {
      document.removeEventListener('visibilitychange', alVolver)
      controller?.abort()
    }
  }, [accessToken, actualizarPerfil])
}
