import { create } from 'zustand'
import { createJSONStorage, persist } from 'zustand/middleware'
import { useAuthStore } from '@/store/authStore'

interface EmpresaSeleccionadaState {
  /** Empresa en la que trabaja el SUPER_ADMIN; null = modo global (todas las empresas). */
  empresaId: number | null
  seleccionar: (empresaId: number) => void
  salir: () => void
}

/**
 * Empresa elegida por el SUPER_ADMIN para trabajar dentro de ella (soporte). Mientras haya
 * una, las peticiones llevan la cabecera X-Tenant-ID (ver lib/axios.ts).
 *
 * Se guarda en sessionStorage: dura lo que la pestaña y se borra al cerrar sesión. GERENTE y
 * USUARIO no la usan: su empresa viene de su sesión y el backend la impone.
 */
export const useEmpresaSeleccionadaStore = create<EmpresaSeleccionadaState>()(
  persist(
    (set) => ({
      empresaId: null,
      seleccionar: (empresaId) => set({ empresaId }),
      salir: () => set({ empresaId: null }),
    }),
    {
      name: 'sgoc.empresa-seleccionada',
      storage: createJSONStorage(() => sessionStorage),
      partialize: ({ empresaId }) => ({ empresaId }),
    },
  ),
)

// Al cerrar sesión (o caducar el token) se vuelve al modo global.
useAuthStore.subscribe((actual, anterior) => {
  if (anterior.accessToken && !actual.accessToken) {
    useEmpresaSeleccionadaStore.getState().salir()
  }
})
