import { Link } from "react-router-dom";
import { Icon, type IconName } from "@/components/Icon";
import { Badge, EmptyState, ErrorState, LoadingState, PageHeader, Panel } from "@/components/ui";
import { api } from "@/lib/api";
import { useAuth } from "@/lib/auth";
import { fmtDate, fmtMoney, fmtQty } from "@/lib/format";
import type { Movement, MovementKind, StockRow, UserRow } from "@/lib/types";
import { useResource } from "@/lib/useResource";

const KIND_LABEL: Record<MovementKind, string> = { ENTRADA: "Entrada", SALIDA: "Salida", AJUSTE: "Ajuste", REVERSION: "Reversión" };
const KIND_TONE: Record<MovementKind, "ok" | "neutral" | "warn" | "danger"> = { ENTRADA: "ok", SALIDA: "neutral", AJUSTE: "warn", REVERSION: "danger" };

type Action = { to: string; label: string; hint: string; icon: IconName };

function ActionGrid({ actions }: { actions: Action[] }) {
  return (
    <ul className="grid gap-3 sm:grid-cols-2 lg:grid-cols-4">
      {actions.map((a) => (
        <li key={a.to + a.label}>
          <Link to={a.to} className="group flex h-full items-start gap-3 rounded-xl border border-line bg-surface p-4 transition-colors hover:border-accent-strong/50 hover:bg-accent-soft/40">
            <span className="grid h-10 w-10 shrink-0 place-items-center rounded-lg bg-ink text-accent"><Icon name={a.icon} /></span>
            <span>
              <span className="block font-bold text-text">{a.label}</span>
              <span className="mt-0.5 block text-sm text-text-2">{a.hint}</span>
            </span>
          </Link>
        </li>
      ))}
    </ul>
  );
}

function LowStock({ rows }: { rows: StockRow[] }) {
  const low = rows.filter((r) => r.below_min);
  return (
    <Panel>
      <h2 className="flex items-center justify-between border-b border-line px-5 py-4 font-bold">
        Bajo el stock mínimo {low.length > 0 && <Badge tone="warn">{low.length}</Badge>}
      </h2>
      {low.length === 0 ? (
        <p className="px-5 py-8 text-sm text-text-2">Ningún producto está por debajo de su stock mínimo.</p>
      ) : (
        <ul className="divide-y divide-line">
          {low.slice(0, 8).map((r) => (
            <li key={`${r.product_id}-${r.warehouse_id}`} className="flex items-center justify-between gap-4 px-5 py-3 text-sm">
              <span className="min-w-0 truncate"><span className="font-semibold">{r.product_name}</span> <span className="font-mono text-xs text-text-2">{r.warehouse_code}</span></span>
              <span className="shrink-0 tnum text-warn">{fmtQty(r.quantity)} / {fmtQty(r.min_stock)}</span>
            </li>
          ))}
        </ul>
      )}
    </Panel>
  );
}

function RecentMovements({ rows, title }: { rows: Movement[]; title: string }) {
  return (
    <Panel>
      <h2 className="flex items-center justify-between border-b border-line px-5 py-4 font-bold">
        {title}
        <Link to="/app/movimientos" className="text-sm font-semibold text-accent-strong hover:underline">Ver historial</Link>
      </h2>
      {rows.length === 0 ? (
        <EmptyState icon="history" title="Sin movimientos todavía" />
      ) : (
        <ul className="divide-y divide-line">
          {rows.slice(0, 8).map((m) => (
            <li key={m.id} className="flex items-center justify-between gap-4 px-5 py-3 text-sm">
              <span className="flex min-w-0 items-center gap-3">
                <Badge tone={KIND_TONE[m.kind]}>{KIND_LABEL[m.kind]}</Badge>
                <span className="min-w-0 truncate text-text-2">{m.user_name} · {fmtDate(m.created_at)}</span>
              </span>
              <span className="shrink-0 font-semibold tnum">{Number(m.quantity) > 0 ? "+" : ""}{fmtQty(m.quantity)}</span>
            </li>
          ))}
        </ul>
      )}
    </Panel>
  );
}

