#!/usr/bin/env bash
# =============================================================================
# Pruebas locales de la propuesta de capa B (límites por IP en Nginx).
#
# Solo usa la máquina local: Nginx (binario del sistema), loopback 127.0.0.0/8 y un directorio
# temporal. No usa Docker, ni datos, ni la red. Nunca apunta a producción.
#   bash deploy/propuesta-capa-b/pruebas/ejecutar.sh
# Requisitos: nginx (con limit_req y realip), envsubst (gettext-base), curl, python3.
#
# Representación local (no es el VPS):
#   - clientes: conexiones reales desde 127.0.0.3, .4, .5... (curl --interface);
#   - «proxy TLS del host»: un server de Nginx en 127.0.0.1:18443 que reenvía desde 127.0.0.2
#     añadiendo X-Forwarded-For (como un proxy inverso típico);
#   - backend: un server de Nginx en 127.0.0.1:18081 que responde 401 y devuelve en el cuerpo las
#     cabeceras X-Forwarded-For y X-Real-IP que le llegan.
# =============================================================================
set -uo pipefail

AQUI=$(cd "$(dirname "$0")" && pwd)
PLANTILLA="$AQUI/../default.conf.template"
T=$(mktemp -d "${TMPDIR:-/tmp}/capa-b-XXXXXX")
chmod 755 "$T"
OK=0
FALLOS=0
VARIABLES='${NGINX_BACKEND} ${NGINX_PROXY_CONFIANZA} ${NGINX_LIMITE_LOGIN} ${NGINX_RAFAGA_LOGIN} ${NGINX_LIMITE_REGISTRO} ${NGINX_RAFAGA_REGISTRO} ${NGINX_RETRY_AFTER} ${NGINX_LIMITE_MODO_PRUEBA}'

comprobar() { # $1 descripción, $2 condición (0 = cierta), [$3 detalle]
    if [[ $2 == 0 ]]; then OK=$((OK + 1)); echo "OK    $1${3:+ — $3}"
    else FALLOS=$((FALLOS + 1)); echo "FALLO $1${3:+ — $3}"; fi
}
parar() { [[ -f $T/nginx.pid ]] && nginx -p "$T" -c "$T/nginx.conf" -s quit 2>/dev/null; sleep 0.5; rm -f "$T/nginx.pid"; }
trap 'parar; rm -rf "$T"' EXIT

# Estáticos mínimos (o el build real del frontend, si existe).
mkdir -p "$T/html/assets"
if [[ -f $AQUI/../../../frontend/dist/index.html ]]; then cp -r "$AQUI/../../../frontend/dist/." "$T/html/"
else echo '<!doctype html><title>Nexora</title>' > "$T/html/index.html"; fi
echo 'console.log(1)' > "$T/html/assets/prueba-abc123.js"
chmod -R a+rX "$T/html"

