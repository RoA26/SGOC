/** Sin caracteres que se confunden al dictarlos o copiarlos a mano (0/O, 1/l/I). */
const MINUSCULAS = 'abcdefghijkmnpqrstuvwxyz'
const MAYUSCULAS = 'ABCDEFGHJKLMNPQRSTUVWXYZ'
const DIGITOS = '23456789'
const SIMBOLOS = '!@#$%*-_+?'
const TODOS = MINUSCULAS + MAYUSCULAS + DIGITOS + SIMBOLOS

/** Índice aleatorio uniforme en [0, max) con el generador criptográfico del navegador (sin sesgo de módulo). */
function indiceAleatorio(max: number): number {
  const limite = Math.floor(0x1_0000_0000 / max) * max
  const buffer = new Uint32Array(1)
  let valor: number
  do {
    crypto.getRandomValues(buffer)
    valor = buffer.at(0) ?? limite
  } while (valor >= limite)
  return valor % max
}

function caracter(alfabeto: string): string {
  return alfabeto.charAt(indiceAleatorio(alfabeto.length))
}

/**
 * Contraseña inicial aleatoria (por defecto 16 caracteres, ~100 bits) con al menos una
 * minúscula, una mayúscula, un dígito y un símbolo. Se genera en el navegador: solo sale de él
 * en el alta, y el backend guarda únicamente su hash BCrypt.
 */
export function generarPassword(longitud = 16): string {
  const caracteres = [caracter(MINUSCULAS), caracter(MAYUSCULAS), caracter(DIGITOS), caracter(SIMBOLOS)]
  while (caracteres.length < longitud) caracteres.push(caracter(TODOS))
  // Fisher-Yates: los obligatorios no quedan siempre al principio.
  for (let i = caracteres.length - 1; i > 0; i--) {
    const j = indiceAleatorio(i + 1)
    const actual = caracteres[i] ?? ''
    caracteres[i] = caracteres[j] ?? ''
    caracteres[j] = actual
  }
  return caracteres.join('')
}
