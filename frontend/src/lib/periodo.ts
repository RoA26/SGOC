/** Zona en la que el backend cuenta los días (app.negocio.zona-horaria). */
export const ZONA_NEGOCIO = 'America/Bogota'

const fechaEnZona = new Intl.DateTimeFormat('en-CA', {
  timeZone: ZONA_NEGOCIO,
  year: 'numeric',
  month: '2-digit',
  day: '2-digit',
})

/** Hoy en Colombia como "AAAA-MM-DD", aunque el navegador esté en otra zona. */
export function hoyNegocio(): string {
  return fechaEnZona.format(new Date())
}

/** Resta días a una fecha "AAAA-MM-DD" (aritmética de calendario, sin zonas). */
export function restarDias(fecha: string, dias: number): string {
  const [anio = 0, mes = 1, dia = 1] = fecha.split('-').map(Number)
  const resultado = new Date(Date.UTC(anio, mes - 1, dia - dias))
  return resultado.toISOString().slice(0, 10)
}

const formatoDia = new Intl.DateTimeFormat('es-CO', { day: 'numeric', month: 'short', timeZone: 'UTC' })
const formatoDiaLargo = new Intl.DateTimeFormat('es-CO', { dateStyle: 'medium', timeZone: 'UTC' })
const formatoMes = new Intl.DateTimeFormat('es-CO', { month: 'short', year: 'numeric', timeZone: 'UTC' })
const formatoMesLargo = new Intl.DateTimeFormat('es-CO', { month: 'long', year: 'numeric', timeZone: 'UTC' })

function comoFecha(fecha: string): Date {
  return new Date(`${fecha}T00:00:00Z`)
}

/** "16 oct" o "oct 2026" según la granularidad de la serie. */
export function etiquetaCorta(fecha: string, granularidad: 'DIA' | 'MES'): string {
  return (granularidad === 'DIA' ? formatoDia : formatoMes).format(comoFecha(fecha))
}

/** "16 oct 2026" o "octubre de 2026". */
export function etiquetaLarga(fecha: string, granularidad: 'DIA' | 'MES'): string {
  return (granularidad === 'DIA' ? formatoDiaLargo : formatoMesLargo).format(comoFecha(fecha))
}

/** "17 sept 2026 – 16 oct 2026" para describir el período que devolvió el backend. */
export function describirPeriodo(desde: string, hasta: string): string {
  return `${formatoDiaLargo.format(comoFecha(desde))} – ${formatoDiaLargo.format(comoFecha(hasta))}`
}
