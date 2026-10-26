import { useState, type FormEvent, type ReactNode } from "react";
import type { ZodType, ZodTypeDef } from "zod";
import { api } from "@/lib/api";
import { serverErrors, validate, type Errors } from "@/lib/forms";
import type { IconName } from "./Icon";
import { Alert, Button } from "./ui";

/** Formulario genérico (alta o edición): valida con Zod, envía al backend y muestra errores por campo. */
export function CreateForm<T>({ schema, path, submitLabel, onCreated, children, map, method = "POST", okMessage = "Registro creado correctamente.", icon = "plus" }: {
  schema: ZodType<T, ZodTypeDef, unknown>; path: string; submitLabel: string; onCreated: () => void;
  children: (errors: Errors) => ReactNode; map?: (v: T) => unknown; method?: "POST" | "PATCH"; okMessage?: string; icon?: IconName;
}) {
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
      await api(path, { method, body: map ? map(r.value) : r.value });
      if (method === "POST") form.reset();
      setOk(true);
      onCreated();
    } catch (err) {
      setErrors(serverErrors(err));
    } finally {
      setBusy(false);
    }
  }

  return (
    <form onSubmit={onSubmit} noValidate className="space-y-4 p-5">
      {ok && <Alert tone="success">{okMessage}</Alert>}
      {errors._ && <Alert tone="error">{errors._}</Alert>}
      {children(errors)}
      <Button type="submit" loading={busy} icon={icon}>{submitLabel}</Button>
    </form>
  );
}
