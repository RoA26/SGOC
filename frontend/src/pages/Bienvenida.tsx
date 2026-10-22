import { ArrowRight, Menu } from 'lucide-react'
import { useId, type ReactNode } from 'react'
import { Link } from 'react-router-dom'
import { AlternadorTema } from '@/components/AlternadorTema'
import { BrandLogo } from '@/components/BrandLogo'
import { AccesoConCodigo } from '@/features/bienvenida/AccesoConCodigo'
import { CODIGO_BARRAS_DEMO } from '@/features/bienvenida/datosDemo'
import { Documentos } from '@/features/bienvenida/Documentos'
import { modulosEan13 } from '@/features/bienvenida/ean13'
import { Escenario } from '@/features/bienvenida/Escenario'
import { FlujoInteractivo } from '@/features/bienvenida/FlujoInteractivo'
import { PanelCalendario, PanelExistencias, PanelInvitacion, PanelPuntoVenta } from '@/features/bienvenida/fragmentos'
import { useRevelar } from '@/features/bienvenida/useRevelar'
import { cn } from '@/lib/cn'

const ANIO = new Date().getFullYear()

const ENLACES = [
  { href: '#flujo', etiqueta: 'Cómo funciona' },
  { href: '#documentos', etiqueta: 'Documentos' },
  { href: '#modulos', etiqueta: 'Módulos' },
  { href: '#roles', etiqueta: 'Roles' },
  { href: '#preguntas', etiqueta: 'Preguntas' },
] as const

/**
 * Página pública de Nexora (ruta `/` sin sesión). Contenido estático: no llama a la API. Todo lo
 * que afirma está implementado; los fragmentos de la interfaz usan datos de ejemplo de la empresa
 * de demostración y lo dicen. Con sesión, `/` es el panel de inicio y esta página no se muestra.
 */
export default function Bienvenida() {
  return (
    <div className="min-h-svh bg-background text-foreground">
      <a
        href="#contenido"
        className="sr-only z-50 rounded-md bg-surface px-4 py-2 focus:not-sr-only focus:fixed focus:top-3 focus:left-3"
      >
        Saltar al contenido
      </a>
      <Encabezado />
      <main id="contenido">
        <Portada />
        <Flujo />
        <SeccionDocumentos />
        <Modulos />
        <Roles />
        <Preguntas />
        <Cierre />
      </main>
      <Pie />
    </div>
  )
}

/* --- Encabezado: marca, enlaces a las secciones y las dos acciones --- */

function Encabezado() {
  return (
    <header className="sticky top-0 z-20 border-b border-border bg-background">
      <div className="mx-auto flex h-16 max-w-[88rem] items-center gap-6 px-4 sm:px-6">
        <Link to="/" aria-label="Nexora, inicio" className="shrink-0 rounded-md">
          <BrandLogo className="h-7 w-auto" />
        </Link>
        <nav aria-label="Secciones" className="hidden flex-1 lg:block">
          <ul className="flex gap-6 text-[0.9375rem]">
            {ENLACES.map((enlace) => (
              <li key={enlace.href}>
                <a href={enlace.href} className="rounded-sm text-foreground-muted transition-colors hover:text-foreground">
                  {enlace.etiqueta}
                </a>
              </li>
            ))}
          </ul>
        </nav>
        <div className="ml-auto flex items-center gap-2 sm:gap-4">
          {/* En móvil no cabe junto a «Iniciar sesión»: va en el menú de secciones. */}
          <AlternadorTema className="hidden sm:inline-flex" />
          <Link to="/registro" className="u-link hidden text-[0.9375rem] sm:inline-flex">
            Crear cuenta
          </Link>
          <Link to="/login" className="u-btn u-btn--primary h-10 px-4">
            Iniciar sesión
          </Link>
          <details className="group relative lg:hidden">
            <summary
              className="grid size-10 cursor-pointer list-none place-items-center rounded-md border border-border-strong [&::-webkit-details-marker]:hidden"
              aria-label="Secciones de la página"
            >
              <Menu className="size-4" aria-hidden="true" />
            </summary>
            <nav
              aria-label="Secciones (móvil)"
              className="u-dialogo absolute right-0 mt-2 w-56 rounded-lg border border-border bg-surface p-2 group-open:shadow-card"
            >
              <ul>
                {ENLACES.map((enlace) => (
                  <li key={enlace.href}>
                    <a
                      href={enlace.href}
                      onClick={(event) => event.currentTarget.closest('details')?.removeAttribute('open')}
                      className="block rounded-md px-3 py-2.5 text-[0.9375rem] hover:bg-surface-muted"
                    >
                      {enlace.etiqueta}
                    </a>
                  </li>
                ))}
                <li className="sm:hidden">
                  <Link to="/registro" className="block rounded-md px-3 py-2.5 text-[0.9375rem] font-medium hover:bg-surface-muted">
                    Crear cuenta
                  </Link>
                </li>
                <li className="flex items-center justify-between gap-3 px-3 py-1.5 text-[0.9375rem] sm:hidden">
                  <span aria-hidden="true">Modo oscuro</span>
                  <AlternadorTema />
                </li>
              </ul>
            </nav>
          </details>
        </div>
      </div>
    </header>
  )
}

