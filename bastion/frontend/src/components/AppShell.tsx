import { useState } from "react";
import { NavLink, Outlet } from "react-router-dom";
import { Logo } from "@/brand/Logo";
import { useAuth } from "@/lib/auth";
import { ROL_LABEL, type Rol } from "@/lib/types";
import { Icon, type IconName } from "./Icon";

type Item = { to: string; label: string; icon: IconName; roles: Rol[] };
const ORG: Rol[] = ["ADMINISTRADOR_COMERCIAL", "TRABAJADOR"];
export const NAV_ITEMS: Item[] = [
  { to: "/app", label: "Resumen", icon: "home", roles: ORG },
  { to: "/app/inventario", label: "Inventario", icon: "boxes", roles: ORG },
  { to: "/app/movimientos", label: "Movimientos", icon: "history", roles: ORG },
  { to: "/app/productos", label: "Productos", icon: "package", roles: ORG },
  { to: "/app/bodegas", label: "Bodegas", icon: "warehouse", roles: ["ADMINISTRADOR_COMERCIAL"] },
  { to: "/app/usuarios", label: "Usuarios", icon: "users", roles: ["ADMINISTRADOR_COMERCIAL"] },
  { to: "/app/organizaciones", label: "Organizaciones", icon: "building", roles: ["SUPER_ADMIN"] },
];

export function AppShell() {
  const { me, logout, hasRole } = useAuth();
  const [open, setOpen] = useState(false);
  const items = NAV_ITEMS.filter((i) => hasRole(...i.roles));

  const nav = (
    <nav aria-label="Secciones" className="flex-1 space-y-0.5 px-3">
      {items.map((i) => (
        <NavLink
          key={i.to}
          to={i.to}
          end={i.to === "/app"}
          onClick={() => setOpen(false)}
          className={({ isActive }) =>
            `flex min-h-10 items-center gap-3 rounded-b px-3 text-sm font-medium transition-colors ${isActive ? "bg-white/10 text-white" : "text-on-ink-2 hover:bg-white/5 hover:text-white"}`
          }
        >
          <Icon name={i.icon} className="h-[18px] w-[18px]" /> {i.label}
        </NavLink>
      ))}
    </nav>
  );

  const footer = me && (
    <div className="border-t border-white/10 p-4">
      <p className="truncate text-sm font-semibold text-white">{me.full_name}</p>
      <p className="truncate text-xs text-on-ink-2">{ROL_LABEL[me.role]}{me.organization_name ? ` · ${me.organization_name}` : ""}</p>
      <button type="button" onClick={() => void logout()} className="mt-3 inline-flex min-h-9 items-center gap-2 text-sm font-medium text-on-ink-2 hover:text-white">
        <Icon name="logout" className="h-4 w-4" /> Cerrar sesión
      </button>
    </div>
  );

  return (
    <div className="min-h-screen lg:grid lg:grid-cols-[248px_1fr]">
      <aside className="on-ink sticky top-0 hidden h-screen flex-col bg-ink lg:flex">
        <div className="px-6 py-6"><Logo tone="light" /></div>
        {nav}
        {footer}
      </aside>
      <header className="on-ink sticky top-0 z-30 flex h-14 items-center justify-between bg-ink px-4 lg:hidden">
        <Logo tone="light" />
        <button type="button" className="grid h-10 w-10 place-items-center text-on-ink" aria-expanded={open} aria-label={open ? "Cerrar menú" : "Abrir menú"} onClick={() => setOpen((o) => !o)}>
          <Icon name={open ? "close" : "menu"} />
        </button>
      </header>
      {open && (
        <div className="on-ink fixed inset-x-0 top-14 bottom-0 z-30 flex flex-col bg-ink pt-2 lg:hidden">
          {nav}
          {footer}
        </div>
      )}
      <main className="min-w-0 px-4 py-8 sm:px-8">
        <div className="mx-auto max-w-6xl"><Outlet /></div>
      </main>
    </div>
  );
}
