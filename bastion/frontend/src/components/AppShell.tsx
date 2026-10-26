import { useState } from "react";
import { NavLink, Outlet } from "react-router-dom";
import { Logo } from "@/brand/Logo";
import { useAuth } from "@/lib/auth";
import { ROL_LABEL, type Rol } from "@/lib/types";
import { Icon, type IconName } from "./Icon";

type Item = { to: string; label: string; icon: IconName; roles: Rol[] };
type Group = { title: string; admin?: boolean; items: Item[] };
const ORG: Rol[] = ["ADMINISTRADOR_COMERCIAL", "TRABAJADOR"];
const ADMIN: Rol[] = ["ADMINISTRADOR_COMERCIAL"];

export const NAV_GROUPS: Group[] = [
  {
    title: "Operación",
    items: [
      { to: "/app", label: "Resumen", icon: "home", roles: ORG },
      { to: "/app/inventario", label: "Inventario", icon: "boxes", roles: ORG },
      { to: "/app/movimientos", label: "Movimientos", icon: "history", roles: ORG },
      { to: "/app/productos", label: "Productos", icon: "package", roles: ["TRABAJADOR"] },
    ],
  },
  {
    title: "Administración",
    admin: true,
    items: [
      { to: "/app/productos", label: "Catálogo de productos", icon: "package", roles: ADMIN },
      { to: "/app/bodegas", label: "Bodegas", icon: "warehouse", roles: ADMIN },
      { to: "/app/usuarios", label: "Equipo de trabajo", icon: "users", roles: ADMIN },
    ],
  },
  {
    title: "Plataforma",
    admin: true,
    items: [{ to: "/app/organizaciones", label: "Organizaciones", icon: "building", roles: ["SUPER_ADMIN"] }],
  },
  {
    title: "Cuenta",
    items: [{ to: "/app/cuenta", label: "Mi cuenta", icon: "lock", roles: ["SUPER_ADMIN", ...ORG] }],
  },
];

/** Distintivo del rol: el administrador se reconoce a primera vista. */
export function RoleBadge({ role }: { role: Rol }) {
  if (role === "TRABAJADOR")
    return (
      <span className="inline-flex items-center gap-1.5 rounded-full border border-white/15 px-2.5 py-1 text-xs font-semibold text-on-ink-2">
        <Icon name="boxes" className="h-3.5 w-3.5" /> Operación
      </span>
    );
  return (
    <span className="inline-flex items-center gap-1.5 rounded-full bg-accent px-2.5 py-1 text-xs font-bold text-ink">
      <Icon name="shield" className="h-3.5 w-3.5" /> {role === "SUPER_ADMIN" ? "Super admin" : "Administrador"}
    </span>
  );
}

export function AppShell() {
  const { me, logout, hasRole } = useAuth();
  const [open, setOpen] = useState(false);
  const groups = NAV_GROUPS.map((g) => ({ ...g, items: g.items.filter((i) => hasRole(...i.roles)) })).filter((g) => g.items.length);
  const isAdmin = hasRole("ADMINISTRADOR_COMERCIAL", "SUPER_ADMIN");

  const nav = (
    <nav aria-label="Secciones" className="flex-1 space-y-6 overflow-y-auto px-3 pb-4">
      {groups.map((g) => (
        <div key={g.title}>
          <p className={`mb-1.5 px-3 text-[11px] font-bold uppercase tracking-[0.14em] ${g.admin ? "text-accent" : "text-on-ink-2/70"}`}>{g.title}</p>
          <div className={`space-y-0.5 ${g.admin ? "border-l-2 border-accent/40 pl-1.5" : ""}`}>
            {g.items.map((i) => (
              <NavLink
                key={i.to + i.label}
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
          </div>
        </div>
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
    <div className="min-h-screen lg:grid lg:grid-cols-[256px_1fr]">
      <aside className={`on-ink sticky top-0 hidden h-screen flex-col bg-ink lg:flex ${isAdmin ? "border-t-4 border-accent" : ""}`}>
        <div className="space-y-3 px-6 pb-5 pt-6">
          <Logo tone="light" />
          {me && <div><RoleBadge role={me.role} /></div>}
        </div>
        {nav}
        {footer}
      </aside>
      <header className={`on-ink sticky top-0 z-30 flex h-14 items-center justify-between bg-ink px-4 lg:hidden ${isAdmin ? "border-t-4 border-accent" : ""}`}>
        <div className="flex items-center gap-3">
          <Logo tone="light" />
          {me && <RoleBadge role={me.role} />}
        </div>
        <button type="button" className="grid h-10 w-10 place-items-center text-on-ink" aria-expanded={open} aria-label={open ? "Cerrar menú" : "Abrir menú"} onClick={() => setOpen((o) => !o)}>
          <Icon name={open ? "close" : "menu"} />
        </button>
      </header>
      {open && (
        <div className="on-ink fixed inset-x-0 top-14 bottom-0 z-30 flex flex-col bg-ink pt-4 lg:hidden">
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
