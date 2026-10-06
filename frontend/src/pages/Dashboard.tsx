import { Button } from '@/components/ui'
import { useAuth, useCurrentUser } from '@/features/auth'
import { getTokenExpiration } from '@/lib/jwt'

const dateTimeFormatter = new Intl.DateTimeFormat('es', {
  dateStyle: 'medium',
  timeStyle: 'short',
})

export default function Dashboard() {
  const { token, logout } = useAuth()
  const user = useCurrentUser()
  const expiration = token ? getTokenExpiration(token) : null

  return (
    <div className="min-h-screen bg-slate-50">
      <header className="border-b border-slate-200 bg-white">
        <div className="mx-auto flex h-16 max-w-6xl items-center justify-between px-4 sm:px-6">
          <span className="flex items-center gap-2 text-lg font-bold text-slate-900">
            <span className="flex size-8 items-center justify-center rounded-lg bg-indigo-600 text-sm text-white">
              S
            </span>
            SGOC
          </span>
          <div className="flex items-center gap-3">
            <span className="hidden text-sm text-slate-500 sm:inline">{user.email}</span>
            <Button variant="secondary" onClick={logout}>
              Cerrar sesión
            </Button>
          </div>
        </div>
      </header>

      <main className="mx-auto max-w-6xl px-4 py-10 sm:px-6">
        <section className="rounded-2xl bg-white p-8 shadow-sm ring-1 ring-slate-200">
          <p className="text-sm font-medium text-indigo-600">Panel principal</p>
          <h1 className="mt-1 text-3xl font-bold tracking-tight text-slate-900">
            ¡Bienvenido, {user.full_name}!
          </h1>
          <p className="mt-3 text-slate-600">
            Has iniciado sesión correctamente en el Sistema de Gestión de Órdenes de Compra.
          </p>

          <dl className="mt-8 grid gap-4 sm:grid-cols-2">
            <div className="rounded-xl bg-slate-50 p-4">
              <dt className="text-xs font-medium tracking-wide text-slate-500 uppercase">
                Correo
              </dt>
              <dd className="mt-1 text-sm font-medium text-slate-900">{user.email}</dd>
            </div>
            <div className="rounded-xl bg-slate-50 p-4">
              <dt className="text-xs font-medium tracking-wide text-slate-500 uppercase">
                Sesión válida hasta
              </dt>
              <dd className="mt-1 text-sm font-medium text-slate-900">
                {expiration ? dateTimeFormatter.format(expiration) : '—'}
              </dd>
            </div>
          </dl>
        </section>
      </main>
    </div>
  )
}
