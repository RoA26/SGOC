/**
 * Codificación EAN-13 en módulos (1 = barra, 0 = espacio): 95 módulos con las guardas.
 * La página pública dibuja con ella la franja de la marca a partir del código de un producto de
 * ejemplo, el mismo tipo de código que Nexora lee en el inventario y el punto de venta.
 */
const L = ['0001101', '0011001', '0010011', '0111101', '0100011', '0110001', '0101111', '0111011', '0110111', '0001011']
const G = ['0100111', '0110011', '0011011', '0100001', '0011101', '0111001', '0000101', '0010001', '0001001', '0010111']
const R = ['1110010', '1100110', '1101100', '1000010', '1011100', '1001110', '1010000', '1000100', '1001000', '1110100']
/** Paridad (L o G) de los seis primeros dígitos según el dígito inicial. */
const PARIDAD = ['LLLLLL', 'LLGLGG', 'LLGGLG', 'LLGGGL', 'LGLLGG', 'LGGLLG', 'LGGGLL', 'LGLGLG', 'LGLGGL', 'LGGLGL']

/** Dígito de control de los 12 primeros dígitos. */
export function digitoControlEan13(doce: string): number {
  const suma = [...doce].reduce((total, c, i) => total + Number(c) * (i % 2 === 0 ? 1 : 3), 0)
  return (10 - (suma % 10)) % 10
}

/** Módulos del código (13 dígitos con el de control correcto); lanza un error si no es válido. */
export function modulosEan13(codigo: string): string {
  if (!/^\d{13}$/.test(codigo) || digitoControlEan13(codigo.slice(0, 12)) !== Number(codigo[12])) {
    throw new Error(`EAN-13 no válido: ${codigo}`)
  }
  const [inicial = 0, ...resto] = [...codigo].map(Number)
  const paridad = PARIDAD[inicial] ?? ''
  const izquierda = resto.slice(0, 6).map((n, i) => (paridad[i] === 'L' ? L[n] : G[n])).join('')
  const derecha = resto.slice(6).map((n) => R[n]).join('')
  return `101${izquierda}01010${derecha}101`
}
