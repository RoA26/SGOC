import { BrowserRouter, Navigate, Route, Routes } from 'react-router-dom'
import { AppLayout } from '@/components/layout/AppLayout'
import { RutaInvitado } from '@/features/auth/RutaInvitado'
import { RutaProtegida } from '@/features/auth/RutaProtegida'
import Inicio from '@/pages/Inicio'
import Login from '@/pages/Login'
import Productos from '@/pages/Productos'
import Proveedores from '@/pages/Proveedores'
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
            <Route path="/proveedores" element={<Proveedores />} />
            <Route path="/productos" element={<Productos />} />
          </Route>
        </Route>

        <Route path="*" element={<Navigate to="/" replace />} />
      </Routes>
    </BrowserRouter>
  )
}
