import { Link } from "react-router-dom";
import { Icon } from "@/components/Icon";
import { ErrorState, LoadingState, PageHeader, Panel } from "@/components/ui";
import { api } from "@/lib/api";
import { useAuth } from "@/lib/auth";
import { fmtMoney, fmtQty } from "@/lib/format";
import type { StockRow } from "@/lib/types";
import { useResource } from "@/lib/useResource";

export function DashboardPage() {
  const { me, hasRole } = useAuth();
  const { data, error, loading, reload } = useResource((signal) => api<StockRow[]>("/inventory", { signal }), []);

  const low = data?.filter((r) => r.below_min) ?? [];
  const value = data?.reduce((s, r) => s + Number(r.total_value), 0) ?? 0;

  return (
    <>
      <PageHeader title={`Hola, ${me?.full_name.split(" ")[0] ?? ""}`} description={me?.organization_name ?? undefined} />
      {loading ? <LoadingState /> : error ? <ErrorState message={error} onRetry={reload} /> : (
        <>
          <dl className="grid gap-px overflow-hidden rounded-xl border border-line bg-line sm:grid-cols-3">
            <div className="bg-surface p-5"><dt className="text-sm text-text-2">Saldos registrados</dt><dd className="mt-1 text-2xl font-extrabold tnum">{fmtQty(data?.length ?? 0)}</dd></div>
            <div className="bg-surface p-5"><dt className="text-sm text-text-2">Bajo stock mínimo</dt><dd className={`mt-1 text-2xl font-extrabold tnum ${low.length ? "text-warn" : ""}`}>{fmtQty(low.length)}</dd></div>
            {hasRole("ADMINISTRADOR_COMERCIAL") && <div className="bg-surface p-5"><dt className="text-sm text-text-2">Valor al costo promedio</dt><dd className="mt-1 text-2xl font-extrabold tnum">{fmtMoney(value)}</dd></div>}
          </dl>
          <div className="mt-8 grid gap-6 lg:grid-cols-[1.4fr_1fr]">
            <Panel>
              <h2 className="border-b border-line px-5 py-4 font-bold">Productos bajo el mínimo</h2>
              {low.length === 0 ? (
                <p className="px-5 py-8 text-sm text-text-2">Ningún producto está por debajo de su stock mínimo.</p>
              ) : (
                <ul className="divide-y divide-line">
                  {low.slice(0, 8).map((r) => (
                    <li key={`${r.product_id}-${r.warehouse_id}`} className="flex items-center justify-between px-5 py-3 text-sm">
                      <span><span className="font-semibold">{r.product_name}</span> <span className="font-mono text-xs text-text-2">{r.warehouse_code}</span></span>
                      <span className="tnum text-warn">{fmtQty(r.quantity)} / {fmtQty(r.min_stock)}</span>
                    </li>
                  ))}
                </ul>
              )}
            </Panel>
            <Panel className="p-5">
              <h2 className="font-bold">Acciones rápidas</h2>
              <ul className="mt-3 space-y-1">
                {[
                  { to: "/app/movimientos?tipo=ENTRADA", label: "Registrar entrada", icon: "entrada" as const },
                  { to: "/app/movimientos?tipo=SALIDA", label: "Registrar salida", icon: "salida" as const },
                  { to: "/app/inventario", label: "Consultar existencias", icon: "boxes" as const },
                ].map((a) => (
                  <li key={a.to}><Link to={a.to} className="flex min-h-11 items-center gap-3 rounded-b px-2 text-sm font-semibold hover:bg-surface-2"><Icon name={a.icon} className="h-[18px] w-[18px] text-accent-strong" />{a.label}</Link></li>
                ))}
              </ul>
            </Panel>
          </div>
        </>
      )}
    </>
  );
}
