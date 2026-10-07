import axios from 'axios'
import { create } from 'zustand'
import { getApiErrorMessage } from '@/lib/errors'
import { useAuthStore } from './authStore'

/** Suficiente para los selectores del catálogo inicial (más adelante, búsqueda remota). */
export const MAX_OPCIONES = 100

export type EstadoCarga = 'inicial' | 'cargando' | 'listo' | 'error'

export interface CatalogoState<T> {
  items: T[]
  estado: EstadoCarga
  error: string | null
  /** Carga la lista si aún no está cargada; con `forzar`, la vuelve a pedir siempre. */
  cargar: (opciones?: { forzar?: boolean }) => Promise<void>
  /** Marca la lista como desactualizada (tras crear, editar o eliminar un elemento). */
  invalidar: () => void
}

/**
 * Store de una lista de catálogo compartida entre páginas y formularios (opciones de un
 * selector, comprobar si hay elementos…). Una petición nueva cancela la anterior, y al
 * cerrar sesión la lista se descarta.
 */
export function crearCatalogoStore<T>(pedir: (signal: AbortSignal) => Promise<T[]>) {
  let peticion: AbortController | null = null

  const useStore = create<CatalogoState<T>>()((set, get) => ({
    items: [],
    estado: 'inicial',
    error: null,

    cargar: async ({ forzar = false } = {}) => {
      const { estado } = get()
      if (!forzar && (estado === 'listo' || estado === 'cargando')) return

      peticion?.abort()
      const controller = new AbortController()
      peticion = controller
      set({ estado: 'cargando', error: null })
      try {
        set({ items: await pedir(controller.signal), estado: 'listo' })
      } catch (error) {
        if (!axios.isCancel(error)) set({ estado: 'error', error: getApiErrorMessage(error) })
      } finally {
        if (peticion === controller) peticion = null
      }
    },

    invalidar: () => {
      peticion?.abort()
      peticion = null
      set({ estado: 'inicial', error: null })
    },
  }))

  useAuthStore.subscribe((actual, anterior) => {
    if (anterior.accessToken && !actual.accessToken) {
      peticion?.abort()
      peticion = null
      useStore.setState({ items: [], estado: 'inicial', error: null })
    }
  })

  return useStore
}
