# Unisen SGP: backend Spring Boot

API REST del Sistema de Gestión de Órdenes de Compra. **Hito 1: core Spring Boot,
persistencia base y seguridad JWT.**

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
├── controller/        AuthController  → POST /api/auth/login, GET /api/auth/me
├── exception/         GlobalExceptionHandler (errores RFC 9457 / Problem Details)
├── model/entity/      Usuario (JPA), Rol
├── model/dto/         LoginRequest, LoginResponse, UsuarioResponse (records)
├── repository/        UsuarioRepository (Spring Data)
├── security/          JwtUtil, JwtAuthenticationFilter, UsuarioPrincipal (UserDetails),
│                      UsuarioDetailsService, entry point 401 y handler 403
└── service/           AuthService, UsuarioService
src/main/resources
├── application.yml
└── db/migration/V1__crear_tabla_usuarios.sql
```

## Puesta en marcha (local)

Requisitos: JDK 21 y Docker (o un PostgreSQL 16 propio). No hace falta instalar Maven: se usa `./mvnw`.

```bash
cd backend-spring
docker compose up -d                       # PostgreSQL en localhost:5432 (bd/usuario/clave: sgp)

cp .env.example .env
# Edita .env y rellena como mínimo:
#   JWT_SECRET      → openssl rand -base64 64
#   ADMIN_PASSWORD  → contraseña del primer administrador (mín. 8 caracteres)

./mvnw spring-boot:run
```

- API: http://localhost:8080
- Swagger UI: http://localhost:8080/swagger-ui.html (botón *Authorize* → pega el `accessToken`)
- Health: http://localhost:8080/actuator/health

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
| `CORS_ALLOWED_ORIGINS` | `http://localhost:5173` | Orígenes del frontend, separados por comas |
| `ADMIN_EMAIL` / `ADMIN_PASSWORD` / `ADMIN_NOMBRE` | vacío | Alta del primer administrador al arrancar (idempotente) |
| `SWAGGER_ENABLED` | `true` | Pon `false` en producción si no quieres exponer la documentación |
| `SERVER_PORT` | `8080` | Puerto HTTP |

## API

| Método | Ruta | Auth | Respuesta |
| --- | --- | --- | --- |
| POST | `/api/auth/login` | Pública | `200` `{accessToken, tokenType, expiresIn, usuario}` |
| GET | `/api/auth/me` | Bearer | `200` `{id, email, nombre, rol}` |
| GET | `/actuator/health` | Pública | `200` `{status: "UP"}` |
| * | cualquier otra | Bearer | `401` sin token válido |

```bash
curl -X POST http://localhost:8080/api/auth/login \
  -H 'Content-Type: application/json' \
  -d '{"email":"admin@unisen.com","password":"<ADMIN_PASSWORD>"}'
```

Los errores siguen RFC 9457 (`application/problem+json`):

```json
{"type":"about:blank","title":"Credenciales inválidas","status":401,
 "detail":"Correo o contraseña incorrectos.","instance":"/api/auth/login"}
```

| Código | Cuándo |
| --- | --- |
| `400` | JSON mal formado o validación fallida (`errors`: campo → mensaje) |
| `401` | Credenciales incorrectas; token ausente, manipulado o expirado; usuario eliminado o desactivado |
| `403` | Login con contraseña correcta de una cuenta deshabilitada; rol insuficiente |

## Decisiones de seguridad

- **Stateless:** CSRF desactivado (no hay cookies de sesión), `SessionCreationPolicy.STATELESS`;
  `formLogin`, `httpBasic` y `logout` deshabilitados.
- **JWT (HS256):** clave obligatoria de ≥256 bits validada al arrancar. Se exigen firma,
  `iss` y `exp`; se rechazan tokens sin firmar (`alg: none`). Tolerancia de reloj: 30 s.
- **El filtro consulta la BD en cada petición:** desactivar o borrar un usuario invalida sus
  tokens al instante, y la autorización nunca se fía del rol escrito en el token.
- **Anti-enumeración:** un correo inexistente y una contraseña incorrecta dan la misma
  respuesta (y Spring iguala los tiempos). El estado "cuenta deshabilitada" solo se revela
  **después** de validar la contraseña (comprobaciones de cuenta movidas a post-autenticación).
- **BCrypt:** coste configurable. Las contraseñas de más de 72 bytes, el límite de BCrypt,
  se rechazan al darlas de alta y en el login devuelven `401`, nunca un error interno.
- **Sin fugas:** `toString()` de `LoginRequest`, `JwtProperties` y del admin enmascaran
  secretos. El hash se borra del `UserDetails` tras autenticar. Los `500` no exponen detalles.
- **CORS** restringido a `CORS_ALLOWED_ORIGINS`, sin credenciales (el token viaja en un header).
- **Esquema gestionado por Flyway:** `ddl-auto: validate` hace que Hibernate solo verifique el
  esquema. `open-in-view: false`.
- **Detrás de proxy (DuckDNS/HTTPS):** `server.forward-headers-strategy: framework`.

## Tests

```bash
./mvnw test        # 38 tests: H2 en modo PostgreSQL, sin dependencias externas

# Contra PostgreSQL real (crea antes la BD sgp_test):
SPRING_DATASOURCE_URL=jdbc:postgresql://localhost:5432/sgp_test \
SPRING_DATASOURCE_USERNAME=sgp SPRING_DATASOURCE_PASSWORD=sgp ./mvnw test
```

Cubren `JwtUtil` (expiración, manipulación, otra clave, otro emisor, `alg: none`, claves
débiles) y el flujo HTTP completo: login, validación, cuentas deshabilitadas, tokens
revocados, rutas protegidas, CORS, Swagger, health y el alta del administrador.

El compilador corre con `-Xlint:all -Werror`: cualquier warning, incluido el uso de APIs
deprecadas, rompe el build.

## Fuera de alcance de este hito

Proveedores, productos y órdenes de compra; refresh tokens; gestión de usuarios vía API;
`Dockerfile` y despliegue en el VPS (DuckDNS + HTTPS).
