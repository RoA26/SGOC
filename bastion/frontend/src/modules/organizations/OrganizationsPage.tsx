import { useState } from "react";
import { z } from "zod";
import { CreateForm } from "@/components/CreateForm";
import { Table, td, th } from "@/components/Table";
import { Alert, Badge, Button, ConfirmDialog, EmptyState, ErrorState, Field, Input, LoadingState, PageHeader, Panel } from "@/components/ui";
import { api, errorMessage } from "@/lib/api";
import { fmtDate } from "@/lib/format";
import type { Organization } from "@/lib/types";
import { useResource } from "@/lib/useResource";
import { password } from "../users/schemas";

const schema = z.object({
  name: z.string().trim().min(2, "Ingrese el nombre").max(160),
  slug: z.string().trim().regex(/^[a-z0-9](?:[a-z0-9-]{0,58}[a-z0-9])?$/, "Minúsculas, números y guiones"),
  admin_full_name: z.string().trim().min(2, "Ingrese el nombre").max(160),
  admin_email: z.string().trim().email("Correo no válido"),
  admin_password: password,
});

export function OrganizationsPage() {
  const list = useResource((signal) => api<Organization[]>("/organizations", { signal }), []);
  const [target, setTarget] = useState<Organization | null>(null);
  const [busy, setBusy] = useState(false);
  const [msg, setMsg] = useState<string | null>(null);

  async function toggle() {
    if (!target) return;
    setBusy(true);
    try {
      await api(`/organizations/${target.id}`, { method: "PATCH", body: { active: !target.active } });
      list.reload();
    } catch (e) {
      setMsg(errorMessage(e));
    } finally {
      setBusy(false);
      setTarget(null);
    }
  }

  return (
    <>
      <PageHeader title="Organizaciones" description="Empresas que usan la plataforma. Cada una se crea con su primer administrador comercial." />
      <div className="grid gap-6 xl:grid-cols-[1fr_380px]">
        <Panel>
          {msg && <div className="p-4 pb-0"><Alert tone="error">{msg}</Alert></div>}
          {list.loading ? <LoadingState /> : list.error ? <ErrorState message={list.error} onRetry={list.reload} /> : !list.data?.length ? (
            <EmptyState icon="building" title="Aún no hay organizaciones">Cree la primera organización y su administrador.</EmptyState>
          ) : (
            <Table caption="Organizaciones" head={<tr><th className={th}>Organización</th><th className={th}>Alta</th><th className={th}>Estado</th><th className={th}><span className="sr-only">Acciones</span></th></tr>}>
              {list.data.map((o) => (
                <tr key={o.id}>
                  <td className={td}><span className="font-semibold">{o.name}</span><span className="block font-mono text-xs text-text-2">{o.slug}</span></td>
                  <td className={`${td} text-text-2`}>{fmtDate(o.created_at)}</td>
                  <td className={td}>{o.active ? <Badge tone="ok">Activa</Badge> : <Badge tone="danger">Desactivada</Badge>}</td>
                  <td className={`${td} text-right`}><Button variant="ghost" className="min-h-8 px-2 text-xs" onClick={() => { setMsg(null); setTarget(o); }}>{o.active ? "Desactivar" : "Activar"}</Button></td>
                </tr>
              ))}
            </Table>
          )}
        </Panel>
        <Panel>
          <h2 className="border-b border-line px-5 py-4 font-bold">Nueva organización</h2>
          <CreateForm schema={schema} path="/organizations" submitLabel="Crear organización" onCreated={list.reload}
            map={(v) => ({ name: v.name, slug: v.slug, admin: { full_name: v.admin_full_name, email: v.admin_email, password: v.admin_password } })}>
            {(e) => (
              <>
                <Field label="Nombre" error={e.name}>{(id, d) => <Input id={id} name="name" aria-describedby={d} invalid={!!e.name} />}</Field>
                <Field label="Identificador" error={e.slug} hint="Ej.: ferreteria-central">{(id, d) => <Input id={id} name="slug" aria-describedby={d} invalid={!!e.slug} className="font-mono" />}</Field>
                <p className="pt-2 text-sm font-bold">Primer administrador comercial</p>
                <Field label="Nombre completo" error={e.admin_full_name ?? e["admin.full_name"]}>{(id, d) => <Input id={id} name="admin_full_name" aria-describedby={d} />}</Field>
                <Field label="Correo" error={e.admin_email ?? e["admin.email"]}>{(id, d) => <Input id={id} name="admin_email" type="email" aria-describedby={d} />}</Field>
                <Field label="Contraseña inicial" error={e.admin_password ?? e["admin.password"]}>{(id, d) => <Input id={id} name="admin_password" type="password" autoComplete="new-password" aria-describedby={d} />}</Field>
              </>
            )}
          </CreateForm>
        </Panel>
      </div>
      <ConfirmDialog open={!!target} title={target?.active ? "Desactivar organización" : "Activar organización"} confirmLabel={target?.active ? "Desactivar" : "Activar"}
        tone={target?.active ? "danger" : "primary"} busy={busy} onCancel={() => setTarget(null)} onConfirm={() => void toggle()}>
        {target?.active ? `Los usuarios de ${target.name} no podrán iniciar sesión y sus sesiones se cerrarán. Los datos se conservan.` : `Los usuarios de ${target?.name ?? ""} podrán volver a iniciar sesión.`}
      </ConfirmDialog>
    </>
  );
}
