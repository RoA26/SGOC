#!/usr/bin/env bash
# Despliegue de Bastión en un VPS compartido (Nexora, Eden u otros servicios).
#
#   DOMINIO=midominio.duckdns.org bash deploy/scripts/bastion-vps.sh <orden>
#
# Órdenes, en el orden habitual:
#   diagnostico   Solo lectura: memoria, disco, Docker, puertos, proxy en 80/443, DNS.
#   preparar      Crea .env con secretos aleatorios (si no existe) y elige un puerto libre.
#   desplegar     Construye y arranca el proyecto Compose "bastion"; espera a que esté sano.
#   admin         Crea el primer SUPER_ADMIN (pide correo, nombre y contraseña).
#   proxy         Genera la configuración del proxy del servidor según lo detectado.
#                 Con --aplicar, y solo si el proxy es Nginx en el host, la instala como
#                 archivo NUEVO, valida con "nginx -t" y recarga (nunca reinicia).
#   estado        Contenedores, memoria y salud de Bastión.
#   respaldo      pg_dump de la base de Bastión (deploy/scripts/respaldo.sh).
#
# Garantías: todo comando de Docker usa "-p bastion"; nunca ejecuta "down", "prune" ni toca
# contenedores, redes, volúmenes o archivos de Nginx de otras aplicaciones.
set -euo pipefail

RAIZ="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
cd "$RAIZ"
COMPOSE=(docker compose -p bastion)
ORDEN="${1:-ayuda}"
MEM_MIN_COMPILAR_MB="${MEM_MIN_COMPILAR_MB:-900}"

ok()    { printf '  \033[32m✔\033[0m %s\n' "$*"; }
aviso() { printf '  \033[33m!\033[0m %s\n' "$*"; }
error() { printf '  \033[31m✘\033[0m %s\n' "$*" >&2; }
titulo(){ printf '\n\033[1m%s\033[0m\n' "$*"; }
falla() { error "$*"; exit 1; }

requiere_dominio() {
  [[ -n "${DOMINIO:-}" ]] || falla "Defina DOMINIO, p. ej.: DOMINIO=b4sti0n.duckdns.org bash $0 $ORDEN"
  [[ "$DOMINIO" =~ ^[a-z0-9]([a-z0-9-]*[a-z0-9])?(\.[a-z0-9]([a-z0-9-]*[a-z0-9])?)+$ ]] || falla "DOMINIO no válido: $DOMINIO"
}

INSTALAR_DOCKER="sudo apt update && sudo apt install -y docker.io docker-compose-v2"
hay_docker() { command -v docker >/dev/null && docker info >/dev/null 2>&1 && docker compose version >/dev/null 2>&1; }
requiere_docker() {
  command -v docker >/dev/null || falla "Docker no está instalado. En Ubuntu: $INSTALAR_DOCKER"
  docker info >/dev/null 2>&1 || falla "Sin acceso al demonio de Docker (¿está iniciado? ¿usa sudo o el grupo docker?)"
  docker compose version >/dev/null 2>&1 || falla "Falta el plugin Compose. En Ubuntu: sudo apt install -y docker-compose-v2"
}

puerto_env() { grep -E '^BASTION_HTTP_PORT=' .env 2>/dev/null | cut -d= -f2 || true; }
puerto_libre() { local o; o="$(ss -ltnH "sport = :$1" 2>/dev/null)"; [[ -z "$o" ]]; }
mem_disponible_mb() { awk '/MemAvailable/ {print int($2/1024)}' /proc/meminfo; }

# Qué proceso escucha en 80/443: nginx, caddy, docker-proxy (un contenedor), otro o nada.
proxy_detectado() {
  local salida
  salida="$(ss -ltnpH '( sport = :80 or sport = :443 )' 2>/dev/null || true)"
  if [[ -z "$salida" ]]; then echo ninguno
  elif grep -q '"nginx"' <<<"$salida"; then echo nginx
  elif grep -q '"caddy"' <<<"$salida"; then echo caddy
  elif grep -q '"docker-proxy"' <<<"$salida"; then echo docker
  elif grep -q 'users:' <<<"$salida"; then echo otro
  else echo desconocido   # sin permisos para ver el proceso: ejecute con sudo
  fi
}

