import type { EstadoOrdenCompra } from '@/services/ordenCompraService'

export const ETIQUETA_ESTADO_ORDEN: Record<EstadoOrdenCompra, string> = {
  BORRADOR: 'Borrador',
  EMITIDA: 'Emitida',
  RECIBIDA_PARCIALMENTE: 'Recibida en parte',
  RECIBIDA: 'Recibida',
  CANCELADA: 'Cancelada',
}

/** Orden del ciclo de vida, para filtros y distribuciones. */
export const ESTADOS_ORDEN: readonly EstadoOrdenCompra[] = [
  'BORRADOR',
  'EMITIDA',
  'RECIBIDA_PARCIALMENTE',
  'RECIBIDA',
  'CANCELADA',
]
