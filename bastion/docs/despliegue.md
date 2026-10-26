# Despliegue en el VPS

Dominio: el que indique el responsable (p. ej. `b4sti0n.duckdns.org`). Todo se hace con
`deploy/scripts/bastion-vps.sh`, que solo opera sobre el proyecto Compose `bastion` y nunca
detiene, reinicia ni modifica Nexora, Eden u otros servicios.

## Qué se probó antes de entregar este procedimiento

En un entorno de pruebas con Docker y un Nginx de host que ya servía otro sitio:

- Construcción de las imágenes `bastion/backend` (280 MB) y `bastion/frontend` (74 MB).
- `preparar` → `desplegar` → bootstrap → `proxy --aplicar` → recorrido completo en navegador a
  través del Nginx del host (super admin → organización → administradora → bodega, producto y
  trabajador → entrada → el trabajador registra una salida), sin errores de consola.
- El sitio existente de Nginx quedó intacto (misma huella SHA-256) y siguió respondiendo.
- Una segunda ejecución de `proxy --aplicar` se negó a tocar nada.
- Límite de intentos de login: a partir del 7.º intento fallido por minuto responde `429`.
- Respaldo con `pg_dump` y su lectura con `pg_restore -l`.
- **Memoria medida en reposo: ~115 MB en total** (frontend 5 MB, backend 70 MB, PostgreSQL 41 MB).

**No probado** (depende del VPS real): DNS de DuckDNS, el certificado de Let's Encrypt y el
proxy que exista realmente en 80/443.

## 0. Requisitos

- Acceso SSH al VPS con un usuario que pueda usar Docker (y `sudo` para el paso del proxy).
- Docker con el plugin Compose. En Ubuntu 24.04:
  `sudo apt update && sudo apt install -y docker.io docker-compose-v2`
- El dominio de DuckDNS apuntando a la IP pública del VPS (se actualiza en duckdns.org).
- Acceso de lectura al repositorio desde el VPS (el mismo con el que se clonó Nexora).

## 1. Clonar Bastión en una carpeta propia

**No use la copia de Nexora** (`/root/sgp-unisen`): su script exige que esté limpia y en `main`.

```bash
git clone --filter=blob:none --sparse https://github.com/roa26/sgoc.git /opt/bastion-src
cd /opt/bastion-src
git sparse-checkout set bastion
git checkout claude/jolly-turing-klsm36        # o la rama que se apruebe
cd bastion
```

## 2. Diagnóstico (solo lectura)

```bash
sudo DOMINIO=b4sti0n.duckdns.org bash deploy/scripts/bastion-vps.sh diagnostico
```

Revise: memoria disponible, que el nombre `bastion` esté libre, el puerto local elegido, **qué
escucha en 80/443** y si el DNS ya apunta al VPS. Si la memoria disponible es baja (< 900 MB),
vea "Compilar fuera del VPS".

## 3. Preparar y desplegar

```bash
bash deploy/scripts/bastion-vps.sh preparar      # crea .env (600) con secretos aleatorios y puerto libre
bash deploy/scripts/bastion-vps.sh desplegar     # construye, arranca y espera a que esté sano
bash deploy/scripts/bastion-vps.sh admin         # crea el SUPER_ADMIN (pide correo, nombre y contraseña)
```

En este punto Bastión responde en `http://127.0.0.1:<puerto>` dentro del VPS, sin exponerse.
Para verlo desde su equipo antes de publicar el dominio:
`ssh -L 8090:127.0.0.1:8090 usuario@vps` y abra `http://localhost:8090`.

## 4. Publicar el dominio

```bash
DOMINIO=b4sti0n.duckdns.org bash deploy/scripts/bastion-vps.sh proxy
```

Según lo que detecte:

- **Nginx en el host:** genera un sitio nuevo. Para instalarlo (archivo nuevo, valida con
  `nginx -t`, recarga sin reiniciar; si la validación falla, lo retira):
  `sudo DOMINIO=b4sti0n.duckdns.org bash deploy/scripts/bastion-vps.sh proxy --aplicar`
  y después el certificado, que solo modifica ese sitio:
  `sudo certbot --nginx -d b4sti0n.duckdns.org --redirect`
- **Caddy, o nada en 80/443** (servidor limpio): `proxy --aplicar` instala Caddy si falta
  (paquete de Ubuntu), guarda una copia del Caddyfile, añade el sitio, valida y recarga. Caddy
  obtiene y renueva el certificado HTTPS solo:
  `sudo DOMINIO=b4sti0n.duckdns.org bash deploy/scripts/bastion-vps.sh proxy --aplicar`
- **Un contenedor ocupa 80/443** (p. ej. el frontend de Nexora): se detiene y explica las
  opciones. Requiere decidir cómo compartir 80/443 entre ambos; **no se toca nada**.

Comprobación final: `curl -I https://b4sti0n.duckdns.org` y `https://b4sti0n.duckdns.org/api/health`.

## 5. Operación

```bash
bash deploy/scripts/bastion-vps.sh estado                 # contenedores, memoria, salud
bash deploy/scripts/bastion-vps.sh respaldo /ruta/segura  # pg_dump; programar con cron y copiar fuera del VPS
deploy/scripts/restaurar.sh archivo.dump                  # destructivo para Bastión; pide confirmación
```

Actualizar a una versión nueva: `git pull` en `/opt/bastion-src` y `bash deploy/scripts/bastion-vps.sh desplegar`.
Las migraciones se aplican solas al arrancar el backend. Antes de actualizar, haga un respaldo.

## Compilar fuera del VPS

Si el VPS tiene poca memoria libre, construya en otra máquina con Docker y cargue las imágenes:

```bash
# en su equipo, dentro de bastion/
docker compose -p bastion build
docker save bastion/backend:latest bastion/frontend:latest | gzip > bastion-imagenes.tar.gz
scp bastion-imagenes.tar.gz usuario@vps:/tmp/
# en el VPS
gunzip -c /tmp/bastion-imagenes.tar.gz | docker load
SIN_CONSTRUIR=si bash deploy/scripts/bastion-vps.sh desplegar
```

## Límites de recursos (`docker-compose.yml`)

| Servicio | Límite | Notas |
|---|---|---|
| postgres | 384 MB | `max_connections=30`, `shared_buffers=96MB` |
| backend | 256 MB | 1 worker de Uvicorn, pool de 5 conexiones |
| frontend | 64 MB | Nginx con estáticos y límite de intentos de login |

## Aislamiento

- Proyecto `bastion`, red `bastion_bastion_net`, volumen `bastion_bastion_pgdata`, imágenes
  `bastion/*`. Ningún nombre coincide con los de Nexora (`sgp`).
- PostgreSQL y la API no publican puertos; el frontend solo en `127.0.0.1`.
- El script nunca ejecuta `docker compose down`, `prune` ni comandos sin `-p bastion`.