/* --- Portada: tarjeta con la propuesta y el escenario con la aplicación --- */

/**
 * Etiqueta de código de barras de un producto de ejemplo, como las que lee el inventario y el punto
 * de venta: barras en su proporción real (2 px por módulo) con los dígitos debajo.
 */
function EtiquetaCodigo({ className }: { className?: string }) {
  const modulos = modulosEan13(CODIGO_BARRAS_DEMO)
  const barras: { x: number; ancho: number }[] = []
  for (let i = 0; i < modulos.length; i++) {
    if (modulos[i] !== '1') continue
    let ancho = 1
    while (modulos[i + ancho] === '1') ancho++
    barras.push({ x: i, ancho })
    i += ancho - 1
  }
  const d = CODIGO_BARRAS_DEMO
  return (
    <div aria-hidden="true" className={cn('inline-flex flex-col', className)}>
      <svg viewBox={`0 0 ${modulos.length} 24`} width={modulos.length * 2} height={48} className="text-foreground">
        {barras.map((barra) => (
          <rect key={barra.x} x={barra.x} y={0} width={barra.ancho} height={24} fill="currentColor" />
        ))}
      </svg>
      <span className="mt-1 flex justify-between text-xs tracking-[0.2em] text-foreground-muted tabular-nums">
        <span>{d[0]}</span>
        <span>{d.slice(1, 7)}</span>
        <span>{d.slice(7)}</span>
      </span>
    </div>
  )
}

function Portada() {
  return (
    <section aria-labelledby="titulo-portada" className="mx-auto max-w-[88rem] px-4 pt-3 sm:px-6">
      <div className="grid grid-cols-[minmax(0,1fr)] gap-3 lg:h-[clamp(38rem,calc(100svh-5rem),48rem)] lg:grid-cols-2">
        <div className="u-vista flex flex-col overflow-hidden rounded-xl bg-surface px-6 py-10 sm:px-10 lg:px-14 lg:py-12">
          <div className="flex flex-1 flex-col justify-center py-6">
            <h1 id="titulo-portada" className="max-w-[17ch] font-serif text-[clamp(2.75rem,4.6vw,4.5rem)] leading-[0.98] tracking-[-0.01em] text-balance">
              Las compras de tu empresa, en orden.
            </h1>
            <p className="mt-8 max-w-[46ch] text-lg leading-relaxed text-foreground">
              Nexora reúne solicitudes, aprobaciones, órdenes de compra, recepción de mercancía e inventario en un solo
              sistema. Cada empresa trabaja únicamente con sus propios datos.
            </p>
            <div className="mt-10 flex flex-wrap items-center gap-3">
              <Link to="/login" className="u-btn u-btn--primary h-12 px-6">
                Iniciar sesión
                <ArrowRight className="size-4" aria-hidden="true" />
              </Link>
              <Link to="/registro" className="u-btn u-btn--ghost h-12 px-6">
                Tengo un código de invitación
              </Link>
            </div>
          </div>
          <EtiquetaCodigo className="mt-10 self-start" />
        </div>
        <div className="rounded-xl bg-surface-muted p-5 sm:p-10 lg:p-14">
          <Escenario />
        </div>
      </div>
    </section>
  )
}

