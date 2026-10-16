# Unisen SGP: backend Spring Boot

API REST del Sistema de Gestión de Órdenes de Compra: core Spring Boot y seguridad JWT
(Hito 1), catálogos de proveedores y productos (Hito 3), login por username con códigos de
invitación (Hito 4), solicitudes internas, SaaS multi-empresa, identidad y onboarding
(registro de trabajadores con el código de su empresa y aprobación por el administrador) y **gestión
centralizada de empresas por el SUPER_ADMIN: código empresarial estable, alta atómica con su
administrador principal, ciclo de vida y supervisión** (ver
[Gestión centralizada de empresas](#gestión-centralizada-de-empresas)).

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
├── controller/        AuthController (login, registro, invitaciones, me), EmpresaController,
│                      TrabajadorController, ProveedorController, ProductoController,
│                      SolicitudController
├── exception/         GlobalExceptionHandler (RFC 9457), RecursoNoEncontrado, Conflicto,
│                      CampoInvalido, InvitacionInvalida, DatoDuplicado
├── model/entity/      Usuario, EstadoUsuario, Empresa, CodigoInvitacion, Rol, Proveedor, Producto,
│                      EntidadAuditable, EntidadDeEmpresa,
│                      Solicitud + DetalleSolicitud (maestro-detalle), EstadoSolicitud,
│                      ProductoReferencia (vista de solo lectura de productos)
├── model/dto/         Records de request/response (LoginRequest, RegistroRequestDTO,
│                      Invitacion…DTO, Empresa…DTO, TrabajadorResponseDTO,
│                      CambioEstadoUsuarioDTO, Proveedor…DTO, Producto…DTO, Solicitud…DTO,
│                      DetalleSolicitud…DTO, CambioEstadoSolicitudDTO), Patrones
├── repository/        UsuarioRepository, EmpresaRepository, CodigoInvitacionRepository, ProveedorRepository,
│                      ProductoRepository, SolicitudRepository, DetalleSolicitudRepository,
│                      ProductoReferenciaRepository, ProveedorReferenciaRepository,
│                      OrdenCompraRepository, RecepcionRepository, ExistenciaRepository,
│                      MovimientoInventarioRepository, AnaliticaRepository (consultas agregadas)
├── security/          JwtUtil, JwtAuthenticationFilter, UsuarioPrincipal (UserDetails),
│                      UsuarioDetailsService, UsuarioActual, CodigosInvitacion, Permisos,
│                      CuentaNoAutorizadaException, entry point 401/403 y handler 403
├── tenant/            TenantContextHolder, TenantFilter, TenantIdentifierResolver
└── service/           AuthService, UsuarioService, EmpresaService, ProveedorService,
                       ProductoService, SolicitudService, OrdenCompraService, RecepcionService,
                       InventarioService, AnaliticaService, CalendarioNegocio
src/main/resources
├── application.yml
└── db/migration/      V1 usuarios · V2 proveedores y productos · V3 username e invitaciones ·
                       V4 solicitudes internas y rol GERENTE · V5 multi-empresa y roles SaaS ·
                       V6 estado de usuario y código de empresa · V7 código empresarial ·
                       V8 roles ADMIN_EMPRESA y TRABAJADOR · V9 nombres y apellidos ·
                       V10 revocación de invitaciones · V11 compras e inventario ·
                       V12 código de barras
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
| `ADMIN_USERNAME` | `admin` | Username del SUPER_ADMIN inicial (con él inicia sesión) |
| `ADMIN_EMAIL` / `ADMIN_PASSWORD` / `ADMIN_NOMBRE` | vacío | Alta del SUPER_ADMIN inicial al arrancar, sin empresa (idempotente: no hace nada si su username o su correo ya existen) |
| `APP_ZONA_HORARIA` | `America/Bogota` | Zona en la que la analítica cuenta los días (desfases de horas completas; se comprueba al arrancar) |
| `SWAGGER_ENABLED` | `true` | Pon `false` en producción si no quieres exponer la documentación |
| `SERVER_PORT` | `8081` | Puerto HTTP (Nginx reenvía `/api/` a `backend:8081`) |

## API

| Método | Ruta | Auth | Respuesta |
| --- | --- | --- | --- |
| POST | `/api/auth/login` | Pública | `{username, password}` → `200` `{accessToken, tokenType, expiresIn, usuario}` |
| POST | `/api/auth/registro` | Pública (requiere `codigoEmpresa` o `codigoInvitacion`) | `201` `{id, username, email, nombre, rol, empresaId, estado}`: PENDIENTE con código de empresa, ACTIVO con invitación · `400` · `409` username/correo en uso |
| POST | `/api/auth/invitaciones` | Bearer **ADMIN_EMPRESA / SUPER_ADMIN** | `201` `{codigo, fechaExpiracion}` para la empresa actual · `400` · `403` |
| GET · DELETE | `/api/auth/invitaciones` · `/{id}` | Bearer **ADMIN_EMPRESA / SUPER_ADMIN con `X-Tenant-ID: <id>`** | `200` página de invitaciones de la empresa con su `estado` (sin el código completo) · revocar una vigente → `200` · `404` de otra empresa · `409` usada, revocada o caducada |
| GET | `/api/auth/me` | Bearer | `200` `{id, username, email, nombre, nombres, apellidos, datosPersonalesCompletos, rol, empresaId, empresaNombre, empresaCodigo, estado}`, leído de la BD |
| PATCH | `/api/auth/me` | Bearer | `{nombres, apellidos}` → `200` perfil actualizado (solo la propia cuenta) · `400` |
| POST | `/api/v1/empresas` | Bearer **SUPER_ADMIN** | `201` empresa + `codigo` (indicado o propuesto) + `codigoEmpresa` + `administrador` (todo o nada) · `400` · `403` · `409` NIT, código o administrador duplicado |
| GET | `/api/v1/empresas` · `/{id}` | Bearer **SUPER_ADMIN** | `200` página / ficha con `codigo`, `codigoEmpresa` y el administrador principal en `administrador` · `403` · `404` |
| GET | `/api/v1/empresas/codigo-sugerido?nombre=` | Bearer **SUPER_ADMIN** | `200` `{codigo, disponible}` (NASCAR → NASC01, o el siguiente libre) · `400` sin nombre |
| GET | `/api/v1/empresas/codigo-disponible?codigo=` | Bearer **SUPER_ADMIN** | `200` `{codigo, disponible}` · `400` formato no válido |
| GET | `/api/v1/empresas/{id}/usuarios` | Bearer **SUPER_ADMIN** | `200` página de usuarios de esa empresa (administradores primero) con su estado · `404` |
| PATCH | `/api/v1/empresas/{id}/estado` | Bearer **SUPER_ADMIN** | `{activa}` → `200` · `400` · `404` · `409` ya estaba así |
| PATCH | `/api/v1/empresas/{id}/administradores/{usuarioId}/estado` | Bearer **SUPER_ADMIN** | `{estado: ACTIVO \| INACTIVO}` → `200` · `404` si no es administrador de esa empresa · `409` |
| GET | `/api/v1/empresas/actual` | Bearer **ADMIN_EMPRESA / SUPER_ADMIN con `X-Tenant-ID`** | `200` empresa en la que se trabaja, con su `codigo` y su `codigoEmpresa` · `400` sin empresa |
| GET | `/api/v1/trabajadores?estado=` | Bearer **ADMIN_EMPRESA / SUPER_ADMIN con `X-Tenant-ID`** | `200` página de trabajadores (rol TRABAJADOR) de esa empresa · `400` sin empresa · `403` |
| PATCH | `/api/v1/trabajadores/{id}/estado` | Bearer **ADMIN_EMPRESA / SUPER_ADMIN con `X-Tenant-ID`** | `{estado}` → `200` · `400` · `403` · `404` si no es trabajador de esa empresa · `409` transición no permitida |
| GET | `/api/v1/proveedores` | Bearer | `200` página de proveedores activos |
| GET | `/api/v1/proveedores/{id}` | Bearer | `200` proveedor · `404` |
| POST | `/api/v1/proveedores` | Bearer **ADMIN_EMPRESA/SUPER_ADMIN** | `201` + `Location` · `400` · `409` NIT duplicado |
| PUT | `/api/v1/proveedores/{id}` | Bearer **ADMIN_EMPRESA/SUPER_ADMIN** | `200` · `400` · `404` · `409` |
| DELETE | `/api/v1/proveedores/{id}` | Bearer **ADMIN_EMPRESA/SUPER_ADMIN** | `204` baja lógica · `409` si tiene productos activos u órdenes de compra abiertas |
| GET | `/api/v1/productos` | Bearer | `200` página de productos activos con su proveedor |
| GET | `/api/v1/productos/{id}` | Bearer | `200` producto · `404` |
| GET | `/api/v1/productos/buscar?codigo=` | Bearer (con empresa en el contexto) | `200` `{producto, existencias, estadoStock, coincidePor}` (código de barras exacto y, si no, SKU) · `400` sin empresa o código vacío · `404` · `409` solo coincide uno dado de baja |
| POST | `/api/v1/productos` | Bearer **ADMIN_EMPRESA/SUPER_ADMIN** | `201` (con su fila de existencias a 0; `codigoBarras` y `stockMinimo` opcionales) · `400` (incluye proveedor inexistente) · `409` SKU o código de barras duplicado |
| PUT | `/api/v1/productos/{id}` | Bearer **ADMIN_EMPRESA/SUPER_ADMIN** | `200` · `400` · `404` · `409` |
| DELETE | `/api/v1/productos/{id}` | Bearer **ADMIN_EMPRESA/SUPER_ADMIN** | `204` baja lógica · `409` si está en solicitudes pendientes o aprobadas, en órdenes abiertas o le quedan existencias |
| GET | `/api/v1/solicitudes?estado=` | Bearer | `200` página: TRABAJADOR solo las suyas, ADMIN_EMPRESA/SUPER_ADMIN todas (de la empresa) |
| GET | `/api/v1/solicitudes/{id}` | Bearer | `200` con sus líneas · `404` si no existe o es de otro TRABAJADOR |
| POST | `/api/v1/solicitudes` | Bearer | `201` PENDIENTE con el usuario autenticado · `400` |
| PATCH | `/api/v1/solicitudes/{id}/estado` | Bearer **ADMIN_EMPRESA/SUPER_ADMIN** | `200` · `400` · `403` · `404` · `409` ya revisada |
| GET | `/api/v1/analitica/global?desde=&hasta=` · `/global/empresas` | Bearer **SUPER_ADMIN con `X-Tenant-ID: global`** | `200` indicadores de la plataforma / página de órdenes por empresa · `400` período no válido o petición dentro de una empresa · `403` |
| GET | `/api/v1/analitica/empresa?desde=&hasta=` | Bearer **ADMIN_EMPRESA / SUPER_ADMIN con `X-Tenant-ID: <id>`** | `200` inventario, compras, recepciones y estados de la empresa · `400` · `403` |
| GET | `/api/v1/analitica/personal` | Bearer **ADMIN_EMPRESA / TRABAJADOR** | `200` disponibilidad de productos y solicitudes propias (sin ventas) |
| GET | `/api/v1/ordenes-compra?estado=&proveedorId=&solicitudId=` · `/{id}` | Bearer **ADMIN_EMPRESA / SUPER_ADMIN con `X-Tenant-ID`** | `200` página / orden con líneas, total, recibido y pendiente · `404` |
| POST | `/api/v1/ordenes-compra` | Bearer **ADMIN_EMPRESA** | `201` BORRADOR numerado · `400` proveedor o productos inválidos, de otro proveedor o repetidos |
| PUT | `/api/v1/ordenes-compra/{id}` | Bearer **ADMIN_EMPRESA** | `200` · `409` si ya no es borrador |
| POST | `/api/v1/ordenes-compra/{id}/emitir` · `/cancelar` | Bearer **ADMIN_EMPRESA** | `200` · `409` transición no permitida o catálogo cambiado |
| POST | `/api/v1/ordenes-compra/desde-solicitud/{solicitudId}` | Bearer **ADMIN_EMPRESA** | `201` un borrador por proveedor · `404` · `409` no aprobada, ya tiene órdenes o productos dados de baja |
| GET · POST | `/api/v1/ordenes-compra/{id}/recepciones` | GET gestión · POST **ADMIN_EMPRESA** | `201` registrada · `200` misma clave y mismos datos · `400` línea ajena, repetida o mayor que lo pendiente · `409` orden sin recepciones posibles o clave con otros datos |
| GET | `/api/v1/recepciones` | Bearer **ADMIN_EMPRESA / SUPER_ADMIN con `X-Tenant-ID`** | `200` página |
| GET | `/api/v1/inventario?estado=&q=` | Bearer (de la empresa) | `200` existencias de los productos activos (sin costos) |
| GET | `/api/v1/inventario/movimientos?productoId=` | Bearer **ADMIN_EMPRESA / SUPER_ADMIN con `X-Tenant-ID`** | `200` kárdex |
| POST | `/api/v1/inventario/ajustes` | Bearer **ADMIN_EMPRESA** | `201` · `200` misma clave · `400` · `409` existencias insuficientes |
| GET | `/actuator/health` | Pública | `200` `{status: "UP"}` |
| * | cualquier otra | Bearer | `401` sin token válido · `403` con token válido de una cuenta no autorizada |

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
| `400` | JSON mal formado, validación fallida, orden inválido, código de invitación inexistente, usado o caducado, o código de empresa inexistente o de una empresa desactivada (`errors`: campo → mensaje) |
| `401` | No hay identidad: credenciales incorrectas; token ausente, manipulado o expirado; usuario eliminado; token de otra empresa |
| `403` | Identidad verificada pero sin autorización: cuenta PENDIENTE, RECHAZADA, INACTIVA o de una empresa desactivada (en el login con la contraseña correcta y en cualquier ruta protegida con un token válido; propiedad `motivo`); rol insuficiente |
| `404` | Recurso inexistente o dado de baja |
| `409` | NIT, SKU, código empresarial, username o correo duplicado (`errors`: campo → mensaje), baja de un proveedor con productos activos o cambio de estado a uno que ya tenía |

## Usuarios e invitaciones (Hito 4)

El alta es **cerrada**: solo se puede crear una cuenta con un código de invitación que genera
un gestor (ADMIN_EMPRESA de la empresa, o SUPER_ADMIN con `X-Tenant-ID`) para su empresa.

```bash
# 1. Un ADMIN_EMPRESA genera un código para su empresa (cuerpo opcional; por defecto caduca en 72 h, máximo 720 h)
curl -X POST http://localhost:8081/api/auth/invitaciones \
  -H "Authorization: Bearer <token de ADMIN_EMPRESA>" -H 'Content-Type: application/json' \
  -d '{"horasValidez": 48}'
# → 201 {"codigo":"7KQ2-M9XA-4HPR-T3VW","fechaExpiracion":"2026-10-09T10:00:00Z"}

# 2. La persona invitada se registra con sus nombres y apellidos (obligatorios desde V9)
curl -X POST http://localhost:8081/api/auth/registro -H 'Content-Type: application/json' \
  -d '{"username":"ana.compras","email":"ana@unisen.com","password":"UnaClaveSegura2026",
       "codigoInvitacion":"7KQ2-M9XA-4HPR-T3VW","nombres":"Ana","apellidos":"Compras"}'
# → 201 {"id":5,"username":"ana.compras","email":"ana@unisen.com","nombre":"Ana Compras","rol":"TRABAJADOR"}
```

- **Códigos:** 16 caracteres Base32 de Crockford generados con `SecureRandom` (80 bits), en
  grupos `XXXX-XXXX-XXXX-XXXX`. Se aceptan en minúsculas, sin guiones o con espacios, y se
  corrigen O→0 e I/L→1. Son de un solo uso y quedan auditados: quién los creó, quién los usó
  y cuándo (`usuario_creador_id`, `usado_por_id`, `fecha_uso`).
- **Registro en una sola transacción:** se busca el código bloqueando su fila
  (`SELECT … FOR NO KEY UPDATE`), se valida `usado = false` y `fecha_expiracion > ahora`, se
  crea el usuario (rol `TRABAJADOR`, contraseña con BCrypt) y se marca el código como usado. Si
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
- **La BD es la fuente de verdad de la autorización:** el filtro JWT carga el usuario en cada
  petición (una consulta, ya existente). Si su estado deja de ser ACTIVO o su empresa se
  desactiva, la siguiente petición recibe `403` aunque el token siga siendo criptográficamente
  válido; no se revoca el token, cambia la autorización. La autorización tampoco se fía del rol
  escrito en el token.
- **Anti-enumeración:** un usuario inexistente y una contraseña incorrecta dan la misma
  respuesta (y Spring iguala los tiempos). El estado de la cuenta (pendiente, rechazada,
  inactiva) solo se revela **después** de validar la contraseña (comprobaciones de cuenta en
  post-autenticación). En el registro, el código se valida antes que el username y el correo.
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
./mvnw test        # 220 tests: H2 en modo PostgreSQL, sin dependencias externas

# Contra PostgreSQL real (crea antes una BD vacía; Flyway aplica V1…V12 y quedan aplicadas):
SPRING_DATASOURCE_URL=jdbc:postgresql://localhost:5432/sgp_test \
SPRING_DATASOURCE_USERNAME=sgp SPRING_DATASOURCE_PASSWORD=sgp ./mvnw test
```

Cubren `JwtUtil` (expiración, manipulación, otra clave, otro emisor, `alg: none`, claves
débiles) y el flujo HTTP completo: login por username, validación, cuentas deshabilitadas,
tokens antiguos, rutas protegidas, CORS, Swagger, health, el alta del administrador, las
migraciones V3, V5, V6, V7, V8–V10 y V11–V12 sobre datos existentes, la analítica, las órdenes de
compra, las recepciones y el inventario (incluidas carreras simultáneas e idempotencia), el
código de barras, el ciclo de invitaciones (permisos, caducidad,
reutilización, duplicados sin consumir el código y dos canjes simultáneos), el aislamiento
entre empresas, el onboarding del Hito 2 (ver [Identidad y onboarding](#identidad-y-onboarding))
y la gestión centralizada de empresas (ver
[Gestión centralizada de empresas](#gestión-centralizada-de-empresas)).

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

## Solicitudes internas de compra

La solicitud (lo que pide un trabajador) es la raíz de un agregado maestro-detalle: cabecera
(`solicitudes`: solicitante, fecha, estado, justificación) y líneas (`detalles_solicitud`:
producto y cantidad). `@OneToMany(cascade = ALL, orphanRemoval = true)` guarda y borra las líneas
con la cabecera, y el alta es una sola transacción: si una línea falla, no se guarda nada.

```bash
curl -X POST http://localhost:8081/api/v1/solicitudes -H "Authorization: Bearer <token>" \
  -H 'Content-Type: application/json' \
  -d '{"justificacion":"Reposición de tornillería para la línea 2.",
       "detalles":[{"productoId":1,"cantidad":10},{"productoId":2,"cantidad":4}]}'
# → 201 {"id":5,"estado":"PENDIENTE","solicitante":{"username":"ana"},"detalles":[…],"totalEstimado":13700}

curl -X PATCH http://localhost:8081/api/v1/solicitudes/5/estado -H "Authorization: Bearer <token de gestor>" \
  -H 'Content-Type: application/json' -d '{"estado":"RECHAZADA","comentario":"Hay stock en bodega."}'
```

- **El cliente no elige ni solicitante ni estado:** el servicio toma el usuario del
  `SecurityContext` y fija `PENDIENTE`; esos campos del JSON se ignoran.
- **Seguridad por fila:** `GET` filtra por `usuario_id` cuando el rol es TRABAJADOR. Una solicitud
  ajena responde `404` (no `403`) para no revelar que existe.
- **Revisión:** solo `PENDIENTE → APROBADA | RECHAZADA` (`409` si ya estaba revisada), con
  revisor, fecha y comentario; el comentario es obligatorio al rechazar. La fila se bloquea
  (`SELECT … FOR NO KEY UPDATE`): si dos gestores revisan a la vez, el segundo recibe `409`.
- **Validación:** justificación de 10 a 1000 caracteres; de 1 a 50 líneas; cantidades enteras de
  1 a 999.999 (un `2.5` se rechaza, no se trunca); cada producto una sola vez y activo. Los
  errores de línea llegan como `errors["detalles[1].productoId"]`.
- **Integridad:** un producto incluido en solicitudes pendientes o aprobadas no se puede dar de
  baja (`409`). Si solo está en rechazadas, sí; la solicitud lo sigue mostrando
  (`producto.activo: false`) gracias a `ProductoReferencia`, que lee la tabla sin el filtro de
  borrado lógico.
- **Importes:** `subtotalEstimado` y `totalEstimado` usan el precio actual del catálogo; son
  orientativos.
- **Rendimiento:** solicitante y revisor llegan en la consulta de la página; las líneas y sus
  productos, por lotes (`default_batch_fetch_size: 50`). Un test comprueba que el listado no
  hace N+1.

**Roles:** ver la sección *SaaS multi-empresa*. Aún no hay pantalla para asignar roles:
`UPDATE usuarios SET rol = 'ADMIN_EMPRESA' WHERE username = '…';`

## SaaS multi-empresa (tenants)

Base de datos y esquema compartidos: cada fila de negocio lleva `empresa_id` y Hibernate 6 filtra
por ella de forma nativa con `@TenantId`.

| Rol | Empresa | Puede |
| --- | --- | --- |
| `SUPER_ADMIN` | Ninguna | Operar la plataforma. Sin `X-Tenant-ID` ve los datos de todas las empresas (solo lectura); con `X-Tenant-ID: <id>` trabaja dentro de esa empresa (soporte) |
| `ADMIN_EMPRESA` | La suya | Trabajadores (aprobar, rechazar, desactivar), catálogos, invitaciones y revisión de solicitudes de su empresa |
| `TRABAJADOR` (trabajador) | La suya | Crear solicitudes y ver las suyas; consultar catálogos |

**Cómo se fija la empresa de cada petición:**

1. `JwtAuthenticationFilter` valida el token y carga el usuario de la BD. El JWT lleva el claim
   `empresaId` (ausente para SUPER_ADMIN); si no coincide con la empresa actual del usuario, el
   token se rechaza (`401`). Si la cuenta no está ACTIVA o su empresa está desactivada, `403`.
2. `TenantFilter` (justo después) guarda la empresa en `TenantContextHolder` (`ThreadLocal`):
   la del usuario para ADMIN_EMPRESA y TRABAJADOR (la cabecera `X-Tenant-ID` se ignora) y la de
   `X-Tenant-ID` para el SUPER_ADMIN, si la envía (`400` si no es un id de empresa existente).
   Login y registro nunca fijan empresa, aunque la petición lleve un token: se comportan como
   una petición anónima. El contexto se borra siempre al terminar la petición.
3. `TenantIdentifierResolver` entrega esa empresa a Hibernate al abrir cada sesión. Las
   entidades de `EntidadDeEmpresa` (proveedores, productos, solicitudes, códigos de invitación)
   reciben `empresa_id` al insertarse y todas sus consultas, también `findById`, añaden
   `empresa_id = ?`. Sin empresa en el contexto se usa el tenant raíz (`0`), que no filtra: login,
   registro y SUPER_ADMIN en modo global.

**Garantías:** los datos de otra empresa no existen para quien consulta (`404`); NIT y SKU son
únicos por empresa; un producto solo puede usar proveedores de su empresa y una solicitud solo
puede ser de un usuario de su empresa (FK compuestas en la BD); escribir datos de negocio sin
empresa responde `400` ("Empresa no seleccionada"), y el SUPER_ADMIN no crea solicitudes (`403`).
`Usuario` no lleva `@TenantId`: es la identidad global (username y correo únicos en toda la
plataforma) y se necesita antes de conocer la empresa.

**Invitaciones y registro:** un código de invitación pertenece a la empresa de quien lo genera;
quien lo canjea entra como TRABAJADOR ACTIVO de esa empresa (si sigue activa). Con el código
permanente de la empresa, entra como TRABAJADOR PENDIENTE. `Usuario` no lleva `@TenantId`, así que
la gestión de trabajadores filtra la empresa de forma explícita (`empresa_id` del tenant + rol).

**Migración V5 sobre una instalación existente:** crea "Empresa Base" (NIT provisional
`000000000-0`: actualízalo) y le asigna todos los usuarios y datos; el antiguo ADMIN pasa a
SUPER_ADMIN, sin empresa. Las empresas nuevas se aprovisionan con `POST /api/v1/empresas`
(ver [Identidad y onboarding](#identidad-y-onboarding)).

## Identidad y onboarding

Hito 2 del plan SGOC. Reutiliza la seguridad y la multi-empresa existentes (sin una segunda
estrategia de tenants): solo añade el estado de la cuenta, el código permanente de empresa y
tres operaciones.

```
SUPER_ADMIN ── POST /api/v1/empresas ──→ Empresa + codigoEmpresa + ADMIN_EMPRESA fundador (ACTIVO)
Trabajador ─── POST /api/auth/registro {codigoEmpresa} ──→ TRABAJADOR PENDIENTE (sin acceso)
ADMIN_EMPRESA ────── PATCH /api/v1/trabajadores/{id}/estado ──→ ACTIVO · RECHAZADO · INACTIVO
```

**Roles:** desde V8, `ADMIN_EMPRESA` (antes `GERENTE`) y `TRABAJADOR` (antes `USUARIO`); ver
[Fase 1](#fase-1-identidad-normalizada-invitaciones-y-modo-global-explícito).

**Estados de la cuenta** (`usuarios.estado`, fuente de verdad; `activo` se conserva como reflejo
de `estado = 'ACTIVO'` y una restricción impide que diverjan):

| Desde | Hacia | Quién |
| --- | --- | --- |
| — | PENDIENTE | El trabajador, al registrarse con el código de su empresa |
| — | ACTIVO | Administrador inicial, invitación de un gestor, SUPER_ADMIN inicial |
| PENDIENTE | ACTIVO (aprobar) · RECHAZADO (rechazar) | ADMIN_EMPRESA de esa empresa, o SUPER_ADMIN con `X-Tenant-ID` |
| ACTIVO | INACTIVO (pierde la autorización) | Ídem |
| INACTIVO · RECHAZADO | ACTIVO (reactivar o corregir un rechazo) | Ídem |

Cualquier otra transición responde `409`, y pasar a PENDIENTE responde `400`. Solo se gestionan
trabajadores (rol TRABAJADOR) de la empresa de la petición: otra empresa, otro gestor o un id
inexistente responden igual (`404`). La fila se bloquea (`SELECT … FOR UPDATE`) para que dos
gestores no la cambien a la vez.

**JWT y autorización:** el token identifica al usuario; la BD decide si puede operar. Una cuenta
no ACTIVA no recibe token en el login (`403` con `motivo`, solo tras validar la contraseña) y,
si ya tenía uno, su siguiente petición protegida recibe `403`:

```json
{"title":"Cuenta no autorizada","status":403,"motivo":"INACTIVO",
 "detail":"La cuenta de usuario está deshabilitada.","instance":"/api/v1/solicitudes"}
```

`motivo`: `PENDIENTE`, `RECHAZADO`, `INACTIVO` o `EMPRESA_INACTIVA`. Las rutas públicas (login,
registro) siguen funcionando aunque la petición lleve ese token. Al reactivar la cuenta, el
mismo token vuelve a servir mientras no caduque.

```bash
# 1. SUPER_ADMIN: empresa y administrador inicial, en una sola transacción
curl -X POST http://localhost:8081/api/v1/empresas -H "Authorization: Bearer $SUPER" \
  -H 'Content-Type: application/json' -d '{"nombre":"Ferretería Andina","nit":"901234567-8",
  "administrador":{"nombres":"Laura","apellidos":"Andina Ruiz","username":"admin.andina",
  "email":"administracion@andina.com","password":"ClaveAdmin2026"}}'
