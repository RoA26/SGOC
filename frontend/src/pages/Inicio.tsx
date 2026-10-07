import { Building2, Package } from 'lucide-react'
import { useEffect, useState } from 'react'
import axios from 'axios'
import { Link } from 'react-router-dom'
import { fetchCurrentUser } from '@/features/auth/authApi'
import type { UsuarioResponse } from '@/features/auth/types'
import { getApiErrorMessage } from '@/lib/errors'
import { useAuthStore } from '@/store/authStore'

type ApiCheck =
  | { status: 'loading' }
  | { status: 'ok'; usuario: UsuarioResponse }
  | { status: 'error'; message: string }

/** Página tras el login. Verifica la sesión contra GET /api/auth/me a través de Nginx. */
export default function Inicio() {
  const nombre = useAuthStore((state) => state.nombre)
  const [apiCheck, setApiCheck] = useState<ApiCheck>({ status: 'loading' })

  useEffect(() => {
    const controller = new AbortController()
    fetchCurrentUser(controller.signal)
      .then((usuario) => setApiCheck({ status: 'ok', usuario }))
      .catch((error: unknown) => {
        if (!axios.isCancel(error)) setApiCheck({ status: 'error', message: getApiErrorMessage(error) })
      })
    return () => controller.abort()
  }, [])

  return (
    <>
      <p className="text-xs font-medium tracking-widest text-foreground-muted uppercase">Panel principal</p>
      <h1 className="mt-3 font-serif text-5xl text-foreground">
        Hola, <em className="text-accent">{nombre}</em>
      </h1>
      <p className="mt-4 max-w-xl text-foreground-muted">
        Has iniciado sesión en el Sistema de Gestión de Órdenes de Compra.
      </p>

      <section className="u-card mt-10 max-w-xl p-6" aria-live="polite">
        <h2 className="text-sm font-semibold text-foreground">Conexión con el backend</h2>
        {apiCheck.status === 'loading' && (
          <p className="mt-2 text-sm text-foreground-muted">Verificando sesión…</p>
        )}
        {apiCheck.status === 'ok' && (
          <dl className="mt-4 grid grid-cols-[auto_1fr] gap-x-6 gap-y-2 text-sm">
            <dt className="text-foreground-muted">Estado</dt>
            <dd className="font-medium text-success">Sesión verificada por la API</dd>
            <dt className="text-foreground-muted">Usuario</dt>
            <dd className="text-foreground">{apiCheck.usuario.username}</dd>
            <dt className="text-foreground-muted">Correo</dt>
            <dd className="text-foreground">{apiCheck.usuario.email}</dd>
            <dt className="text-foreground-muted">Rol</dt>
            <dd className="text-foreground">{apiCheck.usuario.rol}</dd>
          </dl>
        )}
        {apiCheck.status === 'error' && (
          <p role="alert" className="u-alert u-alert--error mt-3">
            {apiCheck.message}
          </p>
        )}
      </section>

      <section aria-label="Catálogos" className="mt-6 grid max-w-xl gap-4 sm:grid-cols-2">
        <Link to="/proveedores" className="u-card group p-5 transition-colors hover:border-border-strong">
          <Building2 className="size-5 text-accent" aria-hidden="true" />
          <p className="mt-3 font-medium text-foreground group-hover:underline">Proveedores</p>
          <p className="text-sm text-foreground-muted">Empresas a las que se compra.</p>
        </Link>
        <Link to="/productos" className="u-card group p-5 transition-colors hover:border-border-strong">
          <Package className="size-5 text-accent" aria-hidden="true" />
          <p className="mt-3 font-medium text-foreground group-hover:underline">Productos</p>
          <p className="text-sm text-foreground-muted">Catálogo de bienes y servicios.</p>
        </Link>
      </section>
    </>
  )
}
