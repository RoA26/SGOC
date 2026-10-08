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
        └── pages/           Login, Registro, Inicio, Proveedores, Productos, admin/Invitaciones
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
usuario es el `SUPER_ADMIN` de la plataforma: se crea en el primer arranque y los siguientes no lo
modifican. El registro está cerrado: el resto de cuentas se crean con códigos de invitación que
genera un GERENTE, o el SUPER_ADMIN tras entrar en una empresa (*Trabajar en una empresa*).
Las cuentas invitadas entran como USUARIO; hoy no hay endpoint para ascender a GERENTE (ver
*Pendiente en el backend*).

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
| POST | `/api/auth/registro` | `{username, email, password, codigoInvitacion, nombre?}` → `201` usuario (rol USUARIO en la empresa del código) |
| POST | `/api/auth/invitaciones` | GERENTE, o SUPER_ADMIN con `X-Tenant-ID`: `{horasValidez?}` → `201` `{codigo, fechaExpiracion}` |
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
| `SUPER_ADMIN` | Ninguna (modo global) o la que elija | Como GERENTE |

- **Panel lateral:** muestra la empresa, el usuario y su rol. GERENTE y USUARIO no pueden cambiar
  de empresa.
- **SUPER_ADMIN:** entra en **modo global** (consulta todas las empresas; el backend rechaza las
  escrituras). Con *Trabajar en una empresa* escribe el id de la empresa; la interfaz lo valida
  contra el backend (`GET /api/auth/me` con `X-Tenant-ID`) y desde entonces **solo sus
  peticiones** llevan `X-Tenant-ID: <id>`. La elección dura la pestaña (sessionStorage), se
  borra al cerrar sesión y *Volver al modo global* la quita. GERENTE y USUARIO nunca envían la
  cabecera (el backend la ignoraría).
- **Rutas protegidas:** `/productos`, `/proveedores` y `/admin/invitaciones` solo para GERENTE y
  SUPER_ADMIN; el resto vuelve al inicio.
- **Sesiones anteriores** (con el rol `ADMIN`, que ya no existe) se descartan y piden iniciar
  sesión de nuevo.

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

**Invitar usuarios (`/admin/invitaciones`):** el GERENTE (o el SUPER_ADMIN dentro de una
empresa) genera un código con un clic y lo copia, o copia el enlace de registro. Cada código se
muestra solo mientras no se sale de la página.

### Pendiente en el backend (la interfaz no lo simula)

| Necesidad del frontend | Endpoint que falta |
| --- | --- |
| Selector de empresas del SUPER_ADMIN (hoy se escribe el id) | `GET /api/v1/empresas` (id, nombre, NIT, activa) |
| Nombre de la empresa en el panel (hoy "Empresa N.º X") | `GET /api/v1/empresas/{id}` o `empresaNombre` en `UsuarioResponse` |
| Empresa de cada solicitud en modo global | `empresaId`/`empresaNombre` en `SolicitudResponseDTO` |
| Órdenes de compra y sus estados | `/api/v1/ordenes-compra` (crear desde una solicitud aprobada, listar, detalle, cambiar estado) |
| Recepciones (también parciales) | `/api/v1/ordenes-compra/{id}/recepciones` |
| Usuarios de la empresa | `GET /api/v1/usuarios` (y cambio de rol / baja) |
| Ficha de la empresa | `GET · PUT /api/v1/empresa` |
| Reportes | Sin definir |

## Calidad

```bash
cd backend-spring && ./mvnw test                  # 118 tests
cd frontend && npm run lint && npm run build      # oxlint + TypeScript estricto
```
