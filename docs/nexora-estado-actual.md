# Nexora · Estado actual y puesta en marcha en rrtf.duckdns.org (demostración)

Objetivo inmediato: que **https://rrtf.duckdns.org** sirva la versión actual de Nexora (capas A y C)
para la demostración, con la infraestructura que ya existe y sin cambiar de dominio.
La migración a `n3xora.duckdns.org` queda en pausa (`deploy/n3xora/`, sin aplicar).

## 1. Qué es rrtf hoy y qué cambia

- `rrtf.duckdns.org` es el despliegue de **este mismo repositorio** (SGP, ahora con la marca
  Nexora) en Aatenea: Docker Compose (`sgp`: postgres, backend, frontend/Nginx) desde
  `/root/sgp-unisen`, rama `main`, con el script por fases `deploy/aatenea/sgp-aatenea.sh`.
  Fuente: `deploy/aatenea/README.md`, `README.md`, `CLAUDE.md`.
- No es otro servicio que haya que convivir con Nexora: **actualizar rrtf es publicar Nexora**.
  No se crea un segundo stack (compartiría nombre de proyecto Compose, imágenes y puertos).
- `main` está en `63df7d6`. El commit probado (`083ee00` + este documento) añade la capa A
  (límites de intentos), la vista de personal, el registro con código de empresa y la capa C
  (página pública, sistema visual). **Sin migraciones nuevas**: la base de datos no cambia y la
  vuelta atrás es solo de imágenes. **`docker-compose.yml` no cambia** en ese commit.
- No verificado desde esta sesión (sin acceso SSH a Aatenea y con `rrtf.duckdns.org` bloqueado por
  la red del entorno): la configuración real del proxy TLS, el `.env` del VPS y el estado vivo.

## 2. Verificado en local (resultados reales)

| Prueba | Resultado |
| --- | --- |
| Frontend: lint, TypeScript, pruebas, build | Sin avisos · 89/89 · build correcto |
| Backend: `mvn test` (H2) | 351/351 (336 previas + 15 de CORS/configuración) |
| Integración con PostgreSQL 16, bundle de producción, `frontend/nginx.conf` real y jar de producción (BCrypt 12, sin perfil) | 49/49 |
| Navegador por HTTPS con el nombre `rrtf.duckdns.org` (proxy TLS → Nginx → backend → PostgreSQL) | 19/19: página pública, login de SUPER_ADMIN y de ADMINISTRADOR_COMERCIAL, 10 módulos, recarga, salir; sin errores de consola ni 5xx |
| Flyway desde cero | 16 migraciones aplicadas (v16) |

## 3. Publicar en rrtf: lo que necesita autorización

| # | Acción | Quién | Autorización |
| --- | --- | --- | --- |
| A | Avanzar `origin/main` en rápido (sin merge ni force) hasta el commit probado | Claude o el propietario | **Sí** (cambia la rama que despliega el VPS) |
| B | Fase 1 en Aatenea (respaldo verificado, etiquetas de retorno) | Propietario (SSH) | **Sí** |
| C | Fase 2 en Aatenea (despliegue de backend y frontend) | Propietario (SSH) | **Sí** |

### A. Avanzar `main` (en local)

```bash
git fetch origin
OBJETIVO=<sha del commit probado, el que se indica en el informe>
git merge-base --is-ancestor origin/main "$OBJETIVO" && echo "avance en rápido posible"
git push origin "$OBJETIVO:refs/heads/main"      # falla si no es avance en rápido: no se fuerza
git fetch origin && git rev-parse origin/main     # debe ser $OBJETIVO
```

### B y C. En Aatenea (como en `deploy/aatenea/README.md`, secciones 1 y 2)