/* --- Secciones --- */

function Seccion({ id, className, children, titulo }: { id: string; className?: string; children: ReactNode; titulo: string }) {
  const ref = useRevelar<HTMLElement>()
  return (
    <section ref={ref} id={id} aria-labelledby={titulo} className={cn('u-revelar mx-auto max-w-[88rem] scroll-mt-20 px-4 sm:px-6', className)}>
      {children}
    </section>
  )
}

function Flujo() {
  const titulo = useId()
  return (
    <Seccion id="flujo" titulo={titulo} className="pt-28 lg:pt-40">
      <div className="mb-10 grid grid-cols-[minmax(0,1fr)] gap-4 lg:grid-cols-12">
        <h2 id={titulo} className="text-[1.75rem] leading-tight font-semibold tracking-tight lg:col-span-5">
          De la necesidad al inventario
        </h2>
        <p className="max-w-xl text-base leading-relaxed lg:col-span-6 lg:col-start-7">
          Una compra pasa por cinco momentos y cada uno lo registra quien le corresponde. Mueve el control para recorrer
          una compra de ejemplo.
        </p>
      </div>
      <FlujoInteractivo />
    </Seccion>
  )
}

function SeccionDocumentos() {
  const titulo = useId()
  return (
    <Seccion id="documentos" titulo={titulo} className="pt-28 lg:pt-40">
      <Documentos tituloId={titulo} />
    </Seccion>
  )
}

const MODULOS: readonly { titulo: string; texto: string[]; panel: ReactNode }[] = [
  {
    titulo: 'Punto de venta',
    texto: [
      'Vende con el lector de código de barras o buscando el producto. Cada venta descuenta existencias y genera su comprobante interno.',
      'Las devoluciones y anulaciones quedan registradas con su motivo.',
    ],
    panel: <PanelPuntoVenta />,
  },
  {
    titulo: 'Calendario de entregas',
    texto: [
      'Las órdenes emitidas se programan con fecha y franja horaria.',
      'El calendario muestra lo que llega cada día, y la vista de entregas señala lo que va con retraso o sin fecha.',
    ],
    panel: <PanelCalendario />,
  },
  {
    titulo: 'Existencias y kárdex',
    texto: [
      'Cada entrada y salida queda en el kárdex del producto, con su saldo.',
      'Los ajustes manuales exigen un motivo, y los productos en su mínimo o sin existencias se ven en el panel de inicio.',
    ],
    panel: <PanelExistencias />,
  },
  {
    titulo: 'Personal e invitaciones',
    texto: [
      'El administrador invita con códigos de un solo uso o comparte el código de la empresa.',
      'Las cuentas creadas con el código de la empresa esperan su aprobación antes de entrar.',
    ],
    panel: <PanelInvitacion />,
  },
]

function Modulos() {
  const titulo = useId()
  return (
    <Seccion id="modulos" titulo={titulo} className="pt-28 lg:pt-40">
      <div className="grid grid-cols-[minmax(0,1fr)] gap-12 lg:grid-cols-12">
        <div className="lg:col-span-4">
          <div className="lg:sticky lg:top-28">
            <h2 id={titulo} className="text-[1.75rem] leading-tight font-semibold tracking-tight">
              Además de comprar
            </h2>
            <p className="mt-3 max-w-sm text-base leading-relaxed">
              Los mismos productos, proveedores y existencias alimentan la venta, la agenda de entregas y los reportes
              comerciales de cada empresa.
            </p>
          </div>
        </div>
        <ul className="space-y-20 lg:col-span-8 lg:space-y-24">
          {MODULOS.map((modulo) => (
            <li key={modulo.titulo} className="grid grid-cols-[minmax(0,1fr)] gap-5 sm:grid-cols-[minmax(0,22.5rem)_minmax(0,1fr)] sm:gap-8">
              <div className="rounded-lg bg-surface-muted p-4">{modulo.panel}</div>
              <div>
                <h3 className="text-lg font-semibold tracking-tight">{modulo.titulo}</h3>
                {modulo.texto.map((parrafo) => (
                  <p key={parrafo} className="mt-2 text-base leading-relaxed text-foreground-muted">
                    {parrafo}
                  </p>
                ))}
              </div>
            </li>
          ))}
        </ul>
      </div>
    </Seccion>
  )
}

