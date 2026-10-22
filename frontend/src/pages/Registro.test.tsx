import { render, screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { AxiosError, type AxiosResponse } from 'axios'
import { MemoryRouter, Route, Routes } from 'react-router-dom'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import { login, registrarUsuario } from '@/features/auth/authApi'
import type { UsuarioResponse } from '@/features/auth/types'
import { useAuthStore } from '@/store/authStore'
import Registro from './Registro'

vi.mock('@/features/auth/authApi', () => ({ login: vi.fn(), registrarUsuario: vi.fn() }))
const registrarMock = vi.mocked(registrarUsuario)
const loginMock = vi.mocked(login)

const USUARIO: UsuarioResponse = {
  id: 5,
  username: 'ana.ruiz',
  email: 'ana@empresa.com',
  nombre: 'Ana Ruiz',
  datosPersonalesCompletos: true,
  rol: 'TRABAJADOR',
  empresaId: 2,
  empresaNombre: 'Ferretería Norte',
  empresaCodigo: 'FENO01',
}

function renderizar(ruta = '/registro') {
  return render(
    <MemoryRouter initialEntries={[ruta]}>
      <Routes>
        <Route path="/registro" element={<Registro />} />
        <Route path="/login" element={<p>Pantalla de login</p>} />
      </Routes>
    </MemoryRouter>,
  )
}

async function completarDatos() {
  await userEvent.type(screen.getByLabelText(/^Nombres/), 'Ana')
  await userEvent.type(screen.getByLabelText(/^Apellidos/), 'Ruiz')
  await userEvent.type(screen.getByLabelText(/^Usuario/), 'ana.ruiz')
  await userEvent.type(screen.getByLabelText(/^Correo electrónico/), 'ana@empresa.com')
  await userEvent.type(screen.getByLabelText(/^Contraseña/), 'ClaveSegura2026')
}

function error400(errors: Record<string, string>) {
  return new AxiosError('error', 'ERR_BAD_REQUEST', undefined, undefined, {
    status: 400,
    data: { title: 'Solicitud no válida', status: 400, errors },
    statusText: '',
    headers: {},
    config: {},
  } as AxiosResponse)
}

describe('Registro: invitación o código de empresa', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    useAuthStore.getState().clearSession()
  })

  it('con el código de la empresa crea una cuenta pendiente, lo explica y no inicia sesión', async () => {
    registrarMock.mockResolvedValue({ ...USUARIO, estado: 'PENDIENTE' })
    renderizar()
    await userEvent.click(screen.getByRole('radio', { name: /Tengo el código de mi empresa/ }))
    await userEvent.type(screen.getByLabelText(/^Código de tu empresa/), ' 4hpr-t3vw-7kq2-m9xa ')
    await completarDatos()
    await userEvent.click(screen.getByRole('button', { name: 'Crear cuenta' }))

    expect(registrarMock).toHaveBeenCalledWith({
      nombres: 'Ana',
      apellidos: 'Ruiz',
      username: 'ana.ruiz',
      email: 'ana@empresa.com',
      password: 'ClaveSegura2026',
      codigoEmpresa: '4HPR-T3VW-7KQ2-M9XA',
    })
    expect(await screen.findByRole('heading', { name: 'Cuenta pendiente de aprobación' })).toBeInTheDocument()
    expect(screen.getByRole('status')).toHaveTextContent('Solicitud enviada a Ferretería Norte')
    expect(screen.getByRole('status')).toHaveTextContent('debe aprobar la cuenta ana.ruiz')
    expect(loginMock).not.toHaveBeenCalled()
    expect(useAuthStore.getState().accessToken).toBeNull()
    expect(screen.getByRole('link', { name: 'Ir a iniciar sesión' })).toHaveAttribute('href', '/login')
  })

  it('muestra junto al campo el error del servidor para un código de empresa inválido', async () => {
    registrarMock.mockRejectedValue(error400({ codigoEmpresa: 'El código de empresa no es válido.' }))
    renderizar()
    await userEvent.click(screen.getByRole('radio', { name: /Tengo el código de mi empresa/ }))
    await userEvent.type(screen.getByLabelText(/^Código de tu empresa/), 'NO-EXISTE')
    await completarDatos()
    await userEvent.click(screen.getByRole('button', { name: 'Crear cuenta' }))

    expect(await screen.findByText('El código de empresa no es válido.')).toBeInTheDocument()
    expect(screen.getByLabelText(/^Código de tu empresa/)).toHaveAttribute('aria-invalid', 'true')
    expect(screen.queryByText('Cuenta pendiente de aprobación')).not.toBeInTheDocument()
    expect(loginMock).not.toHaveBeenCalled()
  })

  it('muestra el 429 del límite de registros sin crear la cuenta', async () => {
    const detalle = 'Demasiados intentos de registro con este código. Espera 60 minutos e inténtalo de nuevo.'
    registrarMock.mockRejectedValue(
      new AxiosError('error', 'ERR_BAD_REQUEST', undefined, undefined, {
        status: 429,
        data: { title: 'Demasiados intentos', status: 429, detail: detalle, reintentarEnSegundos: 3600 },
        statusText: '',
        headers: { 'retry-after': '3600' },
        config: {},
      } as unknown as AxiosResponse),
    )
    renderizar()
    await userEvent.click(screen.getByRole('radio', { name: /Tengo el código de mi empresa/ }))
    await userEvent.type(screen.getByLabelText(/^Código de tu empresa/), '4HPR-T3VW-7KQ2-M9XA')
    await completarDatos()
    await userEvent.click(screen.getByRole('button', { name: 'Crear cuenta' }))

    expect(await screen.findByRole('alert')).toHaveTextContent(detalle)
    expect(screen.queryByText('Cuenta pendiente de aprobación')).not.toBeInTheDocument()
    expect(loginMock).not.toHaveBeenCalled()
  })

  it('exige el código de la vía elegida antes de llamar al backend', async () => {
    renderizar()
    await userEvent.click(screen.getByRole('radio', { name: /Tengo el código de mi empresa/ }))
    await completarDatos()
    await userEvent.click(screen.getByRole('button', { name: 'Crear cuenta' }))
    expect(await screen.findByText('El código de tu empresa es obligatorio.')).toBeInTheDocument()
    // El foco va al primer campo con error en el orden de la pantalla: el código.
    expect(screen.getByLabelText(/^Código de tu empresa/)).toHaveFocus()
    expect(registrarMock).not.toHaveBeenCalled()
  })

  it('el enlace de invitación sigue funcionando: cuenta activa e inicio de sesión', async () => {
    registrarMock.mockResolvedValue({ ...USUARIO, estado: 'ACTIVO' })
    loginMock.mockResolvedValue({ accessToken: 't', tokenType: 'Bearer', expiresIn: 3600, usuario: { ...USUARIO, estado: 'ACTIVO' } })
    renderizar('/registro?codigo=7KQ2-M9XA-4HPR-T3VW')
    expect(screen.getByRole('radio', { name: /Tengo una invitación/ })).toBeChecked()
    await completarDatos()
    await userEvent.click(screen.getByRole('button', { name: 'Crear cuenta' }))

    expect(registrarMock).toHaveBeenCalledWith(expect.objectContaining({ codigoInvitacion: '7KQ2-M9XA-4HPR-T3VW' }))
    expect(registrarMock.mock.calls[0]?.[0]).not.toHaveProperty('codigoEmpresa')
    expect(loginMock).toHaveBeenCalledWith({ username: 'ana.ruiz', password: 'ClaveSegura2026' })
  })

  it('con ?via=empresa abre la vía del código de la empresa con el código del enlace', () => {
    renderizar('/registro?via=empresa&codigo=ABCD-EFGH-JKLM-NPQR')
    expect(screen.getByLabelText(/Tengo el código de mi empresa/)).toBeChecked()
    expect(screen.getByLabelText(/^Código de tu empresa/)).toHaveValue('ABCD-EFGH-JKLM-NPQR')
    expect(screen.queryByLabelText(/^Código de invitación/)).not.toBeInTheDocument()
  })
})