diagnostico() {
  titulo "Sistema"
  echo "  $(uname -sr) · $(nproc) CPU"
  free -m | sed 's/^/  /'
  local mem; mem="$(mem_disponible_mb)"
  if (( mem >= 1200 )); then ok "Memoria disponible: ${mem} MB"
  elif (( mem >= 700 )); then aviso "Memoria disponible: ${mem} MB (justa; Bastión usa ~110 MB en reposo y hasta ~700 MB con sus límites)"
  else error "Memoria disponible: ${mem} MB: no se recomienda desplegar sin liberar memoria"; fi
  df -h / | sed 's/^/  /'

  titulo "Docker"
  local publicados=""
  if hay_docker; then
    docker version --format '  Docker {{.Server.Version}}'
    docker compose version | sed 's/^/  /'
    echo "  Proyectos Compose:"; docker compose ls --all | sed 's/^/    /'
    echo "  Contenedores:"; docker ps --format '    {{.Names}}\t{{.Ports}}\t{{.Status}}'
    docker stats --no-stream --format '    {{.Name}}\t{{.MemUsage}}' 2>/dev/null || true
    local proyectos; proyectos="$(docker compose ls --all --format json)"
    if grep -q '"Name":"bastion"' <<<"$proyectos"; then aviso "Ya existe un proyecto Compose 'bastion' (se actualizará en sitio)"; else ok "Nombre de proyecto 'bastion' libre"; fi
    publicados="$(docker ps --format '{{.Names}} {{.Ports}}')"
  else
    error "Docker (con el plugin Compose) no está instalado o no está activo. Instálelo con:"
    echo "      $INSTALAR_DOCKER"
  fi

  titulo "Puertos"
  local p; p="$(puerto_env)"; p="${p:-8090}"
  if puerto_libre "$p" || grep -q "^bastion-frontend.*127.0.0.1:$p->" <<<"$publicados"; then ok "Puerto 127.0.0.1:$p disponible para Bastión"
  else aviso "El puerto $p está ocupado: 'preparar' elegirá otro"; fi
  local prx; prx="$(proxy_detectado)"
  ss -ltnpH '( sport = :80 or sport = :443 )' 2>/dev/null | sed 's/^/  /' || true
  case "$prx" in
    nginx) ok "Proxy del servidor: Nginx en el host" ;;
    caddy) ok "Proxy del servidor: Caddy en el host" ;;
    docker) aviso "80/443 los publica un contenedor: $(docker ps --filter publish=80 --format '{{.Names}}') $(docker ps --filter publish=443 --format '{{.Names}}')" ;;
    ninguno) aviso "Nada escucha en 80/443" ;;
    *) aviso "No se pudo identificar el proceso en 80/443 (ejecute el diagnóstico con sudo)" ;;
  esac

  titulo "DNS"
  if [[ -n "${DOMINIO:-}" ]]; then
    local ip_dns ip_publica
    ip_dns="$(getent ahostsv4 "$DOMINIO" 2>/dev/null | awk 'NR==1{print $1}' || true)"
    ip_publica="$(curl -fsS --max-time 8 https://api.ipify.org 2>/dev/null || true)"
    echo "  $DOMINIO → ${ip_dns:-(no resuelve)} · IP pública del VPS: ${ip_publica:-(desconocida)}"
    if [[ -n "$ip_dns" && "$ip_dns" == "$ip_publica" ]]; then ok "El dominio apunta a este servidor"
    else aviso "El dominio NO apunta a este servidor todavía: actualícelo en duckdns.org"; fi
  else
    aviso "Defina DOMINIO para comprobar el DNS"
  fi
}

preparar() {
  command -v python3 >/dev/null || command -v openssl >/dev/null || falla "Se necesita python3 u openssl para generar secretos"
  if [[ -f .env ]]; then ok ".env ya existe: no se modifica"; chmod 600 .env; return; fi
  local p=8090
  while ! puerto_libre "$p"; do p=$((p + 1)); (( p <= 8199 )) || falla "No hay puertos libres entre 8090 y 8199"; done
  secreto() { python3 -c "import secrets; print(secrets.token_urlsafe($1))" 2>/dev/null || openssl rand -base64 "$1" | tr -d '/+=\n'; }
  umask 077
  cat > .env <<EOF
BASTION_DB_PASSWORD=$(secreto 24)
BASTION_JWT_SECRET=$(secreto 48)
BASTION_JWT_EXPIRE_MINUTES=30
BASTION_HTTP_PORT=$p
BASTION_VERSION=latest
EOF
  ok ".env creado (600) con secretos aleatorios; puerto local 127.0.0.1:$p"
}

esperar_sano() {
  local estado
  for _ in $(seq 1 60); do
    estado="$("${COMPOSE[@]}" ps --format '{{.Service}}={{.Health}}' | tr '\n' ' ')"
    if [[ "$estado" == *"backend=healthy"* && "$estado" == *"frontend=healthy"* && "$estado" == *"postgres=healthy"* ]]; then
      ok "Servicios sanos: $estado"; return 0
    fi
    sleep 3
  done
  error "Los servicios no quedaron sanos: $estado"
  "${COMPOSE[@]}" logs --tail 40 backend >&2 || true
  return 1
}

