import { BrowserRouter, Navigate, Route, Routes } from 'react-router-dom'
import { AppLayout } from '@/components/layout/AppLayout'
import { ROLES_GESTION, ROLES_INVITACION, ROLES_PLATAFORMA } from '@/features/auth/permisos'
import { RutaConRol } from '@/features/auth/RutaConRol'
import { RutaInvitado } from '@/features/auth/RutaInvitado'
import { RutaProtegida } from '@/features/auth/RutaProtegida'
import EmpresaDetalle from '@/pages/admin/EmpresaDetalle'
import Empresas from '@/pages/admin/Empresas'
import Invitaciones from '@/pages/admin/Invitaciones'
import Inicio from '@/pages/Inicio'
import Login from '@/pages/Login'
import Perfil from '@/pages/Perfil'
import Productos from '@/pages/Productos'
import Proveedores from '@/pages/Proveedores'
import Solicitudes from '@/pages/Solicitudes'
import Registro from '@/pages/Registro'

export default function App() {
  return (
    <BrowserRouter>
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

            {/* Catálogo: gestores (ADMIN_EMPRESA de su empresa; SUPER_ADMIN en modo global o supervisando). */}
            <Route element={<RutaConRol roles={ROLES_GESTION} />}>
              <Route path="/productos" element={<Productos />} />
              <Route path="/proveedores" element={<Proveedores />} />
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
    </BrowserRouter>
  )
}
