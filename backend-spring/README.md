# Unisen SGP: backend Spring Boot

API REST del Sistema de Gestión de Órdenes de Compra: core Spring Boot y seguridad JWT
(Hito 1), catálogos de proveedores y productos (Hito 3) y **login por username con registro
cerrado mediante códigos de invitación (Hito 4)**.

| Tecnología | Versión |
| --- | --- |
| Java | 21 (LTS) |
| Spring Boot | 3.5.16 (Web, Security, Data JPA, Validation, Actuator) |
| JWT | jjwt 0.13.0 (HS256) |
| Base de datos | PostgreSQL 16 + Flyway |
| Documentación | springdoc-openapi 2.8 (Swagger UI) |

## Estructura

```
src/main/java/com/unisen/sgp
├── SgpApplication.java
├── config/            SecurityConfig (SecurityFilterChain, CORS, BCrypt, AuthenticationManager),
│                      OpenApiConfig, propiedades tipadas, AdminBootstrap (alta del primer admin)
├── controller/        AuthController (login, registro, invitaciones, me), ProveedorController,
│                      ProductoController
├── exception/         GlobalExceptionHandler (RFC 9457), RecursoNoEncontrado, Conflicto,
│                      CampoInvalido, InvitacionInvalida, DatoDuplicado
├── model/entity/      Usuario, CodigoInvitacion, Rol, Proveedor, Producto, EntidadAuditable
├── model/dto/         Records de request/response (LoginRequest, RegistroRequestDTO,
│                      Invitacion…DTO, Proveedor…DTO, Producto…DTO), Patrones
├── repository/        UsuarioRepository, CodigoInvitacionRepository, ProveedorRepository,
│                      ProductoRepository
├── security/          JwtUtil, JwtAuthenticationFilter, UsuarioPrincipal (UserDetails),
│                      UsuarioDetailsService, CodigosInvitacion, entry point 401 y handler 403
└── service/           AuthService, UsuarioService, ProveedorService, ProductoService
src/main/resources
├── application.yml
└── db/migration/      V1 usuarios · V2 proveedores y productos · V3 username e invitaciones
```

## Puesta en marcha (local)

Requisitos: JDK 21 y Docker (o un PostgreSQL 16 propio). No hace falta instalar Maven: se usa `./mvnw`.

```bash
cd backend-spring
# PostgreSQL del docker-compose.yml de la raíz (solo escucha en 127.0.0.1:5432)
(cd .. && docker compose up -d postgres)

cp .env.example .env
# Edita .env y rellena como mínimo:
#   DB_PASSWORD     → el mismo valor que POSTGRES_PASSWORD en el .env de la raíz
#   JWT_SECRET      → openssl rand -base64 64
#   ADMIN_PASSWORD  → contraseña del primer administrador (mín. 8 caracteres)

./mvnw spring-boot:run
```

- API: http://localhost:8081
- Swagger UI: http://localhost:8081/swagger-ui.html (botón *Authorize* → pega el `accessToken`)
- Health: http://localhost:8081/actuator/health

El `.env` se carga automáticamente al arrancar desde esta carpeta
(`spring.config.import: optional:file:.env[.properties]`). En Docker o en el VPS, define las
mismas variables en el entorno del contenedor. Sin `JWT_SECRET` la aplicación **no arranca**
e indica qué variable falta.

### Variables de entorno

