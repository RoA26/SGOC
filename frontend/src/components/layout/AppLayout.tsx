import { LogOut, Menu, X } from 'lucide-react'
import { useEffect, useRef, useState } from 'react'
import { NavLink, Outlet, useLocation } from 'react-router-dom'
import { BrandLogo } from '@/components/BrandLogo'
import { ETIQUETA_ROL, useContextoEmpresa, usePermisos } from '@/features/auth/permisos'
import { ContextoEmpresaPanel } from '@/features/empresa/ContextoEmpresaPanel'
import { cn } from '@/lib/cn'
import { useAuthStore } from '@/store/authStore'
import { construirNavegacion } from './navegacion'

/**
 * Estructura de las páginas autenticadas: barra lateral con la empresa de trabajo y el menú
 * del SGOC (en móvil, un panel que se abre desde la cabecera) y el contenido a la derecha.
 */
export function AppLayout() {
  const { empresaId, esSuperAdmin } = useContextoEmpresa()
  const { pathname } = useLocation()
  // Ruta en la que se abrió el panel móvil: al navegar a otra, queda cerrado.
  const [menuAbiertoEn, setMenuAbiertoEn] = useState<string | null>(null)
  const menuAbierto = menuAbiertoEn === pathname

  // Si el SUPER_ADMIN cambia de empresa, la página se monta de nuevo y recarga sus datos.
  const claveContexto = esSuperAdmin ? `empresa-${empresaId ?? 'global'}` : 'propia'

  return (
    <div className="min-h-screen lg:grid lg:grid-cols-[16rem_minmax(0,1fr)]">
      <aside className="sticky top-0 hidden h-screen flex-col border-r border-border bg-surface lg:flex">
        <PanelLateral />
      </aside>

      <header className="sticky top-0 z-10 flex h-16 items-center justify-between gap-3 border-b border-border bg-surface/90 px-5 backdrop-blur lg:hidden">
        <BrandLogo className="h-7 w-auto shrink-0" />
        <button
          type="button"
          className="u-btn u-btn--ghost h-9 px-3"
          onClick={() => setMenuAbiertoEn(pathname)}
          aria-expanded={menuAbierto}
          aria-controls="menu-movil"
        >
          <Menu className="size-4" aria-hidden="true" />
          Menú
        </button>
      </header>
      {menuAbierto && <MenuMovil onClose={() => setMenuAbiertoEn(null)} />}

      <main className="mx-auto w-full max-w-6xl px-5 py-10">
        <Outlet key={claveContexto} />
      </main>
    </div>
  )
}

function PanelLateral() {
  const nombre = useAuthStore((state) => state.nombre)
  const rol = useAuthStore((state) => state.rol)
  const clearSession = useAuthStore((state) => state.clearSession)

  return (
    <div className="flex h-full flex-col">
      <div className="px-5 pt-5 pb-4">
        <BrandLogo className="h-8 w-auto" />
        <p className="mt-2 text-xs font-medium tracking-widest text-foreground-muted uppercase">Gestión de compras</p>
      </div>
      <div className="px-4">
        <ContextoEmpresaPanel />
      </div>
      <Navegacion />
      <div className="flex items-center justify-between gap-3 border-t border-border px-5 py-4">
        <div className="min-w-0 leading-tight">
          <p className="truncate text-sm font-medium text-foreground">{nombre}</p>
          <p className="text-xs text-foreground-muted">{rol && ETIQUETA_ROL[rol]}</p>
        </div>
        <button type="button" className="u-btn u-btn--ghost h-9 shrink-0 px-3" onClick={clearSession}>
          <LogOut className="size-4" aria-hidden="true" />
          Salir
        </button>
      </div>
    </div>
  )
}

function Navegacion() {
  const rol = useAuthStore((state) => state.rol)
  const permisos = usePermisos()
  const grupos = construirNavegacion(rol, permisos)

  return (
    <nav aria-label="Principal" className="flex-1 space-y-5 overflow-y-auto px-3 py-5">
      {grupos.map(({ titulo, items }) => (
        <div key={titulo ?? 'inicio'}>
          {titulo && (
            <p className="px-2 pb-1.5 text-xs font-medium tracking-widest text-foreground-muted uppercase">{titulo}</p>
          )}
          <ul className="space-y-0.5">
            {items.map(({ to, label, icon: Icon, end }) => (
              <li key={label}>
                {to ? (
                  <NavLink
                    to={to}
                    end={end}
                    className={({ isActive }) =>
                      cn(
                        'flex items-center gap-2.5 rounded-md px-2 py-2 text-sm font-medium transition-colors',
                        isActive
                          ? 'bg-surface-muted text-foreground'
                          : 'text-foreground-muted hover:bg-surface-muted/60 hover:text-foreground',
                      )
                    }
                  >
                    <Icon className="size-4 shrink-0" aria-hidden="true" />
                    {label}
                  </NavLink>
                ) : (
                  // Sin endpoints en el backend todavía: se muestra para entender el flujo, sin enlace.
                  <span className="flex items-center gap-2.5 px-2 py-2 text-sm text-foreground-muted/70">
                    <Icon className="size-4 shrink-0" aria-hidden="true" />
                    {label}
                    <span className="ml-auto rounded-full border border-border px-1.5 py-px text-[0.65rem] font-medium tracking-wide uppercase">
                      Próximamente
                    </span>
                  </span>
                )}
              </li>
            ))}
          </ul>
        </div>
      ))}
    </nav>
  )
}

/** Menú en pantallas pequeñas: <dialog> modal nativo (foco atrapado, Escape cierra). */
function MenuMovil({ onClose }: { onClose: () => void }) {
  const dialogRef = useRef<HTMLDialogElement>(null)

  useEffect(() => {
    const dialog = dialogRef.current
    if (dialog && !dialog.open) dialog.showModal()
  }, [])

  return (
    <dialog
      id="menu-movil"
      ref={dialogRef}
      aria-label="Menú"
      onCancel={(event) => {
        event.preventDefault()
        onClose()
      }}
      onClick={(event) => {
        // Clic fuera del panel, o en un enlace (también el de la página actual).
        if (event.target === event.currentTarget || (event.target as HTMLElement).closest('a')) onClose()
      }}
      className="m-0 h-full max-h-none w-72 max-w-[85vw] border-r border-border bg-surface p-0 text-foreground backdrop:bg-black/50 lg:hidden"
    >
      <div className="relative h-full">
        <button
          type="button"
          onClick={onClose}
          className="absolute top-4 right-3 rounded-md p-1.5 text-foreground-muted hover:bg-surface-muted hover:text-foreground"
          aria-label="Cerrar menú"
        >
          <X className="size-5" aria-hidden="true" />
        </button>
        <PanelLateral />
      </div>
    </dialog>
  )
}
