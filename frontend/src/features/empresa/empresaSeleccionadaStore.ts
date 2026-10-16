import { create } from 'zustand'
import { createJSONStorage, persist } from 'zustand/middleware'
import { useAuthStore } from '@/store/authStore'
import type { EmpresaSupervisada } from './types'

interface EmpresaSeleccionadaState {
  /** Empresa que supervisa el SUPER_ADMIN; null = modo global (todas las empresas). */
  empresaId: number | null
  /** Nombre y código de la empresa supervisada, para mostrarla en toda la interfaz. */
  empresa: EmpresaSupervisada | null
  seleccionar: (empresa: EmpresaSupervisada) => void
  salir: () => void
}

/**
 * Supervisión del SUPER_ADMIN: mientras haya una empresa elegida, sus peticiones llevan la
 * cabecera X-Tenant-ID (ver lib/axios.ts) y ve solo los datos de esa empresa, sin dejar de ser
 * SUPER_ADMIN (no hay suplantación: el backend sigue identificándolo con su propia cuenta).
 *
 * Se guarda en sessionStorage: dura lo que la pestaña y se borra al cerrar sesión. ADMIN_EMPRESA y
 * TRABAJADOR no la usan: su empresa viene de su sesión y el backend la impone.
 */
export const useEmpresaSeleccionadaStore = create<EmpresaSeleccionadaState>()(
  persist(
    (set) => ({
      empresaId: null,
      empresa: null,
      seleccionar: (empresa) => set({ empresaId: empresa.id, empresa }),
      salir: () => set({ empresaId: null, empresa: null }),
    }),
    {
      name: 'sgoc.empresa-seleccionada',
      storage: createJSONStorage(() => sessionStorage),
      // Versión 1: guarda también nombre y código. Una selección anterior (solo el id) se descarta.
      version: 1,
      migrate: () => ({ empresaId: null, empresa: null }),
      partialize: ({ empresaId, empresa }) => ({ empresaId, empresa }),
    },
  ),
)

// Al cerrar sesión (o caducar el token) se vuelve al modo global.
useAuthStore.subscribe((actual, anterior) => {
  if (anterior.accessToken && !actual.accessToken) {
    useEmpresaSeleccionadaStore.getState().salir()
  }
})