desplegar() {
  requiere_docker
  [[ -f .env ]] || falla "Falta .env: ejecute primero 'preparar'"
  if [[ "${SIN_CONSTRUIR:-no}" != "si" ]]; then
    local mem; mem="$(mem_disponible_mb)"
    if (( mem < MEM_MIN_COMPILAR_MB )) && [[ "${FORZAR:-no}" != "si" ]]; then
      falla "Solo hay ${mem} MB disponibles y compilar el frontend puede necesitar ~${MEM_MIN_COMPILAR_MB} MB.
    Opciones: compilar en otra máquina y cargar las imágenes (docs/despliegue.md) y usar SIN_CONSTRUIR=si,
    o asumir el riesgo con FORZAR=si."
    fi
    titulo "Construyendo imágenes bastion/backend y bastion/frontend"
    "${COMPOSE[@]}" build
  fi
  titulo "Arrancando (solo el proyecto bastion)"
  "${COMPOSE[@]}" up -d --no-build
  esperar_sano
  local p; p="$(puerto_env)"
  curl -fsS "http://127.0.0.1:${p:-8090}/api/health" >/dev/null && ok "API respondiendo en 127.0.0.1:${p:-8090}"
  local pagina; pagina="$(curl -fsS "http://127.0.0.1:${p:-8090}/")"
  if grep -q '<div id="root">' <<<"$pagina"; then ok "Frontend respondiendo"; else falla "El frontend no devolvió la página"; fi
}

admin() {
  requiere_docker
  local estado_backend; estado_backend="$("${COMPOSE[@]}" ps --format '{{.Service}}={{.Health}}' 2>/dev/null)"
  [[ "$estado_backend" == *"backend=healthy"* ]] || falla "Bastión no está en marcha: ejecute primero 'desplegar'"
  read -r -p "Correo del SUPER_ADMIN: " correo
  read -r -p "Nombre completo: " nombre
  "${COMPOSE[@]}" exec backend python -m app.bootstrap --email "$correo" --name "$nombre"
}

