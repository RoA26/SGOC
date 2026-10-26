import { useState } from "react";
import { Table, td, th } from "@/components/Table";
import { Badge, EmptyState, ErrorState, Field, LoadingState, PageHeader, Panel, Select } from "@/components/ui";
import { api } from "@/lib/api";
import { useAuth } from "@/lib/auth";
import { fmtMoney, fmtQty } from "@/lib/format";
import type { StockRow, Warehouse } from "@/lib/types";
import { useResource } from "@/lib/useResource";

export function InventoryPage() {
  const { hasRole } = useAuth();
  const isAdmin = hasRole("ADMINISTRADOR_COMERCIAL");
  const [warehouse, setWarehouse] = useState("");
  const [lowOnly, setLowOnly] = useState(false);
  const whs = useResource((signal) => api<Warehouse[]>("/warehouses", { signal }), []);
  const qs = new URLSearchParams();
  if (warehouse) qs.set("warehouse_id", warehouse);
  if (lowOnly) qs.set("below_min", "true");
  const { data, error, loading, reload } = useResource((signal) => api<StockRow[]>(`/inventory?${qs}`, { signal }), [warehouse, lowOnly]);

  return (
    <>
      <PageHeader title="Inventario" description="Existencias por producto y bodega." />
      <Panel>
        <div className="flex flex-col gap-4 border-b border-line p-4 sm:flex-row sm:items-end">
          <div className="sm:w-64">
            <Field label="Bodega">{(id) => (
              <Select id={id} value={warehouse} onChange={(e) => setWarehouse(e.target.value)}>
                <option value="">Todas</option>
                {whs.data?.map((w) => <option key={w.id} value={w.id}>{w.code} · {w.name}</option>)}
              </Select>
            )}</Field>
          </div>
          <label className="flex min-h-10 items-center gap-2 text-sm font-medium">
            <input type="checkbox" className="h-4 w-4 accent-[var(--b-accent-strong)]" checked={lowOnly} onChange={(e) => setLowOnly(e.target.checked)} />
            Solo bajo stock mínimo
          </label>
        </div>
        {loading ? <LoadingState /> : error ? <ErrorState message={error} onRetry={reload} /> : !data?.length ? (
          <EmptyState icon="boxes" title="Sin existencias para mostrar">Las existencias aparecen al registrar la primera entrada de un producto.</EmptyState>
        ) : (
          <Table caption="Existencias" head={<tr><th className={th}>Producto</th><th className={th}>Bodega</th><th className={`${th} text-right`}>Cantidad</th><th className={`${th} text-right`}>Mínimo</th>{isAdmin && <><th className={`${th} text-right`}>Costo prom.</th><th className={`${th} text-right`}>Valor</th></>}<th className={th}>Estado</th></tr>}>
            {data.map((r) => (
              <tr key={`${r.product_id}-${r.warehouse_id}`}>
                <td className={td}><span className="font-semibold">{r.product_name}</span><span className="block font-mono text-xs text-text-2">{r.sku}</span></td>
                <td className={`${td} font-mono text-xs`}>{r.warehouse_code}</td>
                <td className={`${td} text-right tnum font-semibold`}>{fmtQty(r.quantity)} <span className="text-xs font-normal text-text-2">{r.unit}</span></td>
                <td className={`${td} text-right tnum text-text-2`}>{fmtQty(r.min_stock)}</td>
                {isAdmin && <><td className={`${td} text-right tnum`}>{r.avg_cost === null ? "—" : fmtMoney(r.avg_cost)}</td><td className={`${td} text-right tnum`}>{r.total_value === null ? "—" : fmtMoney(r.total_value)}</td></>}
                <td className={td}>{r.below_min ? <Badge tone="warn">Bajo mínimo</Badge> : <Badge tone="ok">Normal</Badge>}</td>
              </tr>
            ))}
          </Table>
        )}
      </Panel>
    </>
  );
}
