import { ChevronRight } from 'lucide-react'
import type { ReactNode } from 'react'
import { Link } from 'react-router-dom'

interface PageHeaderProps {
  /** Sección del menú a la que pertenece la página («Compras», «Catálogo»…): primera miga de la ruta. */
  eyebrow: string
  /** Ruta de la sección, si tiene página propia (p. ej. el listado del que viene una ficha). */
  eyebrowTo?: string
  title: ReactNode
  description?: ReactNode
  actions?: ReactNode
}

/**
 * Cabecera de las páginas autenticadas: la ruta (Inicio › sección) como migas, el título en la
 * tipografía de la interfaz a tamaño de aplicación, una frase sobre los datos y las acciones reales.
 */
export function PageHeader({ eyebrow, eyebrowTo, title, description, actions }: PageHeaderProps) {
  return (
    <div className="mb-8 flex flex-col gap-4 sm:flex-row sm:items-end sm:justify-between">
      <div className="min-w-0">
        <Migas seccion={eyebrow} seccionTo={eyebrowTo} />
        <h1 className="mt-2 text-[1.75rem] leading-tight font-semibold tracking-tight text-balance text-foreground">
          {title}
        </h1>
        {description && <p className="mt-1.5 max-w-2xl text-sm text-pretty text-foreground-muted">{description}</p>}
      </div>
      {actions && <div className="flex shrink-0 flex-wrap gap-3">{actions}</div>}
    </div>
  )
}

/** Migas de la ruta: Inicio › sección. La página actual la nombra el título. */
export function Migas({ seccion, seccionTo }: { seccion: string; seccionTo?: string }) {
  return (
    <nav aria-label="Ruta de navegación">
      <ol className="flex flex-wrap items-center gap-1 text-[0.8125rem] text-foreground-muted">
        <li>
          <Link to="/" className="rounded-sm hover:text-foreground">
            Inicio
          </Link>
        </li>
        <li aria-hidden="true">
          <ChevronRight className="size-3.5" />
        </li>
        <li>
          {seccionTo ? (
            <Link to={seccionTo} className="rounded-sm hover:text-foreground">
              {seccion}
            </Link>
          ) : (
            seccion
          )}
        </li>
      </ol>
    </nav>
  )
}
