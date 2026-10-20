/** Pesos colombianos: sin decimales y con punto como separador de miles ("$ 1.250.000"). */
const formatoCOP = new Intl.NumberFormat('es-CO', {
  style: 'currency',
  currency: 'COP',
  minimumFractionDigits: 0,
  maximumFractionDigits: 0,
})

export function formatearCOP(valor: number): string {
  return formatoCOP.format(valor)
}

const formatoConCentavos = new Intl.NumberFormat('es-CO', {
  style: 'currency',
  currency: 'COP',
  minimumFractionDigits: 2,
  maximumFractionDigits: 2,
})

/**
 * Importes de ventas, que pueden llevar centavos (IVA, descuentos): "$ 11.900" o "$ 3.333,33".
 * Solo muestra lo que calculó el backend; nunca se usa para cálculos definitivos.
 */
export function formatearImporte(valor: number | null | undefined): string {
  if (valor === null || valor === undefined) return '—'
  return Number.isInteger(valor) ? formatoCOP.format(valor) : formatoConCentavos.format(valor)
}

/** "19 %", "33,33 %"; "—" si el indicador no está definido (costo o ingreso cero o desconocido). */
export function formatearPorcentaje(valor: number | null | undefined): string {
  if (valor === null || valor === undefined) return '—'
  return `${new Intl.NumberFormat('es-CO', { maximumFractionDigits: 2 }).format(valor)} %`
}
