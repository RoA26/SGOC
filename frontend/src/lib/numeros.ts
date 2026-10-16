const formatoEntero = new Intl.NumberFormat('es-CO', { maximumFractionDigits: 0 })
const formatoCompacto = new Intl.NumberFormat('es-CO', { notation: 'compact', maximumFractionDigits: 1 })

/** "1.284" (separador de miles colombiano). */
export function formatearNumero(valor: number): string {
  return formatoEntero.format(valor)
}

/** "1,3 mil", "12 mil", "4,2 M": para ejes y cifras grandes. */
export function formatearCompacto(valor: number): string {
  return Math.abs(valor) < 1000 ? formatoEntero.format(valor) : formatoCompacto.format(valor)
}
