import { useState } from "react";
import { Link } from "react-router-dom";
import { Logo } from "@/brand/Logo";
import { Icon, type IconName } from "@/components/Icon";

const NAV = [
  { href: "#producto", label: "Producto" },
  { href: "#flujo", label: "Flujo de trabajo" },
  { href: "#seguridad", label: "Seguridad" },
];

const BENEFITS: { icon: IconName; title: string; text: string; soon?: boolean }[] = [
  { icon: "trace", title: "Trazabilidad", text: "Cada entrada, salida y ajuste queda registrado con usuario, fecha, referencia y saldo resultante. Los errores se corrigen con una reversión, nunca borrando." },
  { icon: "boxes", title: "Control de existencias", text: "Saldos por producto y bodega, alertas de stock mínimo y costo promedio ponderado calculado en cada entrada." },
  { icon: "warehouse", title: "Gestión de bodegas", text: "Organice el inventario en bodegas con código propio y consulte existencias por ubicación." },
  { icon: "clipboard", title: "Conteos físicos", text: "Conteos con estados controlados y aprobación de diferencias por un administrador.", soon: true },
  { icon: "chart", title: "Informes", text: "Valorización, consumo y compras sugeridas, exportables a PDF y Excel.", soon: true },
];

const STEPS = [
  { title: "Configure su organización", text: "Un administrador comercial define bodegas, categorías y el catálogo de productos con SKU únicos." },
  { title: "Registre los movimientos", text: "El equipo operativo registra entradas y salidas. Bastión valida existencias antes de confirmar cada salida." },
  { title: "Ajuste con autorización", text: "Los ajustes requieren un administrador y una justificación escrita. Las correcciones quedan como reversiones auditables." },
  { title: "Consulte y decida", text: "Existencias, valor del inventario e historial completo, filtrados por bodega o producto." },
];

const SECURITY: { icon: IconName; title: string; text: string }[] = [
  { icon: "users", title: "Tres roles, mínimo privilegio", text: "Super administrador de la plataforma, administrador comercial y trabajador. Nadie puede elevar sus propios permisos." },
  { icon: "shield", title: "Permisos verificados en el servidor", text: "El rol, el estado de la cuenta y la organización se consultan en la base de datos en cada petición; no se confía en el navegador." },
  { icon: "building", title: "Aislamiento por organización", text: "Cada empresa ve solo sus datos. La base de datos impide relacionar registros de organizaciones distintas." },
  { icon: "lock", title: "Credenciales protegidas", text: "Contraseñas con Argon2id, sesiones con expiración y cierre de sesión que invalida los accesos emitidos." },
];

function LedgerPreview() {
  const rows = [
    { k: "Entrada", ref: "OC-0142", q: "+120", bal: "340", tone: "text-accent" },
    { k: "Salida", ref: "Entrega taller", q: "−36", bal: "304", tone: "text-on-ink" },
    { k: "Ajuste", ref: "Revisión mensual", q: "−4", bal: "300", tone: "text-[#f5c26b]" },
    { k: "Reversión", ref: "de #1187", q: "+36", bal: "336", tone: "text-on-ink-2" },
  ];
  return (
    <figure className="relative w-full max-w-md rounded-2xl border border-white/10 bg-ink-2/80 p-1.5 shadow-2xl shadow-black/40" aria-label="Ejemplo ilustrativo del historial de movimientos">
      <div className="rounded-xl border border-white/5 bg-ink p-5">
        <div className="flex items-center justify-between">
          <div>
            <p className="text-xs font-semibold uppercase tracking-[0.14em] text-on-ink-2">Producto</p>
            <p className="mt-1 font-semibold text-on-ink">Válvula de paso ½″ <span className="font-mono text-xs text-on-ink-2">VLV-012</span></p>
          </div>
          <span className="rounded-md bg-accent/15 px-2 py-1 font-mono text-xs text-accent">BOD-PRINCIPAL</span>
        </div>
        <table className="mt-5 w-full text-sm">
          <thead>
            <tr className="text-left text-xs text-on-ink-2">
              <th className="pb-2 font-medium">Movimiento</th>
              <th className="pb-2 text-right font-medium">Cant.</th>
              <th className="pb-2 text-right font-medium">Saldo</th>
            </tr>
          </thead>
          <tbody className="divide-y divide-white/5">
            {rows.map((r) => (
              <tr key={r.ref}>
                <td className="py-2.5">
                  <span className={`font-semibold ${r.tone}`}>{r.k}</span>
                  <span className="block text-xs text-on-ink-2">{r.ref}</span>
                </td>
                <td className={`py-2.5 text-right font-mono tnum ${r.tone}`}>{r.q}</td>
                <td className="py-2.5 text-right font-mono tnum text-on-ink">{r.bal}</td>
              </tr>
            ))}
          </tbody>
        </table>
      </div>
      <figcaption className="px-3 py-2 text-[11px] text-on-ink-2">Ejemplo ilustrativo con datos ficticios.</figcaption>
    </figure>
  );
}

