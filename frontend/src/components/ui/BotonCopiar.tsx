import { Check, Copy } from 'lucide-react'
import { useEffect, useState } from 'react'
import { cn } from '@/lib/cn'
import { copiarAlPortapapeles } from '@/lib/portapapeles'

const DURACION_COPIADO_MS = 2000
const DURACION_ERROR_MS = 5000

interface BotonCopiarProps {
  texto: string
  /** Texto visible del botón. */
  etiqueta?: string
  /** Nombre accesible, si la etiqueta visible no basta ("Copiar código"). */
  ariaLabel?: string
  className?: string
}

/** Botón que copia `texto` y confirma con un "Copiado" en verde durante unos segundos. */
export function BotonCopiar({ texto, etiqueta = 'Copiar', ariaLabel, className }: BotonCopiarProps) {
  const [estado, setEstado] = useState<'copiado' | 'error' | null>(null)

  useEffect(() => {
    if (!estado) return
    const timeoutId = window.setTimeout(() => setEstado(null), estado === 'copiado' ? DURACION_COPIADO_MS : DURACION_ERROR_MS)
    return () => window.clearTimeout(timeoutId)
  }, [estado])

  async function copiar() {
    setEstado((await copiarAlPortapapeles(texto)) ? 'copiado' : 'error')
  }

  return (
    <div className={cn('flex flex-wrap items-center gap-x-3 gap-y-1', className)}>
      <button type="button" className="u-btn u-btn--ghost h-9 px-3" onClick={copiar} aria-label={ariaLabel}>
        {estado === 'copiado' ? (
          <Check className="size-4 text-success" aria-hidden="true" />
        ) : (
          <Copy className="size-4" aria-hidden="true" />
        )}
        {etiqueta}
      </button>
      <span role="status" className={cn('text-sm font-medium', estado === 'error' ? 'text-danger' : 'text-success')}>
        {estado === 'copiado' && 'Copiado'}
        {estado === 'error' && 'No se pudo copiar: selecciónalo y usa Ctrl+C.'}
      </span>
    </div>
  )
}
