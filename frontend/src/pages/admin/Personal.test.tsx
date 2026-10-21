import { render, screen, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { MemoryRouter } from 'react-router-dom'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import { personalService } from '@/features/personal/personalApi'
import type { PersonalTrabajador } from '@/features/personal/types'
import { iniciarSesionComo } from '@/test/sesion'
import Personal from './Personal'

vi.mock('@/features/personal/personalApi', () => ({ personalService: { listar: vi.fn(), cambiarEstado: vi.fn() } }))
const listarMock = vi.mocked(personalService.listar)
const cambiarEstadoMock = vi.mocked(personalService.cambiarEstado)

const PAGINA = { size: 20, number: 0, totalPages: 1 }
const ANA: PersonalTrabajador = {
  id: 7,
  username: 'ana.ruiz',
  nombre: 'Ana Ruiz',
  estado: 'ACTIVO',
  creadoEn: '2026-09-01T10:00:00Z',
  actividad: { ultimaOperacion: { tipo: 'RECEPCION', en: '2026-10-20T14:00:00Z' }, dias: 30, solicitudes: 3, recepciones: 1, ventas: 0 },
}
const LUIS: PersonalTrabajador = {
  id: 8,
  username: 'luis.gil',
  nombre: 'Luis Gil',
  estado: 'PENDIENTE',
  creadoEn: '2026-10-21T08:00:00Z',
  actividad: { dias: 30, solicitudes: 0, recepciones: 0, ventas: 0 },
}

function renderizar(ruta = '/admin/personal') {
  return render(
    <MemoryRouter initialEntries={[ruta]}>
      <Personal />
    </MemoryRouter>,
  )
}

describe('Personal (ADMINISTRADOR_COMERCIAL)', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    iniciarSesionComo('ADMINISTRADOR_COMERCIAL')
    listarMock.mockResolvedValue({ content: [ANA, LUIS], page: { ...PAGINA, totalElements: 2 } })
    cambiarEstadoMock.mockResolvedValue()
  })

  it('muestra el estado real y la última operación, sin presentarla como conexión', async () => {
    renderizar()
    const tabla = await screen.findByRole('table', { name: 'Trabajadores de la empresa' })
    const [filaAna, filaLuis] = within(tabla).getAllByRole('row').slice(1) as [HTMLElement, HTMLElement]
    expect(within(filaAna).getByText('Activo')).toBeInTheDocument()
    expect(within(filaAna).getByText(/^Recepción de mercancía · /)).toBeInTheDocument()
    expect(within(filaAna).getByText('Últimos 30 días: 3 solicitudes · 1 recepción')).toBeInTheDocument()
    expect(within(filaLuis).getByText('Pendiente')).toBeInTheDocument()
    expect(within(filaLuis).getByText('Sin actividad registrada')).toBeInTheDocument()
    expect(screen.queryByText(/última conexión/i)).not.toBeInTheDocument()
    expect(screen.getByText(/no guarda los inicios de sesión/)).toBeInTheDocument()
  })

  it('el filtro de la URL llega al backend', async () => {
    renderizar('/admin/personal?estado=PENDIENTE')
    await screen.findByRole('table', { name: 'Trabajadores de la empresa' })
    expect(listarMock).toHaveBeenCalledWith(expect.objectContaining({ estado: 'PENDIENTE' }), expect.any(AbortSignal))
    expect(screen.getByRole('button', { name: 'Pendientes' })).toHaveAttribute('aria-pressed', 'true')
  })

  it('aprueba directamente a un pendiente y recarga la lista', async () => {
    renderizar()
    await userEvent.click(await screen.findByRole('button', { name: 'Aprobar a Luis Gil' }))
    expect(cambiarEstadoMock).toHaveBeenCalledWith(8, 'ACTIVO')
    expect(await screen.findByText('Luis Gil ya puede iniciar sesión y operar.')).toBeInTheDocument()
    expect(listarMock).toHaveBeenCalledTimes(2)
  })

  it('desactivar pide confirmación y no hace nada si se cancela', async () => {
    renderizar()
    await userEvent.click(await screen.findByRole('button', { name: 'Desactivar a Ana Ruiz' }))
    const dialogo = await screen.findByRole('dialog', { name: 'Desactivar cuenta' })
    await userEvent.click(within(dialogo).getByRole('button', { name: 'Cancelar' }))
    expect(cambiarEstadoMock).not.toHaveBeenCalled()

    await userEvent.click(screen.getByRole('button', { name: 'Desactivar a Ana Ruiz' }))
    await userEvent.click(within(await screen.findByRole('dialog')).getByRole('button', { name: 'Desactivar' }))
    expect(cambiarEstadoMock).toHaveBeenCalledWith(7, 'INACTIVO')
  })

  it('lista vacía y error de carga con reintento', async () => {
    listarMock.mockResolvedValueOnce({ content: [], page: { ...PAGINA, totalElements: 0 } })
    const { unmount } = renderizar()
    expect(await screen.findByText(/aún no tiene trabajadores/)).toBeInTheDocument()
    unmount()

    listarMock.mockRejectedValueOnce(new Error('Servidor no disponible'))
    renderizar()
    expect(await screen.findByRole('button', { name: /reintentar/i })).toBeInTheDocument()
  })
})
