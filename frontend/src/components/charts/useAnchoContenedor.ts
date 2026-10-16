import { useLayoutEffect, useRef, useState } from 'react'

/**
 * Ancho real del contenedor (para dibujar el SVG a su tamaño, sin deformar el texto). Se mide
 * antes de pintar; el contenedor debe tener `min-w-0` y `overflow-hidden` para que el propio SVG
 * no lo ensanche.
 */
export function useAnchoContenedor<T extends HTMLElement>(inicial = 600) {
  const ref = useRef<T>(null)
  const [ancho, setAncho] = useState(inicial)
  useLayoutEffect(() => {
    const elemento = ref.current
    if (!elemento) return
    if (elemento.clientWidth > 0) setAncho(Math.max(240, elemento.clientWidth))
    const observador = new ResizeObserver(([entrada]) => {
      if (entrada) setAncho(Math.max(240, Math.floor(entrada.contentRect.width)))
    })
    observador.observe(elemento)
    return () => observador.disconnect()
  }, [])
  return { ref, ancho }
}
