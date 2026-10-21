# Nexora: Sistema de Gestión de Órdenes de Compra

Producción: **https://rrtf.duckdns.org**

| Capa | Tecnología |
| --- | --- |
| Backend | Java 21 · Spring Boot 3.5 · Spring Security (JWT) · Spring Data JPA · Flyway |
| Frontend | React 18 · TypeScript · Vite · Tailwind CSS v4 · Zustand · Axios |
| Infraestructura | Docker Compose · Nginx 1.30 · PostgreSQL 16 |

```
Navegador ──HTTPS──▶ host (TLS de rrtf.duckdns.org) ──HTTP──▶ frontend · Nginx :80
                                                               ├── /       → estáticos de React
                                                               └── /api/   → backend:8081 ──▶ postgres:5432
```

El navegador solo habla con Nginx: la SPA llama a `/api` en el **mismo origen**, sin CORS ni URLs
absolutas en el código. PostgreSQL y Spring Boot no publican puertos hacia internet.

## Estructura

```
.
├── docker-compose.yml       PostgreSQL + backend + frontend (Nginx)
├── .env.example             Variables del despliegue (copiar a .env)
├── backend-spring/          API REST Spring Boot (ver backend-spring/README.md)
│   └── Dockerfile           Maven → JRE 21 alpine, usuario sin privilegios
└── frontend/
    ├── Dockerfile           Node (build) → Nginx (estáticos + proxy /api)
    ├── nginx.conf
    ├── tailwind.config.ts   Tema conectado a las variables CSS de la marca
    └── src/
        ├── brand/nexora/    tokens.css, componentes.css, logos SVG (marca Nexora)
        ├── lib/axios.ts     Instancia única de Axios (VITE_API_URL)
        ├── store/           Sesión con Zustand (accessToken, nombre…)
        ├── schemas/         Esquemas Zod (authSchema, proveedorSchema, productoSchema) = validaciones del backend
        ├── services/        proveedorService, productoService (/api/v1/...)
        ├── components/ui/   DataTable, Modal, ConfirmDialog, FormField
        ├── features/        auth (rutas, authApi, AuthLayout), proveedores y productos (formularios en modal)
        └── pages/           Login, Registro, Inicio, Perfil, Proveedores, Productos, Solicitudes,
                             admin/Empresas, admin/EmpresaDetalle, admin/Invitaciones
```

## Limpieza de deuda técnica

El backend FastAPI y el frontend React anterior se eliminaron en el commit `d13066a` con:

```bash
git rm -r backend frontend docker-compose.yml backend-spring/compose.yaml
rm -rf backend frontend          # restos no versionados: node_modules/, .venv/, .env…
git commit -m "Limpieza: eliminar backend FastAPI y frontend obsoleto"
```

> Ya están aplicados: **no los vuelvas a ejecutar**, porque `frontend/` es ahora el frontend nuevo.

## Cómo se generó el frontend

```bash
npm create vite@latest frontend -- --template react-ts
cd frontend
npm install

# React 18 (la plantilla actual de Vite instala React 19)
npm install react@^18.3.1 react-dom@^18.3.1
npm install -D @types/react@^18.3 @types/react-dom@^18.3

# Tailwind CSS v4 (plugin de Vite), HTTP, enrutado y estado
npm install tailwindcss @tailwindcss/vite axios react-router-dom zustand

# Tipografías de la marca, autoalojadas
npm install @fontsource-variable/geist @fontsource/instrument-serif
```

Tailwind v4 se activa con `tailwindcss()` en `vite.config.ts` y, en `src/index.css`,
`@import 'tailwindcss';` más `@config '../tailwind.config.ts';` para usar el tema en TypeScript.

## Despliegue en el VPS

Requisitos: Docker con el plugin Compose, `rrtf.duckdns.org` apuntando a la IP del VPS y el
puerto 80 abierto.

```bash
git clone https://github.com/RoA26/SGOC.git && cd SGOC
cp .env.example .env
# Rellena en .env las variables obligatorias:
#   POSTGRES_PASSWORD   contraseña de la base de datos
#   JWT_SECRET          openssl rand -base64 64
#   ADMIN_PASSWORD      contraseña del primer administrador (ADMIN_USERNAME, por defecto "admin")

docker compose up -d --build
docker compose ps                  # los tres servicios deben quedar "healthy"
```

