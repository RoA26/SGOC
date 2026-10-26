# Despliegue (preparado, NO ejecutado)

Dominio previsto: `b4stin0n.duckdns.org`. Nada de lo descrito aquí se ha ejecutado contra el
VPS. Antes de desplegar se requiere autorización explícita.

## 1. Inspección previa del VPS (solo lectura)

```bash
free -m; uptime; df -h
docker ps --format '{{.Names}}\t{{.Ports}}\t{{.Status}}'
docker stats --no-stream --format '{{.Name}}\t{{.MemUsage}}'
docker network ls; docker volume ls; docker compose ls
sudo ss -ltnp                      # puertos ocupados; confirmar que 8090 está libre
ls /etc/nginx/sites-enabled/; sudo nginx -T | grep -E 'server_name|listen'
dig +short b4stin0n.duckdns.org; curl -s https://ifconfig.me   # deben coincidir
```

Criterio: si la memoria libre (sin contar caché) es inferior a ~800 MB con los servicios
actuales, **no desplegar** y valorar alternativas (bajar límites, apagar servicios inactivos
con permiso, o ampliar el VPS).

## 2. Presupuesto de memoria (límites en `docker-compose.yml`)

| Servicio | Límite | Notas |
|---|---|---|
| postgres | 384 MB | `max_connections=30`, `shared_buffers=96MB` |
| backend | 256 MB | 1 worker de Uvicorn, pool de 5 conexiones |
| frontend | 64 MB | Nginx sirviendo estáticos |
| **Total** | **~704 MB** máximo | consumo real esperado menor; medir con `docker stats` |

## 3. Aislamiento respecto de Nexora, Eden y otros

- Proyecto Compose `bastion`; volumen `bastion_bastion_pgdata`; red `bastion_bastion_net`;
  imágenes `bastion/backend` y `bastion/frontend`. Ningún nombre coincide con `sgp` (Nexora).
- PostgreSQL y backend **no publican puertos**. El frontend solo en `127.0.0.1:8090`.
- Todos los comandos se ejecutan con `-p bastion` desde el directorio de Bastión. **Nunca**
  `docker compose down` global ni `docker system prune`.

## 4. Construcción fuera del VPS

```bash
cd frontend && npm ci && npm test && npm run build      # en la máquina de desarrollo
cd .. && docker compose build                          # o construir en CI
docker save bastion/backend bastion/frontend | gzip > bastion-images.tar.gz
# copiar al VPS y: gunzip -c bastion-images.tar.gz | docker load
```

## 5. Arranque

```bash
cp .env.example .env && chmod 600 .env    # completar secretos en el VPS, nunca en el repo
docker compose -p bastion up -d --no-build
docker compose -p bastion ps              # los tres servicios "healthy"
docker compose -p bastion exec backend python -m app.bootstrap --email ... --name "..."
```

El backend aplica `alembic upgrade head` al iniciar.

## 6. Nginx del servidor y HTTPS

`deploy/nginx/b4stin0n.duckdns.org.conf` es un bloque **nuevo** para el Nginx existente. Pasos,
adaptándolos a cómo el servidor gestiona hoy los certificados (comprobar antes):

1. Copiar a `sites-available/` y enlazar en `sites-enabled/` sin tocar otros archivos.
2. Emitir el certificado solo para `b4stin0n.duckdns.org` con el método ya en uso (p. ej.
   `certbot certonly --webroot` o el plugin de Nginx) — requiere autorización.
3. `sudo nginx -t` y solo si pasa, `sudo systemctl reload nginx` (recarga, no reinicio).
4. Verificar: `curl -I https://b4stin0n.duckdns.org` y `/api/health`.

## 7. Respaldo y restauración

```bash
deploy/scripts/respaldo.sh /ruta/respaldos       # pg_dump formato custom; programar con cron
deploy/scripts/restaurar.sh archivo.dump         # destructivo para Bastión; pide confirmación
```

Probar la restauración periódicamente en una base desechable. Guardar copias fuera del VPS.

## Verificado y no verificado

- Verificado en desarrollo: `docker compose config` válido, sintaxis de los scripts.
- **No verificado:** construcción de imágenes, arranque de contenedores, healthchecks y
  configuración de Nginx (este entorno no tiene demonio Docker ni Nginx).
