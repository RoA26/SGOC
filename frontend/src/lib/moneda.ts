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
