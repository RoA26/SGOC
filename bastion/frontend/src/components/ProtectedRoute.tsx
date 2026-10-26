import type { ReactNode } from "react";
import { Navigate, useLocation } from "react-router-dom";
import { useAuth } from "@/lib/auth";
import type { Rol } from "@/lib/types";
import { EmptyState, LoadingState } from "./ui";

/** Protección de interfaz. La autoridad real sobre permisos es el backend. */
export function ProtectedRoute({ roles, children }: { roles?: Rol[]; children: ReactNode }) {
  const { status, hasRole, signedOut } = useAuth();
  const location = useLocation();
  if (status === "loading") return <LoadingState label="Verificando sesión…" />;
  if (status === "anonymous") return <Navigate to="/login" replace state={signedOut ? null : { from: location.pathname }} />;
  if (roles && !hasRole(...roles))
    return (
      <EmptyState icon="lock" title="Acceso restringido">
        Su rol no tiene permiso para ver esta sección.
      </EmptyState>
    );
  return <>{children}</>;
}
