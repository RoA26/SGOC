# SGOC: Sistema de Gestión de Órdenes de Compra

Reemplazo del sistema monolítico heredado por una arquitectura desacoplada:

| Capa     | Tecnología                                                                   |
| -------- | ---------------------------------------------------------------------------- |
| Backend  | FastAPI · SQLAlchemy 2 (async) · Pydantic v2 · asyncpg · Alembic · PostgreSQL |
| Frontend | React 18 · Vite · TypeScript (strict) · Tailwind CSS v4 · React Router · Axios |

> **Estado: Hito 1, configuración base y autenticación.** Proveedores, productos y
> órdenes de compra quedan fuera de este hito.

## Estructura

```
.
├── docker-compose.yml          # PostgreSQL 16 para desarrollo
├── backend/
│   ├── requirements.txt        # Dependencias de producción (versiones fijadas)
│   ├── requirements-dev.txt    # + pytest, ruff, mypy
│   ├── alembic/                # Migraciones (async)
│   ├── app/
│   │   ├── main.py             # Punto de entrada: FastAPI + CORS + routers
│   │   ├── cli.py              # Administración: crear usuarios
│   │   ├── api/                # Routers HTTP (auth.py) y dependencias (deps.py)
│   │   ├── core/               # config.py, database.py, security.py
│   │   ├── models/             # Modelos SQLAlchemy (User)
│   │   ├── schemas/            # Schemas Pydantic (entrada/salida)
│   │   └── services/           # Lógica de negocio (sin conocimiento de HTTP)
│   └── tests/
└── frontend/
    └── src/
        ├── components/ui/      # Button, Input, Alert, Spinner, FullPageLoader
        ├── features/auth/      # AuthProvider, useAuth, ProtectedRoute, GuestRoute, API
        ├── lib/                # Instancia de Axios, tokenStorage, utilidades JWT/errores
        ├── pages/              # Login.tsx, Dashboard.tsx
        └── App.tsx             # Enrutador
```

## Puesta en marcha

Requisitos: Python 3.12+, Node.js 20.19+ (o 22.12+), y PostgreSQL 16 o Docker.

### 1. Base de datos

```bash
docker compose up -d
```

### 2. Backend (http://localhost:8000)

```bash
cd backend
python -m venv .venv
source .venv/bin/activate            # Windows: .venv\Scripts\activate
pip install -r requirements-dev.txt

cp .env.example .env
# Genera y pega una clave JWT en .env (JWT_SECRET_KEY):
python -c "import secrets; print(secrets.token_urlsafe(64))"

alembic upgrade head                 # Crea la tabla users
python -m app.cli create-user --email admin@example.com --full-name "Administrador"
uvicorn app.main:app --reload
```

La documentación interactiva queda en http://localhost:8000/docs. El botón *Authorize*
acepta el token devuelto por `/api/auth/login`.

> No hay endpoint público de registro: las cuentas se crean con la CLI. En entornos no
> interactivos la contraseña se puede pasar por stdin con `--password-stdin`.

### 3. Frontend (http://localhost:5173)

```bash
cd frontend
npm install
cp .env.example .env                 # VITE_API_URL=http://localhost:8000/api
npm run dev
```

#### Cómo se inicializó el frontend

Estos son los comandos que generaron `frontend/`. Sirven de referencia; no hay que volver a ejecutarlos.

```bash
# 1. Proyecto Vite con la plantilla React + TypeScript
npm create vite@latest frontend -- --template react-ts
cd frontend
npm install

# 2. Fijar React 18 (la plantilla actual de Vite instala React 19)
npm install react@^18.3.1 react-dom@^18.3.1
npm install -D @types/react@^18.3 @types/react-dom@^18.3

# 3. Enrutado y cliente HTTP
npm install react-router-dom axios

# 4. Tailwind CSS v4 con su plugin oficial para Vite
npm install tailwindcss @tailwindcss/vite
```

