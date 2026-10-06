import { Spinner } from './Spinner'

interface FullPageLoaderProps {
  label?: string
}

export function FullPageLoader({ label = 'Cargando…' }: FullPageLoaderProps) {
  return (
    <div
      role="status"
      aria-live="polite"
      className="flex min-h-screen flex-col items-center justify-center gap-3 bg-slate-50 text-slate-500"
    >
      <Spinner className="size-8 text-indigo-600" />
      <p className="text-sm">{label}</p>
    </div>
  )
}
