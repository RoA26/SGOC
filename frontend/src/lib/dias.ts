/**
 * Días del calendario como texto AAAA-MM-DD, sin zona horaria. Un día del negocio (fecha estimada,
 * día de un evento) no es un instante: `new Date('2026-11-03')` sería medianoche UTC y en Colombia
 * se mostraría como el 2 de noviembre. Aquí se trabaja siempre con componentes locales.
 */

export type Dia = string

export function aDate(dia: Dia): Date {
  const [anio, mes, d] = dia.split('-').map(Number)
  return new Date(anio ?? 1970, (mes ?? 1) - 1, d ?? 1)
}

export function deDate(fecha: Date): Dia {
  const mes = String(fecha.getMonth() + 1).padStart(2, '0')
  const dia = String(fecha.getDate()).padStart(2, '0')
  return `${fecha.getFullYear()}-${mes}-${dia}`
}

export function sumarDias(dia: Dia, dias: number): Dia {
  const fecha = aDate(dia)
  fecha.setDate(fecha.getDate() + dias)
  return deDate(fecha)
}

export function sumarMeses(dia: Dia, meses: number): Dia {
  const fecha = aDate(dia)
  const objetivo = new Date(fecha.getFullYear(), fecha.getMonth() + meses, 1)
  // Mismo día del mes, sin desbordar (31 de enero + 1 mes = 28/29 de febrero).
  const ultimo = new Date(objetivo.getFullYear(), objetivo.getMonth() + 1, 0).getDate()
  objetivo.setDate(Math.min(fecha.getDate(), ultimo))
  return deDate(objetivo)
}

/** Lunes de la semana del día (las semanas empiezan en lunes, como en Colombia). */
export function inicioSemana(dia: Dia): Dia {
  const fecha = aDate(dia)
  const desdeLunes = (fecha.getDay() + 6) % 7
  return sumarDias(dia, -desdeLunes)
}

export function inicioMes(dia: Dia): Dia {
  return `${dia.slice(0, 7)}-01`
}

/** Cuadrícula mensual: 6 semanas completas (42 días) que contienen el mes del día. */
export function cuadriculaMes(dia: Dia): Dia[] {
  const primero = inicioSemana(inicioMes(dia))
  return Array.from({ length: 42 }, (_, i) => sumarDias(primero, i))
}

export function semana(dia: Dia): Dia[] {
  const lunes = inicioSemana(dia)
  return Array.from({ length: 7 }, (_, i) => sumarDias(lunes, i))
}

export function mismoMes(a: Dia, b: Dia): boolean {
  return a.slice(0, 7) === b.slice(0, 7)
}

/** Hoy en la zona del negocio (America/Bogota), aunque el navegador esté en otra. */
export function hoyEnBogota(ahora: Date = new Date()): Dia {
  return new Intl.DateTimeFormat('en-CA', { timeZone: 'America/Bogota' }).format(ahora)
}

const formatoLargo = new Intl.DateTimeFormat('es-CO', { weekday: 'long', day: 'numeric', month: 'long', year: 'numeric' })
const formatoCorto = new Intl.DateTimeFormat('es-CO', { weekday: 'short', day: 'numeric', month: 'short' })
const formatoMes = new Intl.DateTimeFormat('es-CO', { month: 'long', year: 'numeric' })
const formatoDiaMes = new Intl.DateTimeFormat('es-CO', { day: 'numeric', month: 'short', year: 'numeric' })

/** "martes, 3 de noviembre de 2026" */
export function formatearDiaLargo(dia: Dia): string {
  return formatoLargo.format(aDate(dia))
}

/** "mar, 3 nov" */
export function formatearDiaCorto(dia: Dia): string {
  return formatoCorto.format(aDate(dia))
}

/** "3 nov 2026" */
export function formatearDia(dia: Dia): string {
  return formatoDiaMes.format(aDate(dia))
}

/** "noviembre de 2026" */
export function formatearMes(dia: Dia): string {
  return formatoMes.format(aDate(dia))
}
