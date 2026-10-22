# Capa B: límites por IP en Nginx (PROPUESTA, NO ACTIVA)

Nada de esta carpeta está en uso: el contenedor `frontend` sigue construyéndose con
`frontend/nginx.conf`. La propuesta está validada en local (ver «Pruebas»), pero **no puede activarse
hasta verificar la topología de Aatenea** (sección «Antes de activar») y con autorización del
propietario, porque cambia Nginx y Docker (`CLAUDE.md`).

Complementa a la capa A (límites por usuario y por código en el backend, `docs/auditoria-sgp.md`);
no la sustituye.

## Archivos

| Archivo | Qué es |
| --- | --- |
| `default.conf.template` | Configuración propuesta de Nginx (sustituiría a `frontend/nginx.conf`), como plantilla de la imagen oficial |
| `activacion.diff` | Cambios que la activarían en `frontend/Dockerfile`, `docker-compose.yml` y `.env.example`. **No aplicado**; `git apply --check` lo acepta |
| `pruebas/ejecutar.sh` | Pruebas aisladas de Nginx (backend simulado, sin Docker ni datos) |
| `pruebas/interaccion-capa-a.sh` | Interacción con la capa A contra un backend local real |

## Qué hace

1. **IP real del cliente** (`realip`): solo se cree `X-Forwarded-For` si la conexión llega desde
   `NGINX_PROXY_CONFIANZA` (el proxy TLS del host); con `real_ip_recursive on` se toma la IP que
   añadió ese proxy y se ignora lo que el cliente escriba delante.
2. **`limit_req`** en `/api/auth/login` y `/api/auth/registro`, por IP real, con ráfaga y `nodelay`.
   Un `map` sobre `$uri` (decodificada y normalizada) por prefijo: `//login`, `%6Cogin`, `login/`,
   `login;x` o `./login` caen en el mismo límite. El resto de la API, los estáticos, `/healthz` y la
   SPA no tienen límite.
3. **429 propio** en Problem Details (`title` «Demasiados intentos», `detail`,
   `reintentarEnSegundos`) con `Retry-After` y las cabeceras de seguridad: el frontend lo muestra
   como el 429 de la capa A.
4. **`X-Forwarded-For` hacia el backend = la IP ya resuelta** (antes se encadenaba lo que enviara el
   cliente y Spring tomaba la primera, falsificable).
5. **Falla seguro**: sin `NGINX_PROXY_CONFIANZA`, Nginx no arranca. **Modo de prueba**
   (`limit_req_dry_run`): registra lo que rechazaría sin rechazar nada.

## Variables

| Variable | Por defecto (en `activacion.diff`) | Motivo |
| --- | --- | --- |
| `NGINX_PROXY_CONFIANZA` | **ninguno** (obligatoria) | Dirección del proxy previo tal como la ve Nginx; `127.0.0.1` si no hay proxy. No se supone: se verifica |
| `NGINX_LIMITE_LOGIN` / `NGINX_RAFAGA_LOGIN` | `20r/m` / `20` | 21 logins seguidos por IP (la verificación de `sgp-aatenea.sh` hace 14; una oficina tras un NAT entrando a la vez), después 1 cada 3 s. La capa A ya corta a 10 fallos por usuario |
| `NGINX_LIMITE_REGISTRO` / `NGINX_RAFAGA_REGISTRO` | `6r/m` / `10` | Altas en grupo (11 seguidas), después 1 cada 10 s. La capa A: 20 por código y 200 en total por hora |
| `NGINX_RETRY_AFTER` | `60` | Nginx no calcula la espera: valor fijo y conservador (el cupo real se repone antes) |
| `NGINX_LIMITE_MODO_PRUEBA` | `on` | Empezar observando; pasar a `off` tras revisar el log |
| `NGINX_BACKEND` | `http://backend:8081` | Igual que hoy |

## Antes de activar: datos que el operador debe verificar en Aatenea (solo lectura)

1. Qué escucha en 80 y 443 y qué es (`ss -ltnp | grep -E ':(80|443) '`).
2. El valor de `HTTP_PORT` (`grep '^HTTP_PORT=' .env`, solo esa línea) y `docker compose port frontend 80`.
3. La configuración del proxy TLS para `rrtf.duckdns.org`: si añade o sustituye `X-Forwarded-For` y
   si confía en el que envía el cliente.
4. **La dirección con la que ese proxy llega a Nginx**: unas líneas de
   `docker compose logs frontend --tail 20` (la primera columna es `$remote_addr`). Si todas las
   peticiones muestran la misma IP (p. ej. la pasarela de la red de Docker), esa es la candidata a
   `NGINX_PROXY_CONFIANZA`, y entonces **`HTTP_PORT` debe ser `127.0.0.1:…`**: si el puerto estuviera
   abierto a internet y Docker reescribiera el origen con esa misma IP, un atacante podría falsificar
   `X-Forwarded-For`.
5. Si Docker usa el *userland proxy* (`docker info`, configuración del daemon).
6. Que la verificación de `sgp-aatenea.sh` (14 logins seguidos desde el propio VPS) sigue cabiendo en
   la ráfaga, y con qué IP llega.

Con esos datos: elegir `NGINX_PROXY_CONFIANZA`, aplicar `activacion.diff` (con autorización),
desplegar con `NGINX_LIMITE_MODO_PRUEBA=on`, revisar `limiting requests, dry run` en el log de
Nginx durante unos días y, si no afecta a usuarios legítimos, pasar a `off`.

## Reproducción local

```bash
# Requisitos: nginx con limit_req y realip (p. ej. apt install nginx-light), gettext-base (envsubst),
# curl y python3. Sin Docker, sin red, sin datos.
bash deploy/propuesta-capa-b/pruebas/ejecutar.sh

# Interacción con la capa A: backend local real sobre una base desechable
# (scripts/validacion-local/README.md), con los límites de la capa A por defecto.
bash deploy/propuesta-capa-b/pruebas/interaccion-capa-a.sh
```

Los clientes se representan con conexiones reales desde 127.0.0.3, .4…; el «proxy TLS» es un
servidor de Nginx que reenvía desde 127.0.0.2 añadiendo `X-Forwarded-For`.

## Limitaciones

- Probado con Nginx 1.24 (Ubuntu); la imagen de producción es `nginx:1.30-alpine` (las directivas
  usadas existen desde 1.17.1).
- No se reprodujeron: redes y puertos publicados de Docker, el proxy TLS real ni el tráfico real.
- Contadores en memoria de Nginx: se vacían al reiniciar el contenedor y son por instancia.
- Varios usuarios legítimos tras una misma IP (NAT de una oficina, CGNAT móvil) comparten límite:
  por eso la ráfaga es amplia y conviene empezar en modo de prueba.