```bash
tmux new -s sgp
cd /root/sgp-unisen
git status --short                                   # debe estar vacío
git fetch origin
OBJETIVO=<el mismo sha>
git rev-parse origin/main                            # debe ser $OBJETIVO
git show "$OBJETIVO:deploy/aatenea/sgp-aatenea.sh" > /root/sgp-private/sgp-aatenea.sh
bash /root/sgp-private/sgp-aatenea.sh estado         # «Sin operaciones incompletas»

OBJETIVO=$OBJETIVO bash /root/sgp-private/sgp-aatenea.sh fase1   # respaldo + restauración de prueba + etiquetas
#   revisar la salida: «Sin migraciones nuevas», «El frontend CAMBIA», backend actual sano
bash /root/sgp-private/sgp-aatenea.sh fase2          # despliega, espera healthy, login por Nginx
```

Tiempo estimado: unos minutos de compilación (backend y frontend). Durante la recreación de los
contenedores puede haber segundos sin servicio: hacerlo fuera del horario de uso.

## 4. Comprobar desde el navegador (antes de dar por publicado)

1. `https://rrtf.duckdns.org/` sin sesión → página pública «Las compras de tu empresa, en orden.».
2. «Iniciar sesión» → entrar con una cuenta real (o una de las cuentas de la seed demo, si se
   sembró: su contraseña está en `/root/sgp-private/demo_password`).
3. Panel de inicio, Solicitudes, Órdenes de compra (descargar un PDF), Existencias, Punto de venta,
   Reportes y, con el administrador comercial, Personal.
4. Herramientas de desarrollo: sin errores en la consola ni respuestas 5xx.
5. En el VPS: `docker compose ps` (backend y frontend `healthy`).

## 5. Detener, recuperar y volver atrás (en Aatenea)

| Situación | Orden |
| --- | --- |
| Ver el estado | `bash /root/sgp-private/sgp-aatenea.sh estado` · `cd /root/sgp-unisen && docker compose ps` |
| Una fase se interrumpió | `bash /root/sgp-private/sgp-aatenea.sh recuperar` (tabla en `deploy/aatenea/README.md` §4) |
| Volver a la versión anterior (sin tocar la BD) | `bash /root/sgp-private/sgp-aatenea.sh rollback` y después el `git reset --hard` al `commit-previo` de `deploy/aatenea/README.md` §5 |
| Reiniciar solo el backend | `cd /root/sgp-unisen && docker compose restart backend` (con autorización) |

Como no hay migraciones nuevas, `rollback` devuelve exactamente la versión anterior con los mismos
datos. No hace falta restaurar la base de datos.

## 6. Plan B: demostración en un equipo local

Si el despliegue no se autoriza a tiempo, Nexora se demuestra en local con datos ficticios
(sección 6 de `docs/nexora-capa-c.md`):

```bash
docker compose up -d postgres            # o un PostgreSQL 16 local; base vacía, p. ej. sgp_demo
cd backend-spring
SPRING_PROFILES_ACTIVE=demo DEMO_PASSWORD=<clave-local> \
DB_URL=jdbc:postgresql://127.0.0.1:5432/sgp_demo DB_USERNAME=<usuario> DB_PASSWORD=<clave> \
JWT_SECRET="$(openssl rand -base64 64 | tr -d '\n')" \
ADMIN_USERNAME=superadmin.local ADMIN_EMAIL=superadmin@ejemplo.test ADMIN_PASSWORD=<clave-local> \
./mvnw spring-boot:run
cd ../frontend && npm install && npm run dev     # http://localhost:5173 (no 127.0.0.1)
```

Cuentas: `carlos.mendoza` (administrador comercial de Mercado Verde), `laura.torres` (trabajadora),
`superadmin.local`; contraseña de las demo: `DEMO_PASSWORD`. Detener: Ctrl-C en cada terminal.

## 7. Avisos para la demostración

- **Límite de intentos (capa A):** 10 contraseñas incorrectas seguidas con un mismo usuario lo
  bloquean 15 minutos (429, con el tiempo de espera en pantalla). Ojo con las erratas en directo.
- Sin sesión, `/` muestra la página pública; con sesión, el panel. Las sesiones abiertas antes del
  despliegue siguen valiendo.
- El comprobante de venta es interno (no es factura electrónica).
- Aún no hay cambio ni recuperación de contraseña.
