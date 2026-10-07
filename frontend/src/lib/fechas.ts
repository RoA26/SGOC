const formatoFechaHora = new Intl.DateTimeFormat('es-CO', { dateStyle: 'medium', timeStyle: 'short' })
const formatoFecha = new Intl.DateTimeFormat('es-CO', { dateStyle: 'medium' })

/** "7 oct 2026, 2:34 p. m." a partir de un instante ISO-8601. */
export function formatearFechaHora(iso: string): string {
  return formatoFechaHora.format(new Date(iso))
}

/** "7 oct 2026" a partir de un instante ISO-8601. */
export function formatearFecha(iso: string): string {
  return formatoFecha.format(new Date(iso))
}
