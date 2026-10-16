import axios from 'axios'
import { useCallback, useEffect, useState } from 'react'
import { getApiErrorMessage } from '@/lib/errors'

interface Estado<T> {
  clave: string | null
  datos: T | null
  error: string | null
}

/**
 * Carga un recurso y lo recarga cuando cambia `clave`. Mientras llega la respuesta nueva se
 * conservan los datos anteriores (la interfaz los atenúa, sin parpadeos) y la petición obsoleta
 * se cancela. `pedir` debe ser estable o cambiar junto con `clave`.
 */
export function useRecurso<T>(pedir: (signal: AbortSignal) => Promise<T>, clave: string) {
  const [token, setToken] = useState(0)
  const [estado, setEstado] = useState<Estado<T>>({ clave: null, datos: null, error: null })
  const claveCompleta = `${clave}|${token}`

  useEffect(() => {
    const controller = new AbortController()
    pedir(controller.signal)
      .then((datos) => setEstado({ clave: claveCompleta, datos, error: null }))
      .catch((error: unknown) => {
        if (axios.isCancel(error)) return
        setEstado((anterior) => ({ clave: claveCompleta, datos: anterior.datos, error: getApiErrorMessage(error) }))
      })
    return () => controller.abort()
  }, [pedir, claveCompleta])

  const recargar = useCallback(() => setToken((actual) => actual + 1), [])
  return {
    datos: estado.datos,
    error: estado.clave === claveCompleta ? estado.error : null,
    cargando: estado.clave !== claveCompleta,
    recargar,
  }
}
