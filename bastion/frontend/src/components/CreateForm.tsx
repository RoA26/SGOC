import { useState, type FormEvent, type ReactNode } from "react";
import type { ZodType, ZodTypeDef } from "zod";
import { api } from "@/lib/api";
import { serverErrors, validate, type Errors } from "@/lib/forms";
import { Alert, Button } from "./ui";

/** Formulario de alta genérico: valida con Zod, envía al backend y muestra errores por campo. */
export function CreateForm<T>({ schema, path, submitLabel, onCreated, children, map }: {
  schema: ZodType<T, ZodTypeDef, unknown>; path: string; submitLabel: string; onCreated: () => void;
  children: (errors: Errors) => ReactNode; map?: (v: T) => unknown;
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
      await api(path, { method: "POST", body: map ? map(r.value) : r.value });
      form.reset();
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
      {ok && <Alert tone="success">Registro creado correctamente.</Alert>}
      {errors._ && <Alert tone="error">{errors._}</Alert>}
      {children(errors)}
      <Button type="submit" loading={busy} icon="plus">{submitLabel}</Button>
    </form>
  );
}
