import { useSyncExternalStore } from 'react'

/**
 * Tema claro / oscuro de la interfaz.
 *
 * Sin elección guardada se sigue el tema del sistema (tokens.css, `prefers-color-scheme`). El
 * alternador fija `data-theme` en <html> y lo guarda en localStorage; el script de index.html lo
 * vuelve a aplicar antes de pintar, así que al recargar no hay parpadeo.
 */
export type Tema = 'light' | 'dark'

/** Misma clave que lee el script de index.html. */
const CLAVE = 'nexora-tema'
const CONSULTA_OSCURO = '(prefers-color-scheme: dark)'

const oyentes = new Set<() => void>()

function consultaSistema(): MediaQueryList | null {
  return typeof window.matchMedia === 'function' ? window.matchMedia(CONSULTA_OSCURO) : null
}

function temaActual(): Tema {
  const forzado = document.documentElement.dataset.theme
  if (forzado === 'light' || forzado === 'dark') return forzado
  return consultaSistema()?.matches ? 'dark' : 'light'
}

function suscribir(oyente: () => void): () => void {
  oyentes.add(oyente)
  // Sin elección guardada, un cambio del tema del sistema también cambia el tema actual.
  const consulta = consultaSistema()
  consulta?.addEventListener('change', oyente)
  return () => {
    oyentes.delete(oyente)
    consulta?.removeEventListener('change', oyente)
  }
}

export function cambiarTema(tema: Tema): void {
  document.documentElement.dataset.theme = tema
  try {
    localStorage.setItem(CLAVE, tema)
  } catch {
    // Almacenamiento bloqueado (modo privado estricto): el tema se aplica igual, solo no se recuerda.
  }
  oyentes.forEach((oyente) => oyente())
}

/** Tema que se ve ahora (el forzado con el alternador o, si no hay, el del sistema). */
export function useTema(): Tema {
  return useSyncExternalStore(suscribir, temaActual, () => 'light')
}
