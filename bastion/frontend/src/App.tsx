import { Link, Navigate, Route, Routes } from "react-router-dom";
import { AppShell } from "@/components/AppShell";
import { ProtectedRoute } from "@/components/ProtectedRoute";
import { EmptyState } from "@/components/ui";
import { useAuth } from "@/lib/auth";
import type { Rol } from "@/lib/types";
import { AccountPage } from "@/modules/account/AccountPage";
import { LoginPage } from "@/modules/auth/LoginPage";
import { ProductsPage } from "@/modules/catalog/ProductsPage";
import { DashboardPage } from "@/modules/dashboard/DashboardPage";
import { InventoryPage } from "@/modules/inventory/InventoryPage";
import { WarehousesPage } from "@/modules/inventory/WarehousesPage";
import { LandingPage } from "@/modules/landing/LandingPage";
import { MovementsPage } from "@/modules/movements/MovementsPage";
import { OrganizationsPage } from "@/modules/organizations/OrganizationsPage";
import { UsersPage } from "@/modules/users/UsersPage";

const ORG: Rol[] = ["ADMINISTRADOR_COMERCIAL", "TRABAJADOR"];
const ADMIN: Rol[] = ["ADMINISTRADOR_COMERCIAL"];

function Home() {
  const { hasRole } = useAuth();
  return hasRole("SUPER_ADMIN") ? <Navigate to="/app/organizaciones" replace /> : <DashboardPage />;
}

function NotFound() {
  return (
    <EmptyState icon="search" title="Página no encontrada" action={<Link className="font-semibold text-accent-strong underline" to="/">Volver al inicio</Link>}>
      La dirección no existe o fue movida.
    </EmptyState>
  );
}

const guard = (roles: Rol[] | undefined, el: JSX.Element) => <ProtectedRoute {...(roles ? { roles } : {})}>{el}</ProtectedRoute>;

export function App() {
  return (
    <Routes>
      <Route path="/" element={<LandingPage />} />
      <Route path="/login" element={<LoginPage />} />
      <Route path="/app" element={guard(undefined, <AppShell />)}>
        <Route index element={<Home />} />
        <Route path="inventario" element={guard(ORG, <InventoryPage />)} />
        <Route path="movimientos" element={guard(ORG, <MovementsPage />)} />
        <Route path="productos" element={guard(ORG, <ProductsPage />)} />
        <Route path="bodegas" element={guard(ADMIN, <WarehousesPage />)} />
        <Route path="usuarios" element={guard(ADMIN, <UsersPage />)} />
        <Route path="cuenta" element={<AccountPage />} />
        <Route path="organizaciones" element={guard(["SUPER_ADMIN"], <OrganizationsPage />)} />
        <Route path="*" element={<NotFound />} />
      </Route>
      <Route path="*" element={<NotFound />} />
    </Routes>
  );
}