| Variable | Por defecto | Descripción |
| --- | --- | --- |
| `DB_URL` / `DB_USERNAME` / `DB_PASSWORD` | `jdbc:postgresql://localhost:5432/sgp` / `sgp` / `sgp` | Conexión a PostgreSQL |
| `JWT_SECRET` | — (obligatoria) | Clave HMAC en Base64, mínimo 256 bits |
| `JWT_EXPIRATION` | `1h` | Validez del token (`30m`, `8h`…) |
| `JWT_ISSUER` | `unisen-sgp` | Claim `iss` firmado y exigido |
| `BCRYPT_STRENGTH` | `12` | Coste de BCrypt |
| `CORS_ALLOWED_ORIGINS` | `https://rrtf.duckdns.org,http://localhost:5173` | Orígenes del frontend, separados por comas |
| `ADMIN_USERNAME` | `admin` | Username del primer administrador (con él inicia sesión) |
| `ADMIN_EMAIL` / `ADMIN_PASSWORD` / `ADMIN_NOMBRE` | vacío | Alta del primer administrador al arrancar (idempotente: no hace nada si su username o su correo ya existen) |
| `SWAGGER_ENABLED` | `true` | Pon `false` en producción si no quieres exponer la documentación |
| `SERVER_PORT` | `8081` | Puerto HTTP (Nginx reenvía `/api/` a `backend:8081`) |

## API

| Método | Ruta | Auth | Respuesta |
| --- | --- | --- | --- |
| POST | `/api/auth/login` | Pública | `{username, password}` → `200` `{accessToken, tokenType, expiresIn, usuario}` |
| POST | `/api/auth/registro` | Pública (requiere invitación) | `201` `{id, username, email, nombre, rol}` · `400` · `409` username/correo en uso |
| POST | `/api/auth/invitaciones` | Bearer **ADMIN** | `201` `{codigo, fechaExpiracion}` · `400` · `403` |
| GET | `/api/auth/me` | Bearer | `200` `{id, username, email, nombre, rol}` |
| GET | `/api/v1/proveedores` | Bearer | `200` página de proveedores activos |
| GET | `/api/v1/proveedores/{id}` | Bearer | `200` proveedor · `404` |
| POST | `/api/v1/proveedores` | Bearer **ADMIN** | `201` + `Location` · `400` · `409` NIT duplicado |
| PUT | `/api/v1/proveedores/{id}` | Bearer **ADMIN** | `200` · `400` · `404` · `409` |
| DELETE | `/api/v1/proveedores/{id}` | Bearer **ADMIN** | `204` baja lógica · `409` si tiene productos activos |
| GET | `/api/v1/productos` | Bearer | `200` página de productos activos con su proveedor |
| GET | `/api/v1/productos/{id}` | Bearer | `200` producto · `404` |
| POST | `/api/v1/productos` | Bearer **ADMIN** | `201` · `400` (incluye proveedor inexistente) · `409` SKU duplicado |
| PUT | `/api/v1/productos/{id}` | Bearer **ADMIN** | `200` · `400` · `404` · `409` |
| DELETE | `/api/v1/productos/{id}` | Bearer **ADMIN** | `204` baja lógica |
| GET | `/actuator/health` | Pública | `200` `{status: "UP"}` |
| * | cualquier otra | Bearer | `401` sin token válido |

**Paginación:** `?page=0&size=10&sort=campo,asc` (tamaño máximo 100; admite campos anidados como
`sort=proveedor.razonSocial`). Respuesta:
`{"content": [...], "page": {"size": 10, "number": 0, "totalElements": 13, "totalPages": 2}}`.

**Catálogos:**
- *Borrado lógico:* `DELETE` ejecuta `UPDATE … SET activo = false` (`@SQLDelete`) y
  `@SQLRestriction("activo = true")` oculta los inactivos en todas las consultas.
  (`@SQLRestriction` sustituye a `@Where`, deprecado desde Hibernate 6.3).
- *Unicidad:* NIT y SKU son únicos **incluidos los dados de baja**; reutilizar uno exige
  reactivar el registro original (funcionalidad pendiente).
- *Integridad:* un proveedor con productos activos no se puede dar de baja (`409`), y un
  producto solo puede asignarse a un proveedor activo (`400` en `errors.proveedorId`).
- *Normalización:* el NIT pierde puntos y espacios (`900.123.456-7` → `900123456-7`), el
  correo pasa a minúsculas y el SKU a mayúsculas. Las mismas reglas están en los esquemas Zod
  del frontend.

```bash
curl -X POST http://localhost:8081/api/auth/login \
  -H 'Content-Type: application/json' \
  -d '{"username":"admin","password":"<ADMIN_PASSWORD>"}'
```

