import type { Rol } from '@/features/auth/types'
import { useEmpresaSeleccionadaStore } from '@/features/empresa/empresaSeleccionadaStore'
import { useAuthStore } from '@/store/authStore'

/** Sesión simulada de un rol (sin backend). El SUPER_ADMIN, en modo global salvo que se indique empresa. */
export function iniciarSesionComo(rol: Rol, empresaSupervisada: number | null = null) {
  useAuthStore.setState({
    accessToken: 'token-de-prueba',
    username: rol.toLowerCase(),
    nombre: `Persona ${rol}`,
    rol,
    empresaId: rol === 'SUPER_ADMIN' ? null : 1,
    empresaNombre: rol === 'SUPER_ADMIN' ? null : 'Nexora',
    empresaCodigo: rol === 'SUPER_ADMIN' ? null : 'UNIS01',
    expiresAt: Date.now() + 3_600_000,
  })
  if (empresaSupervisada !== null) {
    useEmpresaSeleccionadaStore.setState({
      empresaId: empresaSupervisada,
      empresa: { id: empresaSupervisada, nombre: 'Nexora', codigo: 'UNIS01' },
    })
  } else {
    useEmpresaSeleccionadaStore.setState({ empresaId: null, empresa: null })
  }
}
