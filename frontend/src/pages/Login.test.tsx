import { render, screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { AxiosError, AxiosHeaders, type AxiosResponse } from 'axios'
import { MemoryRouter } from 'react-router-dom'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import { login } from '@/features/auth/authApi'
import { useAuthStore } from '@/store/authStore'
import Login from './Login'

vi.mock('@/features/auth/authApi', () => ({ login: vi.fn() }))
const loginMock = vi.mocked(login)

function error(status: number, data: unknown, headers: Record<string, string> = {}) {
  return new AxiosError('error', 'ERR_BAD_REQUEST', undefined, undefined, {
    status,
    data,
    statusText: '',
    headers: new AxiosHeaders(headers),
    config: {},
  } as AxiosResponse)
}

async function entrar(usuario: string, clave: string) {
  await userEvent.clear(screen.getByLabelText('Usuario', { exact: true }))
  await userEvent.type(screen.getByLabelText('Usuario', { exact: true }), usuario)
  await userEvent.type(screen.getByLabelText('Contraseña', { exact: true }), clave)
  await userEvent.click(screen.getByRole('button', { name: 'Ingresar' }))
}

describe('Login: límite de intentos', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    useAuthStore.getState().clearSession()
    render(
      <MemoryRouter>
        <Login />
      </MemoryRouter>,
    )
  })

  it('muestra el mensaje del 429 y deja el formulario usable', async () => {
    const detalle = 'Demasiados intentos fallidos con este usuario. Espera 15 minutos e inténtalo de nuevo.'
    loginMock.mockRejectedValueOnce(error(429, { title: 'Demasiados intentos', status: 429, detail: detalle }, { 'retry-after': '900' }))
    await entrar('ana.ruiz', 'ClaveSegura2026')

    expect(await screen.findByRole('alert')).toHaveTextContent(detalle)
    expect(useAuthStore.getState().accessToken).toBeNull()
    expect(screen.getByLabelText('Contraseña', { exact: true })).toHaveValue('')
    expect(screen.getByRole('button', { name: 'Ingresar' })).toBeEnabled()
  })

  it('el 401 de siempre no cambia', async () => {
    loginMock.mockRejectedValueOnce(error(401, { title: 'Credenciales inválidas', status: 401, detail: 'Usuario o contraseña incorrectos.' }))
    await entrar('ana.ruiz', 'incorrecta')
    expect(await screen.findByRole('alert')).toHaveTextContent('Usuario o contraseña incorrectos.')
  })
})
