import type { EstadoUsuario } from '@/features/auth/types'
import { formatearFechaHora } from '@/lib/fechas'
import type { ActividadTrabajador, TipoOperacion } from './types'

export const ETIQUETA_OPERACION: Record<TipoOperacion, string> = {
  SOLICITUD: 'Solicitud de compra',
  RECEPCION: 'Recepción de mercancía',
  VENTA: 'Venta',
}

/** "Venta · 21 oct 2026, 3:10 p. m.", o null si nunca registró ninguna operación. */
export function describirUltimaOperacion(actividad: ActividadTrabajador): string | null {
  const ultima = actividad.ultimaOperacion
  return ultima ? `${ETIQUETA_OPERACION[ultima.tipo]} · ${formatearFechaHora(ultima.en)}` : null
}

function contar(n: number, singular: string, plural: string): string | null {
  return n > 0 ? `${n} ${n === 1 ? singular : plural}` : null
}

/** "2 solicitudes · 1 recepción" o, sin nada en la ventana, null. */
export function resumirRecientes(actividad: ActividadTrabajador): string | null {
  const partes = [
    contar(actividad.solicitudes, 'solicitud', 'solicitudes'),
    contar(actividad.recepciones, 'recepción', 'recepciones'),
    contar(actividad.ventas, 'venta', 'ventas'),
  ].filter((parte) => parte !== null)
  return partes.length > 0 ? partes.join(' · ') : null
}

export interface AccionEstado {
  destino: EstadoUsuario
  etiqueta: string
  /** Quita o niega el acceso: se confirma antes. */
  restrictiva: boolean
}

/** Transiciones que admite el backend (EstadoUsuario.puedePasarA). */
export function accionesDisponibles(estado: EstadoUsuario): AccionEstado[] {
  switch (estado) {
    case 'PENDIENTE':
      return [
        { destino: 'ACTIVO', etiqueta: 'Aprobar', restrictiva: false },
        { destino: 'RECHAZADO', etiqueta: 'Rechazar', restrictiva: true },
      ]
    case 'ACTIVO':
      return [{ destino: 'INACTIVO', etiqueta: 'Desactivar', restrictiva: true }]
    case 'INACTIVO':
      return [{ destino: 'ACTIVO', etiqueta: 'Reactivar', restrictiva: false }]
    case 'RECHAZADO':
      return [{ destino: 'ACTIVO', etiqueta: 'Aprobar', restrictiva: false }]
  }
}
