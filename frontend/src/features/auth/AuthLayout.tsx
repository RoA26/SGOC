import type { ReactNode } from 'react'
import { BrandLogo } from '@/components/BrandLogo'

const CURRENT_YEAR = new Date().getFullYear()

interface AuthLayoutProps {
  title: string
  description: ReactNode
  children: ReactNode
}

/** Pantallas de invitado (login y registro): panel de marca a la izquierda y formulario a la derecha. */
export function AuthLayout({ title, description, children }: AuthLayoutProps) {
  return (
    <div className="grid min-h-screen lg:grid-cols-[minmax(0,5fr)_minmax(0,6fr)]">
      {/* Panel de marca (escritorio) */}
      <aside className="relative hidden flex-col justify-between overflow-hidden bg-brand-panel p-12 text-brand-panel-foreground lg:flex">
        <div
          aria-hidden="true"
          className="pointer-events-none absolute -right-32 -bottom-40 size-[28rem] rounded-full bg-accent/20 blur-3xl"
        />
        <BrandLogo variant="claro" className="relative h-10 w-auto self-start" />
        <div className="relative max-w-md">
          <p className="text-sm font-medium tracking-widest text-brand-panel-muted uppercase">
            Sistema de gestión de compras
          </p>
          <h2 className="mt-4 font-serif text-5xl leading-[1.05] xl:text-6xl">
            Órdenes de compra, <em className="text-accent">claras</em> de principio a fin.
          </h2>
          <p className="mt-6 text-base leading-relaxed text-brand-panel-muted">
            Solicita, aprueba y da seguimiento a cada compra de Unisen desde un único lugar.
          </p>
        </div>
        <p className="relative text-xs text-brand-panel-muted">© {CURRENT_YEAR} Unisen</p>
      </aside>

      {/* Formulario */}
      <main className="flex items-center justify-center px-5 py-12 sm:px-10">
        <div className="w-full max-w-sm">
          <BrandLogo className="mb-10 h-9 w-auto lg:hidden" />

          <h1 className="font-serif text-4xl text-foreground">{title}</h1>
          <p className="mt-2 text-sm text-foreground-muted">{description}</p>

          {children}

          <p className="mt-10 text-center text-xs text-foreground-muted lg:hidden">© {CURRENT_YEAR} Unisen</p>
        </div>
      </main>
    </div>
  )
}
