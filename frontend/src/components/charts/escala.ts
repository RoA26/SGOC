/**
 * Máximo "redondo" del eje y sus marcas (0, 5, 10, 15…): el eje nunca termina en un valor raro
 * y las marcas son números limpios.
 */
export function escalaLimpia(maximo: number, marcasDeseadas = 4): { tope: number; marcas: number[] } {
  if (maximo <= 0) return { tope: 1, marcas: [0, 1] }
  const pasoBruto = maximo / marcasDeseadas
  const magnitud = 10 ** Math.floor(Math.log10(pasoBruto))
  const normalizado = pasoBruto / magnitud
  const paso = (normalizado <= 1 ? 1 : normalizado <= 2 ? 2 : normalizado <= 5 ? 5 : 10) * magnitud
  const pasoEntero = Math.max(1, paso)
  const tope = Math.ceil(maximo / pasoEntero) * pasoEntero
  const marcas: number[] = []
  for (let valor = 0; valor <= tope + pasoEntero / 2; valor += pasoEntero) marcas.push(valor)
  return { tope, marcas }
}
