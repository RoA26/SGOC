#!/usr/bin/env bash
# =============================================================================
# Nexora · Reproducción LOCAL (sin Docker) de la cadena de producción en n3xora.duckdns.org
#
#   proxy TLS del host            Nginx del contenedor frontend        backend
#   (nginx-host/*.conf, :80/:443) → (frontend/nginx.conf, :8080)    →  127.0.0.1:8081
#
# Usa los archivos REALES del repositorio con sustituciones mínimas y comprobadas:
#   - frontend/nginx.conf: escucha en 127.0.0.1:8080, raíz = frontend/dist, backend = 127.0.0.1:8081
#     (en Docker es el nombre «backend»; resolver con una IP literal no cambia el comportamiento).
#   - nginx-host/n3xora.duckdns.org.conf: escucha en 127.0.0.1:80/443 y certificados de una CA de
#     prueba creada aquí. Una copia para rrtf.duckdns.org simula el sitio que ya existe.
#
# Requisitos: nginx (con ssl y http2), openssl, el backend escuchando en 127.0.0.1:8081 y
# `npm run build` hecho en frontend/. Ejecutar como root (puertos 80/443 de 127.0.0.1).
#
#   NEXORA_PRUEBA_DIR=/ruta/desechable bash deploy/n3xora/pruebas/entorno-local.sh [arrancar|parar]
# =============================================================================
set -euo pipefail
RAIZ=$(cd "$(dirname "$0")/../../.." && pwd)
DIR=${NEXORA_PRUEBA_DIR:?Define NEXORA_PRUEBA_DIR (carpeta desechable)}
ORDEN=${1:-arrancar}
NGINX=(nginx -p "$DIR" -c "$DIR/conf/nginx.conf")

if [ "$ORDEN" = parar ]; then
  [ -f "$DIR/run/nginx.pid" ] && "${NGINX[@]}" -s quit && echo "Nginx de prueba detenido."
  exit 0
fi

DIST=$RAIZ/frontend/dist
[ -f "$DIST/index.html" ] || { echo "Falta $DIST/index.html: ejecuta npm run build en frontend/" >&2; exit 1; }
mkdir -p "$DIR"/{conf,certs,acme/.well-known/acme-challenge,logs,run}

# 1. CA de prueba y un certificado por nombre (con SAN), válidos 2 días.
cd "$DIR/certs"
if [ ! -f ca.crt ]; then
  openssl req -x509 -newkey rsa:2048 -nodes -days 2 -subj "/CN=CA de prueba Nexora" -keyout ca.key -out ca.crt 2>/dev/null
  for h in n3xora.duckdns.org rrtf.duckdns.org; do
    openssl req -newkey rsa:2048 -nodes -subj "/CN=$h" -keyout "$h.key" -out "$h.csr" 2>/dev/null
    printf 'subjectAltName=DNS:%s\n' "$h" > "$h.ext"
    openssl x509 -req -in "$h.csr" -CA ca.crt -CAkey ca.key -CAcreateserial -days 2 -extfile "$h.ext" -out "$h.crt" 2>/dev/null
  done
fi
echo "prueba-acme" > "$DIR/acme/.well-known/acme-challenge/prueba"

# 2. Nginx del contenedor: el archivo real con tres sustituciones (se verifica que se aplicaron).
sed -e 's/listen 80 default_server;/listen 127.0.0.1:8080 default_server;/' \
    -e "s#root /usr/share/nginx/html;#root $DIST;#" \
    -e 's#set $backend http://backend:8081;#set $backend http://127.0.0.1:8081;#' \
    "$RAIZ/frontend/nginx.conf" > "$DIR/conf/frontend.conf"
for patron in 'listen 127.0.0.1:8080 default_server;' "root $DIST;" 'set $backend http://127.0.0.1:8081;'; do
  grep -qF "$patron" "$DIR/conf/frontend.conf" || { echo "Sustitución no aplicada: $patron" >&2; exit 1; }
done

# 3. Proxy TLS del host: el candidato de n3xora y una copia para rrtf (el sitio existente).
host_conf() { # $1 = nombre del sitio
  sed -e '/listen \[::\]/d' \
      -e 's/listen 80;/listen 127.0.0.1:80;/' \
      -e 's/listen 443 ssl http2;/listen 127.0.0.1:443 ssl http2;/' \
      -e "s#/etc/letsencrypt/live/n3xora.duckdns.org/fullchain.pem#$DIR/certs/$1.crt#" \
      -e "s#/etc/letsencrypt/live/n3xora.duckdns.org/privkey.pem#$DIR/certs/$1.key#" \
      -e "s#/var/www/letsencrypt#$DIR/acme#" \
      -e "s/n3xora_tls/tls_${1%%.*}/" \
      -e "s/server_name n3xora.duckdns.org;/server_name $1;/" \
      "$RAIZ/deploy/n3xora/nginx-host/n3xora.duckdns.org.conf"
}
host_conf n3xora.duckdns.org > "$DIR/conf/host-n3xora.conf"
host_conf rrtf.duckdns.org > "$DIR/conf/host-rrtf.conf"

# 4. Configuración principal. El registro incluye X-Forwarded-For para comprobar qué llega al contenedor.
cat > "$DIR/conf/nginx.conf" <<EOF
worker_processes 1;
pid $DIR/run/nginx.pid;
error_log $DIR/logs/error.log warn;
events { worker_connections 256; }
http {
    include /etc/nginx/mime.types;
    default_type application/octet-stream;
    log_format prueba '\$server_port \$host "\$request" \$status xff="\$http_x_forwarded_for" xfp="\$http_x_forwarded_proto"';
    access_log $DIR/logs/access.log prueba;
    client_body_temp_path $DIR/run/body;
    proxy_temp_path $DIR/run/proxy;
    fastcgi_temp_path $DIR/run/fastcgi;
    uwsgi_temp_path $DIR/run/uwsgi;
    scgi_temp_path $DIR/run/scgi;
    include $DIR/conf/frontend.conf;
    include $DIR/conf/host-n3xora.conf;
    include $DIR/conf/host-rrtf.conf;
}
EOF

"${NGINX[@]}" -t
[ -f "$DIR/run/nginx.pid" ] && kill -0 "$(cat "$DIR/run/nginx.pid")" 2>/dev/null && "${NGINX[@]}" -s reload || "${NGINX[@]}"
echo "Listo: https://n3xora.duckdns.org y https://rrtf.duckdns.org en 127.0.0.1 (CA: $DIR/certs/ca.crt)"
