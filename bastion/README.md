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

## Roles y permisos

**Trabajador**: opera el inventario. **Administrador comercial**: además gestiona el catálogo,
las bodegas, el equipo y los ajustes; la interfaz lo distingue con un distintivo, una franja de
acento y la sección "Administración". **Super admin**: el equipo que desarrolla la plataforma;
por ahora gestiona organizaciones (los informes y el control total llegarán en otra fase).

| Operación | SUPER_ADMIN | ADMINISTRADOR_COMERCIAL | TRABAJADOR |
|---|:-:|:-:|:-:|
| Crear / activar / desactivar organizaciones (+ primer administrador) | ✔ | | |
| Crear, editar y desactivar productos y categorías | | ✔ | |
| Crear, renombrar y desactivar bodegas | | ✔ | |
| Crear trabajadores, activarlos/desactivarlos y asignarles contraseña | | ✔ | |
| Registrar entradas y salidas | | ✔ | ✔ |
| Registrar ajustes (con justificación) y revertir movimientos | | ✔ | |
| Consultar catálogo, existencias e historial de la organización | | ✔ | ✔ |
| Ver costos, costo promedio y valor del inventario | | ✔ | |
| Cambiar su propia contraseña | ✔ | ✔ | ✔ |

Notas:

- Los costos se ocultan al TRABAJADOR **en la API** (llegan vacíos), no solo en la interfaz.
- SUPER_ADMIN no pertenece a ninguna organización y todavía no opera datos de negocio.
- Ningún rol puede cambiar su propio rol ni crear roles superiores.

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
ponderado (por producto, alineado con Poliway), ajustes y reversiones.

La auditoría del borrador original está en [`docs/hallazgos-poliway.md`](docs/hallazgos-poliway.md)
y el inventario de hashes en [`docs/poliway-sha256.txt`](docs/poliway-sha256.txt).

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

Ver [`docs/despliegue.md`](docs/despliegue.md): `deploy/scripts/bastion-vps.sh` diagnostica el
VPS, prepara el `.env`, despliega el proyecto Compose `bastion` y configura el proxy del dominio
sin tocar otros servicios. Solo el frontend publica un puerto, y únicamente en `127.0.0.1`.

## Estado y pendientes

Hecho en esta entrega (con pruebas): página de bienvenida, login, roles, organizaciones,
usuarios, categorías, productos, bodegas, consulta de inventario, entradas/salidas/ajustes,
reversiones, idempotencia, concurrencia, historial inmutable, panel con navegación por rol.

Pendiente (fases siguientes): panel completo del SUPER_ADMIN (informes y control global),
proveedores, conteos físicos (`ABIERTO/APROBADO/ANULADO`), escáner de códigos, informes
PDF/Excel, importación, firmas y archivos privados, notificaciones, despiece; traslados entre
bodegas; paginación en la interfaz; destino de las salidas (funcionario, área, cliente,
equipo) como en Poliway. Las decisiones abiertas están en
[`docs/hallazgos-poliway.md`](docs/hallazgos-poliway.md) §9.
