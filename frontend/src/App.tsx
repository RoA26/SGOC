import { lazy, Suspense } from 'react'
import { BrowserRouter, Navigate, Route, Routes } from 'react-router-dom'
import { AppLayout } from '@/components/layout/AppLayout'
import { CargandoVista } from '@/components/layout/CargandoVista'
import { ROLES_EMPRESA, ROLES_GESTION, ROLES_INVITACION, ROLES_PLATAFORMA } from '@/features/auth/permisos'
import { RutaConRol } from '@/features/auth/RutaConRol'
import { RutaInvitado } from '@/features/auth/RutaInvitado'
import { RutaProtegida } from '@/features/auth/RutaProtegida'
import Bienvenida from '@/pages/Bienvenida'
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
const Personal = lazy(() => import('@/pages/admin/Personal'))
const Inventario = lazy(() => import('@/pages/Inventario'))
const OrdenCompraDetalle = lazy(() => import('@/pages/OrdenCompraDetalle'))
const OrdenesCompra = lazy(() => import('@/pages/OrdenesCompra'))
const Recepciones = lazy(() => import('@/pages/Recepciones'))
const Entregas = lazy(() => import('@/pages/Entregas'))
const Calendario = lazy(() => import('@/pages/Calendario'))
const RecepcionMercancia = lazy(() => import('@/pages/RecepcionMercancia'))
const RecepcionOrden = lazy(() => import('@/pages/RecepcionOrden'))
const PuntoVenta = lazy(() => import('@/pages/PuntoVenta'))
const Ventas = lazy(() => import('@/pages/Ventas'))
const VentaDetalle = lazy(() => import('@/pages/VentaDetalle'))
const GestionComercial = lazy(() => import('@/pages/GestionComercial'))
const Reportes = lazy(() => import('@/pages/Reportes'))

export default function App() {
  return (
    <BrowserRouter>
      <Suspense
        fallback={
          <div className="mx-auto max-w-6xl px-4 py-10 sm:px-6 lg:px-8">
            <CargandoVista />
          </div>
        }
      >
        <Routes>
          <Route element={<RutaInvitado />}>
            <Route path="/login" element={<Login />} />
            <Route path="/registro" element={<Registro />} />
          </Route>

          {/* En `/`, sin sesión: la página pública; con sesión: el panel de inicio. */}
          <Route element={<RutaProtegida publicaEnRaiz={<Bienvenida />} />}>
            <Route element={<AppLayout />}>
              <Route path="/" element={<Inicio />} />
              <Route path="/solicitudes" element={<Solicitudes />} />
              <Route path="/perfil" element={<Perfil />} />
              {/* Existencias: toda la empresa (sin costos). Ajustes y kárdex, según el rol. */}
              <Route path="/inventario" element={<Inventario />} />
              {/* Calendario operativo: toda la empresa (sin costos); la página avisa en el modo global. */}
              <Route path="/calendario" element={<Calendario />} />

              {/* Ficha operativa de una entrega: toda la empresa (registra quien tiene permiso; el SUPER_ADMIN la consulta). */}
              <Route path="/recepcion/:ordenId" element={<RecepcionOrden />} />
              {/* Recepción operativa: personas de la empresa (el trabajador recibe; el administrador también puede). */}
              <Route element={<RutaConRol roles={ROLES_EMPRESA} />}>
                <Route path="/recepcion" element={<RecepcionMercancia />} />
                {/* Punto de venta: venden las personas de la empresa (nunca el SUPER_ADMIN). */}
                <Route path="/pos" element={<PuntoVenta />} />
              </Route>
              {/* Ventas: el trabajador ve las suyas; la gestión, todas (el backend lo impone). */}
              <Route path="/ventas" element={<Ventas />} />
              <Route path="/ventas/:id" element={<VentaDetalle />} />

              {/* Catálogo y compras: gestores (ADMINISTRADOR_COMERCIAL de su empresa; SUPER_ADMIN en modo global o supervisando, solo lectura). */}
              <Route element={<RutaConRol roles={ROLES_GESTION} />}>
                <Route path="/productos" element={<Productos />} />
                <Route path="/proveedores" element={<Proveedores />} />
                <Route path="/ordenes-compra" element={<OrdenesCompra />} />
                <Route path="/ordenes-compra/:id" element={<OrdenCompraDetalle />} />
                <Route path="/recepciones" element={<Recepciones />} />
                <Route path="/entregas" element={<Entregas />} />
                {/* Precios, costos, rentabilidad y reportes: gestión (cambios solo del ADMINISTRADOR_COMERCIAL). */}
                <Route path="/comercial" element={<GestionComercial />} />
                <Route path="/reportes" element={<Reportes />} />
              </Route>

              {/* Personal e invitaciones de trabajadores: solo el ADMINISTRADOR_COMERCIAL, para su empresa. */}
              <Route element={<RutaConRol roles={ROLES_INVITACION} />}>
                <Route path="/admin/personal" element={<Personal />} />
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