Los errores siguen RFC 9457 (`application/problem+json`):

```json
{"type":"about:blank","title":"Credenciales inválidas","status":401,
 "detail":"Usuario o contraseña incorrectos.","instance":"/api/auth/login"}
```

| Código | Cuándo |
| --- | --- |
| `400` | JSON mal formado, validación fallida, orden inválido o código de invitación inexistente, usado o caducado (`errors`: campo → mensaje) |
| `401` | Credenciales incorrectas; token ausente, manipulado o expirado; usuario eliminado o desactivado |
| `403` | Login con contraseña correcta de una cuenta deshabilitada; rol insuficiente |
| `404` | Recurso inexistente o dado de baja |
| `409` | NIT, SKU, username o correo duplicado (`errors`: campo → mensaje) o baja de un proveedor con productos activos |

## Usuarios e invitaciones (Hito 4)

El alta es **cerrada**: solo se puede crear una cuenta con un código de invitación que genera
un ADMIN.

```bash
# 1. El ADMIN genera un código (cuerpo opcional; por defecto caduca en 72 h, máximo 720 h)
curl -X POST http://localhost:8081/api/auth/invitaciones \
  -H "Authorization: Bearer <token de ADMIN>" -H 'Content-Type: application/json' \
  -d '{"horasValidez": 48}'
# → 201 {"codigo":"7KQ2-M9XA-4HPR-T3VW","fechaExpiracion":"2026-10-09T10:00:00Z"}

# 2. La persona invitada se registra (nombre es opcional; por defecto, el username)
curl -X POST http://localhost:8081/api/auth/registro -H 'Content-Type: application/json' \
  -d '{"username":"ana.compras","email":"ana@unisen.com","password":"UnaClaveSegura2026",
       "codigoInvitacion":"7KQ2-M9XA-4HPR-T3VW","nombre":"Ana Compras"}'
# → 201 {"id":5,"username":"ana.compras","email":"ana@unisen.com","nombre":"Ana Compras","rol":"USUARIO"}
```

- **Códigos:** 16 caracteres Base32 de Crockford generados con `SecureRandom` (80 bits), en
  grupos `XXXX-XXXX-XXXX-XXXX`. Se aceptan en minúsculas, sin guiones o con espacios, y se
  corrigen O→0 e I/L→1. Son de un solo uso y quedan auditados: quién los creó, quién los usó
  y cuándo (`usuario_creador_id`, `usado_por_id`, `fecha_uso`).
- **Registro en una sola transacción:** se busca el código bloqueando su fila
  (`SELECT … FOR NO KEY UPDATE`), se valida `usado = false` y `fecha_expiracion > ahora`, se
  crea el usuario (rol `USUARIO`, contraseña con BCrypt) y se marca el código como usado. Si
  algo falla (username o correo repetido, contraseña inválida…) no se crea nada y el código
  sigue disponible. Dos registros simultáneos con el mismo código: uno gana y el otro recibe
  "ya fue utilizado".
- **Errores:** código inexistente, usado o caducado → `400` en `errors.codigoInvitacion`
  (mensajes distintos para cada caso). Username o correo en uso → `409` en
  `errors.username` / `errors.email`; si dos altas simultáneas chocan, la restricción `UNIQUE`
  de la BD produce la misma respuesta.
- **Sin enumeración:** el código se valida antes que el username y el correo, así que sin una
  invitación válida no se puede averiguar qué usuarios existen.
- **Username:** de 3 a 50 caracteres `a-z 0-9 . _ -`, empieza y termina con letra o número; se
  guarda en minúsculas y el login no distingue mayúsculas.

**Migración V3 sobre datos existentes:** los usuarios previos reciben como username la parte
local de su correo en minúsculas (`+` → `_`, máximo 40 caracteres) y, si dos coinciden, el de
id mayor recibe el sufijo `_<id>` (`admin@unisen.com` → `admin`, `admin@otra.com` → `admin_3`).
Los JWT emitidos antes llevaban el correo como `sub` y dejan de ser válidos (`401`): hay que
volver a iniciar sesión.

