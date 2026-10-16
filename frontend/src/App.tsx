import { lazy, Suspense } from 'react'
import { BrowserRouter, Navigate, Route, Routes } from 'react-router-dom'
import { AppLayout } from '@/components/layout/AppLayout'
import { ROLES_GESTION, ROLES_INVITACION, ROLES_PLATAFORMA } from '@/features/auth/permisos'
import { RutaConRol } from '@/features/auth/RutaConRol'
import { RutaInvitado } from '@/features/auth/RutaInvitado'
import { RutaProtegida } from '@/features/auth/RutaProtegida'
import Inicio from '@/pages/Inicio'
import Login from '@/pages/Login'
import Perfil from '@/pages/Perfil'
import Productos from '@/pages/Productos'
import Proveedores from '@/pages/Proveedores'
import Solicitudes from '@/pages/Solicitudes'
import Registro from '@/pages/Registro'

// Módulos que no todos los roles usan: se descargan al visitarlos (el paquete inicial no crece).
const EmpresaDetalle = lazy(() => import('@/pages/admin/EmpresaDetalle'))
const Empresas = lazy(() => import('@/pages/admin/Empresas'))
const Invitaciones = lazy(() => import('@/pages/admin/Invitaciones'))
const Inventario = lazy(() => import('@/pages/Inventario'))
const OrdenCompraDetalle = lazy(() => import('@/pages/OrdenCompraDetalle'))
const OrdenesCompra = lazy(() => import('@/pages/OrdenesCompra'))
const Recepciones = lazy(() => import('@/pages/Recepciones'))

export default function App() {
  return (
    <BrowserRouter>
      <Suspense fallback={<p className="p-10 text-sm text-foreground-muted">Cargando…</p>}>
        <Routes>
          <Route element={<RutaInvitado />}>
            <Route path="/login" element={<Login />} />
            <Route path="/registro" element={<Registro />} />
          </Route>

          <Route element={<RutaProtegida />}>
            <Route element={<AppLayout />}>
              <Route path="/" element={<Inicio />} />
              <Route path="/solicitudes" element={<Solicitudes />} />
              <Route path="/perfil" element={<Perfil />} />
              {/* Existencias: toda la empresa (sin costos). Ajustes y kárdex, según el rol. */}
              <Route path="/inventario" element={<Inventario />} />

              {/* Catálogo y compras: gestores (ADMIN_EMPRESA de su empresa; SUPER_ADMIN en modo global o supervisando, solo lectura). */}
              <Route element={<RutaConRol roles={ROLES_GESTION} />}>
                <Route path="/productos" element={<Productos />} />
                <Route path="/proveedores" element={<Proveedores />} />
                <Route path="/ordenes-compra" element={<OrdenesCompra />} />
                <Route path="/ordenes-compra/:id" element={<OrdenCompraDetalle />} />
                <Route path="/recepciones" element={<Recepciones />} />
              </Route>

              {/* Invitaciones de trabajadores: solo el ADMIN_EMPRESA, para su empresa. */}
              <Route element={<RutaConRol roles={ROLES_INVITACION} />}>
                <Route path="/admin/invitaciones" element={<Invitaciones />} />
              </Route>

              {/* Administración de la plataforma: empresas y sus administradores (cuenta raíz). */}
              <Route element={<RutaConRol roles={ROLES_PLATAFORMA} />}>
                <Route path="/admin/empresas" element={<Empresas />} />
                <Route path="/admin/empresas/:id" element={<EmpresaDetalle />} />
              </Route>
            </Route>
          </Route>

          <Route path="*" element={<Navigate to="/" replace />} />
        </Routes>
      </Suspense>
    </BrowserRouter>
  )
}
