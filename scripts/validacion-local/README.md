# Validación local de integración

`integracion.mjs` recorre Nexora con un navegador real (Chromium, vía Playwright) contra el
frontend, el backend y un PostgreSQL **locales**, sin respuestas simuladas. Comprueba, entre otras
cosas: autenticación, alta de empresas, registro con invitación y con el código de la empresa
(válido, inexistente, de una empresa desactivada y con los dos códigos a la vez), cuenta pendiente,
aprobación y desactivación en *Personal*, el código de registro de la propia empresa, la actividad
registrada, el aislamiento entre empresas y los permisos por rol.

> ⚠ **Crea datos ficticios** (dos empresas, administradores, trabajadores, un proveedor, un
> producto y una solicitud). Úsalo solo con una base de datos **local, vacía y desechable**. El
> script se niega a ejecutarse si la URL no es `localhost` o `127.0.0.1`. Nunca contra producción.

## Requisitos

- JDK 21, Node ≥ 20.19 y PostgreSQL 16 (paquete del sistema o un contenedor local).
- Playwright con Chromium. **No es una dependencia del proyecto**: instálalo aparte, por ejemplo en
  una carpeta propia (`npm install playwright@1.56.1 && npx playwright install chromium`), y pasa su
  módulo en `PLAYWRIGHT_MODULE`.

## Pasos

1. Crea una base de datos vacía en un PostgreSQL local (p. ej. `sgp_validacion`). Las migraciones
   de Flyway se aplican solas al arrancar el backend.
2. Arranca el backend contra esa base, con valores **solo locales** (nunca los de producción):

   ```bash
   cd backend-spring
   DB_URL=jdbc:postgresql://127.0.0.1:<puerto>/sgp_validacion DB_USERNAME=<usuario> DB_PASSWORD=<clave> \
   JWT_SECRET="$(openssl rand -base64 64 | tr -d '\n')" \
   ADMIN_USERNAME=superadmin.local ADMIN_EMAIL=superadmin@ejemplo.test ADMIN_PASSWORD=<clave-local> \
   ./mvnw spring-boot:run
   ```

   `ADMIN_*` crea en el primer arranque la cuenta SUPER_ADMIN que usa el recorrido.
3. Arranca el frontend: `cd frontend && npm run dev`. Ábrelo y úsalo como
   **`http://localhost:5173`**: con `127.0.0.1` el backend rechaza el login por CORS (no es un
   origen permitido).
4. Ejecuta el recorrido desde la raíz del repositorio:

   ```bash
   NEXORA_ROOT_USER=superadmin.local NEXORA_ROOT_PASSWORD=<clave-local> \
   NEXORA_CLAVE_CUENTAS=<clave-para-las-cuentas-ficticias> \
   PLAYWRIGHT_MODULE=/ruta/a/node_modules/playwright/index.mjs \
   node scripts/validacion-local/integracion.mjs
   ```

   Termina con `RESUMEN: N/N comprobaciones superadas` y código de salida 0, o 1 si alguna falla.
   Para repetirlo, vuelve a crear la base vacía y reinicia el backend.

## Variables

| Variable | Obligatoria | Uso |
| --- | --- | --- |
| `NEXORA_ROOT_USER`, `NEXORA_ROOT_PASSWORD` | Sí | Cuenta SUPER_ADMIN local (la de `ADMIN_*`) |
| `NEXORA_CLAVE_CUENTAS` | Sí | Contraseña de las cuentas ficticias que crea el recorrido (8 a 72 caracteres) |
| `NEXORA_BASE_URL` | No | Por defecto `http://localhost:5173`; solo se admite `localhost` o `127.0.0.1` |
| `PLAYWRIGHT_MODULE` | No | Módulo de Playwright; por defecto `playwright` (si está instalado junto al script) |
| `PLAYWRIGHT_CHROMIUM` | No | Ejecutable de Chromium, si no es el que descarga Playwright |
| `NEXORA_SALIDA` | No | Carpeta para las capturas de pantalla |

Ninguna credencial se guarda en el repositorio: todas llegan por variables de entorno.
