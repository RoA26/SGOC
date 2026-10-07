import axios from 'axios'
import { create } from 'zustand'
import { getApiErrorMessage } from '@/lib/errors'
import type { ProveedorResumen } from '@/services/productoService'
import { proveedorService } from '@/services/proveedorService'
import { useAuthStore } from './authStore'

/** Suficiente para el selector del catálogo inicial (más adelante, búsqueda remota). */
const MAX_PROVEEDORES = 100

type EstadoCarga = 'inicial' | 'cargando' | 'listo' | 'error'

interface ProveedoresState {
  /** Proveedores activos, ordenados por razón social. */
  proveedores: ProveedorResumen[]
  estado: EstadoCarga
  error: string | null
  /** Carga la lista si aún no está cargada; con `forzar`, la vuelve a pedir siempre. */
  cargar: (opciones?: { forzar?: boolean }) => Promise<void>
  /** Marca la lista como desactualizada (tras crear, editar o eliminar un proveedor). */
  invalidar: () => void
}

let peticion: AbortController | null = null

/**
 * Lista de proveedores compartida por el catálogo de productos: decide si se puede crear un
 * producto (hace falta al menos un proveedor) y alimenta el selector del formulario.
 */
export const useProveedoresStore = create<ProveedoresState>()((set, get) => ({
  proveedores: [],
  estado: 'inicial',
  error: null,

  cargar: async ({ forzar = false } = {}) => {
    const { estado } = get()
    if (!forzar && (estado === 'listo' || estado === 'cargando')) return

    // Una petición nueva sustituye a la anterior: nunca gana una respuesta antigua.
    peticion?.abort()
    const controller = new AbortController()
    peticion = controller
    set({ estado: 'cargando', error: null })
    try {
      const { content } = await proveedorService.listar(
        { page: 0, size: MAX_PROVEEDORES, sort: 'razonSocial,asc' },
        controller.signal,
      )
      set({
        proveedores: content.map(({ id, nit, razonSocial }) => ({ id, nit, razonSocial })),
        estado: 'listo',
      })
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

// Al cerrar sesión se descarta la lista: el siguiente usuario la vuelve a pedir con su token.
useAuthStore.subscribe((actual, anterior) => {
  if (anterior.accessToken && !actual.accessToken) {
    peticion?.abort()
    peticion = null
    useProveedoresStore.setState({ proveedores: [], estado: 'inicial', error: null })
  }
})
