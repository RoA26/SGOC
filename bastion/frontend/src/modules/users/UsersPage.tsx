import { useState } from "react";
import { CreateForm } from "@/components/CreateForm";
import { Table, td, th } from "@/components/Table";
import { Alert, Badge, Button, ConfirmDialog, ErrorState, Field, Input, LoadingState, PageHeader, Panel } from "@/components/ui";
import { api, errorMessage } from "@/lib/api";
import { useAuth } from "@/lib/auth";
import { ROL_LABEL, type UserRow } from "@/lib/types";
import { useResource } from "@/lib/useResource";
import { personSchema } from "./schemas";

export function UsersPage() {
  const { me } = useAuth();
  const list = useResource((signal) => api<UserRow[]>("/users", { signal }), []);
  const [target, setTarget] = useState<UserRow | null>(null);
  const [busy, setBusy] = useState(false);
  const [msg, setMsg] = useState<string | null>(null);

  async function toggle() {
    if (!target) return;
    setBusy(true);
    try {
      await api(`/users/${target.id}`, { method: "PATCH", body: { active: !target.active } });
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
      <PageHeader title="Usuarios" description="Trabajadores de su organización. Desactivar una cuenta cierra sus sesiones de inmediato." />
      <div className="grid gap-6 xl:grid-cols-[1fr_360px]">
        <Panel>
          {msg && <div className="p-4 pb-0"><Alert tone="error">{msg}</Alert></div>}
          {list.loading ? <LoadingState /> : list.error ? <ErrorState message={list.error} onRetry={list.reload} /> : (
            <Table caption="Usuarios" head={<tr><th className={th}>Nombre</th><th className={th}>Rol</th><th className={th}>Estado</th><th className={th}><span className="sr-only">Acciones</span></th></tr>}>
              {list.data?.map((u) => (
                <tr key={u.id}>
                  <td className={td}><span className="font-semibold">{u.full_name}</span><span className="block text-xs text-text-2">{u.email}</span></td>
                  <td className={`${td} text-text-2`}>{ROL_LABEL[u.role]}</td>
                  <td className={td}>{u.active ? <Badge tone="ok">Activo</Badge> : <Badge>Inactivo</Badge>}</td>
                  <td className={`${td} text-right`}>{u.role === "TRABAJADOR" && u.id !== me?.id && (
                    <Button variant="ghost" className="min-h-8 px-2 text-xs" onClick={() => { setMsg(null); setTarget(u); }}>{u.active ? "Desactivar" : "Activar"}</Button>
                  )}</td>
                </tr>
              ))}
            </Table>
          )}
        </Panel>
        <Panel>
          <h2 className="border-b border-line px-5 py-4 font-bold">Nuevo trabajador</h2>
          <CreateForm schema={personSchema} path="/users" submitLabel="Crear trabajador" onCreated={list.reload}>
            {(e) => (
              <>
                <Field label="Nombre completo" error={e.full_name}>{(id, d) => <Input id={id} name="full_name" aria-describedby={d} invalid={!!e.full_name} />}</Field>
                <Field label="Correo" error={e.email}>{(id, d) => <Input id={id} name="email" type="email" aria-describedby={d} invalid={!!e.email} />}</Field>
                <Field label="Contraseña inicial" error={e.password} hint="Mínimo 10 caracteres.">{(id, d) => <Input id={id} name="password" type="password" autoComplete="new-password" aria-describedby={d} invalid={!!e.password} />}</Field>
              </>
            )}
          </CreateForm>
        </Panel>
      </div>
      <ConfirmDialog open={!!target} title={target?.active ? "Desactivar trabajador" : "Activar trabajador"} confirmLabel={target?.active ? "Desactivar" : "Activar"}
        tone={target?.active ? "danger" : "primary"} busy={busy} onCancel={() => setTarget(null)} onConfirm={() => void toggle()}>
        {target?.active ? `${target.full_name} no podrá iniciar sesión y sus sesiones abiertas se cerrarán.` : `${target?.full_name ?? ""} podrá volver a iniciar sesión.`}
      </ConfirmDialog>
    </>
  );
}
