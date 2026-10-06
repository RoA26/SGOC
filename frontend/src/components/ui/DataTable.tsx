import { ChevronLeft, ChevronRight, Inbox, RotateCw } from 'lucide-react'
import type { Key, ReactNode } from 'react'
import { cn } from '@/lib/cn'
import type { PageMetadata } from '@/services/types'

export interface Column<T> {
  id: string
  header: ReactNode
  cell: (row: T) => ReactNode
  align?: 'left' | 'right'
  /** Clases extra para la celda (ancho, ocultar en móvil…). */
  className?: string
}

interface DataTableProps<T> {
  /** Descripción para lectores de pantalla. */
  caption: string
  columns: Column<T>[]
  rows: T[]
  rowKey: (row: T) => Key
  loading?: boolean
  error?: string | null
  onRetry?: () => void
  emptyMessage?: ReactNode
  page?: PageMetadata
  onPageChange?: (page: number) => void
}

const SKELETON_ROWS = 5

export function DataTable<T>({
  caption,
  columns,
  rows,
  rowKey,
  loading = false,
  error,
  onRetry,
  emptyMessage = 'No hay registros.',
  page,
  onPageChange,
}: DataTableProps<T>) {
  const firstLoad = loading && rows.length === 0

  return (
    <div className="u-card overflow-hidden">
      <div className="relative overflow-x-auto">
        <table className="w-full border-collapse text-left text-sm" aria-busy={loading}>
          <caption className="sr-only">{caption}</caption>
          <thead className="border-b border-border bg-surface-muted/60 text-xs tracking-wide text-foreground-muted uppercase">
            <tr>
              {columns.map((column) => (
                <th
                  key={column.id}
                  scope="col"
                  className={cn('px-4 py-3 font-medium whitespace-nowrap', column.align === 'right' && 'text-right', column.className)}
                >
                  {column.header}
                </th>
              ))}
            </tr>
          </thead>

          <tbody className={cn('divide-y divide-border transition-opacity', loading && !firstLoad && 'opacity-50')}>
            {firstLoad &&
              Array.from({ length: SKELETON_ROWS }, (_, index) => (
                <tr key={`skeleton-${index}`}>
                  {columns.map((column) => (
                    <td key={column.id} className={cn('px-4 py-3.5', column.className)}>
                      <div className="h-4 w-full max-w-40 animate-pulse rounded bg-surface-muted" />
                    </td>
                  ))}
                </tr>
              ))}

            {!firstLoad &&
              rows.map((row) => (
                <tr key={rowKey(row)} className="hover:bg-surface-muted/50">
                  {columns.map((column) => (
                    <td
                      key={column.id}
                      className={cn('px-4 py-3 align-middle', column.align === 'right' && 'text-right', column.className)}
                    >
                      {column.cell(row)}
                    </td>
                  ))}
                </tr>
              ))}

            {!loading && rows.length === 0 && (
              <tr>
                <td colSpan={columns.length} className="px-4 py-14 text-center">
                  {error ? (
                    <div role="alert" className="mx-auto flex max-w-sm flex-col items-center gap-3 text-sm text-danger">
                      <p>{error}</p>
                      {onRetry && (
                        <button type="button" className="u-btn u-btn--ghost" onClick={onRetry}>
                          <RotateCw className="size-4" aria-hidden="true" />
                          Reintentar
                        </button>
                      )}
                    </div>
                  ) : (
                    <div className="flex flex-col items-center gap-2 text-sm text-foreground-muted">
                      <Inbox className="size-8 opacity-50" aria-hidden="true" />
                      {emptyMessage}
                    </div>
                  )}
                </td>
              </tr>
            )}
          </tbody>
        </table>
      </div>

      {error && rows.length > 0 && (
        <p role="alert" className="border-t border-border bg-danger-surface px-4 py-2 text-sm text-danger">
          {error}
        </p>
      )}

      {page && onPageChange && page.totalElements > 0 && (
        <Pagination page={page} onPageChange={onPageChange} disabled={loading} />
      )}
    </div>
  )
}

interface PaginationProps {
  page: PageMetadata
  onPageChange: (page: number) => void
  disabled: boolean
}

function Pagination({ page, onPageChange, disabled }: PaginationProps) {
  const from = page.number * page.size + 1
  const to = Math.min(from + page.size - 1, page.totalElements)
  const isFirst = page.number === 0
  const isLast = page.number >= page.totalPages - 1

  return (
    <nav
      aria-label="Paginación"
      className="flex flex-col gap-3 border-t border-border px-4 py-3 text-sm text-foreground-muted sm:flex-row sm:items-center sm:justify-between"
    >
      <p>
        Mostrando <span className="font-medium text-foreground">{from}</span>–
        <span className="font-medium text-foreground">{to}</span> de{' '}
        <span className="font-medium text-foreground">{page.totalElements}</span>
      </p>
      <div className="flex items-center gap-2">
        <span className="mr-2">
          Página {page.number + 1} de {Math.max(page.totalPages, 1)}
        </span>
        <button
          type="button"
          className="u-btn u-btn--ghost h-9 px-2.5"
          onClick={() => onPageChange(page.number - 1)}
          disabled={disabled || isFirst}
          aria-label="Página anterior"
        >
          <ChevronLeft className="size-4" aria-hidden="true" />
        </button>
        <button
          type="button"
          className="u-btn u-btn--ghost h-9 px-2.5"
          onClick={() => onPageChange(page.number + 1)}
          disabled={disabled || isLast}
          aria-label="Página siguiente"
        >
          <ChevronRight className="size-4" aria-hidden="true" />
        </button>
      </div>
    </nav>
  )
}
