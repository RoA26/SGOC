# Nexora (SGOC): Sistema de Gestión de Órdenes de Compra

**Este directorio (`~/dev/SeNa/SGOC`) es el proyecto activo.** `~/dev/SeNa/proyecto_sgoc` es
una copia antigua del mismo repositorio que solo se conserva como referencia.

SaaS multiempresa: solicitudes → aprobación → órdenes de compra → recepción → inventario,
más módulo comercial (precios, POS, devoluciones, reportes). Producción: rrtf.duckdns.org.
Documentación de referencia: `README.md`, `backend-spring/README.md` y `docs/`.

## Arquitectura

Navegador → Nginx (`frontend`, :80; estáticos + proxy `/api`) → Spring Boot (`backend`, :8081)
→ PostgreSQL 16. Mismo origen en producción; PostgreSQL y backend no publican puertos.

- `backend-spring/`: Java 21, Spring Boot 3.5, Spring Security (JWT HS256), Spring Data JPA,
  Flyway, Apache POI (Word), OpenPDF, Actuator, springdoc. Paquete `com.unisen.sgp`:
  `config · controller · exception · model/{entity,dto} · repository · security · service · tenant`.
- `frontend/`: React 18, TypeScript estricto, Vite, Tailwind CSS v4, Zustand, Axios,
  react-hook-form + Zod, Vitest + Testing Library, oxlint.
- `docker-compose.yml` (proyecto Compose `sgp`): servicios `postgres`, `backend`, `frontend`.

## Comandos

Desarrollo local:

- BD: `docker compose up -d postgres` (desde la raíz; requiere un `.env` creado a partir de
  `.env.example`). PostgreSQL queda en 127.0.0.1:5432.
- Backend: `cd backend-spring && ./mvnw spring-boot:run` → http://localhost:8081
- Frontend: `cd frontend && npm install && npm run dev` → http://localhost:5173 (proxy `/api`)
- Datos de demostración (solo desarrollo): `SPRING_PROFILES_ACTIVE=demo ./mvnw spring-boot:run`

Calidad (ejecutar antes de dar algo por terminado):

- `cd backend-spring && ./mvnw test` (H2 en modo PostgreSQL; compila con `-Xlint:all -Werror`)
- `cd frontend && npm run lint && npm test && npm run build`

## Convenciones

- Dominio, código, mensajes y commits en español.
- DTO como `record`; errores con `GlobalExceptionHandler` (RFC 9457 / Problem Details).
- Esquema solo por Flyway (`ddl-auto: validate`). Migraciones en
  `src/main/resources/db/migration` (V1…V15) y `src/main/java/db/migration` (V16, Java).
  Una migración aplicada nunca se edita: siempre una nueva `V<n+1>__descripcion`.
- Ningún warning del compilador está permitido (rompe el build).
- Frontend: los esquemas Zod reflejan las validaciones del backend; la marca va por variables
  CSS en `src/brand/nexora/` y `tailwind.config.ts` solo referencia variables.
- Precios en COP enteros.

## Reglas de negocio y seguridad que no se rompen

- Multiempresa: cada empresa ve solo sus datos y lo impone el backend. SUPER_ADMIN debe enviar
  `X-Tenant-ID: <id>` o `global` en las rutas de negocio.
- La autorización se lee de la BD en cada petición, nunca del rol escrito en el token.
- Roles: `SUPER_ADMIN`, `ADMINISTRADOR_COMERCIAL`, `TRABAJADOR`.
- No leer ni mostrar archivos `.env`; no escribir secretos en código, logs, documentación ni
  commits.
- El comprobante de venta es interno: no es factura electrónica (DIAN fuera de alcance).

## Docker: prohibido sin mi autorización

- No ejecutar Docker Compose (ni ningún comando) contra producción o el VPS.
- No levantar a la vez esta copia y `~/dev/SeNa/proyecto_sgoc`: las dos usan el nombre de
  proyecto Compose `sgp` y las mismas imágenes `unisen/sgp-*:latest`, así que una reemplazaría
  los contenedores de la otra.
- El puerto 5432 también lo usa el PostgreSQL de Eden (`~/dev/EdeN/Eden-app.V1`).

## Pendientes (no corregir sin consultarme)

- El número de pruebas del backend difiere entre `README.md` y `backend-spring/README.md`.
- La documentación menciona CI, pero el repositorio no tiene configuración de CI (`.github/`).
- Las decisiones abiertas de la sección "Pendiente en el backend" del `README.md`.

## Antes de cambios grandes

Consultar conmigo y revisar la documentación correspondiente antes de:

- Nuevas migraciones o cambios de esquema y roles → `backend-spring/README.md` y
  `docs/auditoria-sgp.md`.
- Cálculos de venta, costo promedio, descuentos o devoluciones → `docs/modulo-comercial.md`
  (la valoración del inventario es provisional).
- POS y sus decisiones pendientes → `docs/pos-diseno.md`.
- Cambios en seguridad, JWT, CORS, Nginx o Docker.