const DATOS = [
  { etiqueta: 'Roles', valor: 'Super administrador, administrador comercial y trabajador' },
  { etiqueta: 'Moneda', valor: 'Pesos colombianos, en valores enteros' },
  { etiqueta: 'Comprobante de venta', valor: 'Interno; no es factura electrónica' },
] as const

const ROLES = [
  {
    nombre: 'Trabajador',
    texto: 'Pide lo que necesita, recibe la mercancía, vende en el punto de venta y consulta las existencias.',
  },
  {
    nombre: 'Administrador comercial',
    texto: 'Aprueba solicitudes; gestiona productos, proveedores, órdenes y precios; revisa los reportes y administra a su personal.',
  },
  {
    nombre: 'Super administrador',
    texto: 'Da de alta las empresas y sus administradores, y consulta la información de cada empresa cuando la supervisa.',
  },
] as const

function Roles() {
  const titulo = useId()
  return (
    <Seccion id="roles" titulo={titulo} className="pt-28 lg:pt-40">
      <h2 id={titulo} className="text-[1.75rem] leading-tight font-semibold tracking-tight">
        Cada empresa, con sus datos
      </h2>
      <div className="mt-10 grid grid-cols-[minmax(0,1fr)] gap-10 lg:grid-cols-12">
        <dl className="space-y-5 lg:col-span-3">
          {DATOS.map((dato) => (
            <div key={dato.etiqueta}>
              <dt className="text-sm text-foreground-muted">{dato.etiqueta}</dt>
              <dd className="mt-0.5 text-base">{dato.valor}</dd>
            </div>
          ))}
        </dl>
        <p className="text-[clamp(1.25rem,2vw,1.75rem)] leading-snug text-pretty lg:col-span-8 lg:col-start-5">
          Nexora es multiempresa: cada empresa ve solo sus solicitudes, órdenes, proveedores, inventario y ventas, y es
          el servidor el que lo impone en cada petición, no la pantalla. Los permisos se leen de la base de datos cada
          vez, así que una cuenta desactivada deja de operar en su siguiente acción.
        </p>
      </div>
      <div className="mt-16 grid grid-cols-[minmax(0,1fr)] gap-10 sm:grid-cols-3 lg:mt-20">
        {ROLES.map((rol) => (
          <div key={rol.nombre}>
            <h3 className="text-lg font-semibold tracking-tight">{rol.nombre}</h3>
            <p className="mt-2 text-base leading-relaxed text-foreground-muted">{rol.texto}</p>
          </div>
        ))}
      </div>
    </Seccion>
  )
}