## Decisiones de seguridad

- **Stateless:** CSRF desactivado (no hay cookies de sesión), `SessionCreationPolicy.STATELESS`;
  `formLogin`, `httpBasic` y `logout` deshabilitados.
- **JWT (HS256):** clave obligatoria de ≥256 bits validada al arrancar. Se exigen firma,
  `iss` y `exp`; se rechazan tokens sin firmar (`alg: none`). Tolerancia de reloj: 30 s.
- **El filtro consulta la BD en cada petición:** desactivar o borrar un usuario invalida sus
  tokens al instante, y la autorización nunca se fía del rol escrito en el token.
- **Anti-enumeración:** un usuario inexistente y una contraseña incorrecta dan la misma
  respuesta (y Spring iguala los tiempos). El estado "cuenta deshabilitada" solo se revela
  **después** de validar la contraseña (comprobaciones de cuenta movidas a post-autenticación).
- **BCrypt:** coste configurable. Las contraseñas de más de 72 bytes, el límite de BCrypt,
  se rechazan al darlas de alta y en el login devuelven `401`, nunca un error interno.
- **Sin fugas:** `toString()` de `LoginRequest`, `RegistroRequestDTO`, `CodigoInvitacion`,
  `JwtProperties` y del admin enmascaran contraseñas y códigos. El hash se borra del `UserDetails` tras autenticar. Los `500` no exponen detalles.
- **CORS** restringido a `CORS_ALLOWED_ORIGINS` (`https://rrtf.duckdns.org` y Vite en local), sin
  credenciales (el token viaja en un header). En producción el frontend llama a `/api` en el
  mismo origen a través de Nginx, por lo que CORS solo interviene si otro origen llama a la API.
- **Esquema gestionado por Flyway:** `ddl-auto: validate` hace que Hibernate solo verifique el
  esquema. `open-in-view: false`.
- **Detrás de proxy (DuckDNS/HTTPS):** `server.forward-headers-strategy: framework`.

## Tests

```bash
./mvnw test        # 84 tests: H2 en modo PostgreSQL, sin dependencias externas

# Contra PostgreSQL real (crea antes la BD sgp_test):
SPRING_DATASOURCE_URL=jdbc:postgresql://localhost:5432/sgp_test \
SPRING_DATASOURCE_USERNAME=sgp SPRING_DATASOURCE_PASSWORD=sgp ./mvnw test
```

Cubren `JwtUtil` (expiración, manipulación, otra clave, otro emisor, `alg: none`, claves
débiles) y el flujo HTTP completo: login por username, validación, cuentas deshabilitadas,
tokens revocados o antiguos, rutas protegidas, CORS, Swagger, health, el alta del
administrador, la migración V3 sobre datos existentes y el ciclo de invitaciones (permisos,
caducidad, reutilización, duplicados sin consumir el código y dos canjes simultáneos).

El compilador corre con `-Xlint:all -Werror`: cualquier warning, incluido el uso de APIs
deprecadas, rompe el build.

## Docker

`Dockerfile` multietapa: Maven + JDK 21 compila; la imagen final es `eclipse-temurin:21-jre-alpine`
con usuario sin privilegios, capas de Spring Boot extraídas (las dependencias se cachean entre
versiones) y `HEALTHCHECK` contra `/actuator/health/readiness`. Lo orquesta el
`docker-compose.yml` de la raíz como servicio `backend` (puerto interno 8081).

```bash
docker build -t unisen/sgp-backend ./backend-spring     # desde la raíz del repo
```

Los tests no se ejecutan dentro del build: van en `./mvnw test` (CI).

## Fuera de alcance de este hito

Órdenes de compra; reactivación de registros dados de baja; búsqueda en catálogos; refresh
tokens; listado y revocación de invitaciones; gestión de usuarios vía API. El frontend aún no
está adaptado al Hito 4 (ver README de la raíz).