Configuración de Tailwind v4: no requiere `tailwind.config.js` ni PostCSS.

```ts
// vite.config.ts
import tailwindcss from '@tailwindcss/vite'
export default defineConfig({ plugins: [react(), tailwindcss()] })
```

```css
/* src/index.css */
@import 'tailwindcss';
```

## API (Hito 1)

| Método | Ruta              | Auth   | Descripción                                          |
| ------ | ----------------- | ------ | ---------------------------------------------------- |
| POST   | `/api/auth/login` | —      | `{email, password}` → `{access_token, token_type, expires_in}` |
| GET    | `/api/auth/me`    | Bearer | Perfil del usuario autenticado                       |
| GET    | `/api/health`     | —      | Estado del servicio y de la conexión a la BD         |

Códigos: `401` credenciales o token inválidos/expirados, `403` cuenta deshabilitada,
`422` payload inválido.

## Decisiones de diseño

**Backend**
- **bcrypt** directo (sin passlib, que está sin mantenimiento) con coste configurable
  (`BCRYPT_ROUNDS`). Se respeta su límite de 72 bytes, y el hashing se ejecuta en un
  hilo (`anyio.to_thread`) para no bloquear el event loop.
- **Anti-enumeración de usuarios:** un correo inexistente y una contraseña incorrecta
  devuelven el mismo mensaje y tardan lo mismo, porque se verifica contra un hash señuelo.
  Que una cuenta está deshabilitada solo se revela tras validar la contraseña.
- **JWT (PyJWT, HS256):** el algoritmo se fija en la verificación (evita el ataque `alg: none`).
  Los claims `sub`, `exp`, `iat` y `jti` son obligatorios y el claim `type=access` deja
  sitio para añadir *refresh tokens* más adelante. La clave es obligatoria y debe tener
  al menos 32 caracteres: sin ella la app no arranca.
- **Capas:** `api` (HTTP) → `services` (casos de uso, excepciones de dominio) → `models`.
  `core/security.py` no depende ni de HTTP ni de la BD.
- **Emails normalizados** a minúsculas, así que el login no distingue mayúsculas.
- **CORS** restringido a `CORS_ORIGINS` (por defecto `http://localhost:5173`), sin
  cookies: el token viaja en el header `Authorization`.
- **Alembic** con *naming convention* para tener nombres de constraints deterministas.

**Frontend**
- **Estado de sesión como unión discriminada** (`loading | authenticated | unauthenticated`):
  si `status === 'authenticated'`, TypeScript garantiza que `user` y `token` no son nulos.
- **Persistencia** del JWT en `localStorage`. Al arrancar se descarta si está caducado y,
  si no, se valida contra `/auth/me` antes de mostrar rutas privadas. Una caída de red no
  borra la sesión; un `401`/`403` sí.
- **Cierre de sesión automático** al expirar el token, ante cualquier `401` de la API
  (interceptor de Axios) y de forma sincronizada entre pestañas (evento `storage`).
- **Rutas:** `ProtectedRoute` redirige a `/login` recordando la ruta solicitada.
  `GuestRoute` impide volver a `/login` con sesión activa y redirige tras el login.

> **Nota de seguridad:** `localStorage` es accesible desde JavaScript, así que un XSS podría
> leer el token. Para producción se recomienda valorar una cookie `HttpOnly` +
> `SameSite` con *refresh tokens* (candidato a un hito posterior).

## Calidad

```bash
# Backend (desde backend/)
pytest                                   # SQLite en memoria, sin dependencias externas
# Contra PostgreSQL real (crea antes la base de datos sgoc_test):
TEST_DATABASE_URL=postgresql+asyncpg://sgoc:sgoc@localhost:5432/sgoc_test pytest
ruff check . && ruff format --check .
mypy app tests alembic/env.py            # modo strict

# Frontend (desde frontend/)
npm run lint                             # oxlint
npm run build                            # tsc (strict) + build de producción
```
