import { BrowserRouter, Navigate, Route, Routes } from 'react-router-dom'
import {
  AuthProvider,
  DEFAULT_AUTHENTICATED_PATH,
  GuestRoute,
  LOGIN_PATH,
  ProtectedRoute,
} from '@/features/auth'
import Dashboard from '@/pages/Dashboard'
import Login from '@/pages/Login'

/**
 * Mapa de rutas:
 *   /           -> /dashboard con sesión; sin token, <ProtectedRoute> lleva a /login
 *   /login      -> solo invitados (con sesión activa redirige al dashboard)
 *   /dashboard  -> privada
 *   *           -> /
 */
export default function App() {
  return (
    <AuthProvider>
      <BrowserRouter>
        <Routes>
          <Route element={<GuestRoute />}>
            <Route path={LOGIN_PATH} element={<Login />} />
          </Route>

          <Route element={<ProtectedRoute />}>
            <Route path="/" element={<Navigate to={DEFAULT_AUTHENTICATED_PATH} replace />} />
            <Route path={DEFAULT_AUTHENTICATED_PATH} element={<Dashboard />} />
          </Route>

          <Route path="*" element={<Navigate to="/" replace />} />
        </Routes>
      </BrowserRouter>
    </AuthProvider>
  )
}
