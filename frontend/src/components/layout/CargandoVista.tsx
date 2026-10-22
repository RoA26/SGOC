/** Mientras se descarga un módulo: huecos del tamaño de la cabecera y del contenido (sin saltos). */
export function CargandoVista() {
  return (
    <div role="status" aria-label="Cargando" className="w-full">
      <div className="u-skeleton h-4 w-32" />
      <div className="u-skeleton mt-3 h-8 w-64" />
      <div className="u-skeleton mt-8 h-64 w-full rounded-lg" />
      <span className="sr-only">Cargando…</span>
    </div>
  )
}
