import type { EstadoUsuario } from '@/features/auth/types'

/** Fuente de una operación que firmó el trabajador. */
export type TipoOperacion = 'SOLICITUD' | 'RECEPCION' | 'VENTA'

/**
 * PersonalDTO.Actividad. Solo operaciones registradas: **no** es la última conexión (el sistema no
 * registra los inicios de sesión).
 */
export interface ActividadTrabajador {
  /** Ausente si nunca registró ninguna operación (el backend omite los nulos). */
  ultimaOperacion?: { tipo: TipoOperacion; en: string }
  /** Ventana de los conteos: los últimos `dias` días. */
  dias: number
  solicitudes: number
  recepciones: number
  ventas: number
}

/** PersonalDTO: trabajador de la empresa en la vista de personal (sin correo). */
export interface PersonalTrabajador {
  id: number
  username: string
  nombre: string
  estado: EstadoUsuario
  creadoEn: string
  actividad: ActividadTrabajador
}
