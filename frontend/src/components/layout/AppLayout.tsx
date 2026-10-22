import { LogOut, Menu, X } from 'lucide-react'
import { Suspense, useEffect, useRef, useState } from 'react'
import { Link, NavLink, Outlet, useLocation } from 'react-router-dom'
import { AlternadorTema } from '@/components/AlternadorTema'
import { BrandLogo } from '@/components/BrandLogo'
import { ETIQUETA_ROL, useContextoEmpresa, usePermisos } from '@/features/auth/permisos'
import { useRefrescoPerfil } from '@/features/auth/useRefrescoPerfil'
import { AvisoSupervision } from '@/features/empresa/AvisoSupervision'
import { ContextoEmpresaPanel } from '@/features/empresa/ContextoEmpresaPanel'
import { cn } from '@/lib/cn'
import { useAuthStore } from '@/store/authStore'
import { CargandoVista } from './CargandoVista'
import { construirNavegacion } from './navegacion'

/**
 * Estructura de las páginas autenticadas: barra lateral con la empresa de trabajo y el menú
 * del SGOC (en móvil, un panel que se abre desde la cabecera) y el contenido a la derecha.
 */
export function AppLayout() {
  // Nombre, rol y empresa vienen del backend: se refrescan sin volver a iniciar sesión.
  useRefrescoPerfil()
  const { empresaId, esSuperAdmin } = useContextoEmpresa()
  const { pathname } = useLocation()
  // Ruta en la que se abrió el panel móvil: al navegar a otra, queda cerrado.
  const [menuAbiertoEn, setMenuAbiertoEn] = useState<string | null>(null)
  const menuAbierto = menuAbiertoEn === pathname

  // Si el SUPER_ADMIN entra en supervisión o vuelve al modo global, la página se monta de nuevo y recarga sus datos.
  const claveContexto = esSuperAdmin ? `empresa-${empresaId ?? 'global'}` : 'propia'

  return (
    <div className="min-h-screen lg:grid lg:grid-cols-[16rem_minmax(0,1fr)]">
      <aside className="sticky top-0 hidden h-screen flex-col border-r border-border bg-surface lg:flex">
        <PanelLateral conAlternadorTema />
      </aside>

      <header className="sticky top-0 z-10 flex h-14 items-center justify-between gap-3 border-b border-border bg-surface px-4 sm:px-5 lg:hidden">
        <Link to="/" aria-label="Nexora, ir al inicio" className="rounded-md">
          <BrandLogo className="h-7 w-auto shrink-0" />
        </Link>
        <div className="flex items-center gap-2">
          <AlternadorTema className="size-9" />
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
        </div>
      </header>
      {menuAbierto && <MenuMovil onClose={() => setMenuAbiertoEn(null)} />}

      <main className="mx-auto w-full max-w-6xl px-4 py-8 sm:px-6 lg:px-8 lg:py-10">
        <AvisoSupervision />
        {/* Cada cambio de ruta entra con un ascenso corto: la vista nueva se distingue de la anterior. */}
        <div key={pathname} className="u-vista">
          {/* Al descargar un módulo, solo el contenido espera: el menú lateral no parpadea. */}
          <Suspense fallback={<CargandoVista />}>
            <Outlet key={claveContexto} />
          </Suspense>
        </div>
      </main>
    </div>
  )
}

/** En el menú móvil, el alternador de tema queda en la cabecera (aquí estaría bajo el botón de cerrar). */
function PanelLateral({ conAlternadorTema = false }: { conAlternadorTema?: boolean }) {
  const nombre = useAuthStore((state) => state.nombre)
  const rol = useAuthStore((state) => state.rol)
  const clearSession = useAuthStore((state) => state.clearSession)

  return (
    <div className="flex h-full flex-col">
      <div className="flex items-center justify-between gap-3 px-5 pt-5 pb-4">
        <Link to="/" aria-label="Nexora, ir al inicio" className="inline-block rounded-md">
          <BrandLogo className="h-8 w-auto" />
        </Link>
        {conAlternadorTema && <AlternadorTema className="size-9" />}
      </div>
      <div className="px-4">
        <ContextoEmpresaPanel />
      </div>
      <Navegacion />
      <div className="flex items-center justify-between gap-3 border-t border-border px-5 py-4">
        <Link to="/perfil" className="group flex min-w-0 items-center gap-2.5 rounded-md leading-tight" title="Mi perfil">
          <span
            aria-hidden="true"
            className="grid size-8 shrink-0 place-items-center rounded-full bg-primary/10 text-xs font-semibold text-primary"
          >
            {iniciales(nombre)}
          </span>
          <span className="min-w-0">
          <p className="truncate text-sm font-medium text-foreground group-hover:underline">{nombre}</p>
          <p className="text-xs text-foreground-muted">{rol && ETIQUETA_ROL[rol]}</p>
          </span>
        </Link>
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
            <p className="px-2 pb-1 text-xs font-medium text-foreground-muted">{titulo}</p>
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
                        'group flex items-center gap-2.5 rounded-md px-2 py-[0.4375rem] text-sm font-medium transition-colors duration-150',
                        isActive
                          ? 'bg-surface-muted text-foreground'
                          : 'text-foreground-muted hover:bg-surface-muted/60 hover:text-foreground',
                      )
                    }
                  >
                    {({ isActive }) => (
                      <>
                        <Icon className={cn('size-4 shrink-0', isActive && 'text-primary')} aria-hidden="true" />
                        {label}
                      </>
                    )}
                  </NavLink>
                ) : (
                  // Sin endpoints en el backend todavía: se muestra para entender el flujo, sin enlace.
                  <span className="flex items-center gap-2.5 px-2 py-2 text-sm text-foreground-muted/70">
                    <Icon className="size-4 shrink-0" aria-hidden="true" />
                    {label}
                    <span className="ml-auto rounded-full border border-border px-1.5 py-px text-[0.6875rem] font-medium">
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

/** Dos iniciales para el avatar del usuario («Carlos Mendoza» → «CM»). */
function iniciales(nombre: string | null): string {
  const partes = (nombre ?? '').trim().split(/\s+/).filter(Boolean)
  return ((partes[0]?.[0] ?? '') + (partes.length > 1 ? (partes.at(-1)?.[0] ?? '') : '')).toUpperCase() || '·'
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
      className="u-panel-lateral m-0 h-full max-h-none w-72 max-w-[85vw] border-r border-border bg-surface p-0 text-foreground backdrop:bg-[rgb(20_12_14/0.55)] lg:hidden"
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