const PREGUNTAS: readonly { pregunta: string; respuesta: string }[] = [
  {
    pregunta: '¿Cómo creo una cuenta?',
    respuesta:
      'Con el código que te da el administrador de tu empresa: una invitación de un solo uso, con la que entras directamente, o el código de la empresa, con el que tu cuenta espera su aprobación.',
  },
  {
    pregunta: '¿Mi empresa puede registrarse sola?',
    respuesta:
      'No. Las empresas y sus administradores los da de alta el administrador de la plataforma; después, cada empresa invita a su personal.',
  },
  {
    pregunta: '¿Otras empresas pueden ver mis datos?',
    respuesta:
      'No. Cada petición se resuelve dentro de la empresa de quien la hace. Solo el administrador de la plataforma puede consultar una empresa, y lo hace en modo de supervisión.',
  },
  {
    pregunta: '¿Nexora emite factura electrónica?',
    respuesta: 'No. El comprobante de venta es un documento interno y no tiene validez ante la DIAN.',
  },
  {
    pregunta: '¿Puedo descargar las órdenes de compra?',
    respuesta: 'Sí, en PDF y en Word, desde la ficha de cada orden emitida. El documento conserva su versión.',
  },
  {
    pregunta: '¿Qué pasa si me equivoco de contraseña varias veces?',
    respuesta:
      'Tras varios intentos fallidos con el mismo usuario, el acceso se pausa durante unos minutos y la pantalla indica cuánto esperar.',
  },
  {
    pregunta: '¿Puedo recuperar mi contraseña?',
    respuesta: 'Todavía no: Nexora aún no permite cambiar ni recuperar la contraseña desde la aplicación. Guárdala en un lugar seguro.',
  },
  {
    pregunta: '¿Funciona en el teléfono?',
    respuesta: 'Sí. La interfaz se adapta a teléfonos, tabletas y computadores.',
  },
  {
    pregunta: '¿En qué moneda trabaja?',
    respuesta: 'En pesos colombianos, con valores enteros y sin centavos.',
  },
]

function Preguntas() {
  const titulo = useId()
  return (
    <Seccion id="preguntas" titulo={titulo} className="pt-28 lg:pt-40">
      <div className="grid grid-cols-[minmax(0,1fr)] gap-8 lg:grid-cols-12">
        <h2 id={titulo} className="text-[1.75rem] leading-tight font-semibold tracking-tight lg:col-span-3">
          Antes de entrar
        </h2>
        <div className="gap-14 text-base leading-relaxed lg:col-span-8 lg:col-start-5 md:columns-2">
          {PREGUNTAS.map(({ pregunta, respuesta }) => (
            <p key={pregunta} className="mb-5 break-inside-avoid">
              <strong className="font-semibold">{pregunta}</strong> <span className="text-foreground-muted">{respuesta}</span>
            </p>
          ))}
        </div>
      </div>
    </Seccion>
  )
}

function Cierre() {
  const titulo = useId()
  return (
    <section aria-labelledby={titulo} className="mx-auto mt-28 max-w-[88rem] px-4 sm:px-6 lg:mt-40">
      <div className="grid grid-cols-[minmax(0,1fr)] gap-10 rounded-xl bg-brand-field px-6 py-12 text-brand-field-foreground sm:px-10 lg:min-h-[80svh] lg:grid-cols-12 lg:items-center lg:px-14">
        <div className="lg:col-span-6 lg:self-end">
          <h2 id={titulo} className="font-serif text-[clamp(3rem,7vw,6.5rem)] leading-[0.92] text-balance">
            Empieza con tu código.
          </h2>
          <p className="mt-6 max-w-md text-lg leading-relaxed">
            Escríbelo aquí y completa tus datos en el siguiente paso. Si aún no lo tienes, pídelo al administrador de tu
            empresa.
          </p>
        </div>
        <div className="flex justify-center lg:col-span-5 lg:col-start-8 lg:justify-end">
          <AccesoConCodigo />
        </div>
      </div>
    </section>
  )
}

function Pie() {
  return (
    <footer className="mx-auto mt-32 max-w-[88rem] px-4 pb-8 sm:px-6">
      <div className="grid gap-2 text-sm text-foreground-muted sm:grid-cols-[1fr_auto_1fr] sm:items-baseline">
        <p>Nexora · gestión de compras empresariales</p>
        <ul className="flex flex-wrap gap-x-6 gap-y-1">
          <li>
            <Link to="/login" className="u-link font-normal">
              Iniciar sesión
            </Link>
          </li>
          <li>
            <Link to="/registro" className="u-link font-normal">
              Crear cuenta
            </Link>
          </li>
          <li>
            <a href="#preguntas" className="u-link font-normal">
              Preguntas
            </a>
          </li>
        </ul>
        <p className="sm:text-right">© {ANIO} Nexora</p>
      </div>
    </footer>
  )
}
