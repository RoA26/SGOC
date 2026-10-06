import { Building2, House, LogOut, Package } from 'lucide-react'
import { NavLink, Outlet } from 'react-router-dom'
import { BrandLogo } from '@/components/BrandLogo'
import { cn } from '@/lib/cn'
import { useAuthStore } from '@/store/authStore'

const NAVEGACION = [
  { to: '/', label: 'Inicio', icon: House, end: true },
  { to: '/proveedores', label: 'Proveedores', icon: Building2, end: false },
  { to: '/productos', label: 'Productos', icon: Package, end: false },
] as const

/** Estructura común de las páginas autenticadas: cabecera, navegación y contenido. */
export function AppLayout() {
  const nombre = useAuthStore((state) => state.nombre)
  const rol = useAuthStore((state) => state.rol)
  const clearSession = useAuthStore((state) => state.clearSession)

  return (
    <div className="min-h-screen">
      <header className="sticky top-0 z-10 border-b border-border bg-surface/90 backdrop-blur">
        <div className="mx-auto flex h-16 max-w-6xl items-center gap-6 px-5">
          <BrandLogo className="h-8 w-auto shrink-0" />

          <nav aria-label="Principal" className="-mx-1 flex flex-1 items-center gap-1 overflow-x-auto">
            {NAVEGACION.map(({ to, label, icon: Icon, end }) => (
              <NavLink
                key={to}
                to={to}
                end={end}
                className={({ isActive }) =>
                  cn(
                    'flex items-center gap-2 rounded-md px-3 py-2 text-sm font-medium whitespace-nowrap transition-colors',
                    isActive
                      ? 'bg-surface-muted text-foreground'
                      : 'text-foreground-muted hover:bg-surface-muted/60 hover:text-foreground',
                  )
                }
              >
                <Icon className="size-4" aria-hidden="true" />
                <span className="hidden sm:inline">{label}</span>
              </NavLink>
            ))}
          </nav>

          <div className="flex shrink-0 items-center gap-3">
            <div className="hidden text-right leading-tight md:block">
              <p className="text-sm font-medium text-foreground">{nombre}</p>
              <p className="text-xs text-foreground-muted">{rol === 'ADMIN' ? 'Administrador' : 'Consulta'}</p>
            </div>
            <button type="button" className="u-btn u-btn--ghost h-9 px-3" onClick={clearSession}>
              <LogOut className="size-4" aria-hidden="true" />
              <span className="hidden sm:inline">Salir</span>
            </button>
          </div>
        </div>
      </header>

      <main className="mx-auto max-w-6xl px-5 py-10">
        <Outlet />
      </main>
    </div>
  )
}
