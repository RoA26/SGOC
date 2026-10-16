# Unisen SGP: Sistema de Gestión de Órdenes de Compra

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
        ├── brand/unisen/    tokens.css, componentes.css, logos SVG (marca Unisen)
        ├── lib/axios.ts     Instancia única de Axios (VITE_API_URL)
        ├── store/           Sesión con Zustand (accessToken, nombre…)
        ├── schemas/         Esquemas Zod (authSchema, proveedorSchema, productoSchema) = validaciones del backend
        ├── services/        proveedorService, productoService (/api/v1/...)
        ├── components/ui/   DataTable, Modal, ConfirmDialog, FormField
        ├── features/        auth (rutas, authApi, AuthLayout), proveedores y productos (formularios en modal)
        └── pages/           Login, Registro, Inicio, Proveedores, Productos, Solicitudes,
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
(p. ej. `NASC01`) y su administrador principal (rol GERENTE), que ya puede iniciar sesión. Los
trabajadores entran con una invitación de su gerente (entran directamente) o con el código de
registro secreto de su empresa (quedan pendientes hasta que el gerente los aprueba). Detalle en
[backend-spring/README.md](backend-spring/README.md#gestión-centralizada-de-empresas).

**Al actualizar un despliegue anterior a la gestión de empresas:** la migración V7 no borra
nada; asigna a cada empresa existente el código empresarial `EMP` + su id (`EMP001`, `EMP002`…) y
conserva su código de registro de trabajadores.

**Al actualizar un despliegue anterior al Hito 4:** la migración V3 asigna a cada usuario
existente un username a partir de su correo (`admin@unisen.com` → `admin`), las sesiones
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

## Marca Unisen

Todos los recursos de marca viven en `frontend/src/brand/unisen/`:

| Archivo | Uso |
| --- | --- |
| `tokens.css` | Colores, tipografías, radios y sombras como variables `--unisen-*` (modo claro y oscuro) |
| `componentes.css` | Tokens de componente y clases base `u-input`, `u-btn`, `u-card`, `u-alert`… |
| `unisen-logo-claro.svg` | Logo para fondos oscuros (panel de marca del login) |
| `unisen-logo-oscuro.svg` | Logo para fondos claros (móvil, cabecera) |

> ⚠ **Ahora mismo son marcadores de posición.** Para integrar los oficiales, sustituye estos
> archivos con los mismos nombres. Si el `tokens.css` oficial usa otros nombres de variable,
> ajusta solo el mapeo de `frontend/tailwind.config.ts`.

`tailwind.config.ts` no contiene valores, solo referencias a variables. Por ejemplo, `bg-primary`
genera `var(--unisen-color-primary)`. Así el modo oscuro y cualquier cambio de marca se aplican
desde los tokens. Las opacidades funcionan igual (`bg-accent/20`).

| Clases de Tailwind | Variable |
| --- | --- |
| `bg-background` | `--unisen-color-bg` |
| `bg-surface`, `bg-surface-muted` | `--unisen-color-surface`, `--unisen-color-surface-muted` |
| `text-foreground`, `text-foreground-muted` | `--unisen-color-text`, `--unisen-color-text-muted` |
| `bg-primary`, `text-primary-foreground` | `--unisen-color-primary`, `--unisen-color-on-primary` |
| `text-accent` | `--unisen-color-accent` |
| `bg-brand-panel`, `text-brand-panel-foreground` | `--unisen-color-brand-panel`, `--unisen-color-brand-panel-text` |
| `font-sans`, `font-serif` | `--unisen-font-sans` (Geist), `--unisen-font-serif` (Instrument Serif) |
| `rounded-{sm,md,lg,xl}`, `shadow-card`, `h-control` | `--unisen-radius-*`, `--unisen-shadow-card`, `--unisen-control-height` |

Geist e Instrument Serif se sirven desde el propio dominio con `@fontsource`, sin CDN externos.

## API (a través de Nginx)

| Método | Ruta | Descripción |
| --- | --- | --- |
| POST | `/api/auth/login` | `{username, password}` → `{accessToken, tokenType, expiresIn, usuario}` (`usuario.empresaId`, salvo SUPER_ADMIN) |
| POST | `/api/auth/registro` | `{username, email, password, codigoInvitacion \| codigoEmpresa, nombre?}` → `201` usuario USUARIO de la empresa del código: ACTIVO con invitación, PENDIENTE con código de empresa |
| POST | `/api/v1/empresas` | SUPER_ADMIN: empresa + código empresarial (`codigo`, indicado o propuesto) + administrador principal GERENTE, todo o nada |
| GET | `/api/v1/empresas[/{id}]` | SUPER_ADMIN: listado paginado y ficha, con `codigo` y el administrador principal en `gerente` |
| GET | `/api/v1/empresas/codigo-sugerido?nombre=` · `/codigo-disponible?codigo=` | SUPER_ADMIN: propone (`NASCAR` → `NASC01`) y comprueba un código empresarial |
| GET | `/api/v1/empresas/{id}/usuarios` | SUPER_ADMIN: usuarios de esa empresa (gerentes y trabajadores) con su estado |
| PATCH | `/api/v1/empresas/{id}/estado` · `/{id}/gerentes/{usuarioId}/estado` | SUPER_ADMIN: activar o desactivar la empresa, o la cuenta de uno de sus gerentes |
| GET | `/api/v1/empresas/actual` | GERENTE, o SUPER_ADMIN con `X-Tenant-ID`: empresa en la que se trabaja, con sus códigos |
| GET · PATCH | `/api/v1/trabajadores[?estado=]` · `/{id}/estado` | GERENTE, o SUPER_ADMIN con `X-Tenant-ID`: trabajadores de esa empresa; aprobar, rechazar, desactivar o reactivar |
| POST | `/api/auth/invitaciones` | GERENTE (o SUPER_ADMIN con `X-Tenant-ID`, solo por API): `{horasValidez?}` → `201` `{codigo, fechaExpiracion}` |
| GET | `/api/auth/me` | Perfil del usuario autenticado (Bearer) |
| GET | `/api/v1/proveedores?page=0&size=10&sort=razonSocial,asc` | Listado paginado |
| GET · POST · PUT · DELETE | `/api/v1/proveedores[/{id}]` | Detalle, alta, edición y baja lógica |
| GET | `/api/v1/productos?page=0&size=10&sort=nombre,asc` | Listado paginado (incluye el proveedor) |
| GET · POST · PUT · DELETE | `/api/v1/productos[/{id}]` | Detalle, alta, edición y baja lógica |
| GET · POST | `/api/v1/solicitudes[?estado=]` | USUARIO ve y crea las suyas; GERENTE ve las de su empresa; SUPER_ADMIN consulta |
| PATCH | `/api/v1/solicitudes/{id}/estado` | GERENTE o SUPER_ADMIN con `X-Tenant-ID`: `{estado: APROBADA \| RECHAZADA, comentario}` |

Lecturas: cualquier usuario autenticado (de su empresa). Altas, cambios y bajas de catálogos:
`GERENTE` o `SUPER_ADMIN` dentro de una empresa. Detalle de reglas y errores en
[backend-spring/README.md](backend-spring/README.md#saas-multi-empresa-tenants).

## Roles, empresa y navegación (frontend)

El SGOC es multi-empresa: cada empresa cliente ve solo sus datos y el backend lo impone.

| Rol | Empresa de trabajo | Menú |
| --- | --- | --- |
| `USUARIO` | La suya (de la sesión) | Inicio · Mis solicitudes · Órdenes relacionadas (próximamente) |
| `GERENTE` | La suya (de la sesión) | Inicio · Solicitudes · Órdenes de compra y Recepciones (próximamente) · Productos · Proveedores · Invitar usuarios |
| `SUPER_ADMIN` | Ninguna (modo global) o la que supervisa | Inicio · Solicitudes · Productos · Proveedores (consulta) · Empresas |

- **Panel lateral:** muestra la empresa, el usuario y su rol. GERENTE y USUARIO no pueden cambiar
  de empresa.
- **SUPER_ADMIN:** entra en **modo global** (consulta todas las empresas). En **Empresas** pulsa
  *Supervisar* en una empresa: desde entonces **solo sus peticiones** llevan `X-Tenant-ID: <id>`,
  una franja fija indica qué empresa supervisa y toda la interfaz muestra solo sus datos, en
  **solo lectura** (sigue siendo SUPER_ADMIN: no hay suplantación de ningún usuario). La elección
  dura la pestaña (sessionStorage), se borra al cerrar sesión y *Volver al modo global* la quita.
  GERENTE y USUARIO nunca envían la cabecera (el backend la ignoraría).
- **Rutas protegidas:** `/productos` y `/proveedores` para GERENTE y SUPER_ADMIN;
  `/admin/invitaciones` solo para GERENTE; `/admin/empresas[/:id]` solo para SUPER_ADMIN; el
  resto vuelve al inicio.
- **Sesiones anteriores** (con el rol `ADMIN`, que ya no existe) se descartan y piden iniciar
  sesión de nuevo.
- **Cuenta que pierde la autorización:** si el backend responde `401`, o `403` con `motivo`
  (cuenta pendiente, rechazada, desactivada o empresa desactivada), la interfaz cierra la sesión;
  al volver a entrar, el login muestra el motivo. Un `403` sin `motivo` (falta de permisos para
  una acción) no la cierra.

**Flujo de compra:** Necesidad → Solicitud → Aprobación → Orden de compra → Recepción. Hoy el
backend cubre hasta la aprobación; *Órdenes de compra* y *Recepciones* aparecen como
"Próximamente" y una solicitud aprobada muestra "Generar orden de compra" deshabilitado.

**Inicio:** solicitudes pendientes, aprobadas y rechazadas (de la empresa, o las propias para un
USUARIO), actividad reciente y accesos a *Nueva solicitud* y *Revisar pendientes*.

**Precios en COP:** la interfaz trabaja con pesos colombianos enteros (sin decimales) y los
muestra como `$ 1.250.000`. "Nuevo producto" queda deshabilitado mientras no haya ningún
proveedor registrado.

**Registro:** quien recibe un código de invitación crea su cuenta en `/registro` (o con el
enlace `/registro?codigo=XXXX-XXXX-XXXX-XXXX`, que precarga el código) y entra directamente en la
empresa del código con rol USUARIO. Los errores del servidor (código inválido, usado o caducado;
usuario o correo en uso) aparecen junto al campo afectado.

**Solicitudes de compra (`/solicitudes`):** GERENTE y USUARIO piden productos del catálogo con un
motivo y tantas líneas como necesiten (producto + cantidad), sin elegir proveedor, y ven el total
estimado en COP. La solicitud queda `PENDIENTE`; el GERENTE (o el SUPER_ADMIN dentro de la
empresa) la abre y la aprueba o la rechaza (con motivo). Un USUARIO solo ve las suyas. La URL
admite `?estado=PENDIENTE`, `?ver=<id>` y `?nueva=1`.

**Empresas (`/admin/empresas`, SUPER_ADMIN):** listado de las empresas con su código, su
administrador principal, su estado y su alta; *Crear empresa* abre un formulario único para la
empresa (nombre comercial, NIT, código empresarial propuesto a partir del nombre y comprobado
mientras se escribe) y su administrador principal (nombre, correo, usuario y contraseña inicial,
con un generador de contraseñas seguras). Se crean juntos o no se crea nada. Cada fila ofrece
*Detalles* (ficha con datos, administrador, actividad real y usuarios; desde ella se desactiva o
reactiva la empresa o la cuenta de su administrador, con confirmación) y *Supervisar*.

**Invitar usuarios (`/admin/invitaciones`, GERENTE):** el gerente genera un código con un clic y
lo copia, o copia el enlace de registro. Es el alta de trabajadores de su empresa (no de
empresas). Cada código se muestra solo mientras no se sale de la página.

### Pendiente en el backend (la interfaz no lo simula)

| Necesidad del frontend | Endpoint que falta |
| --- | --- |
| Nombre de la empresa en el panel de GERENTE y USUARIO (hoy "Empresa N.º X"; el SUPER_ADMIN ya ve nombre y código) | `GET /api/v1/empresas/actual` sirve a los gestores; para todos los roles falta `empresaNombre` en `UsuarioResponse` |
| Registro con código de empresa y aprobación de trabajadores | Endpoints ya disponibles (Hito 2); falta la interfaz |
| Cambio de contraseña (también la inicial del administrador) y recuperación | Sin definir: hoy no hay ningún mecanismo |
| Empresa de cada solicitud en modo global | `empresaId`/`empresaNombre` en `SolicitudResponseDTO` |
| Órdenes de compra y sus estados | `/api/v1/ordenes-compra` (crear desde una solicitud aprobada, listar, detalle, cambiar estado) |
| Recepciones (también parciales) | `/api/v1/ordenes-compra/{id}/recepciones` |
| Gestión de gerentes (alta de más gerentes, cambio de rol) | Sin definir; el SUPER_ADMIN ya desactiva o reactiva sus cuentas y los trabajadores se gestionan con `/api/v1/trabajadores` |
| Edición de la ficha de la empresa (nombre, NIT) | `PUT /api/v1/empresas/{id}`; el código empresarial es estable y no se edita |
| Reportes | Sin definir |

## Calidad

```bash
cd backend-spring && ./mvnw test                  # 170 tests
cd frontend && npm run lint && npm run build      # oxlint + TypeScript estricto
```
