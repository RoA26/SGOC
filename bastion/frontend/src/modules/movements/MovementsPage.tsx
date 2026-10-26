import { useMemo, useRef, useState, type FormEvent } from "react";
import { useSearchParams } from "react-router-dom";
import { Icon, type IconName } from "@/components/Icon";
import { Table, td, th } from "@/components/Table";
import { Alert, Badge, Button, ConfirmDialog, EmptyState, ErrorState, Field, Input, LoadingState, PageHeader, Panel, Select } from "@/components/ui";
import { api, errorMessage } from "@/lib/api";
import { useAuth } from "@/lib/auth";
import { fmtDate, fmtMoney, fmtQty, newIdempotencyKey } from "@/lib/format";
import { serverErrors, validate, type Errors } from "@/lib/forms";
import type { Movement, MovementKind, Product, Warehouse } from "@/lib/types";
import { useResource } from "@/lib/useResource";
import { movementSchema, toPayload } from "./schema";

type FormKind = "ENTRADA" | "SALIDA" | "AJUSTE";
const KIND: Record<MovementKind, { label: string; icon: IconName; tone: "ok" | "neutral" | "warn" | "danger" }> = {
  ENTRADA: { label: "Entrada", icon: "entrada", tone: "ok" },
  SALIDA: { label: "Salida", icon: "salida", tone: "neutral" },
  AJUSTE: { label: "Ajuste", icon: "ajuste", tone: "warn" },
  REVERSION: { label: "Reversión", icon: "undo", tone: "danger" },
};

export function MovementForm({ products, warehouses, onDone }: { products: Product[]; warehouses: Warehouse[]; onDone: () => void }) {
  const { hasRole } = useAuth();
  const isAdmin = hasRole("ADMINISTRADOR_COMERCIAL");
  const [params] = useSearchParams();
  const initial = (params.get("tipo") as FormKind | null) ?? "ENTRADA";
  const [kind, setKind] = useState<FormKind>(initial === "AJUSTE" && !isAdmin ? "ENTRADA" : initial);
  const [errors, setErrors] = useState<Errors>({});
  const [busy, setBusy] = useState(false);
  const [done, setDone] = useState<string | null>(null);
  // La clave se conserva entre reintentos del mismo envío y se renueva tras un registro exitoso.
  const key = useRef(newIdempotencyKey());
  const kinds: FormKind[] = isAdmin ? ["ENTRADA", "SALIDA", "AJUSTE"] : ["ENTRADA", "SALIDA"];

  async function onSubmit(e: FormEvent<HTMLFormElement>) {
    e.preventDefault();
    const form = e.currentTarget;
    const fd = Object.fromEntries(new FormData(form)) as Record<string, string>;
    const r = validate(movementSchema, { ...fd, kind });
    setDone(null);
    if (!r.ok) return setErrors(r.errors);
    setErrors({});
    setBusy(true);
    try {
      const m = await api<Movement>("/movements", { method: "POST", body: toPayload(r.value), headers: { "Idempotency-Key": key.current } });
      key.current = newIdempotencyKey();
      form.reset();
      setDone(`${KIND[m.kind].label} registrada. Saldo resultante: ${fmtQty(m.balance_after)}.`);
      onDone();
    } catch (err) {
      setErrors(serverErrors(err));
    } finally {
      setBusy(false);
    }
  }

  return (
    <form onSubmit={onSubmit} noValidate className="space-y-5 p-5" onChange={() => { key.current = newIdempotencyKey(); }}>
      <fieldset>
        <legend className="mb-2 text-sm font-semibold">Tipo de movimiento</legend>
        <div className="inline-flex rounded-b border border-line-strong p-0.5" role="radiogroup">
          {kinds.map((k) => (
            <button key={k} type="button" role="radio" aria-checked={kind === k} onClick={() => { setKind(k); setErrors({}); }}
              className={`inline-flex min-h-9 items-center gap-1.5 rounded-[7px] px-3 text-sm font-semibold transition-colors ${kind === k ? "bg-ink text-on-ink" : "text-text-2 hover:text-text"}`}>
              <Icon name={KIND[k].icon} className="h-4 w-4" />{KIND[k].label}
            </button>
          ))}
        </div>
      </fieldset>
      {done && <Alert tone="success">{done}</Alert>}
      {errors._ && <Alert tone="error">{errors._}</Alert>}
      <div className="grid gap-4 sm:grid-cols-2">
        <Field label="Producto" error={errors.product_id}>{(id, d) => (
          <Select id={id} name="product_id" defaultValue="" aria-describedby={d} invalid={!!errors.product_id}>
            <option value="" disabled>Seleccione…</option>
            {products.map((p) => <option key={p.id} value={p.id}>{p.name} · {p.sku}</option>)}
          </Select>
        )}</Field>
        <Field label="Bodega" error={errors.warehouse_id}>{(id, d) => (
          <Select id={id} name="warehouse_id" defaultValue={warehouses.length === 1 ? String(warehouses[0]?.id) : ""} aria-describedby={d} invalid={!!errors.warehouse_id}>
            <option value="" disabled>Seleccione…</option>
            {warehouses.map((w) => <option key={w.id} value={w.id}>{w.code} · {w.name}</option>)}
          </Select>
        )}</Field>
        <Field label="Cantidad" error={errors.quantity} hint={kind === "AJUSTE" ? "Use signo negativo para restar (p. ej. -3)." : undefined}>{(id, d) => (
          <Input id={id} name="quantity" inputMode="decimal" aria-describedby={d} invalid={!!errors.quantity} className="tnum" />
        )}</Field>
        {kind === "ENTRADA" && (
          <Field label="Costo unitario (opcional)" error={errors.unit_cost} hint="Sin costo, se conserva el costo promedio actual.">{(id, d) => (
            <Input id={id} name="unit_cost" inputMode="decimal" aria-describedby={d} invalid={!!errors.unit_cost} className="tnum" />
          )}</Field>
        )}
        <Field label="Referencia" error={errors.reference}>{(id, d) => <Input id={id} name="reference" maxLength={120} aria-describedby={d} placeholder="Factura, orden, entrega…" />}</Field>
        <Field label={kind === "AJUSTE" ? "Justificación" : "Notas"} error={errors.notes}>{(id, d) => <Input id={id} name="notes" maxLength={500} aria-describedby={d} invalid={!!errors.notes} />}</Field>
      </div>
      <Button type="submit" loading={busy} icon={KIND[kind].icon}>Registrar {KIND[kind].label.toLowerCase()}</Button>
    </form>
  );
}