# → 201 {"id":2,"codigo":"FERR01","codigoEmpresa":"4HPR-T3VW-7KQ2-M9XA","administrador":{"rol":"ADMIN_EMPRESA","estado":"ACTIVO",…},…}

# 2. Trabajador: se registra con el código de su empresa
curl -X POST http://localhost:8081/api/auth/registro -H 'Content-Type: application/json' \
  -d '{"nombres":"Pedro","apellidos":"Bodega","username":"pedro","email":"pedro@andina.com","password":"ClaveTrabajador2026","codigoEmpresa":"4HPR-T3VW-7KQ2-M9XA"}'
# → 201 {"rol":"TRABAJADOR","estado":"PENDIENTE","empresaId":2,…}; su login responde 403 (motivo PENDIENTE)

# 3. ADMIN_EMPRESA: revisa los pendientes de SU empresa y aprueba
curl "http://localhost:8081/api/v1/trabajadores?estado=PENDIENTE" -H "Authorization: Bearer $ADMIN_EMPRESA"
curl -X PATCH http://localhost:8081/api/v1/trabajadores/5/estado -H "Authorization: Bearer $ADMIN_EMPRESA" \
  -H 'Content-Type: application/json' -d '{"estado":"ACTIVO"}'
```

**Aprovisionamiento atómico:** la empresa se inserta (con `flush`) antes que el administrador; si el
NIT ya existe (`409 errors.nit`), si el username o el correo del administrador están en uso
(`409 errors['administrador.username']` / `errors['administrador.email']`) o si falla cualquier otro paso, la
transacción se deshace entera: nunca queda una empresa sin administrador ni un administrador huérfano.

**Código de empresa** (`empresas.codigo_empresa`, el *código de registro de trabajadores*; no
confundir con el código empresarial `empresas.codigo` de V7): secreto, permanente, único y con el
formato de las invitaciones (`XXXX-XXXX-XXXX-XXXX`, Base32 de Crockford, 80 bits, mismo generador
`CodigosInvitacion`). Al registrarse se acepta en minúsculas, sin guiones o con espacios. No es
una invitación: no caduca ni se consume. El administrador lo consulta en `GET /api/v1/empresas/actual`.
El registro exige exactamente uno de los dos códigos; el rol, el estado y la empresa nunca
vienen del cliente (se ignoran si se envían).

**`CodigoInvitacion` (auditoría):** sigue en uso y no se elimina. Lo usan `AuthService`
(generar y canjear), `AuthController` (`/invitaciones`, `/registro`), `CodigoInvitacionRepository`,
`InvitacionInvalidaException`, la migración V3 (y V5, `empresa_id`), 25 tests y el frontend
(`/registro` y `/admin/invitaciones`). Una invitación la genera un gestor para una persona
concreta, así que quien la canjea entra ACTIVO, como antes del Hito 2. Si en el futuro todo alta
debe pasar por la aprobación del administrador, la deprecación segura sería: (1) que el frontend
registre con el código de empresa; (2) dejar de generar invitaciones (`/invitaciones` → `410`);
(3) cuando no queden códigos vigentes, retirar el canje; (4) solo entonces, y con copia de
seguridad, una migración que archive y elimine `codigos_invitacion`.

**Migración V6 sobre una instalación existente:** no borra nada. Los usuarios activos pasan a
ACTIVO y los inactivos a INACTIVO (nadie pierde el acceso que tenía); la columna reservada
`codigo_invitacion_actual` pasa a llamarse `codigo_empresa` y cada empresa existente recibe un
código aleatorio.

## Gestión centralizada de empresas

El SUPER_ADMIN es la cuenta raíz de la plataforma: no pertenece a ninguna empresa y desde
**Empresas** (frontend) crea, consulta, supervisa y activa o desactiva las empresas y la cuenta
de sus administradores. Reutiliza la multi-empresa existente (`@TenantId` + `X-Tenant-ID`): no
hay una segunda estrategia de tenants.

```
SUPER_ADMIN ── POST /api/v1/empresas ──→ Empresa (codigo NASC01) + ADMIN_EMPRESA principal (ACTIVO), todo o nada
SUPER_ADMIN ── GET /api/v1/empresas[/{id}[/usuarios]] ──→ listado, ficha y usuarios con su estado
SUPER_ADMIN ── X-Tenant-ID: <id> ──→ supervisión: datos de esa empresa, con su propia identidad
```

**Dos códigos por empresa, con funciones distintas:**

| Columna | JSON | Qué es |
| --- | --- | --- |
| `codigo` (V7) | `codigo` | **Código empresarial**: identificador legible, único y estable (`NASC01`). Público: no es una contraseña ni concede ningún permiso, y **no sirve para registrarse** |
| `codigo_empresa` (V6) | `codigoEmpresa` | **Código de registro de trabajadores**: aleatorio (80 bits) y secreto; quien lo conoce se registra como PENDIENTE hasta que el administrador lo apruebe |

Se mantienen separados a propósito: si el código predecible sirviera para registrarse,
cualquiera podría crear cuentas pendientes en cualquier empresa (y averiguar qué empresas
existen) adivinando `NASC01`, `NASC02`…

**Código empresarial:** de 3 a 12 letras mayúsculas o dígitos, empezando por letra
(`Patrones.CODIGO_EMPRESARIAL`, restricción `ck_empresas_codigo`); se acepta en minúsculas y se
normaliza. Si el alta no lo indica, el servidor lo propone de forma determinista: prefijo de hasta
4 letras del nombre sin tildes ni signos y el primer número libre en la BD (`NASCAR` → `NASC01`;
`Nascimento` → `NASC02`; `3M Colombia` → `MCOL01`). `GET /codigo-sugerido` y
`GET /codigo-disponible` permiten al formulario proponerlo y comprobarlo antes de guardar, pero
la decisión final es del alta y de la restricción `UNIQUE`. No se edita (`updatable = false`).

**Alta atómica y concurrencia:** empresa y administrador se crean en una sola transacción
(`TransactionTemplate`). NIT, código, username o correo repetidos → `409` en `errors.nit`,
`errors.codigo`, `errors['administrador.username']` o `errors['administrador.email']`, y no queda nada: ni
empresa sin administrador ni administrador huérfano. Si dos altas simultáneas chocan por el
código, la `UNIQUE` de la BD salta: un código **propuesto** por el servidor se reintenta con el
siguiente libre en una transacción nueva (hasta 3 veces; tras una violación PostgreSQL no admite
más sentencias en la transacción en curso); un código **elegido** por el SUPER_ADMIN nunca se
cambia sin avisar: `409`. Rol, estado y empresa del administrador los fija el servidor (se
ignoran si el JSON los trae).

**Administrador principal:** el primer ADMIN_EMPRESA de la empresa (el de id más bajo, es decir, el
fundador creado con ella). Se calcula al consultar (una consulta por página de empresas, sin
N+1), sin una FK circular entre `empresas` y `usuarios`. Se muestra en `administrador` en el listado y la
ficha; se omite si la empresa no tiene administrador (p. ej. la "Empresa Base" de V5).

**Contraseña inicial:** la define el SUPER_ADMIN en el alta (el frontend ofrece un generador de
contraseñas aleatorias) y se guarda solo como hash BCrypt. Hoy no existe cambio ni recuperación
de contraseña: hay que entregarla por un canal seguro (ver *Fuera de alcance*).

**Ciclo de vida** (siempre SUPER_ADMIN y siempre registrado en el log con su username):

- `PATCH /{id}/estado {activa}`: desactivar una empresa bloquea a **todos** sus usuarios desde su
  siguiente petición (`403`, motivo `EMPRESA_INACTIVA`, también en el login), aunque su JWT siga
  vigente; reactivarla les devuelve el acceso. No se borra ningún dato.
- `PATCH /{id}/administradores/{usuarioId}/estado {estado}`: desactivar o reactivar la cuenta de un
  ADMIN_EMPRESA de esa empresa (mismas transiciones que los trabajadores; fila bloqueada). Otra empresa,
  otro rol o un id inexistente → `404`.

**Supervisión:** el SUPER_ADMIN envía `X-Tenant-ID` y consulta los datos de esa empresa con los
endpoints de siempre (catálogos, solicitudes, `/empresas/actual`); `TenantFilter` registra cada
petición (`Supervisión: <usuario> <método> <ruta> en la empresa <id>`). Sigue siendo SUPER_ADMIN
(`/api/auth/me` no cambia y conserva sus permisos globales) y no suplanta a ningún usuario. La
interfaz la presenta como **solo lectura**; la API conserva el modo soporte (escrituras del
SUPER_ADMIN con `X-Tenant-ID`, quedan con su identidad como autor), sin pantalla que lo exponga.
Empresa y usuarios de una empresa se consultan por el id de la ruta (`/empresas/{id}`,
`/empresas/{id}/usuarios`): son tablas globales y la cabecera no altera el resultado.

**Invitaciones:** ya no intervienen en el alta de empresas (nunca lo hicieron en el backend: la
pantalla «Invitar usuarios» era la única herramienta del SUPER_ADMIN). Se conservan sin cambios
para el alta de **trabajadores** por su ADMIN_EMPRESA (`/api/auth/invitaciones` y `/registro`), que
entran ACTIVOS. El frontend ya no muestra la pantalla de invitaciones al SUPER_ADMIN.

**Migración V7 sobre una instalación existente:** no borra nada. Añade `empresas.codigo` y da a
cada empresa existente `EMP` + su id con al menos tres cifras (`EMP001`, `EMP1234`): determinista
y único. `codigo_empresa` (registro de trabajadores), usuarios e invitaciones no cambian.
`ck_empresas_codigo` usa `regexp_like` (PostgreSQL 15+ y H2).

## Fase 1: identidad normalizada, invitaciones y modo global explícito

Diagnóstico y plan completos en [`docs/auditoria-sgp.md`](../docs/auditoria-sgp.md).

**Roles (V8).** `SUPER_ADMIN` (administración global), `ADMIN_EMPRESA` (administra solo su
empresa; antes `GERENTE`) y `TRABAJADOR` (antes `USUARIO`). Se renombraron los valores
persistidos, las autoridades de Spring (`ROLE_ADMIN_EMPRESA`), `Permisos` y el contrato JSON
(`administrador` en lugar de `gerente`, ruta `/administradores/{id}/estado`). Ninguna cuenta pierde
el acceso: los JWT emitidos antes siguen valiendo porque el rol se lee de la BD en cada petición
(probado con un token que aún dice `rol: GERENTE`). Backend y BD se despliegan juntos; volver
atrás exige los `UPDATE` inversos.

**Nombres y apellidos (V9).** Columnas `nombres` y `apellidos`; `nombre` se conserva como nombre
visible derivado ("nombres apellidos") y lo siguen usando los listados, las solicitudes y los
revisores. Las cuentas nuevas los exigen (alta de empresa, registro). Las existentes **no se
parten** (sería frágil): conservan su nombre completo en `nombre`, responden
`datosPersonalesCompletos: false` y cada persona los completa con `PATCH /api/auth/me`. El
SUPER_ADMIN inicial puede recibir `ADMIN_APELLIDOS`.

**Empresa de la sesión.** El login y `GET /api/auth/me` incluyen `empresaNombre` y `empresaCodigo`,
leídos de la BD en cada petición (no del JWT): un cambio de nombre comercial se ve sin volver a
entrar. La interfaz consulta `/me` al cargar y al volver a la pestaña.

**Invitaciones (V10).** Se conserva `CodigoInvitacion` (sin tabla paralela) y se completa:

| Requisito | Cómo |
| --- | --- |
| Asociada a la empresa y a quien la creó | `@TenantId` + `usuario_creador_id` |
| Código seguro de un solo uso | 80 bits (`SecureRandom`), `usado` + quién y cuándo |
| Expiración verificable (72 h por defecto) | Se comprueba al canjear, sin depender de tareas programadas |
| Revocación | `DELETE /api/auth/invitaciones/{id}`: baja lógica con `fecha_revocacion` y `revocado_por_id` |
| Un código usado, revocado o caducado no vuelve a aceptarse | `CodigoInvitacion.estado()`; restricción `ck_codigos_invitacion_revocacion` |
| Sin usos concurrentes | Fila bloqueada (`FOR UPDATE`) al canjear y al revocar: si chocan, gana uno y el otro ve el resultado |
| Ni otra empresa ni roles privilegiados | Empresa y rol (`TRABAJADOR`) salen de la invitación; FK compuesta `(empresa_id, usado_por_id)` |
| Solo invitaciones de la propia empresa | `@TenantId`: las de otra empresa no existen (`404`) |
| El código no se expone de nuevo | El listado solo muestra `codigoParcial` (`••••-••••-••••-T3VW`) |

**Modo global explícito.** En las rutas de datos de negocio (todo `/api` salvo `/api/auth/**` y
`/api/v1/empresas/**`), el SUPER_ADMIN debe elegir el contexto: `X-Tenant-ID: <id>` (supervisión)
o `X-Tenant-ID: global` (modo global, solo lectura). Sin cabecera, `400 Contexto de empresa
requerido`: una ruta nueva (p. ej. ventas) nunca pierde el filtro de empresa por omisión. Cada
petición en modo global o en supervisión queda registrada (`Modo global: …`, `Supervisión: …`).
La cabecera sigue ignorándose para ADMIN_EMPRESA y TRABAJADOR (`global` incluido).

## Fase 2: analítica, compras, inventario y código de barras

Informe por entregas en [`docs/auditoria-sgp.md`](../docs/auditoria-sgp.md) y diseño del POS
(sin implementar) en [`docs/pos-diseno.md`](../docs/pos-diseno.md).

```
Solicitud APROBADA ── desde-solicitud ──→ OrdenCompra BORRADOR (una por proveedor)
BORRADOR ── emitir ──→ EMITIDA ── recepciones ──→ RECIBIDA_PARCIALMENTE ──→ RECIBIDA
BORRADOR | EMITIDA (sin mercancía) ── cancelar (motivo) ──→ CANCELADA
Recepción ──→ existencias (+) y un movimiento RECEPCION por línea, en una transacción
Ajuste (motivo) ──→ existencias (±, nunca < 0) y un movimiento AJUSTE
```

- **Aislamiento:** todas las tablas nuevas llevan `empresa_id` (`@TenantId`) y FK compuestas
  `(empresa_id, x_id)` hacia proveedores, productos, solicitudes, usuarios y entre sí.
- **Concurrencia:** la orden se bloquea en cada transición o recepción; las existencias, siempre
  en orden de producto; la numeración, con la fila de la empresa bloqueada.
- **Idempotencia:** recepciones y ajustes llevan `claveIdempotencia` (única por empresa).
- **Analítica:** consultas agregadas en la BD; días de Colombia calculados desde horas UTC
  (`floor(extract(epoch) / 3600)`), porque H2 no aplica bien `AT TIME ZONE`.
- **Valoración:** cada movimiento guarda su costo factual; el costo promedio es una decisión pendiente.

## Fuera de alcance de este hito

Ventas/POS (solo diseño); impuestos y descuentos en órdenes; valoración del inventario; reactivación de registros dados de baja; búsqueda en catálogos
y en el listado de empresas; refresh tokens; cambio de rol
de un usuario (p. ej. ascender a ADMIN_EMPRESA) y alta de administradores adicionales; cambio y recuperación de
contraseña (también la inicial del administrador); edición de los datos de una empresa y
regeneración de su código de registro; supervisión de solo lectura impuesta por el backend en los
módulos anteriores a la Fase 2 (en compras e inventario ya la impone); tabla de auditoría (hoy, registro en el log); nombre de la empresa en el
login; límite de intentos de registro y de login; edición o cancelación de solicitudes.
