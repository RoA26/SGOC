# Bastión

Plataforma genérica y multiempresa de gestión de inventario: organizaciones, usuarios con tres
roles, catálogo, bodegas, existencias y movimientos auditables.

> **Estado: primera entrega (MVP) en desarrollo local.** No está desplegada. Ver
> [Estado y pendientes](#estado-y-pendientes).

## Arquitectura

Monolito modular:

```
Navegador → Nginx del servidor (TLS) → contenedor frontend (Nginx: estáticos + /api)
          → contenedor backend (FastAPI, 1 worker) → PostgreSQL 16 (red interna)
```

| Parte | Tecnología |
|---|---|
| `backend/` | Python 3.13, FastAPI, SQLAlchemy 2, Alembic, Pydantic 2, PyJWT, Argon2id, Pytest |
| `frontend/` | React 18, TypeScript estricto, Vite 6, Tailwind CSS 4, React Router 6, Zod, Vitest |
| Infra | Docker Compose (proyecto `bastion`), Nginx |

Módulos del backend (`backend/app/modules/`), cada uno con `models`, `schemas`, `service`
(lógica y transacciones) y `router` (HTTP): `auth`, `organizations`, `users`, `catalog`,
`inventory`, `movements`. Los módulos `suppliers`, `stocktakes`, `reports`, `notifications` y
`files` llegarán en fases siguientes; no se crearon vacíos para no aparentar funcionalidad.

Dependencias del frontend y su motivo: `react-router-dom` (rutas y protección), `zod`
(validación que refleja la del backend), `@fontsource-variable/*` (tipografía alojada en el
propio sitio, sin peticiones a terceros). Los iconos son SVG propios (`src/components/Icon.tsx`).

## Roles y permisos (política inicial)

| Operación | SUPER_ADMIN | ADMINISTRADOR_COMERCIAL | TRABAJADOR |
|---|:-:|:-:|:-:|
| Crear / activar / desactivar organizaciones (+ primer administrador) | ✔ | | |
| Gestionar trabajadores de su organización | | ✔ | |
| Crear productos, categorías y bodegas | | ✔ | |
| Consultar catálogo y existencias | | ✔ | ✔ |
| Ver costo promedio y valor del inventario (interfaz) | | ✔ | |
| Registrar entradas y salidas | | ✔ | ✔ |
| Registrar ajustes (con justificación) | | ✔ | |
| Revertir movimientos | | ✔ | |
| Ver historial de movimientos | | toda la organización | solo los propios |

Decisiones tomadas y que conviene confirmar:

- **SUPER_ADMIN no opera datos de negocio** (productos, inventario) en esta fase: no pertenece a
  ninguna organización. Si se necesita soporte sobre los datos de una empresa, habrá que diseñar
  un acceso explícito, limitado y auditado.
- El ADMINISTRADOR_COMERCIAL solo crea **TRABAJADOR**; otros administradores de la organización
  los crea SUPER_ADMIN (en esta fase solo el primero, al crear la organización).
- El costo y el valor se ocultan al TRABAJADOR en la interfaz, pero **la API de inventario sí
  los devuelve** a cualquier miembro. Si deben ser confidenciales, hay que filtrarlos en el backend.

### Cómo se aplica

- El JWT (HS256, expiración 30 min) solo lleva `sub` y una versión de token. En **cada petición**
  se lee el usuario de la BD y se comprueban: cuenta activa, organización activa, versión del token
  y rol vigente. Cerrar sesión, desactivar un usuario o una organización incrementa la versión e
  invalida los tokens emitidos.
- Multiempresa: el `organization_id` sale siempre del usuario autenticado, nunca de la petición.
  Además, la BD tiene **claves foráneas compuestas** `(organization_id, id)`: un movimiento, saldo
  o producto no puede referenciar una bodega, producto, categoría o usuario de otra organización
  aunque un servicio fallara. El SKU es único por organización.
- No hay registro público. El primer SUPER_ADMIN se crea con un bootstrap de un solo uso.

## Inventario

Ver [`docs/inventario.md`](docs/inventario.md): concurrencia, idempotencia, costo promedio
ponderado, ajustes y reversiones.

## Desarrollo local

Requisitos: Python ≥ 3.12, Node 22, PostgreSQL 16 (local o en contenedor).

```bash
# Base de datos (ejemplo con un PostgreSQL ya instalado; use un puerto que no esté ocupado)
createdb -h 127.0.0.1 -p 55432 -U bastion bastion_dev

# Backend
cd backend
python3 -m venv .venv && . .venv/bin/activate
pip install -r requirements-dev.txt
export BASTION_DATABASE_URL=postgresql+psycopg://bastion@127.0.0.1:55432/bastion_dev
export BASTION_JWT_SECRET=$(python3 -c "import secrets; print(secrets.token_urlsafe(48))")
alembic upgrade head
python -m app.bootstrap --email admin@example.com --name "Administrador"   # pide la contraseña
uvicorn app.main:app --reload --port 8000        # documentación: http://localhost:8000/api/docs

# Frontend (en otra terminal)
cd frontend && npm install && npm run dev        # http://localhost:5174 (proxy /api → :8000)
```

Flujo inicial: entrar como SUPER_ADMIN → crear una organización con su administrador → entrar
como ese administrador → crear bodega, productos y trabajadores → registrar movimientos.

### Calidad (ejecutar antes de dar algo por terminado)

```bash
cd backend
export BASTION_TEST_DATABASE_URL=postgresql+psycopg://bastion@127.0.0.1:55432/bastion_test  # BD desechable
pytest                     # usa PostgreSQL real: bloqueos de fila, índices parciales, triggers
ruff check . && ruff format --check . && mypy app
BASTION_DATABASE_URL=$BASTION_TEST_DATABASE_URL alembic check   # modelos y migraciones coinciden

cd ../frontend
npm test && npm run build  # build incluye tsc estricto
```

> Las pruebas del backend **borran y recrean** el esquema de `BASTION_TEST_DATABASE_URL`.
> Nunca la apunte a una base con datos.

## Despliegue

Ver [`docs/despliegue.md`](docs/despliegue.md). Resumen: proyecto Compose `bastion`, solo el
frontend publica un puerto y únicamente en `127.0.0.1`; PostgreSQL y la API quedan en la red
interna `bastion_net`. El frontend se compila fuera del VPS.

## Estado y pendientes

Hecho en esta entrega (con pruebas): página de bienvenida, login, roles, organizaciones,
usuarios, categorías, productos, bodegas, consulta de inventario, entradas/salidas/ajustes,
reversiones, idempotencia, concurrencia, historial inmutable, panel con navegación por rol.

Pendiente (fases siguientes): proveedores, conteos físicos (`ABIERTO/APROBADO/ANULADO`),
escáner de códigos, informes PDF/Excel, importación, firmas y archivos privados, notificaciones,
despiece; edición de productos y bodegas desde la interfaz (la API ya permite editar productos);
traslados entre bodegas; limitación de intentos de inicio de sesión; cambio de contraseña;
paginación en la interfaz; despliegue real. Ver también
[`docs/hallazgos-poliway.md`](docs/hallazgos-poliway.md).