# Renderiza la plantilla como el entrypoint de la imagen oficial: solo las variables definidas.
renderizar() { # $1 topología: directa | proxy | sin-variable
    export NGINX_BACKEND=http://127.0.0.1:18081
    export NGINX_LIMITE_LOGIN=${LOGIN_RATE:-10r/m} NGINX_RAFAGA_LOGIN=${LOGIN_RAFAGA:-5}
    export NGINX_LIMITE_REGISTRO=${REGISTRO_RATE:-10r/m} NGINX_RAFAGA_REGISTRO=${REGISTRO_RAFAGA:-3}
    export NGINX_RETRY_AFTER=60 NGINX_LIMITE_MODO_PRUEBA=${MODO_PRUEBA:-off}
    case $1 in
        directa) export NGINX_PROXY_CONFIANZA=127.0.0.1 ;;
        proxy) export NGINX_PROXY_CONFIANZA=127.0.0.2 ;;
        sin-variable) unset NGINX_PROXY_CONFIANZA ;;
    esac
    local definidas="" v
    for v in $VARIABLES; do v=${v#\$\{}; v=${v%\}}; [[ -v $v ]] && definidas+="\${$v} "; done
    envsubst "$definidas" < "$PLANTILLA" > "$T/default.conf"
    cat > "$T/nginx.conf" <<EOF
worker_processes 1;
pid $T/nginx.pid;
error_log $T/error.log warn;
events { worker_connections 256; }
http {
    include /etc/nginx/mime.types;
    default_type application/octet-stream;
    log_format clientes '\$remote_addr par=\$realip_remote_addr "\$request" \$status';
    access_log $T/access.log clientes;
    client_body_temp_path $T/tmp_body; proxy_temp_path $T/tmp_proxy;
    fastcgi_temp_path $T/tmp_fcgi; uwsgi_temp_path $T/tmp_uwsgi; scgi_temp_path $T/tmp_scgi;

    include $T/default.conf;

    # Backend simulado: 401 y las cabeceras de IP que recibe.
    server {
        listen 127.0.0.1:18081;
        location / {
            default_type application/json;
            return 401 '{"xff":"\$http_x_forwarded_for","xrealip":"\$http_x_real_ip"}';
        }
    }
    # «Proxy TLS del host» simulado: reenvía desde 127.0.0.2 y añade X-Forwarded-For.
    server {
        listen 127.0.0.1:18443;
        location / {
            proxy_pass http://127.0.0.1:80;
            proxy_bind 127.0.0.2;
            proxy_set_header Host \$host;
            proxy_set_header X-Forwarded-For \$proxy_add_x_forwarded_for;
            proxy_set_header X-Forwarded-Proto https;
        }
    }
}
EOF
    # La plantilla publica en el puerto 80 y usa la raíz de la imagen: aquí, el directorio temporal.
    sed -i "s#root /usr/share/nginx/html;#root $T/html;#" "$T/default.conf"
}
arrancar() { : > "$T/access.log"; : > "$T/error.log"; nginx -p "$T" -c "$T/nginx.conf" && sleep 0.5; }
peticion() { # $1 IP de origen, $2 método, $3 URL, [resto: argumentos de curl] → "estado"
    local ip=$1 metodo=$2 url=$3; shift 3
    curl -s -o "$T/cuerpo" -D "$T/cabeceras" -w '%{http_code}' --interface "$ip" -X "$metodo" \
        -H 'Content-Type: application/json' --data '{"username":"ana","password":"x"}' "$@" "$url"
}
login() { peticion "$1" POST "http://127.0.0.1/api/auth/login" "${@:2}"; }
cabecera() { grep -i "^$1:" "$T/cabeceras" | head -1 | cut -d' ' -f2- | tr -d '\r'; }
hasta_429() { # $1 IP, $2 URL, [resto] → nº de respuestas antes del primer 429 (máx. 40)
    local i c
    for ((i = 0; i < 40; i++)); do c=$(peticion "$1" POST "$2" "${@:3}"); [[ $c == 429 ]] && { echo "$i"; return; }; done
    echo "sin-429"
}

echo "== Sintaxis y arranque"
renderizar sin-variable
nginx -t -p "$T" -c "$T/nginx.conf" >"$T/t.log" 2>&1; r=$?
comprobar "Sin NGINX_PROXY_CONFIANZA, Nginx se niega a arrancar (falla seguro)" $((r == 0)) "$(grep -o 'invalid.*' "$T/t.log" | head -1)"
for topo in directa proxy; do
    renderizar $topo
    nginx -t -p "$T" -c "$T/nginx.conf" >"$T/t.log" 2>&1
    comprobar "nginx -t con la topología «$topo»" $? "$(tail -1 "$T/t.log")"
done

