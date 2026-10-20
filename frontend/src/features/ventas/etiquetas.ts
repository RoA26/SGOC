import type { TratamientoIva } from '@/services/comercialService'
import type { EstadoVenta, MedioPago } from '@/services/ventaService'

export const ETIQUETA_MEDIO: Record<MedioPago, string> = {
  EFECTIVO: 'Efectivo',
  TARJETA_DEBITO: 'Tarjeta débito',
  TARJETA_CREDITO: 'Tarjeta crédito',
  TRANSFERENCIA: 'Transferencia',
  OTRO: 'Otro medio',
}

export const ETIQUETA_ESTADO_VENTA: Record<EstadoVenta, string> = {
  CONFIRMADA: 'Confirmada',
  DEVUELTA_PARCIALMENTE: 'Devuelta en parte',
  DEVUELTA: 'Devuelta',
  ANULADA: 'Anulada',
}

export const ETIQUETA_TRATAMIENTO: Record<TratamientoIva, string> = {
  GRAVADO: 'Gravado',
  EXENTO: 'Exento (0 %)',
  EXCLUIDO: 'Excluido',
}

/** "IVA 19 %", "Exento", "Excluido": la tarifa cero de un exento no se confunde con un excluido. */
export function etiquetaIva(tratamiento: TratamientoIva | undefined, tarifa: number | undefined): string {
  if (!tratamiento) return 'IVA sin clasificar'
  if (tratamiento === 'GRAVADO') return `IVA ${new Intl.NumberFormat('es-CO', { maximumFractionDigits: 2 }).format(tarifa ?? 0)} %`
  return tratamiento === 'EXENTO' ? 'Exento' : 'Excluido'
}
