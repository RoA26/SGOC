import type { EstadoStock } from '@/services/inventarioService'

export const ETIQUETA_ESTADO_STOCK: Record<EstadoStock, string> = {
  AGOTADO: 'Sin existencias',
  CRITICO: 'Stock crítico',
  NORMAL: 'Disponible',
}
