import { Building, Globe2, Loader2, LogIn, X } from 'lucide-react'
import { useId, useState, type FormEvent } from 'react'
import { ETIQUETA_ROL, useContextoEmpresa } from '@/features/auth/permisos'
import { getApiErrorMessage } from '@/lib/errors'
import { verificarEmpresa } from './empresaApi'
import { useEmpresaSeleccionadaStore } from './empresaSeleccionadaStore'
import { etiquetaEmpresa } from './etiquetas'

/**
 * Empresa sobre la que se trabaja. GERENTE y USUARIO solo la ven (es la suya). El SUPER_ADMIN
 * puede entrar en una empresa (soporte: las peticiones llevan X-Tenant-ID) o volver al modo
 * global (todas las empresas, solo lectura).
 */
export function ContextoEmpresaPanel() {
  const { rol, empresaId, esSuperAdmin } = useContextoEmpresa()

  if (!esSuperAdmin) {
    return (
      <div className="rounded-lg border border-border bg-surface-muted/50 px-3 py-2.5">
        <p className="text-xs text-foreground-muted">Empresa</p>
        <p className="flex items-center gap-2 text-sm font-medium text-foreground">
          <Building className="size-4 shrink-0 text-accent" aria-hidden="true" />
          {empresaId !== null ? etiquetaEmpresa(empresaId) : 'Sin empresa'}
        </p>
        {rol && <p className="mt-0.5 text-xs text-foreground-muted">{ETIQUETA_ROL[rol]}</p>}
      </div>
    )
  }
  return <SelectorEmpresa empresaId={empresaId} />
}

function SelectorEmpresa({ empresaId }: { empresaId: number | null }) {
  const seleccionar = useEmpresaSeleccionadaStore((state) => state.seleccionar)
  const salir = useEmpresaSeleccionadaStore((state) => state.salir)
  const [abierto, setAbierto] = useState(false)
  const [valor, setValor] = useState('')
  const [error, setError] = useState<string | null>(null)
  const [verificando, setVerificando] = useState(false)
  const inputId = useId()

  async function entrar(event: FormEvent<HTMLFormElement>) {
    event.preventDefault()
    const id = Number(valor)
    if (!Number.isInteger(id) || id <= 0) {
      setError('Escribe el número (id) de la empresa.')
      return
    }
    setVerificando(true)
    setError(null)
    try {
      await verificarEmpresa(id)
      seleccionar(id)
      setAbierto(false)
      setValor('')
    } catch (err) {
      setError(getApiErrorMessage(err))
    } finally {
      setVerificando(false)
    }
  }

  if (empresaId !== null) {
    return (
      <div className="rounded-lg border border-accent/40 bg-accent/10 px-3 py-2.5">
        <p className="text-xs text-foreground-muted">Trabajando en</p>
        <p className="flex items-center gap-2 text-sm font-medium text-foreground">
          <Building className="size-4 shrink-0 text-accent" aria-hidden="true" />
          {etiquetaEmpresa(empresaId)}
        </p>
        <button
          type="button"
          onClick={salir}
          className="mt-2 inline-flex items-center gap-1.5 text-xs font-medium text-foreground underline-offset-4 hover:underline"
        >
          <Globe2 className="size-3.5" aria-hidden="true" />
          Volver al modo global
        </button>
      </div>
    )
  }

  return (
    <div className="rounded-lg border border-border bg-surface-muted/50 px-3 py-2.5">
      <p className="text-xs text-foreground-muted">Super administrador</p>
      <p className="flex items-center gap-2 text-sm font-medium text-foreground">
        <Globe2 className="size-4 shrink-0 text-accent" aria-hidden="true" />
        Modo global
      </p>
      <p className="mt-0.5 text-xs text-foreground-muted">Ves todas las empresas, sin poder modificar sus datos.</p>

      {abierto ? (
        <form onSubmit={entrar} noValidate className="mt-2 space-y-1.5">
          <label htmlFor={inputId} className="text-xs font-medium text-foreground">
            Id de la empresa
          </label>
          <div className="flex gap-1.5">
            <input
              id={inputId}
              value={valor}
              onChange={(event) => {
                setValor(event.target.value)
                setError(null)
              }}
              inputMode="numeric"
              autoComplete="off"
              autoFocus
              className="u-input h-9 min-w-0 flex-1 text-sm"
              aria-invalid={error ? true : undefined}
              aria-describedby={error ? `${inputId}-error` : undefined}
            />
            <button type="submit" className="u-btn u-btn--primary h-9 px-2.5" disabled={verificando} aria-label="Entrar en la empresa">
              {verificando ? <Loader2 className="size-4 animate-spin" aria-hidden="true" /> : <LogIn className="size-4" aria-hidden="true" />}
            </button>
            <button
              type="button"
              className="u-btn u-btn--ghost h-9 px-2.5"
              onClick={() => {
                setAbierto(false)
                setError(null)
              }}
              aria-label="Cancelar"
            >
              <X className="size-4" aria-hidden="true" />
            </button>
          </div>
          {error && (
            <p id={`${inputId}-error`} role="alert" className="u-field-error text-xs">
              {error}
            </p>
          )}
        </form>
      ) : (
        <button
          type="button"
          onClick={() => setAbierto(true)}
          className="mt-2 inline-flex items-center gap-1.5 text-xs font-medium text-foreground underline-offset-4 hover:underline"
        >
          <Building className="size-3.5" aria-hidden="true" />
          Trabajar en una empresa
        </button>
      )}
    </div>
  )
}
