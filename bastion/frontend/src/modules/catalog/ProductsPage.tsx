import { useMemo, useState } from "react";
import { z } from "zod";
import { CreateForm } from "@/components/CreateForm";
import { Table, td, th } from "@/components/Table";
import { Badge, Button, EmptyState, ErrorState, Field, Input, LoadingState, Modal, PageHeader, Panel, Select } from "@/components/ui";
import { api } from "@/lib/api";
import { useAuth } from "@/lib/auth";
import { fmtQty } from "@/lib/format";
import type { Category, Product } from "@/lib/types";
import { useResource } from "@/lib/useResource";

const UNIDADES = ["UND", "M", "M2", "KG", "ROLLO", "L"] as const;
const opt = (max: number) => z.string().trim().max(max).transform((v) => v || undefined);

export const productSchema = z.object({
  sku: z.string().trim().min(1, "Ingrese el SKU").max(64).regex(/^[A-Za-z0-9._\-/]+$/, "Solo letras, números y . _ - /"),
  name: z.string().trim().min(1, "Ingrese el nombre").max(200),
  barcode: opt(64),
  unit: z.enum(UNIDADES),
  min_stock: z.string().trim().regex(/^\d{1,11}(\.\d{1,3})?$/, "Número no válido").default("0"),
  category_id: z.string().transform((v) => (v ? Number(v) : undefined)),
});
const categorySchema = z.object({ name: z.string().trim().min(1, "Ingrese el nombre").max(120) });

// Edición: el SKU no cambia (identifica al producto en el historial).
const editSchema = productSchema.omit({ sku: true, barcode: true, category_id: true }).extend({
  barcode: z.string().trim().max(64).transform((v) => v || null),
  category_id: z.string().transform((v) => (v ? Number(v) : null)),
  active: z.string().transform((v) => v === "true"),
});

function EditProduct({ product, categories, onClose, onSaved }: { product: Product; categories: Category[]; onClose: () => void; onSaved: () => void }) {
  return (
    <Modal open title={`Editar ${product.sku}`} onClose={onClose}>
      <CreateForm schema={editSchema} path={`/products/${product.id}`} method="PATCH" icon="check" submitLabel="Guardar cambios" okMessage="Cambios guardados." onCreated={onSaved}>
        {(e) => (
          <>
            <Field label="Nombre" error={e.name}>{(id, d) => <Input id={id} name="name" defaultValue={product.name} aria-describedby={d} invalid={!!e.name} />}</Field>
            <Field label="Código de barras" error={e.barcode}>{(id, d) => <Input id={id} name="barcode" defaultValue={product.barcode ?? ""} aria-describedby={d} className="font-mono" />}</Field>
            <div className="grid grid-cols-2 gap-3">
              <Field label="Unidad">{(id) => <Select id={id} name="unit" defaultValue={product.unit}>{UNIDADES.map((u) => <option key={u}>{u}</option>)}</Select>}</Field>
              <Field label="Stock mínimo" error={e.min_stock}>{(id, d) => <Input id={id} name="min_stock" defaultValue={String(Number(product.min_stock))} inputMode="decimal" aria-describedby={d} invalid={!!e.min_stock} />}</Field>
            </div>
            <div className="grid grid-cols-2 gap-3">
              <Field label="Categoría">{(id) => (
                <Select id={id} name="category_id" defaultValue={product.category_id ?? ""}><option value="">Sin categoría</option>{categories.map((c) => <option key={c.id} value={c.id}>{c.name}</option>)}</Select>
              )}</Field>
              <Field label="Estado" hint="Un producto inactivo no admite movimientos.">{(id, d) => (
                <Select id={id} name="active" defaultValue={String(product.active)} aria-describedby={d}><option value="true">Activo</option><option value="false">Inactivo</option></Select>
              )}</Field>
            </div>
          </>
        )}
      </CreateForm>
    </Modal>
  );
}