export function MovementsPage() {
  const { hasRole } = useAuth();
  const isAdmin = hasRole("ADMINISTRADOR_COMERCIAL");
  const products = useResource((signal) => api<Product[]>("/products", { signal }), []);
  const warehouses = useResource((signal) => api<Warehouse[]>("/warehouses", { signal }), []);
  const history = useResource((signal) => api<Movement[]>("/movements?limit=100", { signal }), []);
  const [target, setTarget] = useState<Movement | null>(null);
  const [reason, setReason] = useState("");
  const [revBusy, setRevBusy] = useState(false);
  const [revMsg, setRevMsg] = useState<{ tone: "success" | "error"; text: string } | null>(null);

  const productName = useMemo(() => new Map(products.data?.map((p) => [p.id, p.name]) ?? []), [products.data]);
  const whCode = useMemo(() => new Map(warehouses.data?.map((w) => [w.id, w.code]) ?? []), [warehouses.data]);
  const reversed = new Set(history.data?.map((m) => m.reverses_id).filter(Boolean));

  async function reverse() {
    if (!target || reason.trim().length < 3) return;
    setRevBusy(true);
    try {
      await api(`/movements/${target.id}/reversal`, { method: "POST", body: { notes: reason.trim() } });
      setRevMsg({ tone: "success", text: `Movimiento #${target.id} revertido.` });
      history.reload();
    } catch (e) {
      setRevMsg({ tone: "error", text: errorMessage(e) });
    } finally {
      setRevBusy(false);
      setTarget(null);
      setReason("");
    }
  }

  const prods = products.data;
  const whs = warehouses.data;
  return (
    <>
      <PageHeader title="Movimientos" description={isAdmin ? "Registre entradas, salidas y ajustes, y revierta movimientos con error." : "Registre entradas y salidas. Los ajustes y las correcciones los hace el administrador."} />
      <Panel className="mb-8">
        <h2 className="border-b border-line px-5 py-4 font-bold">Nuevo movimiento</h2>
        {products.error || warehouses.error ? <ErrorState message={products.error ?? warehouses.error ?? ""} onRetry={() => { products.reload(); warehouses.reload(); }} />
          : !prods || !whs ? <LoadingState />
          : !prods.length || !whs.length ? (
            <EmptyState icon="package" title="Faltan datos maestros">Para registrar movimientos se necesita al menos un producto y una bodega. {isAdmin ? "Créelos en Productos y Bodegas." : "Solicítelos a su administrador."}</EmptyState>
          ) : <MovementForm products={prods} warehouses={whs} onDone={history.reload} />}
      </Panel>

      <Panel>
        <h2 className="border-b border-line px-5 py-4 font-bold">Historial</h2>
        {revMsg && <div className="p-4 pb-0"><Alert tone={revMsg.tone}>{revMsg.text}</Alert></div>}
        {history.loading ? <LoadingState /> : history.error ? <ErrorState message={history.error} onRetry={history.reload} /> : !history.data?.length ? (
          <EmptyState icon="history" title="Aún no hay movimientos">Los movimientos registrados aparecerán aquí en orden cronológico.</EmptyState>
        ) : (
          <Table caption="Historial de movimientos" head={<tr><th className={th}>#</th><th className={th}>Fecha</th><th className={th}>Tipo</th><th className={th}>Producto</th><th className={`${th} text-right`}>Cantidad</th><th className={`${th} text-right`}>Saldo</th><th className={th}>Registró</th><th className={th}>Referencia</th>{isAdmin && <th className={th}><span className="sr-only">Acciones</span></th>}</tr>}>
            {history.data.map((m) => (
              <tr key={m.id}>
                <td className={`${td} font-mono text-xs text-text-2`}>{m.id}</td>
                <td className={`${td} whitespace-nowrap text-text-2`}>{fmtDate(m.created_at)}</td>
                <td className={td}><Badge tone={KIND[m.kind].tone}>{KIND[m.kind].label}</Badge></td>
                <td className={td}>{productName.get(m.product_id) ?? `#${m.product_id}`} <span className="font-mono text-xs text-text-2">{whCode.get(m.warehouse_id)}</span></td>
                <td className={`${td} text-right tnum font-semibold`}>{Number(m.quantity) > 0 ? "+" : ""}{fmtQty(m.quantity)}{m.unit_cost && <span className="block text-xs font-normal text-text-2">{fmtMoney(m.unit_cost)} c/u</span>}</td>
                <td className={`${td} text-right tnum`}>{fmtQty(m.balance_after)}</td>
                <td className={`${td} whitespace-nowrap text-text-2`}>{m.user_name}</td>
                <td className={`${td} max-w-[220px] truncate text-text-2`} title={m.notes ?? undefined}>{m.reference ?? "—"}</td>
                {isAdmin && <td className={`${td} text-right`}>{m.kind !== "REVERSION" && !reversed.has(m.id) && (
                  <Button variant="ghost" icon="undo" className="min-h-8 px-2 text-xs" onClick={() => { setRevMsg(null); setTarget(m); }} aria-label={`Revertir movimiento ${m.id}`}>Revertir</Button>
                )}</td>}
              </tr>
            ))}
          </Table>
        )}
      </Panel>

      <ConfirmDialog open={!!target} title={`Revertir movimiento #${target?.id ?? ""}`} confirmLabel="Registrar reversión" tone="danger" busy={revBusy}
        onCancel={() => { setTarget(null); setReason(""); }} onConfirm={() => void reverse()}>
        <p>Se registrará un movimiento de signo contrario. El original se conserva en el historial.</p>
        <div className="mt-4"><Field label="Motivo" error={reason && reason.trim().length < 3 ? "Mínimo 3 caracteres" : undefined}>{(id, d) => (
          <Input id={id} value={reason} onChange={(e) => setReason(e.target.value)} aria-describedby={d} maxLength={500} />
        )}</Field></div>
      </ConfirmDialog>
    </>
  );
}
