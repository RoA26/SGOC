import { cuadriculaMes, semana, type Dia } from '@/lib/dias'

export type Vista = 'mes' | 'semana' | 'dia'
export const VISTAS: readonly { valor: Vista; etiqueta: string }[] = [
  { valor: 'mes', etiqueta: 'Mes' },
  { valor: 'semana', etiqueta: 'Semana' },
  { valor: 'dia', etiqueta: 'Día' },
]

export function esDia(valor: string | null): valor is Dia {
  return valor !== null && /^\d{4}-\d{2}-\d{2}$/.test(valor)
}

/** Primer y último día que se piden al backend para la vista. */
export function rangoDeVista(vista: Vista, fecha: Dia): { desde: Dia; hasta: Dia } {
  if (vista === 'mes') {
    const dias = cuadriculaMes(fecha)
    return { desde: dias[0]!, hasta: dias[dias.length - 1]! }
  }
  if (vista === 'semana') {
    const dias = semana(fecha)
    return { desde: dias[0]!, hasta: dias[6]! }
  }
  return { desde: fecha, hasta: fecha }
}

