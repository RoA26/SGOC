import { ArrowRight } from 'lucide-react'
import { useId, useState, type FormEvent } from 'react'
import { Link, useNavigate } from 'react-router-dom'
import { cn } from '@/lib/cn'

type Via = 'invitacion' | 'empresa'

const OPCIONES: { valor: Via; etiqueta: string }[] = [
  { valor: 'invitacion', etiqueta: 'Invitación' },
  { valor: 'empresa', etiqueta: 'Código de empresa' },
]

/**
 * Primer paso del registro, ya abierto: la vía y el código que se escriban aquí pasan a /registro
 * (`?via=empresa&codigo=…`), donde el formulario completo los valida contra el backend. Aquí no se
 * envía nada al servidor.
 */
export function AccesoConCodigo() {
  const navigate = useNavigate()
  const [via, setVia] = useState<Via>('invitacion')
  const [codigo, setCodigo] = useState('')
  const idCodigo = useId()
  const idAyuda = useId()

  function continuar(event: FormEvent) {
    event.preventDefault()
    const parametros = new URLSearchParams()
    if (via === 'empresa') parametros.set('via', 'empresa')
    const limpio = codigo.trim().toUpperCase()
    if (limpio) parametros.set('codigo', limpio)
    const consulta = parametros.toString()
    navigate(consulta ? `/registro?${consulta}` : '/registro')
  }

  return (
    <form
      data-ui
      onSubmit={continuar}
      aria-labelledby={`${idCodigo}-titulo`}
      className="w-full max-w-[27.5rem] rounded-xl border border-border bg-surface p-6 text-foreground shadow-card sm:p-7"
    >
      <p id={`${idCodigo}-titulo`} className="text-lg font-semibold tracking-tight">
        Crea tu cuenta
      </p>
      <p className="mt-1 text-sm text-foreground-muted">El administrador de tu empresa te da uno de los dos códigos.</p>

      <fieldset className="mt-5">
        <legend className="sr-only">¿Con qué código te registras?</legend>
        <div className="grid grid-cols-2 gap-1 rounded-md bg-surface-muted p-1">
          {OPCIONES.map((opcion) => (
            <label
              key={opcion.valor}
              className={cn(
                'cursor-pointer rounded-sm px-2 py-2 text-center text-sm font-medium transition-colors duration-150 has-[:focus-visible]:outline-2 has-[:focus-visible]:outline-focus',
                via === opcion.valor ? 'bg-surface text-foreground' : 'text-foreground-muted hover:text-foreground',
              )}
            >
              <input
                type="radio"
                name="via-acceso"
                value={opcion.valor}
                checked={via === opcion.valor}
                onChange={() => setVia(opcion.valor)}
                className="sr-only"
              />
              {opcion.etiqueta}
            </label>
          ))}
        </div>
      </fieldset>

      <label htmlFor={idCodigo} className="u-label mt-5">
        {via === 'invitacion' ? 'Código de invitación' : 'Código de tu empresa'}
      </label>
      <input
        id={idCodigo}
        value={codigo}
        onChange={(event) => setCodigo(event.target.value)}
        aria-describedby={idAyuda}
        className="u-input mt-1.5 font-mono tracking-wider uppercase"
        autoComplete="off"
        autoCapitalize="characters"
        spellCheck={false}
        placeholder="XXXX-XXXX-XXXX-XXXX"
      />
      <p id={idAyuda} className="mt-1.5 text-xs text-foreground-muted">
        {via === 'invitacion' ? 'Con una invitación entras directamente.' : 'Con el código de la empresa, tu administrador aprueba la cuenta.'}
      </p>

      <button type="submit" className="u-btn u-btn--primary mt-6 h-12 w-full">
        Continuar
        <ArrowRight className="size-4" aria-hidden="true" />
      </button>
      <p className="mt-4 text-center text-sm text-foreground-muted">
        ¿Ya tienes cuenta?{' '}
        <Link to="/login" className="u-link">
          Inicia sesión
        </Link>
      </p>
    </form>
  )
}
