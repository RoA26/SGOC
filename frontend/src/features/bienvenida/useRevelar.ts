import { useEffect, useRef } from 'react'

/**
 * Entrada de una sección al llegar a la pantalla: marca el elemento con `data-revelar` y, al
 * cruzar el 15 % del alto visible, lo cambia a `visible` (una sola vez). El CSS escalona a sus
 * hijos directos. Sin IntersectionObserver (navegadores antiguos, pruebas) no se marca nada y el
 * contenido se ve desde el principio: nunca queda oculto si el script falla.
 */
export function useRevelar<T extends HTMLElement>() {
  const ref = useRef<T>(null)

  useEffect(() => {
    const elemento = ref.current
    if (!elemento || typeof IntersectionObserver === 'undefined') return
    // Lo que ya está en pantalla al cargar no espera a una animación.
    if (elemento.getBoundingClientRect().top < window.innerHeight * 0.85) return
    elemento.dataset.revelar = 'pendiente'
    const observador = new IntersectionObserver(
      ([entrada]) => {
        if (entrada?.isIntersecting) {
          elemento.dataset.revelar = 'visible'
          observador.disconnect()
        }
      },
      { rootMargin: '0px 0px -15% 0px' },
    )
    observador.observe(elemento)
    return () => observador.disconnect()
  }, [])

  return ref
}
