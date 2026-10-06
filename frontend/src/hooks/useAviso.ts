import { useEffect, useState } from 'react'

const DURACION_MS = 4000

/** Mensaje de confirmación temporal ("Proveedor creado"), que desaparece solo. */
export function useAviso() {
  const [aviso, setAviso] = useState<string | null>(null)

  useEffect(() => {
    if (!aviso) return
    const timeoutId = window.setTimeout(() => setAviso(null), DURACION_MS)
    return () => window.clearTimeout(timeoutId)
  }, [aviso])

  return { aviso, mostrarAviso: setAviso }
}
