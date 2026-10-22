#!/usr/bin/env bash
# =============================================================================
# Interacción de la capa B (Nginx, por IP) con la capa A (backend, por usuario y por código),
# con el backend REAL en local y los umbrales propuestos para producción.
#
# Requiere un backend local en marcha (por defecto http://127.0.0.1:8081, sobre una base de datos
# local desechable; ver scripts/validacion-local/README.md) con los límites de la capa A por
# defecto. No crea datos: usa usuarios inexistentes y un código de empresa inválido. Nunca
# producción.
#   bash deploy/propuesta-capa-b/pruebas/interaccion-capa-a.sh
# =============================================================================
set -uo pipefail
AQUI=$(cd "$(dirname "$0")" && pwd)
BACKEND=${NEXORA_BACKEND_LOCAL:-http://127.0.0.1:8081}
case $BACKEND in http://127.0.0.1:*|http://localhost:*) ;; *) echo "Rechazado: $BACKEND no es local."; exit 2 ;; esac
T=$(mktemp -d "${TMPDIR:-/tmp}/capa-b-int-XXXXXX"); chmod 755 "$T"
trap '[[ -f $T/nginx.pid ]] && nginx -p "$T" -c "$T/nginx.conf" -s quit; sleep 0.5; rm -rf "$T"' EXIT
OK=0; FALLOS=0
comprobar() { if [[ $2 == 0 ]]; then OK=$((OK + 1)); echo "OK    $1${3:+ — $3}"; else FALLOS=$((FALLOS + 1)); echo "FALLO $1${3:+ — $3}"; fi; }

export NGINX_BACKEND=$BACKEND NGINX_PROXY_CONFIANZA=127.0.0.1 NGINX_RETRY_AFTER=60 NGINX_LIMITE_MODO_PRUEBA=off
export NGINX_LIMITE_LOGIN=20r/m NGINX_RAFAGA_LOGIN=20 NGINX_LIMITE_REGISTRO=6r/m NGINX_RAFAGA_REGISTRO=10
envsubst '${NGINX_BACKEND} ${NGINX_PROXY_CONFIANZA} ${NGINX_LIMITE_LOGIN} ${NGINX_RAFAGA_LOGIN} ${NGINX_LIMITE_REGISTRO} ${NGINX_RAFAGA_REGISTRO} ${NGINX_RETRY_AFTER} ${NGINX_LIMITE_MODO_PRUEBA}' \
    < "$AQUI/../default.conf.template" > "$T/default.conf"
mkdir -p "$T/html" && echo '<!doctype html>' > "$T/html/index.html" && chmod -R a+rX "$T/html"
sed -i "s#root /usr/share/nginx/html;#root $T/html;#" "$T/default.conf"
cat > "$T/nginx.conf" <<CONF
worker_processes 1; pid $T/nginx.pid; error_log $T/error.log warn; events {}
http { include /etc/nginx/mime.types; access_log off;
       client_body_temp_path $T/b; proxy_temp_path $T/p; fastcgi_temp_path $T/f; uwsgi_temp_path $T/u; scgi_temp_path $T/s;
       include $T/default.conf; }
CONF
nginx -t -p "$T" -c "$T/nginx.conf" 2>/dev/null && nginx -p "$T" -c "$T/nginx.conf" && sleep 0.5 || { echo "Nginx no arrancó"; exit 1; }

# Cada respuesta se etiqueta por su origen: 401/400 del backend, 429-A (capa A) o 429-B (Nginx).
pedir() { # $1 IP, $2 ruta, $3 cuerpo JSON
    local c; c=$(curl -s -o "$T/cuerpo" -w '%{http_code}' --interface "$1" -H 'Content-Type: application/json' --data "$3" "http://127.0.0.1$2")
    if [[ $c == 429 ]]; then grep -q 'desde tu conexión' "$T/cuerpo" && echo 429-B || echo 429-A; else echo "$c"; fi
}
secuencia() { tr '\n' ' ' | python3 -I -c "
import sys, itertools
v = sys.stdin.read().split()
print(' '.join(f'{k}×{len(list(g))}' for k, g in itertools.groupby(v)))"; }
login() { pedir "$1" /api/auth/login "{\"username\":\"$2\",\"password\":\"incorrecta\"}"; }
# Un 400 solo vale si es el error del código (un 400 de validación no llega al limitador).
registro() {
    local r; r=$(pedir "$1" /api/auth/registro "{\"nombres\":\"A\",\"apellidos\":\"B\",\"username\":\"registro.$2\",\"email\":\"registro.$2@ejemplo.test\",\"password\":\"ClaveSegura2026\",\"codigoEmpresa\":\"QQQQ-QQQQ-QQQQ-QQQQ\"}")
    [[ $r == 400 ]] && ! grep -q '"codigoEmpresa"' "$T/cuerpo" && r=400-validacion
    echo "$r"
}

echo "== Una IP, un usuario, contraseña incorrecta (fuerza bruta clásica)"
s=$(for i in $(seq 1 24); do login 127.0.0.3 "objetivo.uno"; done | secuencia)
# Cuántos 429-A caben antes del 429-B depende del tiempo (Nginx repone 1 cada 3 s): se comprueba el orden.
comprobar "Responde primero la capa A (por usuario) y después Nginx (por IP)" $([[ $s =~ ^401×10\ 429-A×[0-9]+\ 429-B×[0-9]+$ ]]; echo $?) "$s"
echo "== Una IP, muchos usuarios (pocas contraseñas en muchas cuentas)"
# 40 intentos: cada 401 cuesta un BCrypt y Nginx repone 1 cupo cada 3 s, así que pasan 21 o algunos más.
s=$(for i in $(seq 1 40); do login 127.0.0.4 "cuenta.$i"; done | secuencia)
n=$([[ $s =~ ^401×([0-9]+)\ 429-B ]] && echo "${BASH_REMATCH[1]}" || echo 0)
comprobar "La capa A no lo ve (1 intento por cuenta); Nginx lo corta por IP" $([[ $s =~ ^401×[0-9]+\ 429-B×[0-9]+ && $n -ge 21 ]]; echo $?) "$s"
echo "== Muchas IP, un usuario (fuerza bruta distribuida)"
s=$(for i in $(seq 1 12); do login "127.0.1.$i" "objetivo.dos"; done | secuencia)
comprobar "Nginx no lo ve (1 intento por IP); la capa A lo corta por usuario" $([[ $s == "401×10 429-A×2" ]]; echo $?) "$s"
echo "== Registro: una IP, un mismo código inválido"
s=$(for i in $(seq 1 14); do registro 127.0.0.5 "$i"; done | secuencia)
comprobar "Nginx (ráfaga 10) actúa antes que la capa A por código (20)" $([[ $s == "400×11 429-B×3" ]]; echo $?) "$s"
echo "== Registro: muchas IP, el mismo código"
s=$(for i in $(seq 1 12); do registro "127.0.2.$i" "x$i"; done | secuencia)
comprobar "Con IP distintas, la capa A por código sigue contando (los 11 anteriores + 9 = 20)" $([[ $s == "400×9 429-A×3" ]]; echo $?) "$s"

echo; echo "RESUMEN: $OK superadas, $FALLOS fallidas"
[[ $FALLOS == 0 ]]
