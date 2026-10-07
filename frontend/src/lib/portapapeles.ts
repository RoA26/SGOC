/**
 * Copia texto al portapapeles.
 *
 * `navigator.clipboard` solo existe en contextos seguros (HTTPS o localhost). Mientras el
 * dominio se sirva por HTTP se recurre a `execCommand('copy')`, obsoleto pero aún soportado.
 *
 * @returns `true` si se copió.
 */
export async function copiarAlPortapapeles(texto: string): Promise<boolean> {
  if (window.isSecureContext && navigator.clipboard) {
    try {
      await navigator.clipboard.writeText(texto)
      return true
    } catch {
      // Permiso denegado o documento sin foco: se intenta el método alternativo.
    }
  }
  return copiarConSeleccion(texto)
}

function copiarConSeleccion(texto: string): boolean {
  const foco = document.activeElement instanceof HTMLElement ? document.activeElement : null
  const area = document.createElement('textarea')
  area.value = texto
  area.setAttribute('readonly', '')
  area.style.position = 'fixed'
  area.style.opacity = '0'
  document.body.append(area)
  area.select()
  try {
    return document.execCommand('copy')
  } catch {
    return false
  } finally {
    area.remove()
    foco?.focus()
  }
}