export function LandingPage() {
  const [open, setOpen] = useState(false);
  return (
    <div className="min-h-screen bg-bg">
      <a href="#contenido" className="sr-only focus:not-sr-only focus:fixed focus:left-4 focus:top-4 focus:z-50 focus:rounded focus:bg-surface focus:px-3 focus:py-2">
        Saltar al contenido
      </a>
      <header className="on-ink sticky top-0 z-40 border-b border-white/10 bg-ink/95 backdrop-blur">
        <nav className="mx-auto flex h-16 max-w-6xl items-center justify-between px-4 sm:px-6" aria-label="Principal">
          <Link to="/" aria-label="Bastión, inicio"><Logo tone="light" /></Link>
          <ul className="hidden items-center gap-8 md:flex">
            {NAV.map((n) => (
              <li key={n.href}><a href={n.href} className="text-sm font-medium text-on-ink-2 transition-colors hover:text-white">{n.label}</a></li>
            ))}
          </ul>
          <div className="flex items-center gap-2">
            <Link to="/login" className="hidden min-h-10 items-center rounded-b bg-accent px-4 text-sm font-bold text-ink transition-colors hover:bg-[#5fe0cb] sm:inline-flex">
              Iniciar sesión
            </Link>
            <button type="button" className="grid h-10 w-10 place-items-center rounded-b text-on-ink md:hidden" aria-expanded={open} aria-controls="menu-movil" aria-label={open ? "Cerrar menú" : "Abrir menú"} onClick={() => setOpen((o) => !o)}>
              <Icon name={open ? "close" : "menu"} />
            </button>
          </div>
        </nav>
        {open && (
          <div id="menu-movil" className="border-t border-white/10 px-4 pb-4 md:hidden">
            <ul className="py-2">
              {NAV.map((n) => (
                <li key={n.href}><a href={n.href} onClick={() => setOpen(false)} className="block py-3 font-medium text-on-ink">{n.label}</a></li>
              ))}
            </ul>
            <Link to="/login" className="flex min-h-11 items-center justify-center rounded-b bg-accent font-bold text-ink">Iniciar sesión</Link>
          </div>
        )}
      </header>

      <main id="contenido">
        <section className="on-ink relative overflow-hidden bg-ink">
          <div aria-hidden="true" className="pointer-events-none absolute inset-0 opacity-[0.07]" style={{ backgroundImage: "linear-gradient(var(--b-on-ink) 1px, transparent 1px), linear-gradient(90deg, var(--b-on-ink) 1px, transparent 1px)", backgroundSize: "48px 48px" }} />
          <div className="relative mx-auto grid max-w-6xl items-center gap-12 px-4 py-20 sm:px-6 lg:grid-cols-[1.1fr_1fr] lg:py-28">
            <div>
              <p className="inline-flex items-center gap-2 rounded-full border border-accent/30 bg-accent/10 px-3 py-1 text-xs font-semibold uppercase tracking-[0.14em] text-accent">
                <Icon name="shield" className="h-3.5 w-3.5" /> Inventario empresarial
              </p>
              <h1 className="mt-6 text-4xl font-extrabold leading-[1.05] tracking-[-0.035em] text-white sm:text-5xl lg:text-6xl">
                Cada existencia,<br />bajo control.
              </h1>
              <p className="mt-6 max-w-xl text-lg leading-relaxed text-on-ink-2">
                Bastión registra cada movimiento de su inventario con trazabilidad completa, protege los saldos frente a errores y operaciones simultáneas, y separa con claridad quién administra y quién opera.
              </p>
              <div className="mt-9 flex flex-col gap-3 sm:flex-row">
                <Link to="/login" className="inline-flex min-h-12 items-center justify-center gap-2 rounded-b bg-accent px-6 font-bold text-ink transition-colors hover:bg-[#5fe0cb]">
                  Ingresar a Bastión <Icon name="arrowRight" className="h-4 w-4" />
                </Link>
                <a href="#flujo" className="inline-flex min-h-12 items-center justify-center rounded-b border border-white/20 px-6 font-semibold text-on-ink transition-colors hover:border-white/40 hover:bg-white/5">
                  Cómo funciona
                </a>
              </div>
            </div>
            <div className="flex justify-center lg:justify-end"><LedgerPreview /></div>
          </div>
        </section>

        <section id="producto" className="scroll-mt-16 border-b border-line bg-surface">
          <div className="mx-auto max-w-6xl px-4 py-20 sm:px-6">
            <div className="grid gap-10 lg:grid-cols-[1fr_1.6fr]">
              <div>
                <h2 className="text-3xl font-extrabold tracking-[-0.025em]">Una estructura clara para su inventario</h2>
                <p className="mt-4 text-text-2 leading-relaxed">
                  Bastión organiza la información en cuatro niveles. Cada nivel pertenece a una sola organización y ningún dato cruza esa frontera.
                </p>
              </div>
              <ol className="grid gap-px overflow-hidden rounded-xl border border-line bg-line sm:grid-cols-2">
                {[
                  ["Organización", "La empresa y sus usuarios, con roles asignados."],
                  ["Bodegas", "Ubicaciones físicas donde se guarda el inventario."],
                  ["Productos", "Catálogo con SKU, código de barras, unidad y stock mínimo."],
                  ["Movimientos", "El historial que explica cada saldo, de forma inmutable."],
                ].map(([t, d], i) => (
                  <li key={t} className="bg-surface p-6">
                    <span className="font-mono text-xs font-semibold text-accent-strong">0{i + 1}</span>
                    <h3 className="mt-2 font-bold">{t}</h3>
                    <p className="mt-1 text-sm text-text-2">{d}</p>
                  </li>
                ))}
              </ol>
            </div>

            <div className="mt-20">
              <h2 className="text-3xl font-extrabold tracking-[-0.025em]">Lo que Bastión le resuelve</h2>
              <ul className="mt-10 grid gap-x-12 gap-y-10 md:grid-cols-2 lg:grid-cols-3">
                {BENEFITS.map((b) => (
                  <li key={b.title} className="flex gap-4">
                    <span className="grid h-10 w-10 shrink-0 place-items-center rounded-lg bg-accent-soft text-accent-strong"><Icon name={b.icon} /></span>
                    <div>
                      <h3 className="flex flex-wrap items-center gap-2 font-bold">
                        {b.title}
                        {b.soon && <span className="rounded-full border border-line-strong px-2 py-0.5 text-[11px] font-semibold text-text-2">Próxima fase</span>}
                      </h3>
                      <p className="mt-1.5 text-sm leading-relaxed text-text-2">{b.text}</p>
                    </div>
                  </li>
                ))}
              </ul>
            </div>
          </div>
        </section>

        <section id="flujo" className="scroll-mt-16">
          <div className="mx-auto max-w-6xl px-4 py-20 sm:px-6">
            <h2 className="text-3xl font-extrabold tracking-[-0.025em]">Flujo de trabajo</h2>
            <p className="mt-3 max-w-2xl text-text-2">Del catálogo al informe, cada paso deja un registro verificable.</p>
            <ol className="mt-12 grid gap-8 md:grid-cols-4">
              {STEPS.map((s, i) => (
                <li key={s.title} className="relative border-t-2 border-ink pt-5">
                  <span className="font-mono text-sm font-bold text-accent-strong">Paso {i + 1}</span>
                  <h3 className="mt-2 text-lg font-bold leading-snug">{s.title}</h3>
                  <p className="mt-2 text-sm leading-relaxed text-text-2">{s.text}</p>
                </li>
              ))}
            </ol>
          </div>
        </section>

        <section id="seguridad" className="on-ink scroll-mt-16 bg-ink-2">
          <div className="mx-auto grid max-w-6xl gap-12 px-4 py-20 sm:px-6 lg:grid-cols-[1fr_1.5fr]">
            <div>
              <h2 className="text-3xl font-extrabold tracking-[-0.025em] text-white">Seguridad y control de acceso</h2>
              <p className="mt-4 leading-relaxed text-on-ink-2">
                Los controles descritos aquí están implementados y cubiertos por pruebas automatizadas en esta versión. Las capacidades en desarrollo se indican como próxima fase.
              </p>
            </div>
            <ul className="grid gap-8 sm:grid-cols-2">
              {SECURITY.map((s) => (
                <li key={s.title}>
                  <Icon name={s.icon} className="h-6 w-6 text-accent" />
                  <h3 className="mt-3 font-bold text-white">{s.title}</h3>
                  <p className="mt-1.5 text-sm leading-relaxed text-on-ink-2">{s.text}</p>
                </li>
              ))}
            </ul>
          </div>
        </section>

        <section className="border-b border-line bg-surface">
          <div className="mx-auto flex max-w-6xl flex-col items-start justify-between gap-6 px-4 py-14 sm:px-6 md:flex-row md:items-center">
            <div>
              <h2 className="text-2xl font-extrabold tracking-[-0.02em]">¿Ya tiene una cuenta en Bastión?</h2>
              <p className="mt-1 text-text-2">Las cuentas las crea el administrador de su organización.</p>
            </div>
            <Link to="/login" className="inline-flex min-h-12 items-center gap-2 rounded-b bg-ink px-6 font-bold text-on-ink transition-colors hover:bg-ink-3">
              Iniciar sesión <Icon name="arrowRight" className="h-4 w-4" />
            </Link>
          </div>
        </section>
      </main>

      <footer className="bg-bg">
        <div className="mx-auto flex max-w-6xl flex-col gap-6 px-4 py-10 text-sm text-text-2 sm:px-6 md:flex-row md:items-center md:justify-between">
          <div className="flex items-center gap-4">
            <Logo />
            <span className="hidden h-5 w-px bg-line-strong sm:block" />
            <span className="hidden sm:inline">Control de inventario con trazabilidad</span>
          </div>
          <nav aria-label="Pie de página">
            <ul className="flex flex-wrap gap-x-6 gap-y-2">
              {NAV.map((n) => <li key={n.href}><a className="hover:text-text" href={n.href}>{n.label}</a></li>)}
              <li><Link className="hover:text-text" to="/login">Iniciar sesión</Link></li>
            </ul>
          </nav>
          <p>© {new Date().getFullYear()} Bastión</p>
        </div>
      </footer>
    </div>
  );
}