echo "== Topología directa (Nginx recibe a los clientes; NGINX_PROXY_CONFIANZA=127.0.0.1)"
renderizar directa; arrancar
c=$(peticion 127.0.0.3 GET http://127.0.0.1/); comprobar "GET / (SPA) → 200 con cabeceras de seguridad" $([[ $c == 200 && -n $(cabecera X-Frame-Options) ]]; echo $?) "$c"
c=$(peticion 127.0.0.3 GET http://127.0.0.1/inventario); comprobar "Ruta de la SPA → 200" $([[ $c == 200 ]]; echo $?) "$c"
c=$(peticion 127.0.0.3 GET http://127.0.0.1/healthz); comprobar "/healthz → 200" $([[ $c == 200 && $(cat "$T/cuerpo") == ok ]]; echo $?) "$c"
c=$(peticion 127.0.0.3 GET http://127.0.0.1/assets/prueba-abc123.js); comprobar "/assets → 200 con caché larga" $([[ $c == 200 && -n $(cabecera Expires) ]]; echo $?) "$c"

n=$(hasta_429 127.0.0.3 http://127.0.0.1/api/auth/login)
comprobar "Login: 1 + ráfaga (5) peticiones llegan al backend y la siguiente → 429" $([[ $n == 6 ]]; echo $?) "$n antes del 429"
c=$(login 127.0.0.3)
comprobar "429: Problem Details, Retry-After y cabeceras de seguridad" $(python3 -I -c "
import json,sys; d=json.load(open('$T/cuerpo'))
sys.exit(0 if d['status']==429 and d['title']=='Demasiados intentos' and d['reintentarEnSegundos']==60 else 1)" 2>/dev/null && [[ $c == 429 && $(cabecera Retry-After) == 60 && $(cabecera Content-Type) == application/problem+json && -n $(cabecera X-Content-Type-Options) ]]; echo $?) "Retry-After $(cabecera Retry-After)"
c=$(login 127.0.0.4); comprobar "Otro cliente (otra IP) no se ve afectado" $([[ $c == 401 ]]; echo $?) "$c"
c=$(login 127.0.0.3 -H 'X-Forwarded-For: 10.9.8.7' -H 'X-Real-IP: 10.9.8.7')
comprobar "Cabeceras de IP falsas no esquivan el límite" $([[ $c == 429 ]]; echo $?) "$c"
c=$(peticion 127.0.0.3 GET http://127.0.0.1/api/v1/productos); comprobar "Resto de la API sin límite (aunque el login esté agotado)" $([[ $c == 401 ]]; echo $?) "$c"
c=$(peticion 127.0.0.3 POST http://127.0.0.1/api/auth/registro); comprobar "El registro tiene su propia zona" $([[ $c == 401 ]]; echo $?) "$c"
n=$(hasta_429 127.0.0.5 'http://127.0.0.1/api/auth//login' --path-as-is)
comprobar "Variantes de ruta comparten límite (//login)" $([[ $n == 6 ]]; echo $?) "$n antes del 429"
for v in '/api/auth/%6Cogin' '/api/auth/login/' '/api/auth/login;x' '/api/auth/./login'; do
    c=$(peticion 127.0.0.5 POST "http://127.0.0.1$v" --path-as-is); comprobar "Variante $v también limitada" $([[ $c == 429 ]]; echo $?) "$c"
done
n=$(hasta_429 127.0.0.6 http://127.0.0.1/api/auth/registro)
comprobar "Registro: 1 + ráfaga (3) y la siguiente → 429" $([[ $n == 4 ]]; echo $?) "$n antes del 429"
c=$(login 127.0.0.8 -H 'X-Forwarded-For: 6.6.6.6, 1.2.3.4')
comprobar "El backend recibe X-Forwarded-For = IP real, no la cadena del cliente" $([[ $(python3 -I -c "import json;print(json.load(open('$T/cuerpo'))['xff'])") == 127.0.0.8 ]]; echo $?) "$(cat "$T/cuerpo")"
sleep 7
c=$(login 127.0.0.3); comprobar "Recuperación: con 10r/m, a los 6 s vuelve a pasar una petición" $([[ $c == 401 ]]; echo $?) "$c"
c=$(login 127.0.0.3); comprobar "… y la siguiente inmediata vuelve a ser 429" $([[ $c == 429 ]]; echo $?) "$c"
parar

echo "== Modo de prueba (limit_req_dry_run on)"
MODO_PRUEBA=on renderizar directa; arrancar
n=$(hasta_429 127.0.0.3 http://127.0.0.1/api/auth/login)
comprobar "En modo de prueba no se rechaza nada…" $([[ $n == sin-429 ]]; echo $?) "$n"
comprobar "… pero se registra lo que se habría rechazado" $(grep -q 'limiting requests, dry run' "$T/error.log"; echo $?)
parar

echo "== Umbrales propuestos para producción (20r/m, ráfaga 20) frente a la verificación del despliegue"
LOGIN_RATE=20r/m LOGIN_RAFAGA=20 renderizar directa; arrancar
cuenta=0; for i in $(seq 1 14); do [[ $(login 127.0.0.3) == 429 ]] && cuenta=$((cuenta + 1)); done
comprobar "14 logins seguidos desde una misma IP (los de sgp-aatenea.sh) → ningún 429" $((cuenta)) "$cuenta rechazados"
n=$(hasta_429 127.0.0.4 http://127.0.0.1/api/auth/login)
comprobar "Con esos umbrales, el 22.º intento seguido de una IP → 429" $([[ $n == 21 ]]; echo $?) "$n antes del 429"
parar

echo "== Topología con proxy previo (NGINX_PROXY_CONFIANZA=127.0.0.2, el «proxy» simulado)"
renderizar proxy; arrancar
PROXY=http://127.0.0.1:18443/api/auth/login
n=$(hasta_429 127.0.0.3 "$PROXY")
comprobar "Cliente A a través del proxy: 6 y luego 429" $([[ $n == 6 ]]; echo $?) "$n antes del 429"
c=$(peticion 127.0.0.4 POST "$PROXY"); comprobar "Cliente B a través del mismo proxy: no comparte el límite de A" $([[ $c == 401 ]]; echo $?) "$c"
c=$(peticion 127.0.0.3 POST "$PROXY" -H 'X-Forwarded-For: 127.0.0.9')
comprobar "A falsificando X-Forwarded-For a través del proxy sigue limitado" $([[ $c == 429 ]]; echo $?) "$c"
grep -q '^127.0.0.3 par=127.0.0.2 ' "$T/access.log"
comprobar "Nginx ve al cliente real (127.0.0.3) y al proxy como par (127.0.0.2)" $? "$(grep -m1 '^127.0.0.3 par=127.0.0.2 ' "$T/access.log")"
n=$(hasta_429 127.0.0.7 http://127.0.0.1/api/auth/login -H 'X-Forwarded-For: 127.0.0.4')
comprobar "Acceso directo (saltándose el proxy) fingiendo ser B: se le limita por su propia IP" $([[ $n == 6 ]]; echo $?) "$n antes del 429"
c=$(peticion 127.0.0.4 POST "$PROXY"); comprobar "… y B no ha perdido cupo por ello" $([[ $c == 401 ]]; echo $?) "$c"
grep -q '^127.0.0.7 par=127.0.0.7 ' "$T/access.log"
comprobar "Sin pasar por el proxy no se cree X-Forwarded-For" $? "$(grep -m1 '^127.0.0.7 ' "$T/access.log")"
c=$(peticion 127.0.0.6 POST "$PROXY" -H 'X-Forwarded-For: 6.6.6.6')
comprobar "El backend recibe solo la IP real del cliente" $([[ $(python3 -I -c "import json;print(json.load(open('$T/cuerpo'))['xff'])") == 127.0.0.6 ]]; echo $?) "$(cat "$T/cuerpo")"
parar

echo "== Aproximación local de HTTP_PORT=127.0.0.1:… (publicar Nginx solo en loopback)"
cat > "$T/solo-loopback.conf" <<EOF
worker_processes 1; pid $T/nginx.pid; error_log $T/error.log warn; events {}
http { access_log off; server { listen 127.0.0.1:18090; return 200 "ok"; } }
EOF
nginx -p "$T" -c "$T/solo-loopback.conf" && sleep 0.5
IP_EXTERNA=$(hostname -I 2>/dev/null | awk '{print $1}')
c=$(curl -s -o /dev/null -w '%{http_code}' -m 3 http://127.0.0.1:18090/)
comprobar "Escuchando solo en 127.0.0.1: accesible desde la propia máquina" $([[ $c == 200 ]]; echo $?) "$c"
if [[ -n $IP_EXTERNA ]]; then
    c=$(curl -s -o /dev/null -w '%{http_code}' -m 3 "http://$IP_EXTERNA:18090/")
    comprobar "… e inaccesible por la interfaz de red ($IP_EXTERNA)" $([[ $c == 000 ]]; echo $?) "$c"
fi
nginx -p "$T" -c "$T/solo-loopback.conf" -s quit 2>/dev/null; sleep 0.5; rm -f "$T/nginx.pid"

echo
echo "RESUMEN: $OK superadas, $FALLOS fallidas ($(nginx -v 2>&1))"
[[ $FALLOS == 0 ]]
