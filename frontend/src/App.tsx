import { BrowserRouter, Navigate, Route, Routes } from 'react-router-dom'
import { RutaInvitado } from '@/features/auth/RutaInvitado'
import { RutaProtegida } from '@/features/auth/RutaProtegida'
import Inicio from '@/pages/Inicio'
import Login from '@/pages/Login'

export default function App() {
  return (
    <BrowserRouter>
      <Routes>
        <Route element={<RutaInvitado />}>
          <Route path="/login" element={<Login />} />
        </Route>

        <Route element={<RutaProtegida />}>
          <Route path="/" element={<Inicio />} />
        </Route>

        <Route path="*" element={<Navigate to="/" replace />} />
      </Routes>
    </BrowserRouter>
  )
}