function AdminDashboard({ stock, movements }: { stock: StockRow[]; movements: Movement[] }) {
  const team = useResource((signal) => api<UserRow[]>("/users", { signal }), []);
  const value = stock.reduce((s, r) => s + Number(r.total_value ?? 0), 0);
  const today = new Date().toDateString();
  const todayCount = movements.filter((m) => new Date(m.created_at).toDateString() === today).length;
  const activeWorkers = team.data?.filter((u) => u.role === "TRABAJADOR" && u.active).length;

  return (
    <>
      <dl className="grid gap-px overflow-hidden rounded-xl border border-line bg-line sm:grid-cols-2 lg:grid-cols-4">
        {[
          ["Valor del inventario", fmtMoney(value), "al costo promedio"],
          ["Bajo stock mínimo", fmtQty(stock.filter((r) => r.below_min).length), "saldos por reponer"],
          ["Movimientos hoy", fmtQty(todayCount), "de toda la organización"],
          ["Trabajadores activos", activeWorkers === undefined ? "—" : fmtQty(activeWorkers), "en su equipo"],
        ].map(([label, val, hint]) => (
          <div key={label} className="bg-surface p-5">
            <dt className="text-sm text-text-2">{label}</dt>
            <dd className="mt-1 text-2xl font-extrabold tnum">{val}</dd>
            <dd className="text-xs text-text-2">{hint}</dd>
          </div>
        ))}
      </dl>
      <h2 className="mb-3 mt-8 text-sm font-bold uppercase tracking-[0.12em] text-text-2">Gestión</h2>
      <ActionGrid actions={[
        { to: "/app/productos", label: "Nuevo producto", hint: "Amplíe el catálogo", icon: "package" },
        { to: "/app/bodegas", label: "Bodegas", hint: "Cree o desactive ubicaciones", icon: "warehouse" },
        { to: "/app/usuarios", label: "Equipo", hint: "Agregue trabajadores", icon: "users" },
        { to: "/app/movimientos?tipo=AJUSTE", label: "Registrar ajuste", hint: "Corrija existencias", icon: "ajuste" },
      ]} />
      <div className="mt-8 grid gap-6 lg:grid-cols-2">
        <RecentMovements rows={movements} title="Actividad reciente del equipo" />
        <LowStock rows={stock} />
      </div>
    </>
  );
}

function WorkerDashboard({ stock, movements }: { stock: StockRow[]; movements: Movement[] }) {
  return (
    <>
      <ActionGrid actions={[
        { to: "/app/movimientos?tipo=ENTRADA", label: "Registrar entrada", hint: "Mercancía que llega", icon: "entrada" },
        { to: "/app/movimientos?tipo=SALIDA", label: "Registrar salida", hint: "Entregas y despachos", icon: "salida" },
        { to: "/app/inventario", label: "Consultar existencias", hint: "Saldos por bodega", icon: "boxes" },
        { to: "/app/productos", label: "Buscar producto", hint: "Por nombre, SKU o código", icon: "search" },
      ]} />
      <div className="mt-8 grid gap-6 lg:grid-cols-2">
        <LowStock rows={stock} />
        <RecentMovements rows={movements} title="Últimos movimientos" />
      </div>
    </>
  );
}

export function DashboardPage() {
  const { me, hasRole } = useAuth();
  const isAdmin = hasRole("ADMINISTRADOR_COMERCIAL");
  const stock = useResource((signal) => api<StockRow[]>("/inventory", { signal }), []);
  const movements = useResource((signal) => api<Movement[]>("/movements?limit=50", { signal }), []);
  const first = me?.full_name.split(" ")[0] ?? "";

  return (
    <>
      <PageHeader
        admin={isAdmin}
        title={isAdmin ? `Panel de administración` : `Hola, ${first}`}
        description={isAdmin ? `${me?.organization_name ?? ""} · Hola, ${first}.` : me?.organization_name ?? undefined}
      />
      {stock.loading || movements.loading ? <LoadingState /> : stock.error || movements.error ? (
        <ErrorState message={stock.error ?? movements.error ?? ""} onRetry={() => { stock.reload(); movements.reload(); }} />
      ) : isAdmin ? (
        <AdminDashboard stock={stock.data ?? []} movements={movements.data ?? []} />
      ) : (
        <WorkerDashboard stock={stock.data ?? []} movements={movements.data ?? []} />
      )}
    </>
  );
}
