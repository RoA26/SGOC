import type { ZodType, ZodTypeDef } from "zod";
import { ApiError } from "./api";

export type Errors = Record<string, string>;

/** Valida con Zod y devuelve los mensajes por campo (el primero de cada uno). */
export function validate<T>(schema: ZodType<T, ZodTypeDef, unknown>, data: unknown): { ok: true; value: T } | { ok: false; errors: Errors } {
  const r = schema.safeParse(data);
  if (r.success) return { ok: true, value: r.data };
  const errors: Errors = {};
  for (const issue of r.error.issues) {
    const k = issue.path.join(".") || "_";
    errors[k] ??= issue.message;
  }
  return { ok: false, errors };
}

/** Lleva los errores de validación del backend (422) a los campos del formulario. */
export function serverErrors(e: unknown): Errors {
  if (!(e instanceof ApiError)) return { _: "Ocurrió un error inesperado." };
  const out: Errors = {};
  for (const f of e.fields) out[f.campo || "_"] ??= f.mensaje;
  if (!e.fields.length) out._ = e.message;
  return out;
}