proxy() {
  requiere_dominio
  local p tipo; p="$(puerto_env)"; p="${p:-8090}"; tipo="$(proxy_detectado)"
  local generado="deploy/nginx/generado/${DOMINIO}.conf"
  case "$tipo" in
    nginx)
      mkdir -p deploy/nginx/generado
      sed -e "s/__DOMINIO__/${DOMINIO}/g" -e "s/__PUERTO__/${p}/g" deploy/nginx/bastion-host.conf.template > "$generado"
      ok "Configuración generada: $generado"
      local destino=/etc/nginx/sites-available/bastion-"$DOMINIO".conf
      [[ -d /etc/nginx/sites-available ]] || destino=/etc/nginx/conf.d/bastion-"$DOMINIO".conf
      if [[ "${2:-}" != "--aplicar" ]]; then
        echo "  Para instalarla (archivo nuevo; no modifica otros sitios):"
        echo "    sudo DOMINIO=$DOMINIO bash $0 proxy --aplicar"
        return
      fi
      [[ $EUID -eq 0 ]] || falla "--aplicar requiere sudo"
      local nombres; nombres="$(nginx -T 2>/dev/null | grep -E '^\s*server_name\s' || true)"
      if grep -qw -- "$DOMINIO" <<<"$nombres"; then
        falla "Nginx ya tiene un server_name $DOMINIO: no se toca nada. Revíselo a mano."
      fi
      [[ ! -e "$destino" ]] || falla "$destino ya existe: no se sobrescribe"
      install -m 644 "$generado" "$destino"
      if [[ "$destino" == /etc/nginx/sites-available/* ]]; then ln -s "$destino" /etc/nginx/sites-enabled/; fi
      if nginx -t; then
        systemctl reload nginx 2>/dev/null || nginx -s reload
        ok "Nginx recargado con el sitio $DOMINIO (HTTP)"
        local salud="" _
        for _ in 1 2 3 4 5; do  # la recarga es asíncrona
          sleep 1
          salud="$(curl -fsS -H "Host: $DOMINIO" http://127.0.0.1/api/health 2>/dev/null || true)"
          [[ "$salud" == *'"ok"'* ]] && break
        done
        if [[ "$salud" == *'"ok"'* ]]; then ok "Bastión responde a través de Nginx"; else aviso "Nginx aún no enruta $DOMINIO hacia Bastión: revise 'nginx -T'"; fi
        echo "  Siguiente paso, certificado HTTPS solo para este dominio:"
        echo "    sudo certbot --nginx -d $DOMINIO --redirect"
      else
        rm -f "/etc/nginx/sites-enabled/$(basename "$destino")" "$destino"
        falla "nginx -t falló: se retiró el archivo nuevo y no se recargó nada"
      fi
      ;;
    caddy|ninguno)
      if [[ "${2:-}" != "--aplicar" ]]; then
        [[ "$tipo" == ninguno ]] && aviso "Nada escucha en 80/443: se usará Caddy (HTTPS automático con Let's Encrypt)."
        echo "  Bloque que se añadirá a /etc/caddy/Caddyfile:"
        bloque_caddy "$p" | sed 's/^/    /'
        echo "  Para instalar Caddy (si falta) y aplicarlo:"
        echo "    sudo DOMINIO=$DOMINIO bash $0 proxy --aplicar"
        return
      fi
      aplicar_caddy "$tipo" "$p"
      ;;
    docker)
      error "Los puertos 80/443 los ocupa un contenedor: $(docker ps --filter publish=80 --format '{{.Names}}') $(docker ps --filter publish=443 --format '{{.Names}}')"
      echo "  Dos sitios no pueden compartir 80/443 sin un proxy común. Opciones (requieren autorización,"
      echo "  porque implican mover el otro servicio detrás del proxy):"
      echo "   1. Instalar Nginx o Caddy en el host para ambos dominios y publicar el otro servicio en 127.0.0.1."
      echo "   2. Mientras tanto, probar Bastión por túnel SSH: ssh -L $p:127.0.0.1:$p usuario@vps → http://localhost:$p"
      exit 1
      ;;
    *)
      falla "No se pudo identificar el proxy. Ejecute: sudo DOMINIO=$DOMINIO bash $0 diagnostico"
      ;;
  esac
}

bloque_caddy() {
  printf '# Bastión (generado por bastion-vps.sh)\n%s {\n\tencode gzip\n\treverse_proxy 127.0.0.1:%s\n}\n' "$DOMINIO" "$1"
}

# Instala Caddy si falta y añade el sitio de Bastión. Si el Caddyfile es el de ejemplo del paquete
# (sirve /usr/share/caddy en :80), se guarda una copia y se reemplaza; si tiene otros sitios, se
# añade el bloque al final sin tocarlos.
aplicar_caddy() {
  local tipo="$1" p="$2" cf=/etc/caddy/Caddyfile
  [[ $EUID -eq 0 ]] || falla "--aplicar requiere sudo"
  if ! command -v caddy >/dev/null; then
    [[ "$tipo" == ninguno ]] || falla "Algo escucha en 80/443 pero no se encontró el binario de Caddy"
    titulo "Instalando Caddy"
    apt-get update -q && apt-get install -y -q caddy
  fi
  [[ -f "$cf" ]] || touch "$cf"
  if grep -qw -- "$DOMINIO" "$cf"; then falla "$cf ya menciona $DOMINIO: no se toca nada. Revíselo a mano."; fi
  local copia; copia="$cf.antes-de-bastion.$(date +%Y%m%d%H%M%S)"
  cp -p "$cf" "$copia"
  local sitios; sitios="$(grep -vE '^\s*(#|$)' "$cf" || true)"
  if [[ -z "$sitios" ]] || { grep -q 'root \* /usr/share/caddy' <<<"$sitios" && [[ "$(grep -c '{' <<<"$sitios")" -le 1 ]]; }; then
    bloque_caddy "$p" > "$cf"          # Caddyfile vacío o de ejemplo
  else
    printf '\n' >> "$cf"; bloque_caddy "$p" >> "$cf"
  fi
  if ! caddy validate --config "$cf" --adapter caddyfile >/dev/null 2>&1; then
    cp -p "$copia" "$cf"
    falla "La configuración no es válida: se restauró el Caddyfile anterior ($copia)"
  fi
  ok "Caddyfile actualizado (copia previa: $copia)"
  systemctl enable --now caddy >/dev/null 2>&1 || true
  systemctl reload caddy || falla "No se pudo recargar Caddy: revise 'journalctl -u caddy'"
  ok "Caddy recargado. Pedirá el certificado HTTPS en cuanto el dominio apunte a este servidor."
  echo "  Compruebe en 1-2 minutos: curl -I https://$DOMINIO"
}

estado() {
  requiere_docker
  "${COMPOSE[@]}" ps
  docker stats --no-stream --format '{{.Name}}\t{{.MemUsage}}\t{{.CPUPerc}}' | grep '^bastion-' || true
  local p; p="$(puerto_env)"
  curl -fsS "http://127.0.0.1:${p:-8090}/api/health" && echo
}

case "$ORDEN" in
  diagnostico) diagnostico ;;
  preparar) preparar ;;
  desplegar) desplegar ;;
  admin) admin ;;
  proxy) proxy "$@" ;;
  estado) estado ;;
  respaldo) bash deploy/scripts/respaldo.sh "${2:-./backups}" ;;
  *) sed -n '2,20p' "$0" | sed 's/^# \{0,1\}//' ;;
esac
