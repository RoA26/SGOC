import { fireEvent, render, screen, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { MemoryRouter, Outlet, Route, Routes, useLocation } from 'react-router-dom'
import { beforeEach, describe, expect, it } from 'vitest'
import { RutaInvitado } from '@/features/auth/RutaInvitado'
import { RutaProtegida } from '@/features/auth/RutaProtegida'
import { AccesoConCodigo } from '@/features/bienvenida/AccesoConCodigo'
import {
  CAMBIO_VENTA_DEMO,
  LINEAS_DEMO,
  TOTAL_DEMO,
  TOTAL_VENTA_DEMO,
  UNIDADES_PEDIDAS,
  UNIDADES_RECIBIDAS,
  VENTA_DEMO,
} from '@/features/bienvenida/datosDemo'
import { digitoControlEan13, modulosEan13 } from '@/features/bienvenida/ean13'
import { FlujoInteractivo } from '@/features/bienvenida/FlujoInteractivo'
import { useAuthStore } from '@/store/authStore'
import { iniciarSesionComo } from '@/test/sesion'
import Bienvenida from './Bienvenida'

/** Muestra la ruta actual (con su consulta) para comprobar redirecciones. */
function Ubicacion() {
  const { pathname, search } = useLocation()
  return <p data-testid="ubicacion">{pathname + search}</p>
}

/** Las mismas guardas que App.tsx, con páginas simuladas para lo que no se prueba aquí. */
function renderizarRutas(ruta: string) {
  return render(
    <MemoryRouter initialEntries={[ruta]}>
      <Routes>
        <Route element={<RutaInvitado />}>
          <Route path="/login" element={<p>Pantalla de login</p>} />
          <Route path="/registro" element={<p>Pantalla de registro</p>} />
        </Route>
        <Route element={<RutaProtegida publicaEnRaiz={<Bienvenida />} />}>
          <Route
            element={
              <>
                <p>Aplicación</p>
                <Outlet />
              </>
            }
          >
            <Route path="/" element={<p>Panel de inicio</p>} />
            <Route path="/solicitudes" element={<p>Solicitudes</p>} />
          </Route>
        </Route>
      </Routes>
      <Ubicacion />
    </MemoryRouter>,
  )
}

describe('Página pública en /', () => {
  beforeEach(() => {
    useAuthStore.getState().clearSession()
  })

  it('sin sesión muestra la bienvenida con sus acciones de acceso', () => {
    renderizarRutas('/')
    expect(screen.getByRole('heading', { level: 1, name: 'Las compras de tu empresa, en orden.' })).toBeInTheDocument()
    expect(screen.queryByText('Aplicación')).not.toBeInTheDocument()
    const portada = screen.getByRole('region', { name: 'Las compras de tu empresa, en orden.' })
    expect(within(portada).getByRole('link', { name: /Iniciar sesión/ })).toHaveAttribute('href', '/login')
    expect(within(portada).getByRole('link', { name: 'Tengo un código de invitación' })).toHaveAttribute('href', '/registro')
  })

  it('cada sección tiene su encabezado y su enlace en la navegación', () => {
    renderizarRutas('/')
    for (const nombre of ['De la necesidad al inventario', 'Lo que queda por escrito', 'Además de comprar', 'Cada empresa, con sus datos', 'Antes de entrar']) {
      expect(screen.getByRole('heading', { level: 2, name: nombre })).toBeInTheDocument()
    }
    const navegacion = screen.getByRole('navigation', { name: 'Secciones' })
    for (const ancla of ['#flujo', '#documentos', '#modulos', '#roles', '#preguntas']) {
      expect(within(navegacion).getByRole('link', { name: (_, el) => el.getAttribute('href') === ancla })).toBeInTheDocument()
      expect(document.querySelector(ancla)).not.toBeNull()
    }
  })

  it('no promete factura electrónica y marca los datos como de ejemplo', () => {
    renderizarRutas('/')
    expect(screen.getByText(/no tiene validez ante la DIAN/)).toBeInTheDocument()
    expect(screen.getByText('Datos de ejemplo de una empresa de demostración')).toBeInTheDocument()
  })

  it('con sesión, / es el panel de inicio y nunca la bienvenida', () => {
    iniciarSesionComo('TRABAJADOR')
    renderizarRutas('/')
    expect(screen.getByText('Panel de inicio')).toBeInTheDocument()
    expect(screen.queryByRole('heading', { name: 'Las compras de tu empresa, en orden.' })).not.toBeInTheDocument()
  })

  it('las demás rutas privadas siguen llevando a /login sin sesión', () => {
    renderizarRutas('/solicitudes')
    expect(screen.getByText('Pantalla de login')).toBeInTheDocument()
    expect(screen.getByTestId('ubicacion')).toHaveTextContent('/login')
  })

  it('una sesión caducada en / vuelve a la bienvenida', () => {
    iniciarSesionComo('TRABAJADOR')
    useAuthStore.setState({ expiresAt: Date.now() - 1000 })
    renderizarRutas('/')
    expect(screen.getByRole('heading', { level: 1, name: 'Las compras de tu empresa, en orden.' })).toBeInTheDocument()
  })
})

describe('Recorrido interactivo de una compra', () => {
  it('el control deslizante y los nombres de etapa cambian la frase', async () => {
    render(<FlujoInteractivo />)
    expect(screen.getByText('Alguien pide lo que hace falta.')).toBeInTheDocument()

    const control = screen.getByRole('slider', { name: 'Etapa de la compra' })
    fireEvent.change(control, { target: { value: '3' } })
    expect(screen.getByText('Se recibe lo que llegó.')).toBeInTheDocument()
    expect(control).toHaveAttribute('aria-valuetext', 'Recepción (4 de 5)')

    await userEvent.click(screen.getByRole('button', { name: 'Existencias' }))
    expect(screen.getByText('El inventario se pone al día.')).toBeInTheDocument()
    expect(screen.getByRole('button', { name: 'Existencias' })).toHaveAttribute('aria-pressed', 'true')
  })
})

describe('Acceso con código desde la página pública', () => {
  function renderizarAcceso() {
    return render(
      <MemoryRouter initialEntries={['/']}>
        <Routes>
          <Route path="/" element={<AccesoConCodigo />} />
          <Route path="/registro" element={<p>Registro</p>} />
        </Routes>
        <Ubicacion />
      </MemoryRouter>,
    )
  }

  it('lleva el código de invitación a /registro', async () => {
    renderizarAcceso()
    await userEvent.type(screen.getByLabelText('Código de invitación'), '7kq2-m9xa-4hpr-t3vw')
    await userEvent.click(screen.getByRole('button', { name: 'Continuar' }))
    expect(screen.getByTestId('ubicacion')).toHaveTextContent('/registro?codigo=7KQ2-M9XA-4HPR-T3VW')
  })

  it('con el código de la empresa, abre esa vía', async () => {
    renderizarAcceso()
    await userEvent.click(screen.getByLabelText('Código de empresa'))
    await userEvent.type(screen.getByLabelText('Código de tu empresa'), 'ABCD-EFGH-JKLM-NPQR')
    await userEvent.click(screen.getByRole('button', { name: 'Continuar' }))
    expect(screen.getByTestId('ubicacion')).toHaveTextContent('/registro?via=empresa&codigo=ABCD-EFGH-JKLM-NPQR')
  })

  it('sin código, abre el registro vacío', async () => {
    renderizarAcceso()
    await userEvent.click(screen.getByRole('button', { name: 'Continuar' }))
    expect(screen.getByTestId('ubicacion')).toHaveTextContent(/^\/registro$/)
  })
})

describe('Datos de ejemplo y código de barras', () => {
  it('los totales salen de las líneas', () => {
    expect(TOTAL_DEMO).toBe(12 * 3_400 + 4 * 14_000)
    expect(UNIDADES_PEDIDAS).toBe(16)
    expect(UNIDADES_RECIBIDAS).toBe(8)
    expect(LINEAS_DEMO.every((linea) => linea.recibido <= linea.cantidad)).toBe(true)
    expect(TOTAL_VENTA_DEMO).toBe(2 * 4_200 + 17_500)
    expect(CAMBIO_VENTA_DEMO).toBe(VENTA_DEMO.recibido - TOTAL_VENTA_DEMO)
    expect(CAMBIO_VENTA_DEMO).toBeGreaterThanOrEqual(0)
  })

  it('codifica un EAN-13 válido con sus 95 módulos y rechaza uno mal formado', () => {
    expect(digitoControlEan13('400638133393')).toBe(1)
    const modulos = modulosEan13('4006381333931')
    expect(modulos).toHaveLength(95)
    expect(modulos.startsWith('101')).toBe(true)
    expect(modulos.slice(45, 50)).toBe('01010')
    expect(modulos.endsWith('101')).toBe(true)
    expect(() => modulosEan13('4006381333932')).toThrow()
    expect(() => modulosEan13('123')).toThrow()
  })
})
