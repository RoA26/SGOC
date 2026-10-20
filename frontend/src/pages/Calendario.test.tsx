import { render, screen, waitFor, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { MemoryRouter, Route, Routes, useLocation } from 'react-router-dom'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import { calendarioService, type Calendario, type EventoCalendario } from '@/services/calendarioService'
import { iniciarSesionComo } from '@/test/sesion'
import CalendarioPage from './Calendario'

vi.mock('@/services/calendarioService', () => ({ calendarioService: { eventos: vi.fn() } }))
const eventosMock = vi.mocked(calendarioService.eventos)

function evento(parcial: Partial<EventoCalendario>): EventoCalendario {
  return {
    id: 'entrega-1', tipo: 'ENTREGA', estado: 'PROGRAMADA', fecha: '2026-11-03', todoElDia: true,
    titulo: 'Entrega OC-00001', detalle: 'Aceros Andinos', ordenId: 1, ordenNumero: 1, proveedor: 'Aceros Andinos',
    confirmada: false, parcial: false, incidencias: 0, ...parcial,
  }
}

function respuesta(desde: string, hasta: string, eventos: EventoCalendario[]): Calendario {
  return { desde, hasta, hoy: '2026-11-03', zonaHoraria: 'America/Bogota', eventos, truncado: false }
}

function Ubicacion() {
  const location = useLocation()
  return <output data-testid="ubicacion">{location.pathname + location.search}</output>
}

function renderizar(url = '/calendario?vista=mes&fecha=2026-11-03') {
  return render(
    <MemoryRouter initialEntries={[url]}>
      <Routes>
        <Route path="/calendario" element={<><CalendarioPage /><Ubicacion /></>} />
        <Route path="*" element={<Ubicacion />} />
      </Routes>
    </MemoryRouter>,
  )
}

const EVENTOS = [
  evento({}),
  evento({ id: 'entrega-2', titulo: 'Entrega OC-00002', estado: 'RETRASADA', fecha: '2026-11-02', ordenId: 2, ordenNumero: 2 }),
  evento({
    id: 'recepcion-9', tipo: 'RECEPCION', estado: 'PARCIAL', titulo: 'Recepción OC-00003', fecha: '2026-11-03', todoElDia: false,
    horaInicio: '09:15', recepcionId: 9, ordenId: 3, ordenNumero: 3, incidencias: 1,
  }),
  evento({ id: 'emision-4', tipo: 'ORDEN_EMITIDA', estado: 'EMITIDA', titulo: 'Emitida OC-00004', todoElDia: false, horaInicio: '11:00', ordenId: 4 }),
]

describe('Calendario', () => {
  beforeEach(() => {
    eventosMock.mockReset()
    eventosMock.mockImplementation(async (desde, hasta) => respuesta(desde, hasta, EVENTOS))
  })

  it('pide el período visible, pinta los eventos y navega entre meses', async () => {
    iniciarSesionComo('ADMINISTRADOR_COMERCIAL')
    const user = userEvent.setup()
    renderizar()

    expect(await screen.findByRole('button', { name: /Entrega OC-00001, entregas programadas, Programada \(estimada\), todo el día/ }))
      .toBeInTheDocument()
    expect(eventosMock).toHaveBeenCalledWith('2026-10-26', '2026-12-06', expect.anything())
    // Recepción con hora real; entrega sin franja = todo el día (sin hora inventada).
    expect(screen.getByRole('button', { name: /Recepción OC-00003.*09:15/ })).toBeInTheDocument()
    expect(screen.getByRole('button', { name: /Entrega OC-00002.*Retrasada/ })).toBeInTheDocument()

    await user.click(screen.getByRole('button', { name: 'Período siguiente' }))
    await waitFor(() => expect(eventosMock).toHaveBeenLastCalledWith('2026-11-30', '2027-01-10', expect.anything()))
    expect(screen.getByTestId('ubicacion')).toHaveTextContent('fecha=2026-12-03')

    await user.click(screen.getByRole('radio', { name: 'Semana' }))
    await waitFor(() => expect(eventosMock).toHaveBeenLastCalledWith('2026-11-30', '2026-12-06', expect.anything()))
    await user.click(screen.getByRole('button', { name: 'Hoy' }))
    await waitFor(() => expect(eventosMock).toHaveBeenLastCalledWith('2026-11-02', '2026-11-08', expect.anything()))
    await user.click(screen.getByRole('radio', { name: 'Día' }))
    expect(await screen.findByText('Todo el día')).toBeInTheDocument()
    expect(screen.getByText('Con hora')).toBeInTheDocument()
  })

  it('filtra por tipo de evento y por estado', async () => {
    iniciarSesionComo('ADMINISTRADOR_COMERCIAL')
    const user = userEvent.setup()
    renderizar()
    await screen.findByRole('button', { name: /Entrega OC-00001,/ })

    await user.click(screen.getByRole('button', { name: /Recepciones/, pressed: true }))
    expect(screen.queryByRole('button', { name: /Recepción OC-00003/ })).not.toBeInTheDocument()
    expect(screen.getByRole('button', { name: /Recepciones/, pressed: false })).toBeInTheDocument()

    await user.selectOptions(screen.getByRole('combobox', { name: 'Estado' }), 'RETRASADAS')
    expect(screen.queryByRole('button', { name: /Entrega OC-00001,/ })).not.toBeInTheDocument()
    expect(screen.getByRole('button', { name: /Entrega OC-00002,/ })).toBeInTheDocument()

    await user.selectOptions(screen.getByRole('combobox', { name: 'Estado' }), 'COMPLETAS')
    expect(await screen.findByText('Ningún evento coincide con los filtros.')).toBeInTheDocument()
  })

  it('el gerente abre la orden y puede reprogramar; el trabajador va a registrar la recepción', async () => {
    iniciarSesionComo('ADMINISTRADOR_COMERCIAL')
    const user = userEvent.setup()
    const { unmount } = renderizar()
    await user.click(await screen.findByRole('button', { name: /Entrega OC-00001,/ }))
    let dialogo = screen.getByRole('dialog', { name: 'Entrega OC-00001' })
    expect(within(dialogo).getByText(/no una recepción confirmada/)).toBeInTheDocument()
    expect(within(dialogo).getByRole('link', { name: 'Ver orden' })).toHaveAttribute('href', '/ordenes-compra/1')
    expect(within(dialogo).getByRole('button', { name: 'Reprogramar' })).toBeInTheDocument()
    unmount()

    iniciarSesionComo('TRABAJADOR')
    renderizar()
    // El trabajador no ve emisiones (son de gestión).
    expect(await screen.findByRole('button', { name: /Entrega OC-00001,/ })).toBeInTheDocument()
    expect(screen.queryByRole('button', { name: /Órdenes emitidas/ })).not.toBeInTheDocument()
    await user.click(screen.getByRole('button', { name: /Entrega OC-00001,/ }))
    dialogo = screen.getByRole('dialog', { name: 'Entrega OC-00001' })
    expect(within(dialogo).queryByRole('link', { name: 'Ver orden' })).not.toBeInTheDocument()
    expect(within(dialogo).queryByRole('button', { name: 'Reprogramar' })).not.toBeInTheDocument()
    expect(within(dialogo).getByRole('link', { name: 'Registrar recepción' })).toHaveAttribute('href', '/recepcion/1')
  })

  it('muestra los estados vacío y de error (con reintento)', async () => {
    iniciarSesionComo('TRABAJADOR')
    eventosMock.mockImplementationOnce(async (desde, hasta) => respuesta(desde, hasta, []))
    const user = userEvent.setup()
    const { unmount } = renderizar()
    expect(await screen.findByText('No hay eventos en este período.')).toBeInTheDocument()
    unmount()

    eventosMock.mockRejectedValueOnce(new Error('caída'))
    renderizar()
    const alerta = await screen.findByRole('alert')
    expect(alerta).toHaveTextContent('No se pudo cargar el calendario')
    await user.click(within(alerta).getByRole('button', { name: 'Reintentar' }))
    expect(await screen.findByRole('button', { name: /Entrega OC-00001,/ })).toBeInTheDocument()
  })

  it('en modo global el SUPER_ADMIN debe elegir empresa (no se pide nada)', async () => {
    iniciarSesionComo('SUPER_ADMIN')
    renderizar()
    expect(await screen.findByText(/el calendario es de una empresa/)).toBeInTheDocument()
    expect(eventosMock).not.toHaveBeenCalled()
  })
})
