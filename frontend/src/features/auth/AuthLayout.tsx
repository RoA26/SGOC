import { ArrowLeft } from 'lucide-react'
import type { ReactNode } from 'react'
import { Link } from 'react-router-dom'
import { AlternadorTema } from '@/components/AlternadorTema'
import { BrandLogo } from '@/components/BrandLogo'

const CURRENT_YEAR = new Date().getFullYear()

/** Las cinco etapas de una compra en Nexora, tal como las registra el sistema. */
const ETAPAS = [
  { nombre: 'Solicitud', detalle: 'Quien necesita algo lo pide.' },
  { nombre: 'Aprobación', detalle: 'El administrador comercial decide.' },
  { nombre: 'Orden de compra', detalle: 'Sale al proveedor en PDF y Word.' },
  { nombre: 'Recepción', detalle: 'Se anota lo que llegó y lo que no.' },
  { nombre: 'Existencias', detalle: 'El inventario se actualiza solo.' },
] as const

interface AuthLayoutProps {
  title: string
  description: ReactNode
  children: ReactNode
}

/** Pantallas de invitado (login y registro): panel de marca a la izquierda y formulario a la derecha. */
export function AuthLayout({ title, description, children }: AuthLayoutProps) {
  return (
    <div className="grid min-h-svh grid-cols-[minmax(0,1fr)] lg:grid-cols-[minmax(0,5fr)_minmax(0,6fr)]">
      {/* Panel de marca (escritorio) */}
      <aside className="sticky top-0 hidden h-screen flex-col justify-between bg-brand-panel p-12 text-brand-panel-foreground lg:flex">
        <Link to="/" aria-label="Nexora, página de inicio" className="self-start rounded-md">
          <BrandLogo variant="claro" className="h-10 w-auto" />
        </Link>
        <div className="max-w-md">
          <h2 className="font-serif text-5xl leading-[1.04] text-balance xl:text-6xl">Órdenes de compra, claras de principio a fin.</h2>
          <ol className="mt-10 space-y-4 border-l border-brand-panel-muted/30 pl-5">
            {ETAPAS.map((etapa) => (
              <li key={etapa.nombre} className="relative">
                <span aria-hidden="true" className="absolute top-2 -left-[1.4rem] size-1.5 rounded-full bg-brand-panel-accent" />
                <p className="text-[0.9375rem] font-medium">{etapa.nombre}</p>
                <p className="text-sm text-brand-panel-muted">{etapa.detalle}</p>
              </li>
            ))}
          </ol>
        </div>
        <p className="text-xs text-brand-panel-muted">© {CURRENT_YEAR} Nexora</p>
      </aside>

      {/* Formulario */}
      <main className="flex flex-col px-5 py-8 sm:px-10">
        <div className="flex items-center justify-between gap-3">
          <Link to="/" className="u-link text-sm font-normal text-foreground-muted hover:text-foreground">
            <ArrowLeft className="size-4" aria-hidden="true" />
            Volver a la página de inicio
          </Link>
          <AlternadorTema />
        </div>
        <div className="u-vista mx-auto flex w-full max-w-sm flex-1 flex-col justify-center py-10">
          <Link to="/" aria-label="Nexora, página de inicio" className="mb-10 self-start rounded-md lg:hidden">
            <BrandLogo className="h-9 w-auto" />
          </Link>

          <h1 className="font-serif text-[2.75rem] leading-[1.05] text-foreground">{title}</h1>
          <p className="mt-2 text-sm text-foreground-muted">{description}</p>

          {children}

          <p className="mt-10 text-center text-xs text-foreground-muted lg:hidden">© {CURRENT_YEAR} Nexora</p>
        </div>
      </main>
    </div>
  )
}
