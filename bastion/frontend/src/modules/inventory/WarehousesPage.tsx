import { z } from "zod";
import { CreateForm } from "@/components/CreateForm";
import { Table, td, th } from "@/components/Table";
import { useState } from "react";
import { Alert, Badge, Button, ConfirmDialog, EmptyState, ErrorState, Field, Input, LoadingState, Modal, PageHeader, Panel } from "@/components/ui";
import { api, errorMessage } from "@/lib/api";
import type { Warehouse } from "@/lib/types";
import { useResource } from "@/lib/useResource";

const schema = z.object({
  code: z.string().trim().min(1, "Ingrese el código").max(30).regex(/^[A-Za-z0-9._-]+$/, "Sin espacios: letras, números y . _ -"),
  name: z.string().trim().min(1, "Ingrese el nombre").max(120),
});

const renameSchema = z.object({ name: z.string().trim().min(1, "Ingrese el nombre").max(120) });

export function WarehousesPage() {
  const list = useResource((signal) => api<Warehouse[]>("/warehouses", { signal }), []);
  const [editing, setEditing] = useState<Warehouse | null>(null);
  const [toggling, setToggling] = useState<Warehouse | null>(null);
  const [busy, setBusy] = useState(false);
  const [msg, setMsg] = useState<string | null>(null);

  async function toggle() {
    if (!toggling) return;
    setBusy(true);
    try {
      await api(`/warehouses/${toggling.id}`, { method: "PATCH", body: { active: !toggling.active } });
      list.reload();
    } catch (e) {
      setMsg(errorMessage(e));
    } finally {
      setBusy(false);
      setToggling(null);
    }
  }

  return (
    <>
      <PageHeader admin title="Bodegas" description="Ubicaciones físicas del inventario de su organización." />
      <div className="grid gap-6 xl:grid-cols-[1fr_360px]">
        <Panel>
          {msg && <div className="p-4 pb-0"><Alert tone="error">{msg}</Alert></div>}
          {list.loading ? <LoadingState /> : list.error ? <ErrorState message={list.error} onRetry={list.reload} /> : !list.data?.length ? (
            <EmptyState icon="warehouse" title="Aún no hay bodegas">Cree la primera bodega para empezar a registrar existencias.</EmptyState>
          ) : (
            <Table caption="Bodegas" head={<tr><th className={th}>Código</th><th className={th}>Nombre</th><th className={th}>Estado</th><th className={th}><span className="sr-only">Acciones</span></th></tr>}>
              {list.data.map((w) => (
                <tr key={w.id}>
                  <td className={`${td} font-mono`}>{w.code}</td>
                  <td className={td}>{w.name}</td>
                  <td className={td}>{w.active ? <Badge tone="ok">Activa</Badge> : <Badge>Inactiva</Badge>}</td>
                  <td className={`${td} whitespace-nowrap text-right`}>
                    <Button variant="ghost" className="min-h-8 px-2 text-xs" onClick={() => setEditing(w)}>Renombrar</Button>
                    <Button variant="ghost" className="min-h-8 px-2 text-xs" onClick={() => { setMsg(null); setToggling(w); }}>{w.active ? "Desactivar" : "Activar"}</Button>
                  </td>
                </tr>
              ))}
            </Table>
          )}
        </Panel>
        <Panel>
          <h2 className="border-b border-line px-5 py-4 font-bold">Nueva bodega</h2>
          <CreateForm schema={schema} path="/warehouses" submitLabel="Crear bodega" onCreated={list.reload}>
            {(e) => (
              <>
                <Field label="Código" error={e.code}>{(id, d) => <Input id={id} name="code" aria-describedby={d} invalid={!!e.code} className="font-mono" />}</Field>
                <Field label="Nombre" error={e.name}>{(id, d) => <Input id={id} name="name" aria-describedby={d} invalid={!!e.name} />}</Field>
              </>
            )}
          </CreateForm>
        </Panel>
      </div>
      {editing && (
        <Modal open title={`Renombrar ${editing.code}`} onClose={() => setEditing(null)}>
          <CreateForm schema={renameSchema} path={`/warehouses/${editing.id}`} method="PATCH" icon="check" submitLabel="Guardar" okMessage="Nombre actualizado." onCreated={list.reload}>
            {(e) => <Field label="Nombre" error={e.name}>{(id, d) => <Input id={id} name="name" defaultValue={editing.name} aria-describedby={d} invalid={!!e.name} />}</Field>}
          </CreateForm>
        </Modal>
      )}
      <ConfirmDialog open={!!toggling} title={toggling?.active ? "Desactivar bodega" : "Activar bodega"} confirmLabel={toggling?.active ? "Desactivar" : "Activar"}
        tone={toggling?.active ? "danger" : "primary"} busy={busy} onCancel={() => setToggling(null)} onConfirm={() => void toggle()}>
        {toggling?.active ? "No se podrán registrar movimientos en esta bodega. Sus existencias e historial se conservan." : "La bodega volverá a admitir movimientos."}
      </ConfirmDialog>
    </>
  );
}
