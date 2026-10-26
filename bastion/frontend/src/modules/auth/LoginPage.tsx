import { useState, type FormEvent } from "react";
import { Link, Navigate, useLocation, useNavigate } from "react-router-dom";
import { z } from "zod";
import { Logo } from "@/brand/Logo";
import { Alert, Button, Field, Input } from "@/components/ui";
import { useAuth } from "@/lib/auth";
import { validate, serverErrors, type Errors } from "@/lib/forms";

const schema = z.object({
  email: z.string().trim().min(1, "Ingrese su correo").email("Correo no válido"),
  password: z.string().min(1, "Ingrese su contraseña"),
});

export function LoginPage() {
  const { login, status, expired } = useAuth();
  const navigate = useNavigate();
  const from = (useLocation().state as { from?: string } | null)?.from ?? "/app";
  const [errors, setErrors] = useState<Errors>({});
  const [busy, setBusy] = useState(false);

  if (status === "authenticated") return <Navigate to={from} replace />;

  async function onSubmit(e: FormEvent<HTMLFormElement>) {
    e.preventDefault();
    const fd = new FormData(e.currentTarget);
    const r = validate(schema, { email: fd.get("email"), password: fd.get("password") });
    if (!r.ok) return setErrors(r.errors);
    setErrors({});
    setBusy(true);
    try {
      await login(r.value.email, r.value.password);
      navigate(from, { replace: true });
    } catch (err) {
      setErrors(serverErrors(err));
    } finally {
      setBusy(false);
    }
  }

  return (
    <div className="grid min-h-screen lg:grid-cols-[1fr_1.1fr]">
      <aside className="on-ink relative hidden flex-col justify-between overflow-hidden bg-ink p-12 lg:flex">
        <Link to="/" aria-label="Volver al inicio"><Logo tone="light" /></Link>
        <div>
          <p className="max-w-sm text-3xl font-extrabold leading-tight tracking-[-0.02em] text-white">Cada movimiento queda registrado. Cada saldo tiene explicación.</p>
          <p className="mt-4 max-w-sm text-on-ink-2">Ingrese con la cuenta que le asignó el administrador de su organización.</p>
        </div>
        <p className="text-sm text-on-ink-2">© {new Date().getFullYear()} Bastión</p>
      </aside>
      <main className="flex items-center justify-center px-4 py-12">
        <div className="w-full max-w-sm">
          <Link to="/" className="mb-10 inline-block lg:hidden" aria-label="Volver al inicio"><Logo /></Link>
          <h1 className="text-2xl font-extrabold tracking-[-0.02em]">Iniciar sesión</h1>
          <p className="mt-1.5 text-sm text-text-2">Acceda al panel de control de su inventario.</p>
          <form className="mt-8 space-y-5" onSubmit={onSubmit} noValidate>
            {expired && !errors._ && <Alert tone="warn">Su sesión expiró. Vuelva a iniciar sesión.</Alert>}
            {errors._ && <Alert tone="error">{errors._}</Alert>}
            <Field label="Correo electrónico" error={errors.email}>
              {(id, d) => <Input id={id} name="email" type="email" autoComplete="username" aria-describedby={d} invalid={!!errors.email} autoFocus />}
            </Field>
            <Field label="Contraseña" error={errors.password}>
              {(id, d) => <Input id={id} name="password" type="password" autoComplete="current-password" aria-describedby={d} invalid={!!errors.password} />}
            </Field>
            <Button type="submit" className="w-full" loading={busy}>Ingresar</Button>
          </form>
          <p className="mt-8 text-sm text-text-2">¿No tiene cuenta? Solicítela al administrador de su organización. Bastión no ofrece registro público.</p>
        </div>
      </main>
    </div>
  );
}
