#!/usr/bin/env bash
# =============================================================================
# Nexora · Diagnóstico de SOLO LECTURA de Aatenea antes de publicar n3xora.duckdns.org
#
#   bash deploy/n3xora/diagnostico-aatenea.sh [REPO]      # REPO por defecto: /root/sgp-unisen
#
# No escribe archivos, no reinicia servicios, no cambia Nginx/Caddy, DNS, certificados ni Docker:
# solo lee y consulta. Del .env imprime únicamente APP_DOMAIN, HTTP_PORT y CORS_ORIGENES_ADICIONALES
# (nunca contraseñas ni JWT_SECRET) y enmascara cualquier token de DuckDNS. Ejecutarlo en Aatenea
# requiere la autorización del propietario (incluye órdenes `docker ... ps/port`, de lectura).
# Pega la salida completa en el informe de despliegue (sección «Datos verificados en Aatenea»).
# =============================================================================
set -u
REPO=${1:-/root/sgp-unisen}
NUEVO=n3xora.duckdns.org
ACTUAL=rrtf.duckdns.org
ocultar() { sed -E 's/(token=)[^&" ]+/\1<oculto>/Ig; s/([0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12})/<oculto>/Ig'; }
titulo() { printf '\n== %s ==\n' "$1"; }
existe() { command -v "$1" >/dev/null 2>&1; }

titulo "Máquina"
date -u '+%F %T UTC'; uname -sr; hostname
existe ip && ip -4 -o addr show scope global | awk '{print $2, $4}'

titulo "Qué escucha en 80, 443 y 8080 (proceso dueño)"
ss -ltnp 2>/dev/null | awk 'NR==1 || /:(80|443|8080) /'

titulo "Servicios de proxy en el host"
for s in caddy nginx apache2 httpd traefik haproxy; do
  existe systemctl && printf '%-8s %s\n' "$s" "$(systemctl is-active "$s" 2>/dev/null || true)"
done

titulo "Caddy (si existe)"
for f in /etc/caddy/Caddyfile /etc/caddy/conf.d/*; do
  [ -f "$f" ] && { echo "-- $f"; grep -nE 'duckdns|reverse_proxy|header|tls|^[^#[:space:]].*\{' "$f" | ocultar; }
done
existe caddy && caddy version 2>/dev/null

titulo "Nginx del host (si existe): server_name, listen, proxy_pass, certificados, cabeceras"
existe nginx && nginx -T 2>/dev/null | grep -nE '^# configuration file|server_name|listen |proxy_pass|ssl_certificate |X-Forwarded|Strict-Transport' | ocultar

titulo "Certificados de Let's Encrypt"
existe certbot && certbot certificates 2>/dev/null | grep -E 'Certificate Name|Domains|Expiry|Path'
[ -d /etc/letsencrypt/live ] && ls -1 /etc/letsencrypt/live

titulo "Certificado que se sirve hoy en 443 para cada nombre"
for h in "$ACTUAL" "$NUEVO"; do
  echo "-- $h"
  echo | timeout 8 openssl s_client -servername "$h" -connect 127.0.0.1:443 2>/dev/null \
    | openssl x509 -noout -subject -issuer -enddate -ext subjectAltName 2>/dev/null || echo "(sin certificado o sin escucha en 443)"
done

titulo "Despliegue de Nexora (Docker Compose, solo lectura)"
if [ -d "$REPO" ]; then
  echo "REPO=$REPO  rama=$(git -C "$REPO" rev-parse --abbrev-ref HEAD 2>/dev/null)  commit=$(git -C "$REPO" rev-parse --short HEAD 2>/dev/null)"
  git -C "$REPO" status --short 2>/dev/null | head -5
  [ -f "$REPO/.env" ] && grep -E '^(APP_DOMAIN|HTTP_PORT|CORS_ORIGENES_ADICIONALES)=' "$REPO/.env"
  if existe docker; then
    (cd "$REPO" && docker compose ps --format 'table {{.Service}}\t{{.Status}}\t{{.Ports}}' 2>/dev/null)
    (cd "$REPO" && echo "frontend:80 publicado en: $(docker compose port frontend 80 2>/dev/null)")
    echo "Primeras columnas del log de Nginx (dirección con la que llega el proxy):"
    (cd "$REPO" && docker compose logs frontend --tail 8 --no-log-prefix 2>/dev/null | awk '{print $1, $6, $7, $9}')
  fi
else
  echo "No existe $REPO"
fi

titulo "Cortafuegos (80/443)"
existe ufw && ufw status 2>/dev/null | grep -E 'Status|80|443'
existe nft && nft list ruleset 2>/dev/null | grep -E 'dport.*(80|443)' | head

titulo "DNS (resolución desde el propio VPS)"
for h in "$ACTUAL" "$NUEVO"; do
  printf '%-22s %s\n' "$h" "$(getent ahostsv4 "$h" 2>/dev/null | awk '{print $1}' | sort -u | tr '\n' ' ')"
done

titulo "Actualización de DuckDNS (token enmascarado)"
crontab -l 2>/dev/null | grep -i duckdns | ocultar
ls -1 /etc/cron.d 2>/dev/null | grep -i duck
existe systemctl && systemctl list-timers --all 2>/dev/null | grep -i duck

titulo "Respuestas HTTP vistas desde el VPS"
PUERTO=$( [ -d "$REPO" ] && existe docker && (cd "$REPO" && docker compose port frontend 80 2>/dev/null | tail -1 | sed 's/.*://') )
for h in "$ACTUAL" "$NUEVO"; do
  [ -n "${PUERTO:-}" ] && printf '%-40s %s\n' "http://127.0.0.1:$PUERTO (Host: $h)/healthz" \
    "$(curl -s -o /dev/null -m 8 -w '%{http_code}' -H "Host: $h" "http://127.0.0.1:$PUERTO/healthz")"
  for esquema in http https; do
    printf '%-40s %s\n' "$esquema://$h/healthz" \
      "$(curl -s -o /dev/null -m 10 -w '%{http_code} → %{redirect_url}' "$esquema://$h/healthz")"
  done
done
echo
echo "Fin del diagnóstico (nada se ha modificado)."