export function ProductsPage() {
  const { hasRole } = useAuth();
  const isAdmin = hasRole("ADMINISTRADOR_COMERCIAL");
  const [q, setQ] = useState("");
  const cats = useResource((signal) => api<Category[]>("/categories", { signal }), []);
  const list = useResource((signal) => api<Product[]>(`/products?include_inactive=${isAdmin}${q ? `&q=${encodeURIComponent(q)}` : ""}`, { signal }), [q, isAdmin]);
  const [editing, setEditing] = useState<Product | null>(null);
  const catName = useMemo(() => new Map(cats.data?.map((c) => [c.id, c.name]) ?? []), [cats.data]);

  return (
    <>
      <PageHeader admin={isAdmin} title={isAdmin ? "Catálogo de productos" : "Productos"} description={isAdmin ? "Cree y edite los productos de su organización. El SKU es único y no se modifica." : "Consulte el catálogo de su organización."} />
      <div className={`grid gap-6 ${isAdmin ? "xl:grid-cols-[1fr_360px]" : ""}`}>
        <Panel>
          <div className="border-b border-line p-4">
            <label className="sr-only" htmlFor="buscar">Buscar productos</label>
            <Input id="buscar" type="search" placeholder="Buscar por nombre, SKU o código de barras" value={q} onChange={(e) => setQ(e.target.value)} />
          </div>
          {list.loading ? <LoadingState /> : list.error ? <ErrorState message={list.error} onRetry={list.reload} /> : !list.data?.length ? (
            <EmptyState icon="package" title={q ? "Sin resultados" : "Aún no hay productos"}>{q ? "Pruebe con otro término de búsqueda." : isAdmin ? "Cree el primer producto con el formulario." : "Su administrador aún no ha creado productos."}</EmptyState>
          ) : (
            <Table caption="Productos" head={<tr><th className={th}>Producto</th><th className={th}>Categoría</th><th className={th}>Unidad</th><th className={`${th} text-right`}>Mínimo</th><th className={th}>Estado</th>{isAdmin && <th className={th}><span className="sr-only">Acciones</span></th>}</tr>}>
              {list.data.map((p) => (
                <tr key={p.id}>
                  <td className={td}><span className="font-semibold">{p.name}</span><span className="block font-mono text-xs text-text-2">{p.sku}{p.barcode ? ` · ${p.barcode}` : ""}</span></td>
                  <td className={`${td} text-text-2`}>{p.category_id ? catName.get(p.category_id) : "—"}</td>
                  <td className={`${td} font-mono text-xs`}>{p.unit}</td>
                  <td className={`${td} text-right tnum`}>{fmtQty(p.min_stock)}</td>
                  <td className={td}>{p.active ? <Badge tone="ok">Activo</Badge> : <Badge>Inactivo</Badge>}</td>
                  {isAdmin && <td className={`${td} text-right`}><Button variant="ghost" className="min-h-8 px-2 text-xs" onClick={() => setEditing(p)} aria-label={`Editar ${p.name}`}>Editar</Button></td>}
                </tr>
              ))}
            </Table>
          )}
        </Panel>
        {isAdmin && (
          <div className="space-y-6">
            <Panel>
              <h2 className="border-b border-line px-5 py-4 font-bold">Nuevo producto</h2>
              <CreateForm schema={productSchema} path="/products" submitLabel="Crear producto" onCreated={list.reload}>
                {(e) => (
                  <>
                    <Field label="SKU" error={e.sku}>{(id, d) => <Input id={id} name="sku" aria-describedby={d} invalid={!!e.sku} className="font-mono" />}</Field>
                    <Field label="Nombre" error={e.name}>{(id, d) => <Input id={id} name="name" aria-describedby={d} invalid={!!e.name} />}</Field>
                    <Field label="Código de barras (opcional)" error={e.barcode}>{(id, d) => <Input id={id} name="barcode" aria-describedby={d} className="font-mono" />}</Field>
                    <div className="grid grid-cols-2 gap-3">
                      <Field label="Unidad">{(id) => <Select id={id} name="unit" defaultValue="UND">{UNIDADES.map((u) => <option key={u}>{u}</option>)}</Select>}</Field>
                      <Field label="Stock mínimo" error={e.min_stock}>{(id, d) => <Input id={id} name="min_stock" defaultValue="0" inputMode="decimal" aria-describedby={d} invalid={!!e.min_stock} />}</Field>
                    </div>
                    <Field label="Categoría" error={e.category_id}>{(id) => (
                      <Select id={id} name="category_id" defaultValue=""><option value="">Sin categoría</option>{cats.data?.map((c) => <option key={c.id} value={c.id}>{c.name}</option>)}</Select>
                    )}</Field>
                  </>
                )}
              </CreateForm>
            </Panel>
            <Panel>
              <h2 className="border-b border-line px-5 py-4 font-bold">Nueva categoría</h2>
              <CreateForm schema={categorySchema} path="/categories" submitLabel="Crear categoría" onCreated={cats.reload}>
                {(e) => <Field label="Nombre" error={e.name}>{(id, d) => <Input id={id} name="name" aria-describedby={d} invalid={!!e.name} />}</Field>}
              </CreateForm>
            </Panel>
          </div>
        )}
      </div>
      {editing && <EditProduct product={editing} categories={cats.data ?? []} onClose={() => setEditing(null)} onSaved={list.reload} />}
    </>
  );
}