Abre http://rrtf.duckdns.org e inicia sesión con `ADMIN_USERNAME` / `ADMIN_PASSWORD`. Ese
usuario es el `SUPER_ADMIN` de la plataforma (la cuenta raíz): se crea en el primer arranque y los
siguientes no lo modifican. Desde **Empresas** crea cada empresa con su código empresarial
(p. ej. `NASC01`) y su administrador (rol `ADMINISTRADOR_COMERCIAL`, con nombres y apellidos), que ya puede
iniciar sesión. Los trabajadores entran con una invitación de su administrador (entran
directamente) o con el código de registro secreto de su empresa (quedan pendientes hasta que el
administrador los aprueba). Detalle en
[backend-spring/README.md](backend-spring/README.md#gestión-centralizada-de-empresas).

**Al actualizar al módulo comercial (V14–V16):** nada se borra. V14 renombra el rol
`ADMIN_EMPRESA` a `ADMINISTRADOR_COMERCIAL` (mismas cuentas, contraseñas y empresas; los tokens
emitidos siguen valiendo porque el rol se lee de la BD); V15 añade precio de venta e IVA a los
productos (quedan **sin precio**: hay que definirlo en *Gestión comercial* antes de vender), ventas,
pagos, devoluciones, política y autorizaciones; V16 calcula el costo promedio inicial desde el
kárdex. Detalle en [docs/modulo-comercial.md](docs/modulo-comercial.md).

**Al actualizar a la Fase 3 (V13):** nada se borra ni cambia. V13 añade las instantáneas de los
documentos de las órdenes (`documentos_orden_compra`), el historial de programación de entregas
(`programaciones_entrega`, con `ordenes_compra.programacion_entrega_id` apuntando a la vigente) y
las incidencias de recepción. Las órdenes emitidas antes de V13 no tienen instantánea: su Word se
reconstruye al descargarlo con los datos vigentes y el propio documento lo indica.

**Al actualizar a la Fase 2 (V11–V12):** nada se borra. V11 crea órdenes de compra,
recepciones, existencias y movimientos de inventario; cada producto existente recibe su fila de
existencias con 0 unidades (regístralas con un ajuste de *Inventario inicial*) y `stock_minimo`
0; las líneas de solicitud guardan su empresa. V12 añade el código de barras (opcional). Los días
de la analítica se cuentan en `APP_ZONA_HORARIA` (por defecto `America/Bogota`).

**Al actualizar a la Fase 1 (V8–V10):** ninguna cuenta pierde el acceso ni hay que volver a
iniciar sesión. V8 renombra los roles (`GERENTE` → `ADMIN_EMPRESA`, `USUARIO` → `TRABAJADOR`);
los tokens emitidos siguen valiendo (el rol se lee de la BD) y la interfaz traduce la sesión
guardada. V9 añade nombres y apellidos **sin partir** los nombres existentes: cada persona los
completa en *Mi perfil*. V10 añade la revocación de invitaciones. Backend y BD deben desplegarse
juntos (Flyway migra al arrancar); volver atrás exige una migración inversa.

**Al actualizar un despliegue anterior a la gestión de empresas:** la migración V7 no borra
nada; asigna a cada empresa existente el código empresarial `EMP` + su id (`EMP001`, `EMP002`…) y
conserva su código de registro de trabajadores.

**Al actualizar un despliegue anterior al Hito 4:** la migración V3 asigna a cada usuario
existente un username a partir de su correo (`admin@nexora.com` → `admin`), las sesiones
abiertas caducan (hay que volver a entrar) y el login pasa a pedir el username.

El arranque va en orden: PostgreSQL sano → backend listo (readiness) → Nginx. Si el backend se
reinicia, Nginx lo vuelve a encontrar solo. Mientras no responde, `/api` devuelve un `503` en
JSON que el login muestra como "servidor no disponible".

### TLS (siguiente paso, en el host)

Nginx escucha en HTTP y respeta `X-Forwarded-Proto`. Para terminar HTTPS en el host:

1. En `.env`: `HTTP_PORT=127.0.0.1:8080`, para que solo el proxy del host llegue al contenedor.
2. Un proxy inverso en el host para `rrtf.duckdns.org`. Por ejemplo, con Caddy y certificado
   automático de Let's Encrypt:

   ```
   rrtf.duckdns.org {
       reverse_proxy 127.0.0.1:8080
   }
   ```

## Desarrollo local

```bash
# Base de datos (desde la raíz)
cp .env.example .env                 # rellena las variables obligatorias
docker compose up -d postgres        # PostgreSQL en 127.0.0.1:5432

# Terminal 1: backend
cd backend-spring
cp .env.example .env                 # DB_PASSWORD = POSTGRES_PASSWORD; JWT_SECRET; ADMIN_*
./mvnw spring-boot:run               # http://localhost:8081

# Terminal 2: frontend
cd frontend
npm install
npm run dev                          # http://localhost:5173 (proxy /api → :8081)
```

## Marca Nexora

Todos los recursos de marca viven en `frontend/src/brand/nexora/` (tomados del paquete
`nexora-brand-assets`; el favicon está en `frontend/public/favicon.svg`):

| Archivo | Uso |
| --- | --- |
| `tokens.css` | Colores, tipografías, radios y sombras como variables `--nexora-*` (modo claro y oscuro) |
| `componentes.css` | Tokens de componente y clases base `u-input`, `u-btn`, `u-card`, `u-alert`… |
| `nexora-logo-claro.svg` | Logo para fondos **claros** (cabecera, login en móvil) |
| `nexora-logo-oscuro.svg` | Logo para fondos **oscuros** (panel de marca del login, tema oscuro) |

Paleta: borgoña `#6B1E2E` (primario en claro), acento dorado `#C9A86A`, tinta `#1C1416`, crema
`#F5EFE6` y superficie oscura `#1E181A`. Donde un color de la marca no alcanza el contraste de
texto (el dorado sobre claro; `#B2475C` sobre oscuro) `tokens.css` usa un tono derivado y anota el
contraste.

`tailwind.config.ts` no contiene valores, solo referencias a variables. Por ejemplo, `bg-primary`
genera `var(--nexora-color-primary)`. Así el modo oscuro y cualquier cambio de marca se aplican
desde los tokens. Las opacidades funcionan igual (`bg-accent/20`).

| Clases de Tailwind | Variable |
| --- | --- |
| `bg-background` | `--nexora-color-bg` |
| `bg-surface`, `bg-surface-muted` | `--nexora-color-surface`, `--nexora-color-surface-muted` |
| `text-foreground`, `text-foreground-muted` | `--nexora-color-text`, `--nexora-color-text-muted` |
| `bg-primary`, `text-primary-foreground` | `--nexora-color-primary`, `--nexora-color-on-primary` |
| `text-accent` | `--nexora-color-accent` |
| `bg-brand-panel`, `text-brand-panel-foreground`, `text-brand-panel-accent` | `--nexora-color-brand-panel`, `--nexora-color-brand-panel-text`, `--nexora-color-brand-panel-accent` |
| `font-sans`, `font-serif` | `--nexora-font-sans` (Geist), `--nexora-font-serif` (Instrument Serif) |
| `rounded-{sm,md,lg,xl}`, `shadow-card`, `h-control` | `--nexora-radius-*`, `--nexora-shadow-card`, `--nexora-control-height` |

Geist e Instrument Serif se sirven desde el propio dominio con `@fontsource`, sin CDN externos.

## API (a través de Nginx)

| Método | Ruta | Descripción |
| --- | --- | --- |
| POST | `/api/auth/login` | `{username, password}` → `{accessToken, tokenType, expiresIn, usuario}` (`usuario.empresaId`, salvo SUPER_ADMIN) |
| POST | `/api/auth/registro` | `{nombres, apellidos, username, email, password, codigoInvitacion \| codigoEmpresa}` → `201` usuario TRABAJADOR de la empresa del código: ACTIVO con invitación, PENDIENTE con código de empresa |
| POST | `/api/v1/empresas` | SUPER_ADMIN: empresa + código empresarial (`codigo`, indicado o propuesto) + `administrador` inicial (`ADMINISTRADOR_COMERCIAL`, con `nombres` y `apellidos`), todo o nada |
| GET | `/api/v1/empresas[/{id}]` | SUPER_ADMIN: listado paginado y ficha, con `codigo` y el administrador principal en `administrador` |
| GET | `/api/v1/empresas/codigo-sugerido?nombre=` · `/codigo-disponible?codigo=` | SUPER_ADMIN: propone (`NASCAR` → `NASC01`) y comprueba un código empresarial |
| GET | `/api/v1/empresas/{id}/usuarios` | SUPER_ADMIN: usuarios de esa empresa (administradores y trabajadores) con su estado |
| PATCH | `/api/v1/empresas/{id}/estado` · `/{id}/administradores/{usuarioId}/estado` | SUPER_ADMIN: activar o desactivar la empresa, o la cuenta de uno de sus administradores |
| GET | `/api/v1/empresas/actual` | ADMINISTRADOR_COMERCIAL, o SUPER_ADMIN con `X-Tenant-ID: <id>`: empresa en la que se trabaja, con sus códigos |
| GET · PATCH | `/api/v1/trabajadores[?estado=]` · `/{id}/estado` | ADMINISTRADOR_COMERCIAL, o SUPER_ADMIN con `X-Tenant-ID: <id>`: trabajadores de esa empresa; aprobar, rechazar, desactivar o reactivar |
| GET | `/api/v1/trabajadores/actividad[?estado=]` | Mismos roles y filtros, sin el correo: última operación registrada (solicitud, recepción o venta) y conteos de los últimos 30 días. No es la última conexión (no se registran los inicios de sesión) |
| POST | `/api/auth/invitaciones` | ADMINISTRADOR_COMERCIAL (o SUPER_ADMIN con `X-Tenant-ID: <id>`, solo por API): `{horasValidez?}` → `201` `{codigo, fechaExpiracion}` |
| GET · DELETE | `/api/auth/invitaciones` · `/{id}` | Mismos roles: invitaciones de la empresa con su estado (sin el código completo); revocar una vigente (baja lógica) |
| GET · PATCH | `/api/auth/me` | Perfil del usuario autenticado, leído de la BD: nombres, apellidos, rol, estado y empresa (`empresaNombre`, `empresaCodigo`); `PATCH {nombres, apellidos}` corrige los propios |
| GET | `/api/v1/proveedores?page=0&size=10&sort=razonSocial,asc` | Listado paginado |
| GET · POST · PUT · DELETE | `/api/v1/proveedores[/{id}]` | Detalle, alta, edición y baja lógica |
| GET | `/api/v1/productos?page=0&size=10&sort=nombre,asc` | Listado paginado (incluye el proveedor) |
| GET · POST · PUT · DELETE | `/api/v1/productos[/{id}]` | Detalle, alta, edición y baja lógica |
| GET · POST | `/api/v1/solicitudes[?estado=]` | TRABAJADOR ve y crea las suyas; ADMINISTRADOR_COMERCIAL ve las de su empresa; SUPER_ADMIN consulta |
| PATCH | `/api/v1/solicitudes/{id}/estado` | ADMINISTRADOR_COMERCIAL, o SUPER_ADMIN con `X-Tenant-ID: <id>`: `{estado: APROBADA \| RECHAZADA, comentario}` |
| GET | `/api/v1/analitica/global[/empresas]?desde=&hasta=&alcance=` | SUPER_ADMIN con `X-Tenant-ID: global`: empresas, órdenes emitidas, top 5, evolución por día/mes y órdenes por empresa. `alcance`: `REALES` (por defecto, sin las empresas demo), `DEMO` o `TODAS` |
| GET | `/api/v1/analitica/empresa?desde=&hasta=` · `/personal` | Indicadores de la empresa (gestión) y personales (sin ventas) |
| GET · POST · PUT | `/api/v1/ordenes-compra[/{id}]` | Gestión consulta; ADMINISTRADOR_COMERCIAL crea y edita borradores (un proveedor por orden) |
| POST | `/api/v1/ordenes-compra/{id}/emitir` · `/cancelar` · `/desde-solicitud/{solicitudId}` | ADMINISTRADOR_COMERCIAL: transiciones validadas y borradores desde una solicitud aprobada |
| GET · POST | `/api/v1/ordenes-compra/{id}/recepciones` · `GET /api/v1/recepciones[?desde=&hasta=&proveedorId=&ordenId=&registradaPorId=&conIncidencias=]` | Recepciones parciales o totales con incidencias (faltante, dañado, excedente, producto incorrecto, otra), idempotentes (`claveIdempotencia`). Registran ADMINISTRADOR_COMERCIAL y TRABAJADOR; el listado con costos es de gestión |
| GET | `/api/v1/ordenes-compra/{id}/documento` · `/documentos` | Gestión: Word (.docx) de la orden emitida, generado desde la instantánea inmutable de la emisión; versiones documentales |
| PUT · GET | `/api/v1/ordenes-compra/{id}/entrega` · `/entrega/historial` | ADMINISTRADOR_COMERCIAL programa o reprograma (con motivo) la fecha estimada, franja opcional, confirmación del proveedor, referencia y observaciones; historial de versiones (toda la empresa) |
| GET | `/api/v1/ordenes-compra/{id}/historial` | Gestión: secuencia completa (solicitud, emisión, documento, programaciones, recepciones con incidencias y kárdex, cancelación) |
| GET | `/api/v1/entregas[?vista=&desde=&hasta=&proveedorId=&numero=]` · `/resumen` · `/{ordenId}` | Entregas sin costos (el trabajador, solo las pendientes); cifras de supervisión (gestión); detalle operativo de una orden |
| GET | `/api/v1/recepciones/mias` · `/{id}` | Recepciones registradas por quien pregunta; detalle (sin costos para el trabajador) |
| GET | `/api/v1/calendario?desde=&hasta=[&tipos=]` | Eventos derivados (emisiones, entregas, recepciones, solicitudes pendientes), máx. 62 días, siempre dentro de una empresa |
| GET | `/api/v1/inventario[?estado=&q=]` · `/movimientos` | Existencias (toda la empresa) y kárdex (gestión) |
| POST | `/api/v1/inventario/ajustes` | ADMINISTRADOR_COMERCIAL: ajuste con motivo, sin existencias negativas, idempotente |
| GET | `/api/v1/productos/buscar?codigo=` | Escáner: código de barras exacto y, si no, SKU, en la empresa del contexto |

Lecturas: cualquier usuario autenticado (de su empresa). Altas, cambios y bajas de catálogos:
`ADMINISTRADOR_COMERCIAL` o `SUPER_ADMIN` dentro de una empresa. El SUPER_ADMIN debe elegir siempre el
contexto en las rutas de datos de negocio: `X-Tenant-ID: <id>` (supervisión) o
`X-Tenant-ID: global` (modo global explícito y registrado); sin cabecera, `400`. Detalle de reglas y errores en
[backend-spring/README.md](backend-spring/README.md#saas-multi-empresa-tenants).

## Roles, empresa y navegación (frontend)

El SGOC es multi-empresa: cada empresa cliente ve solo sus datos y el backend lo impone.

| Rol | Empresa de trabajo | Menú |
| --- | --- | --- |
| `TRABAJADOR` («Trabajador») | La suya (de la sesión) | Inicio · Mis solicitudes · Punto de venta · Mis ventas · Recibir mercancía · Calendario · Existencias |
| `ADMINISTRADOR_COMERCIAL` («Administrador comercial») | La suya (de la sesión) | Inicio · Solicitudes · Órdenes de compra · Entregas · Recepciones · Punto de venta · Ventas · Reportes · Gestión comercial · Calendario · Existencias · Productos · Proveedores · Personal · Invitar usuarios |
| `SUPER_ADMIN` | Ninguna (modo global) o la que supervisa | Inicio · Solicitudes · Órdenes de compra · Entregas · Recepciones · Ventas, Reportes y Gestión comercial (supervisando, solo consulta) · Calendario (supervisando) · Existencias · Productos · Proveedores (consulta) · Empresas |

- **Panel lateral:** muestra el nombre comercial y el código de la empresa, el nombre real del
  usuario (enlace a *Mi perfil*) y su rol, según `GET /api/auth/me`, que la interfaz consulta al
  cargar y al volver a la pestaña. ADMINISTRADOR_COMERCIAL y TRABAJADOR no pueden cambiar
  de empresa.
- **SUPER_ADMIN:** entra en **modo global** (consulta todas las empresas; sus peticiones llevan
  `X-Tenant-ID: global`, que el backend registra). En **Empresas** pulsa
  *Supervisar* en una empresa: desde entonces **solo sus peticiones** llevan `X-Tenant-ID: <id>`,
  una franja fija indica qué empresa supervisa y toda la interfaz muestra solo sus datos, en
  **solo lectura** (sigue siendo SUPER_ADMIN: no hay suplantación de ningún usuario). La elección
  dura la pestaña (sessionStorage), se borra al cerrar sesión y *Volver al modo global* la quita.
  ADMINISTRADOR_COMERCIAL y TRABAJADOR nunca envían la cabecera (el backend la ignoraría).
- **Rutas protegidas:** `/productos`, `/proveedores`, `/ordenes-compra[/:id]`, `/entregas`, `/recepciones`, `/comercial` y `/reportes` para ADMINISTRADOR_COMERCIAL y SUPER_ADMIN; `/recepcion` y `/pos` para ADMINISTRADOR_COMERCIAL y TRABAJADOR; `/ventas[/:id]` para todos (el trabajador, las suyas); `/recepcion/:ordenId` para todos (el SUPER_ADMIN, en consulta); `/inventario` y `/calendario` para todos;
  `/admin/personal` y `/admin/invitaciones` solo para ADMINISTRADOR_COMERCIAL; `/perfil` para todos; `/admin/empresas[/:id]` solo para SUPER_ADMIN; el
  resto vuelve al inicio.
- **Sesiones anteriores** (con el rol `ADMIN`, que ya no existe) se descartan y piden iniciar
  sesión de nuevo. Las guardadas con `ADMIN_EMPRESA` (antes de V14) se traducen a
  `ADMINISTRADOR_COMERCIAL` sin cerrar la sesión.
- **Cuenta que pierde la autorización:** si el backend responde `401`, o `403` con `motivo`
  (cuenta pendiente, rechazada, desactivada o empresa desactivada), la interfaz cierra la sesión;
  al volver a entrar, el login muestra el motivo. Un `403` sin `motivo` (falta de permisos para
  una acción) no la cierra.

**Flujo de compra:** Necesidad → Solicitud → Aprobación → Orden de compra → Recepción, completo.
Una solicitud aprobada ofrece al administrador *Generar órdenes de compra* (un borrador por
proveedor); la orden se edita, se emite y recibe mercancía en una o varias entregas, que suman
existencias y quedan en el kárdex.

**Entregas y recepción:** el administrador programa la fecha estimada de llegada de cada orden
(un día; franja horaria opcional, sin ella es «todo el día»), marca si el proveedor la confirmó y
la reprograma con motivo; cada cambio queda en el historial. En **Entregas** (`/entregas`) supervisa
lo pendiente, retrasado, sin programar, recibido en parte, completo o con incidencias, con filtros
por fecha estimada, proveedor y número. El trabajador, en **Recibir mercancía** (`/recepcion`), ve lo
que está por llegar y lo retrasado, abre una orden, cuenta por producto lo recibido, lo dañado y lo
que faltó, informa excedentes y productos incorrectos, revisa un resumen y confirma; después
consulta sus recepciones. Solo lo aceptado entra al inventario; nada se recibe por encima de lo
pendiente; quién, la empresa y la hora los pone el servidor. Una recepción confirmada no se edita.

**Calendario (`/calendario`):** vistas de mes, semana y día con eventos derivados de los datos
reales: entregas en su fecha estimada vigente (retrasadas resaltadas), recepciones en su hora real,
órdenes emitidas (gestión) y solicitudes por aprobar (el trabajador, las suyas). Filtros por tipo y
estado, acciones según el rol (abrir la orden y reprogramar, o ir a registrar la recepción).

**Módulo comercial (precios, POS, comprobantes, devoluciones y reportes):** el administrador
comercial define el precio de venta sin IVA y el tratamiento del IVA (gravado, exento o excluido)
de cada producto con historial, ve el costo promedio y la rentabilidad bruta, fija la política de
descuentos y emite códigos de autorización; quien vende usa el *Punto de venta* (`/pos`), donde el
backend calcula todo, descuenta existencias con bloqueo y deja el kárdex; el comprobante PDF es
interno (**no es factura electrónica**). Reglas, fórmulas, decisiones pendientes y cuentas de
demostración en [docs/modulo-comercial.md](docs/modulo-comercial.md).

**Documento de la orden (PDF y Word):** la ficha de una orden emitida ofrece *Descargar PDF* (formato
prioritario para compartir) y *Word*, ambos desde la misma instantánea (misma versión y huella). El .docx lleva la empresa,
el proveedor, la entrega, las líneas y el total tal como se emitieron (instantánea inmutable con
huella SHA-256), sin impuestos ni descuentos (no hay reglas aprobadas) ni datos que el sistema no
tiene (dirección de la empresa, logo, unidad de medida, firma).

**Inicio:** un dashboard por contexto con cifras que calcula el backend (nunca inventadas):
plataforma para el SUPER_ADMIN en modo global (empresas, órdenes emitidas, evolución, top 5 y
órdenes por empresa), empresa para el administrador o el SUPER_ADMIN que la supervisa (inventario,
compras del período, recepciones, órdenes y solicitudes por estado, productos que requieren
atención) y personal para el trabajador (disponibilidad y sus solicitudes; sin ventas hasta que
exista el POS). Filtro de período (7/30/90 días, 12 meses o personalizado), vista de tabla de
cada gráfica y estados de carga, error y "sin datos". Saluda con los nombres de la persona y, si
su cuenta es anterior a V9, la invita a completar sus nombres y apellidos.

**Órdenes de compra (`/ordenes-compra`):** listado con filtros por estado y por solicitud de
origen; ficha con líneas (pedido, recibido, pendiente), historial de recepciones y las acciones
que admite su estado (editar, emitir, registrar recepción, cancelar con motivo). El SUPER_ADMIN
las consulta en solo lectura.

**Existencias (`/inventario`):** buscador por código de barras o SKU (los lectores USB/Bluetooth
funcionan como teclado: código + Enter), existencias con su estado frente al stock mínimo,
ajustes con motivo (ADMINISTRADOR_COMERCIAL) y kárdex (gestión). El trabajador consulta sin costos.

**Mi perfil (`/perfil`):** nombre real, usuario de acceso (no es el nombre visible), correo, rol
(«Administrador de empresa», «Trabajador»…) y empresa; formulario para completar o corregir los
propios nombres y apellidos.

**Precios en COP:** la interfaz trabaja con pesos colombianos enteros (sin decimales) y los
muestra como `$ 1.250.000`. "Nuevo producto" queda deshabilitado mientras no haya ningún
proveedor registrado.

**Registro (`/registro`):** la persona elige con qué código se da de alta, con sus nombres y
apellidos, y siempre como TRABAJADOR de la empresa del código (lo decide el servidor):

- *Tengo una invitación*: código de un solo uso (o el enlace `/registro?codigo=XXXX-XXXX-XXXX-XXXX`,
  que lo precarga). La cuenta queda activa y entra directamente.
- *Tengo el código de mi empresa*: el código de registro que el administrador ve y copia en
  *Personal*. La cuenta queda **pendiente**: no se inicia sesión y la pantalla explica que el
  administrador debe aprobarla; hasta entonces el login responde con ese motivo.

Solo se envía el código de la vía elegida (el backend exige exactamente uno). Los errores del
servidor (código inexistente, usado, revocado o caducado; empresa desactivada; usuario o correo en
uso) aparecen junto al campo afectado.

**Solicitudes de compra (`/solicitudes`):** ADMINISTRADOR_COMERCIAL y TRABAJADOR piden productos del
catálogo con un motivo y tantas líneas como necesiten (producto + cantidad), sin elegir proveedor,
y ven el total estimado en COP. La solicitud queda `PENDIENTE`; el administrador la abre y la
aprueba o la rechaza (con motivo); el SUPER_ADMIN solo la consulta. Un TRABAJADOR solo ve las
suyas. La URL
admite `?estado=PENDIENTE`, `?ver=<id>` y `?nueva=1`.

**Empresas (`/admin/empresas`, SUPER_ADMIN):** listado de las empresas con su código, su
administrador principal, su estado y su alta; *Crear empresa* abre un formulario único para la
empresa (nombre comercial, NIT, código empresarial propuesto a partir del nombre y comprobado
mientras se escribe) y su administrador (nombres, apellidos, correo, usuario y contraseña inicial,
con un generador de contraseñas seguras). Se crean juntos o no se crea nada. Cada fila ofrece
*Detalles* (ficha con datos, administrador, actividad real y usuarios; desde ella se desactiva o
reactiva la empresa o la cuenta de su administrador, con confirmación) y *Supervisar*.

**Personal (`/admin/personal`, ADMINISTRADOR_COMERCIAL):** los trabajadores de su empresa con el estado
real de su cuenta y su actividad: la última operación registrada (solicitud, recepción o venta, con
fecha) y los conteos de los últimos 30 días, o «Sin actividad registrada». No muestra la última
conexión porque el sistema no guarda los inicios de sesión. Filtro por estado en la URL
(`?estado=PENDIENTE`); aprueba o rechaza a quien se registró con el código de empresa y desactiva o
reactiva cuentas (rechazar y desactivar piden confirmación). Arriba muestra el **código de registro
de su empresa** con un botón para copiarlo (`GET /api/v1/empresas/actual`: siempre la empresa de la
cuenta, nunca otra); el trabajador no lo ve (403).

**Invitar usuarios (`/admin/invitaciones`, ADMINISTRADOR_COMERCIAL):** el administrador genera un código con
un clic y lo copia, o copia el enlace de registro. Es el alta de trabajadores de su empresa (no de
empresas). El código completo solo se muestra al generarlo; debajo, el historial de invitaciones
de la empresa (vigente, usada, revocada o caducada, quién la creó, la usó o la revocó) permite
revocar una vigente, con confirmación.

### Pendiente en el backend (la interfaz no lo simula)

| Necesidad del frontend | Endpoint que falta |
| --- | --- |
| Registro con código de empresa | Endpoint disponible (Hito 2); falta el campo en `/registro` (la aprobación ya está en *Personal*) |
| Cambio de contraseña (también la inicial del administrador) y recuperación | Sin definir: hoy no hay ningún mecanismo |
| Empresa de cada solicitud en modo global | `empresaId`/`empresaNombre` en `SolicitudResponseDTO` |
| Impuestos y descuentos en las órdenes; cierre de una orden con saldo pendiente | Pendiente de decisión |
| Aceptar excedentes; corregir o anular una recepción confirmada; devoluciones al proveedor | Pendiente de decisión: hoy el excedente se informa como incidencia y una corrección es un ajuste de inventario |
| Datos de la empresa para el documento (dirección, logo) y unidad de medida de los productos | Sin modelo: el documento no los muestra |
| Valoración del inventario | Costo promedio ponderado aplicado como **provisional**; pendiente de validación contable (ver `docs/modulo-comercial.md`) |
| Facturación electrónica (DIAN) | Fuera del alcance: el comprobante de venta es interno |
| Gestión de administradores (alta de más administradores, cambio de rol) | Sin definir; el SUPER_ADMIN ya desactiva o reactiva sus cuentas y los trabajadores se gestionan con `/api/v1/trabajadores` |
| Edición de la ficha de la empresa (nombre, NIT) | `PUT /api/v1/empresas/{id}`; el código empresarial es estable y no se edita |
| Cajas, turnos y arqueo; clientes como entidad | Sin definir (ver decisiones pendientes en `docs/modulo-comercial.md`) |

## Datos de demostración

Cinco empresas ficticias con un administrador comercial y un trabajador cada una: Mercado Verde
(`MVSA01`, flujo comercial completo), Suministros del Caribe (`SCA002`, para comprobar el
aislamiento), Distribuciones Nova (`DNS003`, trabajador pendiente), Comercializadora Horizonte
(`CHS004`, trabajador rechazado) y Logística Andina (`LAS005`, desactivada). Solo con el perfil
`demo` (con un perfil de producción activo el arranque se detiene). Idempotente: ejecutarla otra
vez no duplica nada ni cambia contraseñas, estados ni datos existentes; si un código, NIT, username
o correo suyo ya lo usa otro registro, se detiene sin escribir nada. En el VPS se ejecuta solo con
el procedimiento de [deploy/aatenea](deploy/aatenea/README.md).

La contraseña de las cuentas demo **no está en el repositorio**: la eliges tú en la variable
`DEMO_PASSWORD` (de 8 caracteres a 72 bytes), sin valor por defecto; si falta, la seed no escribe
nada y el perfil `demo` no arranca. También puede ir en `backend-spring/.env` (no en el `.env` de
la raíz); formato, prioridad, cuentas y escenarios en
[docs/modulo-comercial.md](docs/modulo-comercial.md#10-datos-de-demostración-seed).

```bash
cd backend-spring
read -rsp 'DEMO_PASSWORD: ' DEMO_PASSWORD; echo   # bash: sin eco y sin quedar en el historial
export DEMO_PASSWORD
SPRING_PROFILES_ACTIVE=demo ./mvnw spring-boot:run
unset DEMO_PASSWORD                                 # al terminar
```

## Calidad

```bash
cd backend-spring && ./mvnw test                  # 303 tests (H2; también contra PostgreSQL)
cd frontend && npm run lint && npm test && npm run build   # oxlint, Vitest + Testing Library, TypeScript estricto
```
