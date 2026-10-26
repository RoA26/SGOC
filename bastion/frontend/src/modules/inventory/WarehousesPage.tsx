import { z } from "zod";
import { CreateForm } from "@/components/CreateForm";
import { Table, td, th } from "@/components/Table";
import { Badge, EmptyState, ErrorState, Field, Input, LoadingState, PageHeader, Panel } from "@/components/ui";
import { api } from "@/lib/api";
import type { Warehouse } from "@/lib/types";
import { useResource } from "@/lib/useResource";

const schema = z.object({
  code: z.string().trim().min(1, "Ingrese el código").max(30).regex(/^[A-Za-z0-9._-]+$/, "Sin espacios: letras, números y . _ -"),
  name: z.string().trim().min(1, "Ingrese el nombre").max(120),
});

export function WarehousesPage() {
  const list = useResource((signal) => api<Warehouse[]>("/warehouses", { signal }), []);
  return (
    <>
      <PageHeader title="Bodegas" description="Ubicaciones físicas del inventario de su organización." />
      <div className="grid gap-6 xl:grid-cols-[1fr_360px]">
        <Panel>
          {list.loading ? <LoadingState /> : list.error ? <ErrorState message={list.error} onRetry={list.reload} /> : !list.data?.length ? (
            <EmptyState icon="warehouse" title="Aún no hay bodegas">Cree la primera bodega para empezar a registrar existencias.</EmptyState>
          ) : (
            <Table caption="Bodegas" head={<tr><th className={th}>Código</th><th className={th}>Nombre</th><th className={th}>Estado</th></tr>}>
              {list.data.map((w) => <tr key={w.id}><td className={`${td} font-mono`}>{w.code}</td><td className={td}>{w.name}</td><td className={td}>{w.active ? <Badge tone="ok">Activa</Badge> : <Badge>Inactiva</Badge>}</td></tr>)}
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
    </>
  );
}
