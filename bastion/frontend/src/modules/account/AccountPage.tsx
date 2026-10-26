import { useState, type FormEvent } from "react";
import { z } from "zod";
import { RoleBadge } from "@/components/AppShell";
import { Alert, Button, Field, Input, PageHeader, Panel } from "@/components/ui";
import { api, tokenStore } from "@/lib/api";
import { useAuth } from "@/lib/auth";
import { serverErrors, validate, type Errors } from "@/lib/forms";
import { ROL_LABEL } from "@/lib/types";
import { password } from "../users/schemas";

const schema = z
  .object({ current_password: z.string().min(1, "Ingrese su contraseña actual"), new_password: password, confirm: z.string() })
  .refine((v) => v.new_password === v.confirm, { path: ["confirm"], message: "Las contraseñas no coinciden" });

export function AccountPage() {
  const { me } = useAuth();
  const [errors, setErrors] = useState<Errors>({});
  const [busy, setBusy] = useState(false);
  const [ok, setOk] = useState(false);

  async function onSubmit(e: FormEvent<HTMLFormElement>) {
    e.preventDefault();
    const form = e.currentTarget;
    const r = validate(schema, Object.fromEntries(new FormData(form)));
    setOk(false);
    if (!r.ok) return setErrors(r.errors);
    setErrors({});
    setBusy(true);
    try {
      const t = await api<{ access_token: string }>("/auth/password", {
        method: "POST",
        body: { current_password: r.value.current_password, new_password: r.value.new_password },
      });
      tokenStore.set(t.access_token); // las demás sesiones quedan cerradas; esta continúa
      form.reset();
      setOk(true);
    } catch (err) {
      setErrors(serverErrors(err));
    } finally {
      setBusy(false);
    }
  }

  if (!me) return null;
  return (
    <>
      <PageHeader title="Mi cuenta" />
      <div className="grid gap-6 lg:grid-cols-[1fr_1.2fr]">
        <Panel className="p-5">
          <dl className="space-y-4 text-sm">
            <div><dt className="text-text-2">Nombre</dt><dd className="mt-0.5 font-semibold">{me.full_name}</dd></div>
            <div><dt className="text-text-2">Correo</dt><dd className="mt-0.5 font-semibold">{me.email}</dd></div>
            <div><dt className="text-text-2">Rol</dt><dd className="mt-1 flex items-center gap-2"><span className="rounded-full bg-ink px-1 py-1"><RoleBadge role={me.role} /></span><span className="text-text-2">{ROL_LABEL[me.role]}</span></dd></div>
            {me.organization_name && <div><dt className="text-text-2">Organización</dt><dd className="mt-0.5 font-semibold">{me.organization_name}</dd></div>}
          </dl>
        </Panel>
        <Panel>
          <h2 className="border-b border-line px-5 py-4 font-bold">Cambiar contraseña</h2>
          <form onSubmit={onSubmit} noValidate className="space-y-4 p-5">
            {ok && <Alert tone="success">Contraseña actualizada. Sus otras sesiones se cerraron.</Alert>}
            {errors._ && <Alert tone="error">{errors._}</Alert>}
            <Field label="Contraseña actual" error={errors.current_password}>{(id, d) => <Input id={id} name="current_password" type="password" autoComplete="current-password" aria-describedby={d} invalid={!!errors.current_password} />}</Field>
            <Field label="Contraseña nueva" error={errors.new_password} hint="Mínimo 10 caracteres.">{(id, d) => <Input id={id} name="new_password" type="password" autoComplete="new-password" aria-describedby={d} invalid={!!errors.new_password} />}</Field>
            <Field label="Repita la contraseña nueva" error={errors.confirm}>{(id, d) => <Input id={id} name="confirm" type="password" autoComplete="new-password" aria-describedby={d} invalid={!!errors.confirm} />}</Field>
            <Button type="submit" loading={busy} icon="lock">Cambiar contraseña</Button>
          </form>
        </Panel>
      </div>
    </>
  );
}
