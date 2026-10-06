import axios from 'axios'
import { useCallback, useEffect, useState } from 'react'
import { getApiErrorMessage } from '@/lib/errors'
import type { Page, PageParams } from '@/services/types'

type Fetcher<T> = (params: PageParams, signal: AbortSignal) => Promise<Page<T>>

interface ResourceState<T> {
  /** Petición a la que corresponde este estado. */
  key: string | null
  data: Page<T> | null
  error: string | null
}

/**
 * Carga una página de un recurso y la recarga cuando cambian los parámetros.
 * Mientras llega la nueva página se conservan los datos anteriores (sin parpadeos)
 * y las peticiones obsoletas se cancelan.
 *
 * `fetcher` debe ser estable (p. ej. `proveedorService.listar`).
 */
export function usePaginatedResource<T>(fetcher: Fetcher<T>, page: number, size: number, sort: string) {
  const [reloadToken, setReloadToken] = useState(0)
  const [state, setState] = useState<ResourceState<T>>({ key: null, data: null, error: null })
  const key = `${page}|${size}|${sort}|${reloadToken}`

  useEffect(() => {
    const controller = new AbortController()
    fetcher({ page, size, sort }, controller.signal)
      .then((data) => setState({ key, data, error: null }))
      .catch((error: unknown) => {
        if (axios.isCancel(error)) return
        setState((previous) => ({ key, data: previous.data, error: getApiErrorMessage(error) }))
      })
    return () => controller.abort()
  }, [fetcher, page, size, sort, key])

  const reload = useCallback(() => setReloadToken((token) => token + 1), [])

  return {
    data: state.data,
    error: state.key === key ? state.error : null,
    loading: state.key !== key,
    reload,
  }
}
