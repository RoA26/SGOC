#!/usr/bin/env bash
# =============================================================================
# SGP en Aatenea: despliegue por fases con puntos de control y seed demo opcional.
# Procedimiento completo, recuperación, restauración y vuelta atrás: deploy/aatenea/README.md
#
#   OBJETIVO=<sha> bash sgp-aatenea.sh fase1   # estado, respaldo verificado (restauración de prueba aislada)
#                                              # y punto de retorno; no cambia código ni contenedores
#   bash sgp-aatenea.sh fase2                  # avanza el código (solo fast-forward) y despliega
#   bash sgp-aatenea.sh clave-demo             # pide (sin eco) la contraseña COMÚN y pública de las cuentas demo
#   CONFIRMAR_SEED=si bash sgp-aatenea.sh fase3   # seed demo temporal y vuelta al perfil normal
#   bash sgp-aatenea.sh fase4                  # archivo privado de cuentas y pruebas por HTTP
#   bash sgp-aatenea.sh estado                 # solo lectura: operaciones incompletas, empresas demo y conteos
#   bash sgp-aatenea.sh recuperar              # tras una interrupción: vuelve al perfil normal sin borrar nada
#   bash sgp-aatenea.sh rollback               # vuelve a las imágenes de la fase 1 (sin tocar la BD)
#   RESPALDO=<ruta .dump> CONFIRMAR_RESTAURACION=<nombre del .dump> bash sgp-aatenea.sh restaurar
#                                              # DESTRUCTIVO: respalda la BD actual y después restaura el volcado
#   bash sgp-aatenea.sh normalizar-probar      # prueba la normalización de los datos de prueba en una copia desechable
#   CONFIRMAR_NORMALIZACION=si [DIRECCION=revertir] bash sgp-aatenea.sh normalizar
#                                              # quita la marca demo y los textos «demo» de las empresas de la seed
#
# Una operación interrumpida (fase2, fase3, rollback, restaurar) queda anotada en un diario que no
# depende de ningún trap; se reanuda repitiendo la misma orden con REANUDAR=1 (README, «Recuperación»).
#
# Ejecútalo desde una copia FUERA del repositorio (la fase 2 cambia los archivos del repositorio):
#   git -C /root/sgp-unisen show <sha>:deploy/aatenea/sgp-aatenea.sh > /root/sgp-private/sgp-aatenea.sh
#
# Nunca imprime contraseñas, tokens ni el contenido de .env (ni "docker compose config"). Lo privado
# va a $PRIVADO (700) con archivos 600. No borra datos, volúmenes ni imágenes: solo elimina sus propias
# restauraciones de prueba (contenedores desechables sin red) y sus respaldos parciales.
# =============================================================================
set -Eeuo pipefail
umask 077
# Solo el número de línea: el texto del comando podría contener expansiones con secretos.
trap 'printf "\n  [ERROR] fallo inesperado en la línea %s; no se continúa.\n" "$LINENO"' ERR

REPO=${REPO:-/root/sgp-unisen}
RAMA=${RAMA:-main}
PRIVADO=${PRIVADO:-/root/sgp-private}
RESPALDOS=${RESPALDOS:-/root/backups}
PROPIETARIO=${PROPIETARIO:-root}           # dueño esperado de respaldos y archivos privados
IMAGENES=${IMAGENES:-unisen/sgp}           # prefijo de las imágenes de docker-compose.yml (<prefijo>-backend…)
ESTADO=$PRIVADO/estado                     # despliegues/, en-curso/, historial/, ejecuciones/
DIARIO=$ESTADO/en-curso                    # una entrada por operación abierta; no la borra ningún trap
HISTORIAL=$ESTADO/historial                # diarios cerrados (nunca se borran)
CLAVE=$PRIVADO/demo_password               # contraseña común de las cuentas demo (600, «clave-demo»); nunca se imprime
# Contraseña demo: la política de SGP (8-72 bytes) y solo caracteres que no hay que escapar en JSON ni YAML.
# Letras enumeradas, no en rango: en una configuración regional UTF-8, [a-z] también admite «ñ» o «á».
FORMATO_CLAVE='^[ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789._-]{8,72}$'
CERROJO=$PRIVADO/sgp-aatenea.lock          # flock: una sola ejecución a la vez
OVERRIDE_HEREDADO=$PRIVADO/sgp-demo.override.yml   # ubicación de la versión anterior del script
ETIQUETA_PRUEBA=sgp.aatenea=restauracion-prueba
MARCA=$(date +%Y%m%d-%H%M%S)-$$            # identifica esta ejecución (y el despliegue que abre la fase 1)
OPERACIONES='fase2 fase3 rollback restauracion'

# Estado de esta ejecución que usa la limpieza de salida (al_salir).
EJEC=                  # directorio propio de esta ejecución
DESP=; ID_DESP=        # despliegue en uso
CONTENEDOR_PRUEBA=; N_PRUEBA=0
COPIA_PRUEBA=          # volcado temporal de «normalizar-probar» (copia de datos reales)
PARCIALES=()           # respaldos .parcial creados por esta ejecución
TMP_HTTP=              # cabeceras con tokens de las pruebas HTTP
PERFIL_DEMO_ACTIVO=0   # 1 mientras el backend puede estar con el perfil demo
REANUDANDO=0
RESPALDO_HECHO=

# ---- Datos de la seed (DatosDemo.java). Cambiar uno exige cambiar el otro. ----
SEED_EMPRESAS="('MVSA01','999100000001','Mercado Verde S.A.S.',true),
  ('SCA002','999100000002','Suministros del Caribe S.A.S.',true),
  ('DNS003','999100000003','Distribuciones Nova S.A.S.',true),
  ('CHS004','999100000004','Comercializadora Horizonte S.A.S.',true),
  ('LAS005','999100000005','Logística Andina S.A.S.',false)"
# username | rol | código | estado esperado tras la seed
SEED_CUENTAS="('carlos.mendoza','ADMINISTRADOR_COMERCIAL','MVSA01','ACTIVO'),
  ('laura.torres','TRABAJADOR','MVSA01','ACTIVO'),
  ('andres.rojas','ADMINISTRADOR_COMERCIAL','SCA002','ACTIVO'),
  ('maria.castro','TRABAJADOR','SCA002','ACTIVO'),
  ('diego.martinez','ADMINISTRADOR_COMERCIAL','DNS003','ACTIVO'),
  ('sofia.ramirez','TRABAJADOR','DNS003','PENDIENTE'),
  ('miguel.ortiz','ADMINISTRADOR_COMERCIAL','CHS004','ACTIVO'),
  ('valentina.gomez','TRABAJADOR','CHS004','RECHAZADO'),
  ('sergio.vega','ADMINISTRADOR_COMERCIAL','LAS005','ACTIVO'),
  ('paula.navarro','TRABAJADOR','LAS005','ACTIVO')"
# código | proveedores | productos | solicitudes | órdenes | recepciones | ventas | devoluciones
CONTEOS_ESPERADOS='CHS004|0|0|0|0|0|0|0
DNS003|0|0|0|0|0|0|0
LAS005|0|0|0|0|0|0|0
MVSA01|3|7|3|3|2|5|1
SCA002|2|4|1|2|1|2|0'

# Mensajes por stderr: la salida estándar de algunas funciones son datos.
ok()    { printf '  [OK]  %s\n' "$*" >&2; }
info()  { printf '  [..]  %s\n' "$*" >&2; }
aviso() { printf '  [!!]  %s\n' "$*" >&2; }
alto()  { printf '\n  [ALTO] %s\n  Nada se ha deshecho automáticamente salvo que se indique arriba.\n' "$*" >&2; exit 1; }
fase()  { printf '\n==== %s ====\n' "$*"; }

# psql dentro del contenedor (sus propias variables POSTGRES_*): SQL por stdin, salida sin adornos.
sql() { docker compose exec -T postgres sh -c 'psql -U "$POSTGRES_USER" -d "$POSTGRES_DB" -v ON_ERROR_STOP=1 -X -q -At -F "|"'; }

# -a: también un contenedor detenido (p. ej. durante una restauración).
id_de() { docker compose ps -a -q "$1"; }

# Solo los NOMBRES de las variables del contenedor backend, nunca sus valores.
nombres_env() { docker inspect -f '{{range .Config.Env}}{{println .}}{{end}}' "$(id_de backend)" | cut -d= -f1 | sort; }
sin_variables_demo() { ! nombres_env | grep -qxE 'SPRING_PROFILES_ACTIVE|DEMO_PASSWORD'; }

esperar_sano() { # $1 = servicio, $2 = segundos máximos
    local limite=$(( $(date +%s) + ${2:-240} )) estado
    while (( $(date +%s) < limite )); do
        estado=$(docker inspect -f '{{.State.Health.Status}}' "$(id_de "$1")" 2>/dev/null || echo desconocido)
        [[ $estado == healthy ]] && return 0
        sleep 5
    done
    return 1
}

readiness() { docker compose exec -T backend wget -qO- http://127.0.0.1:8081/actuator/health/readiness; }

# URL local del frontend (Nginx), p. ej. http://127.0.0.1:80
url_base() { local p; p=$(docker compose port frontend 80 | tail -1); echo "http://127.0.0.1:${p##*:}"; }

FLYWAY_SQL="select version || '|' || success from flyway_schema_history order by installed_rank;"
flyway() { sql <<<"$FLYWAY_SQL"; }

# Backend y frontend con la configuración normal (docker-compose.yml, sin override) y las imágenes :latest.
arrancar_servicios() {
    docker compose up -d --no-deps --no-build backend || return 1
    esperar_sano backend 240 || return 1
    sin_variables_demo || return 1
    docker compose up -d --no-deps --no-build frontend || return 1
    esperar_sano frontend 120
}

# Recrea el backend sin el override demo. Verdadero si queda sano y sin variables demo.
volver_perfil_normal() {
    docker compose up -d --no-deps --no-build backend || return 1
    esperar_sano backend 240 || return 1
    sin_variables_demo
}

# ---- Archivos de estado -----------------------------------------------------
sincronizar() { sync -- "$@" 2>/dev/null || sync; }

escribir() { # $1 archivo, $2 contenido: escritura atómica (temporal en el mismo directorio + rename)
    local tmp; tmp=$(mktemp "$1.XXXXXX")
    printf '%s\n' "$2" > "$tmp"
    sincronizar "$tmp"
    mv -f "$tmp" "$1"
    sincronizar "$(dirname "$1")"
}

fijar() { # Dato inmutable de un despliegue: se escribe una sola vez.
    [[ ! -e $1 ]] || alto "No se sobrescribe $1 (dato inmutable del despliegue)."
    escribir "$1" "$2"
}

# ---- Diario de operaciones --------------------------------------------------
# $DIARIO/<operación> existe desde ANTES del primer paso con efectos hasta que la operación termina o
# se aborta. Es un registro clave=valor de solo anexado (gana la última línea de cada clave), escrito y
# sincronizado antes de cada paso: detecta una interrupción aunque ningún trap llegue a ejecutarse
# (kill -9, corte de luz, OOM). Al cerrarse se mueve a $HISTORIAL; nunca se borra.
pendientes() { local op; for op in $OPERACIONES; do if [[ -e $DIARIO/$op ]]; then echo "$op"; fi; done; }
dato()       { sed -n "s/^$2=//p" "$DIARIO/$1" | tail -1; }
ya_paso()    { grep -qx "paso=$2" "$DIARIO/$1"; }
anotar()     { local op=$1; shift; printf '%s\n' "$@" >> "$DIARIO/$op"; sincronizar "$DIARIO/$op"; }
paso()       { anotar "$1" "paso=$2" "paso_fecha=$(date -Iseconds)"; info "Diario «$1» → $2"; }
mostrar_diario() { sed 's/^/        /' "$DIARIO/$1" >&2; }

iniciar_operacion() { # $1 operación, [$2… clave=valor]. Abre su diario o, con REANUDAR=1, continúa el abierto.
    local op=$1; shift
    if [[ -e $DIARIO/$op ]]; then
        if [[ ${REANUDAR:-} != 1 ]]; then
            mostrar_diario "$op"
            alto "La operación «$op» quedó incompleta (paso $(dato "$op" paso)). Para reanudarla, repite la orden con REANUDAR=1; para abortarla, README, «Recuperación»."
        fi
        [[ $(dato "$op" despliegue) == "$ID_DESP" ]] || alto "El diario de «$op» pertenece al despliegue $(dato "$op" despliegue), no a $ID_DESP."
        REANUDANDO=1
        anotar "$op" "reanudacion=$MARCA"
        info "Reanudando «$op» desde el paso $(dato "$op" paso)"
    else
        REANUDANDO=0
        escribir "$DIARIO/$op" "$(printf '%s\n' "operacion=$op" "despliegue=$ID_DESP" "ejecucion=$MARCA" \
            "registro=$EJEC/registro.log" "inicio=$(date -Iseconds)" "$@" "paso=inicio")"
    fi
}

cerrar_operacion() { # $1 operación, $2 resultado
    local op=$1 destino
    anotar "$op" "fin=$(date -Iseconds)" "resultado=$2"
    destino=$HISTORIAL/$op-$(dato "$op" ejecucion)-cerrada-$MARCA-$2
    mv "$DIARIO/$op" "$destino"
    sincronizar "$HISTORIAL" "$DIARIO"
    info "Diario «$op» cerrado ($2): $destino"
}

# Se detiene si hay una operación incompleta que esta orden no puede reanudar ni abortar ($@ = las que sí).
exigir_sin_pendientes() {
    local op
    for op in $(pendientes); do
        [[ " $* " == *" $op "* ]] && continue
        mostrar_diario "$op"
        alto "La operación «$op» está incompleta (paso $(dato "$op" paso)). Revisa «bash $0 estado» y README, «Recuperación»."
    done
    [[ ! -e $OVERRIDE_HEREDADO ]] || alto "Existe $OVERRIDE_HEREDADO (versión anterior del script): ejecuta «bash $0 recuperar»."
}

# ---- Despliegues --------------------------------------------------------------
# Cada fase 1 crea $ESTADO/despliegues/<marca>/ con datos que no se sobrescriben (commit previo,
# objetivo, imágenes de retorno, Flyway, respaldo). $ESTADO/actual apunta al vigente; los anteriores se
# conservan y se pueden elegir con DESPLIEGUE=<marca>.
cargar_despliegue() {
    local id=${DESPLIEGUE:-}
    if [[ -z $id ]]; then
        [[ -s $ESTADO/actual ]] || alto "No hay despliegue vigente: ejecuta antes la fase 1."
        id=$(<"$ESTADO/actual")
    fi
    [[ $id =~ ^[0-9]{8}-[0-9]{6}-[0-9]+$ ]] || alto "Identificador de despliegue no válido: $id"
    [[ -e $ESTADO/despliegues/$id/fase1.ok ]] || alto "El despliegue $id no existe o no completó la fase 1."
    ID_DESP=$id DESP=$ESTADO/despliegues/$id
    echo "$id" > "$EJEC/despliegue"
    info "Despliegue $ID_DESP · objetivo $(<"$DESP/objetivo") · commit previo $(<"$DESP/commit-previo")"
}

# ---- Restauraciones de prueba y respaldos ---------------------------------------
# Contenedores de restauración de prueba de este script (de cualquier ejecución) que siguen existiendo.
# Solo cuentan los que tienen el nombre exacto que usa el script y no tienen red: nunca uno ajeno.
contenedores_prueba() {
    local nombre
    docker ps -a --filter name=sgp-restore-check- --format '{{.Names}}' | while read -r nombre; do
        [[ $nombre =~ ^sgp-restore-check-[0-9]{8}-[0-9]{6}(-[0-9]+)*$ ]] || continue
        [[ $(docker inspect -f '{{.HostConfig.NetworkMode}}' "$nombre" 2>/dev/null) == none ]] || continue
        echo "$nombre"
    done
}

respaldos_parciales() { find "$RESPALDOS" -maxdepth 1 -name '*.parcial' 2>/dev/null | sort || true; }

limpiar_contenedor_prueba() {
    [[ -n $CONTENEDOR_PRUEBA ]] || return 0
    docker rm -fv "$CONTENEDOR_PRUEBA" >/dev/null 2>&1 || true
    local limite=$(( $(date +%s) + 20 ))
    while docker inspect "$CONTENEDOR_PRUEBA" >/dev/null 2>&1; do      # --rm puede estar eliminándolo ya
        (( $(date +%s) < limite )) || { aviso "No se pudo eliminar $CONTENEDOR_PRUEBA: «bash $0 recuperar»"; return 1; }
        sleep 1
    done
    CONTENEDOR_PRUEBA=
}

# Con el cerrojo tomado, ninguna otra ejecución puede estar usando estos restos.
limpiar_huerfanos() {
    local nombre parciales
    for nombre in $(contenedores_prueba); do
        info "Restauración de prueba huérfana $nombre (copia de datos reales): se elimina con su volumen anónimo"
        docker rm -fv "$nombre" >/dev/null
    done
    parciales=$(respaldos_parciales)
    [[ -z $parciales ]] || aviso "Respaldos parciales de ejecuciones interrumpidas (incompletos, nunca válidos; bórralos a mano tras revisarlos):"$'\n'"$parciales"
}

# Filas por tabla (todas las tablas base de public). $1 = función que ejecuta SQL por stdin.
conteos_tablas() {
    "$1" <<'SQL'
select format('select %L || ''|'' || count(*) from %I', table_name, table_name)
from information_schema.tables where table_schema = 'public' and table_type = 'BASE TABLE' order by table_name
\gexec
SQL
}

# PostgreSQL desechable, sin red y con la misma imagen que producción, con la base restore_check vacía.
# Deja su nombre en CONTENEDOR_PRUEBA (lo elimina limpiar_contenedor_prueba o, si la ejecución se corta,
# al_salir; si ni eso llega a ejecutarse, la siguiente ejecución).
arrancar_contenedor_prueba() {
    local imagen limite
    N_PRUEBA=$(( N_PRUEBA + 1 ))
    CONTENEDOR_PRUEBA="sgp-restore-check-$MARCA-$N_PRUEBA"
    imagen=$(docker inspect -f '{{.Config.Image}}' "$(id_de postgres)")
    docker run -d --rm --name "$CONTENEDOR_PRUEBA" --label "$ETIQUETA_PRUEBA" --label "sgp.aatenea.ejecucion=$MARCA" \
        --network none -e POSTGRES_HOST_AUTH_METHOD=trust -e POSTGRES_DB=restore_check "$imagen" >/dev/null
    limite=$(( $(date +%s) + 120 ))
    until docker logs "$CONTENEDOR_PRUEBA" 2>&1 | grep -q 'PostgreSQL init process complete' \
            && docker exec "$CONTENEDOR_PRUEBA" pg_isready -U postgres -d restore_check -q; do
        (( $(date +%s) < limite )) || alto "El PostgreSQL de prueba no arrancó."
        sleep 2
    done
}

# Restauración de prueba en un PostgreSQL desechable, sin red, con la misma imagen que producción.
# Comprueba que pg_restore termina sin errores y, si se da $2, que cada tabla tiene esas filas.
# Deja en $EJEC los conteos y el historial de Flyway de la copia. El contenedor lo elimina esta función
# o, si la ejecución se corta, al_salir; si ni eso llega a ejecutarse, la siguiente ejecución.
verificar_restauracion() { # $1 = volcado, [$2 = archivo con los conteos esperados]
    local base
    base=$(basename "${1%.parcial}")
    arrancar_contenedor_prueba
    docker exec -i "$CONTENEDOR_PRUEBA" pg_restore -U postgres -d restore_check --no-owner --no-privileges --exit-on-error < "$1" \
        || alto "La restauración de prueba de $1 falló: el respaldo NO es válido."
    restaurada() { docker exec -i "$CONTENEDOR_PRUEBA" psql -U postgres -d restore_check -v ON_ERROR_STOP=1 -X -q -At -F "|"; }
    conteos_tablas restaurada > "$EJEC/conteos-restaurados-$base"
    restaurada <<<"$FLYWAY_SQL" > "$EJEC/flyway-restaurado-$base" 2>/dev/null || echo sin-flyway > "$EJEC/flyway-restaurado-$base"
    limpiar_contenedor_prueba || alto "No se pudo eliminar la restauración de prueba."
    if [[ -n ${2:-} ]]; then
        diff "$2" "$EJEC/conteos-restaurados-$base" \
            || alto "La copia restaurada no tiene las mismas filas que producción (¿actividad durante el respaldo?)."
    fi
    ok "Restauración de prueba aislada de $base: sin errores, $(wc -l < "$EJEC/conteos-restaurados-$base") tablas${2:+ con las mismas filas}"
}

# Volcado completo, verificado, con SHA-256 y conteos. Todo se escribe primero como *.parcial; solo
# tras comprobar conteos, suma y restauración de prueba adquiere el nombre definitivo (el .dump es lo
# último en renombrarse: un .dump con su nombre final siempre está completo). Deja la ruta en RESPALDO_HECHO.
respaldar() { # $1 = prefijo
    local nombre="${1}_$MARCA.dump" destino parcial libre tamano suma
    destino=$RESPALDOS/$nombre parcial=$RESPALDOS/$nombre.parcial
    mkdir -p "$RESPALDOS" && chmod 700 "$RESPALDOS"
    [[ ! -e $destino && ! -e $parcial ]] || alto "Ya existe $destino o $parcial."
    tamano=$(sql <<<"select pg_database_size(current_database());")
    libre=$(( $(df -Pk "$RESPALDOS" | awk 'NR==2 {print $4}') * 1024 ))
    (( libre > 3 * tamano )) || alto "Poco espacio en $RESPALDOS: $libre bytes libres para una BD de $tamano."
    conteos_tablas sql > "$EJEC/conteos-antes-$nombre"
    PARCIALES+=("$parcial" "$destino.sha256.parcial" "$destino.conteos.parcial")
    docker compose exec -T postgres sh -c 'pg_dump -U "$POSTGRES_USER" -d "$POSTGRES_DB" -Fc' > "$parcial"
    conteos_tablas sql > "$EJEC/conteos-despues-$nombre"
    cmp -s "$EJEC/conteos-antes-$nombre" "$EJEC/conteos-despues-$nombre" \
        || alto "Hubo escrituras durante el volcado; repite en una ventana sin actividad."
    [[ -s $parcial ]] || alto "El volcado está vacío."
    suma=$(sha256sum < "$parcial" | cut -d' ' -f1)
    printf '%s  %s\n' "$suma" "$nombre" > "$destino.sha256.parcial"
    cp "$EJEC/conteos-antes-$nombre" "$destino.conteos.parcial"
    chmod 600 "$parcial" "$destino.sha256.parcial" "$destino.conteos.parcial"
    verificar_restauracion "$parcial" "$EJEC/conteos-antes-$nombre"
    [[ $(sha256sum < "$parcial" | cut -d' ' -f1) == "$suma" ]] || alto "El volcado cambió durante la verificación."
    sincronizar "$parcial" "$destino.sha256.parcial" "$destino.conteos.parcial"
    mv -n "$destino.conteos.parcial" "$destino.conteos"
    mv -n "$destino.sha256.parcial" "$destino.sha256"
    mv -n "$parcial" "$destino"
    sincronizar "$RESPALDOS"
    [[ -f $destino && ! -e $parcial ]] || alto "No se pudo dar a $parcial su nombre definitivo."
    (cd "$RESPALDOS" && sha256sum -c --quiet "$nombre.sha256") || alto "El SHA-256 de $destino no coincide."
    [[ $(stat -c '%a %U' "$destino") == "600 $PROPIETARIO" ]] || alto "Permisos inesperados en $destino"
    printf '%s %s %s\n' "$(date -Iseconds)" "$1" "$destino" >> "$ESTADO/respaldos.registro"
    ok "Respaldo $destino ($(stat -c %s "$destino") bytes, 600 $PROPIETARIO, SHA-256 en .sha256, filas en .conteos)"
    RESPALDO_HECHO=$destino
}

# ---- Consultas de la seed -------------------------------------------------------
# Huella de TODO lo que no pertenece a las cinco empresas de esta seed: por tabla, filas y MD5 del
# contenido completo (no solo conteos). Incluye empresas, cuentas sin empresa (SUPER_ADMIN), otras
# empresas demo y el historial de Flyway. Si apareciera una tabla sin empresa_id, se hace constar.
huella_ajena() {
    sql <<SQL
create temp table seed_ids as
  select e.id from empresas e join (values $SEED_EMPRESAS) s(codigo, nit, nombre, activa)
    on s.codigo = e.codigo and s.nit = e.nit where e.demo;
select 'empresas|' || count(*) || '|' || md5(coalesce(string_agg(t::text, E'\n' order by t::text), ''))
  from empresas t where t.id not in (select id from seed_ids);
select format('select %L || ''|'' || count(*) || ''|'' || md5(coalesce(string_agg(t::text, E''\\n'' order by t::text), '''')) '
              'from %I t where t.empresa_id is null or t.empresa_id not in (select id from seed_ids)', c.table_name, c.table_name)
  from information_schema.columns c join information_schema.tables tb
    on tb.table_schema = c.table_schema and tb.table_name = c.table_name and tb.table_type = 'BASE TABLE'
  where c.table_schema = 'public' and c.column_name = 'empresa_id' order by c.table_name
\gexec
select 'flyway_schema_history|' || count(*) || '|' || md5(coalesce(string_agg(t::text, E'\n' order by t::text), ''))
  from flyway_schema_history t;
select 'sin_empresa_id|' || coalesce(string_agg(t.table_name, ',' order by t.table_name), '-')
  from information_schema.tables t where t.table_schema = 'public' and t.table_type = 'BASE TABLE'
   and t.table_name not in ('empresas', 'flyway_schema_history')
   and not exists (select 1 from information_schema.columns c
                   where c.table_schema = 'public' and c.table_name = t.table_name and c.column_name = 'empresa_id');
SQL
}

# Registros ajenos que chocan con la seed (siempre deben ser 0) y empresas de la seed ya existentes.
colisiones() {
    sql <<SQL
with seed(codigo, nit, nombre, activa) as (values $SEED_EMPRESAS),
     cuentas(username, rol, codigo, estado) as (values $SEED_CUENTAS),
     propias as (select e.id, e.codigo from empresas e join seed s on s.codigo = e.codigo and s.nit = e.nit where e.demo)
select 'código ' || e.codigo || ' en uso por una empresa ajena a la seed'
  from empresas e join seed s on s.codigo = e.codigo where e.id not in (select id from propias)
union all
select 'NIT de ' || s.codigo || ' en uso por la empresa ' || e.codigo
  from empresas e join seed s on s.nit = e.nit where e.id not in (select id from propias)
union all
select 'empresa demo ajena a esta seed: ' || e.codigo from empresas e where e.demo and e.id not in (select id from propias)
union all
select 'username ' || u.username || ' en uso fuera de su empresa demo'
  from usuarios u join cuentas c on c.username = u.username left join propias p on p.id = u.empresa_id
  where p.codigo is distinct from c.codigo
union all
select 'correo de ' || c.username || ' en uso por la cuenta ' || u.username
  from usuarios u join cuentas c on lower(u.email) = c.username || '@' || lower(c.codigo) || '.sgp-demo.test'
  where u.username <> c.username
union all
select 'cuenta ajena con un correo del dominio demo: ' || u.username
  from usuarios u where lower(u.email) like '%.sgp-demo.test' and u.username not in (select username from cuentas);
SQL
}

empresas_propias() {
    sql <<SQL
select count(*) from empresas e join (values $SEED_EMPRESAS) s(codigo, nit, nombre, activa)
  on s.codigo = e.codigo and s.nit = e.nit where e.demo;
SQL
}

conteos_demo() {
    sql <<'SQL'
select e.codigo,
  (select count(*) from proveedores x where x.empresa_id = e.id),
  (select count(*) from productos x where x.empresa_id = e.id),
  (select count(*) from solicitudes x where x.empresa_id = e.id),
  (select count(*) from ordenes_compra x where x.empresa_id = e.id),
  (select count(*) from recepciones x where x.empresa_id = e.id),
  (select count(*) from ventas x where x.empresa_id = e.id),
  (select count(*) from devoluciones_venta x where x.empresa_id = e.id)
from empresas e where e.demo order by e.codigo;
SQL
}

# ---- Contraseña común de las cuentas demo ---------------------------------------
# La elige una persona (es pública: solo protege datos ficticios) y la guarda «clave-demo»; la fase 3
# nunca la inventa. Todas las cuentas demo la comparten; las cuentas reales no la usan nunca.
validar_clave() {
    [[ -s $CLAVE ]] || alto "No hay contraseña demo: defínela antes con «bash $0 clave-demo»."
    [[ $(stat -c '%a %U' "$CLAVE") == "600 $PROPIETARIO" ]] || alto "Permisos inesperados en $CLAVE (se esperaba 600 $PROPIETARIO)."
    (( $(wc -l < "$CLAVE") == 1 )) && [[ $(<"$CLAVE") =~ $FORMATO_CLAVE ]] \
        || alto "La contraseña demo de $CLAVE no tiene el formato admitido (8-72 caracteres: letras, dígitos, . _ -)."
}

clave_demo() {
    fase "CONTRASEÑA COMÚN DE LAS CUENTAS DEMO"
    exigir_sin_pendientes
    [[ ! -e $CLAVE ]] || alto "Ya existe $CLAVE y no se sobrescribe: las cuentas demo que ya existan la usan. Para cambiarla antes de la fase 3, retírala a mano tras revisar «bash $0 estado»."
    info "Contraseña PÚBLICA de demostración: la compartirán las 10 cuentas demo; nunca la uses en una cuenta real."
    info "8-72 caracteres: letras sin tilde, dígitos, punto, guion y guion bajo. No se muestra al escribirla."
    local una otra
    IFS= read -rs -p "  Contraseña demo: " una || alto "No se leyó la contraseña."
    printf '\n' >&2
    IFS= read -rs -p "  Repítela: " otra || alto "No se leyó la confirmación."
    printf '\n' >&2
    [[ $una == "$otra" ]] || alto "Las dos contraseñas no coinciden; no se guarda nada."
    [[ $una =~ $FORMATO_CLAVE ]] || alto "Formato no admitido (8-72 caracteres: letras sin tilde, dígitos, . _ -); no se guarda nada."
    escribir "$CLAVE" "$una"
    chmod 600 "$CLAVE"
    validar_clave
    ok "Contraseña demo guardada en $CLAVE (600 $PROPIETARIO, ${#una} caracteres; no se imprime)"
}

# -----------------------------------------------------------------------------
fase1() {
    fase "FASE 1 — Estado, respaldo verificado y punto de retorno"
    exigir_sin_pendientes
    [[ ${OBJETIVO:-} =~ ^[0-9a-f]{40}$ ]] || alto "Indica OBJETIVO=<sha completo de 40 caracteres> del commit aprobado."
    if [[ -s $ESTADO/actual ]]; then
        local anterior
        anterior=$ESTADO/despliegues/$(<"$ESTADO/actual")
        if [[ -e $anterior/fase2.ok && $(cat "$anterior/objetivo" 2>/dev/null) == "$OBJETIVO" ]]; then
            alto "El despliegue $(<"$ESTADO/actual") ya llevó $OBJETIVO a producción: una fase 1 nueva movería el punto de retorno a las imágenes actuales. Para la seed usa directamente la fase 3."
        fi
        info "El despliegue anterior $(<"$ESTADO/actual") se conserva en $anterior"
    fi
    info "Repositorio $REPO · rama $(git branch --show-current) · HEAD $(git rev-parse HEAD) · upstream $(git rev-parse --abbrev-ref '@{u}' 2>/dev/null || echo ninguno)"
    [[ $(git branch --show-current) == "$RAMA" ]] || alto "La rama actual no es $RAMA; no se cambia de rama automáticamente."
    if [[ -n $(git status --porcelain) ]]; then
        git status --short
        alto "Hay cambios locales en $REPO. No se descartan: revísalos (git diff) y decide si se guardan con git stash o un commit."
    fi
    ok "Sin cambios locales"
    git fetch --quiet origin
    git cat-file -e "$OBJETIVO^{commit}" 2>/dev/null || alto "El commit objetivo no está disponible tras git fetch."
    [[ $(git rev-parse "origin/$RAMA") == "$OBJETIVO" ]] || alto "origin/$RAMA es $(git rev-parse "origin/$RAMA"), no $OBJETIVO."
    git merge-base --is-ancestor HEAD "$OBJETIVO" || alto "HEAD no es antecesor de $OBJETIVO: no se puede avanzar en rápido (¿commits locales?). No se fuerza."
    ok "origin/$RAMA = $OBJETIVO y HEAD avanza en rápido"

    # Despliegue nuevo con sus propios archivos; $ESTADO/actual solo apunta a él al final, si todo fue bien.
    ID_DESP=$MARCA DESP=$ESTADO/despliegues/$MARCA
    mkdir -p "$DESP"
    echo "$ID_DESP" > "$EJEC/despliegue"
    fijar "$DESP/commit-previo" "$(git rev-parse HEAD)"
    fijar "$DESP/objetivo" "$OBJETIVO"

    info "Cambios entre HEAD y el objetivo:"
    git diff --stat HEAD "$OBJETIVO" | tail -1
    local migraciones; migraciones=$(git diff --name-only HEAD "$OBJETIVO" -- backend-spring/src/main/resources/db/migration backend-spring/src/main/java/db/migration)
    if [[ -n $migraciones ]]; then
        info "Migraciones nuevas o cambiadas (tras desplegarlas, volver atrás exige restaurar la BD):"; echo "$migraciones"
        git diff --name-only --diff-filter=MD HEAD "$OBJETIVO" -- backend-spring/src/main/resources/db/migration backend-spring/src/main/java/db/migration \
            | grep . && alto "Se modifica o borra una migración ya existente: no se despliega."
    else
        ok "Sin migraciones nuevas"
    fi
    git diff --quiet HEAD "$OBJETIVO" -- docker-compose.yml || info "docker-compose.yml cambia: revisa 'git diff HEAD $OBJETIVO -- docker-compose.yml'"
    if git diff --quiet HEAD "$OBJETIVO" -- frontend; then fijar "$DESP/frontend-cambia" no; else fijar "$DESP/frontend-cambia" si; fi
    info "El frontend $( [[ $(<"$DESP/frontend-cambia") == si ]] && echo 'CAMBIA (se reconstruirá)' || echo 'no cambia')"

    info "Servicios de Compose: $(docker compose config --services | tr '\n' ' ')"
    docker compose ps
    esperar_sano backend 30 || alto "El backend actual no está sano: no se despliega sobre un estado roto."
    ok "Backend actual sano: $(readiness)"
    info "Variables del backend (solo nombres): $(nombres_env | tr '\n' ' ')"
    sin_variables_demo || alto "El backend actual ya define SPRING_PROFILES_ACTIVE o DEMO_PASSWORD: «bash $0 recuperar» e investigar antes de seguir."
    ok "El backend actual no define SPRING_PROFILES_ACTIVE ni DEMO_PASSWORD"

    flyway > "$DESP/flyway-previo"
    info "Flyway: $(tail -3 "$DESP/flyway-previo" | tr '\n' ' ')"
    grep -q '|f$' "$DESP/flyway-previo" && alto "Hay migraciones fallidas en flyway_schema_history."

    if [[ -n ${RESPALDO_PREVIO:-} ]]; then
        [[ -f $RESPALDO_PREVIO ]] || alto "No existe $RESPALDO_PREVIO"
        info "Respaldo previo: $(stat -c '%s bytes, %a, %U' "$RESPALDO_PREVIO")"
        [[ $(stat -c '%a' "$RESPALDO_PREVIO") == 600 ]] || info "AVISO: el respaldo previo no tiene permisos 600"
        [[ -z ${SHA_RESPALDO_PREVIO:-} || $(sha256sum "$RESPALDO_PREVIO" | cut -d' ' -f1) == "$SHA_RESPALDO_PREVIO" ]] \
            || alto "El SHA-256 del respaldo previo no coincide."
        ok "Respaldo previo íntegro${SHA_RESPALDO_PREVIO:+ (SHA-256 coincide)}; el punto de retorno será el respaldo nuevo"
    fi
    respaldar sgp_pre_deploy
    fijar "$DESP/respaldo-pre-deploy" "$RESPALDO_HECHO"

    local servicio id_img etiqueta
    for servicio in backend frontend; do
        id_img=$(docker inspect -f '{{.Image}}' "$(id_de "$servicio")")
        etiqueta="$IMAGENES-$servicio:pre-deploy-$MARCA"
        docker image tag "$id_img" "$etiqueta"
        fijar "$DESP/imagen-previa-$servicio" "$etiqueta"
        fijar "$DESP/imagen-previa-$servicio-id" "$id_img"
        ok "Punto de retorno de la imagen $servicio: $etiqueta"
    done
    fijar "$DESP/fase1.ok" "$MARCA"
    escribir "$ESTADO/actual" "$ID_DESP"
    ok "Despliegue $ID_DESP en $DESP"
    ok "Plan de recuperación: imágenes etiquetadas + $(<"$DESP/respaldo-pre-deploy") (ver README, «Vuelta atrás»)"
}

# -----------------------------------------------------------------------------
rollback() {
    fase "ROLLBACK — imágenes de la fase 1 (la base de datos no se toca)"
    exigir_sin_pendientes fase2 rollback
    cargar_despliegue
    flyway > "$EJEC/flyway-ahora"
    cmp -s "$DESP/flyway-previo" "$EJEC/flyway-ahora" \
        || alto "Flyway cambió desde la fase 1: la imagen anterior no valida el esquema nuevo. Hay que restaurar la BD (README)."
    local servicio etiqueta
    for servicio in backend frontend; do
        etiqueta=$(<"$DESP/imagen-previa-$servicio")
        [[ $(docker image inspect -f '{{.Id}}' "$etiqueta" 2>/dev/null) == "$(<"$DESP/imagen-previa-$servicio-id")" ]] \
            || alto "La etiqueta $etiqueta ya no apunta a la imagen guardada en la fase 1."
    done
    iniciar_operacion rollback
    paso rollback imagenes
    for servicio in backend frontend; do
        docker image tag "$(<"$DESP/imagen-previa-$servicio")" "$IMAGENES-$servicio:latest"
    done
    paso rollback backend
    docker compose up -d --no-deps --no-build backend
    esperar_sano backend 240 || alto "El backend anterior no quedó sano: revisa docker compose logs backend"
    sin_variables_demo || alto "El backend anterior tiene variables demo."
    paso rollback frontend
    docker compose up -d --no-deps --no-build frontend
    esperar_sano frontend 120 || alto "El frontend anterior no quedó sano."
    [[ -e $DESP/rollback.hecho ]] || fijar "$DESP/rollback.hecho" "$MARCA"
    cerrar_operacion rollback completada
    if [[ -e $DIARIO/fase2 ]]; then cerrar_operacion fase2 abortada-por-rollback; fi
    ok "Imágenes anteriores en ejecución. El código de $REPO sigue en $(git rev-parse --short HEAD); devolverlo a $(<"$DESP/commit-previo") es un paso aparte (README, «Vuelta atrás»)"
}

fase2() {
    fase "FASE 2 — Código y despliegue (sin seed)"
    exigir_sin_pendientes fase2
    cargar_despliegue
    [[ ! -e $DESP/fase2.ok ]] || alto "La fase 2 del despliegue $ID_DESP ya terminó."
    [[ ! -e $DESP/rollback.hecho ]] || alto "Se hizo rollback del despliegue $ID_DESP: empieza uno nuevo con la fase 1."
    local objetivo; objetivo=$(<"$DESP/objetivo")
    [[ -z $(git status --porcelain) ]] || alto "Hay cambios locales; no se actualiza el código."
    [[ $(git branch --show-current) == "$RAMA" ]] || alto "La rama actual no es $RAMA."
    iniciar_operacion fase2
    if (( REANUDANDO )) && [[ $(git rev-parse HEAD) == "$objetivo" ]]; then
        ok "HEAD ya es el objetivo (reanudación)"
    else
        [[ $(git rev-parse HEAD) == "$(<"$DESP/commit-previo")" ]] || alto "HEAD cambió desde la fase 1; repítela."
        paso fase2 codigo
        git merge --ff-only "$objetivo" || alto "No se pudo avanzar en rápido hasta el objetivo. No se fuerza."
    fi
    [[ $(git rev-parse HEAD) == "$objetivo" ]] || alto "HEAD no es el objetivo."
    ok "HEAD = $objetivo"
    if grep -nE 'SPRING_PROFILES_ACTIVE|DEMO_PASSWORD' docker-compose.yml; then
        alto "docker-compose.yml menciona el perfil o la contraseña demo: investigar antes de desplegar."
    fi
    ok "docker-compose.yml no activa el perfil demo"

    paso fase2 backend
    docker compose build backend || alto "Falló la compilación del backend; sigue en ejecución la imagen anterior."
    ok "Imagen del backend compilada"
    local desde; desde=$(date -u +%Y-%m-%dT%H:%M:%SZ)
    docker compose up -d --no-deps --no-build backend
    if ! esperar_sano backend 240; then
        docker compose logs --since "$desde" backend | grep -E 'ERROR|Exception|Caused by' | tail -20 || true
        alto "El backend nuevo no quedó sano. Para volver a la imagen anterior: bash $0 rollback"
    fi
    ok "Backend sano: $(readiness)"
    sin_variables_demo || alto "El backend nuevo tiene variables demo."
    ok "Sin SPRING_PROFILES_ACTIVE ni DEMO_PASSWORD"
    docker compose logs --since "$desde" backend | grep -iE 'flyway|Successfully validated|up to date|Migrating' | tail -5 || true
    docker compose logs --since "$desde" backend | grep -E 'Perfil demo|Seed de demostración' && alto "¡La seed se ejecutó sin querer!"
    info "Errores en el log desde el arranque: $(docker compose logs --since "$desde" backend | grep -c ' ERROR ' || true)"
    flyway > "$EJEC/flyway-despues"
    if cmp -s "$DESP/flyway-previo" "$EJEC/flyway-despues"; then ok "Flyway sin cambios"
    else info "Flyway aplicó: $(diff "$DESP/flyway-previo" "$EJEC/flyway-despues" | grep '^>' | tr '\n' ' ')"
         grep -q '|f$' "$EJEC/flyway-despues" && alto "Hay una migración fallida."; fi

    if [[ $(<"$DESP/frontend-cambia") == si ]]; then
        paso fase2 frontend
        docker compose build frontend || alto "Falló la compilación del frontend; el backend nuevo ya está en marcha (rollback disponible)."
        docker compose up -d --no-deps --no-build frontend
        esperar_sano frontend 120 || alto "El frontend nuevo no quedó sano: bash $0 rollback"
        ok "Frontend reconstruido y sano"
    fi
    paso fase2 comprobacion
    local codigo; codigo=$(printf '{"username":"%s","password":"%s"}' "no.existe.$MARCA" "credencial-invalida" \
        | curl -sS -o /dev/null -w '%{http_code}' -H 'Content-Type: application/json' --data-binary @- "$(url_base)/api/auth/login")
    [[ $codigo == 401 ]] || alto "Login por Nginx respondió $codigo (se esperaba 401)."
    ok "Login por Nginx responde (401 con credenciales inválidas, consulta a PostgreSQL incluida)"
    fijar "$DESP/fase2.ok" "$MARCA"
    cerrar_operacion fase2 completada
}

# -----------------------------------------------------------------------------
fase3() {
    fase "FASE 3 — Seed demo controlada"
    [[ ${CONFIRMAR_SEED:-} == si ]] || alto "La seed solo se ejecuta con CONFIRMAR_SEED=si (autorización expresa)."
    exigir_sin_pendientes fase3
    cargar_despliegue
    [[ -e $DESP/fase2.ok ]] || alto "Ejecuta antes la fase 2."
    [[ ! -e $DESP/rollback.hecho ]] || alto "Se hizo rollback del despliegue $ID_DESP: no se siembra sobre las imágenes anteriores."
    [[ $(git rev-parse HEAD) == "$(<"$DESP/objetivo")" ]] || alto "HEAD no es el objetivo del despliegue."
    esperar_sano backend 30 || alto "El backend no está sano."
    sin_variables_demo || alto "El backend ya tiene variables demo: «bash $0 recuperar» antes de seguir."
    validar_clave
    ok "Contraseña común de las cuentas demo definida en $CLAVE (600; no se imprime)"

    local choques; choques=$(colisiones)
    [[ -z $choques ]] || { echo "$choques"; alto "Hay registros ajenos que chocan con la seed: no se siembra. Decide qué hacer con ellos."; }
    ok "Sin colisiones de código, NIT, username, correo ni empresas demo ajenas"
    local propias; propias=$(empresas_propias)
    if (( propias > 0 )); then
        [[ ${REANUDAR:-} == 1 ]] || alto "Ya existen $propias empresas de esta seed: para completar una ejecución anterior usa REANUDAR=1."
        info "Reanudación: $propias empresas de la seed ya existen; se completará lo que falte"
    fi

    iniciar_operacion fase3
    paso fase3 respaldo
    respaldar sgp_pre_seed
    anotar fase3 "respaldo_pre_seed=$RESPALDO_HECHO"
    paso fase3 huella
    huella_ajena > "$EJEC/ajeno-antes"
    anotar fase3 "huella=$EJEC/ajeno-antes"
    grep -q '^sin_empresa_id|-$' "$EJEC/ajeno-antes" || info "AVISO: tablas sin empresa_id no cubiertas por la huella: $(grep '^sin_empresa_id' "$EJEC/ajeno-antes")"
    ok "Huella de los datos ajenos a la seed: $(grep -vc '^sin_empresa_id' "$EJEC/ajeno-antes") tablas"

    # La contraseña es la de «clave-demo» (validada arriba); con REANUDAR=1, la misma de la ejecución anterior.
    validar_clave

    # Sin secretos: DEMO_PASSWORD se interpola desde el entorno. Se conserva en la ejecución como constancia.
    local override=$EJEC/sgp-demo.override.yml
    cat > "$override" <<'YML'
services:
  backend:
    environment:
      SPRING_PROFILES_ACTIVE: demo
      DEMO_PASSWORD: ${DEMO_PASSWORD:?falta DEMO_PASSWORD}
YML
    chmod 600 "$override"

    # Desde aquí hasta «normal», una salida por error, Ctrl-C o TERM intenta volver al perfil normal
    # (al_salir). Si ni eso se ejecuta, el diario queda en demo-* y «recuperar» lo resuelve.
    info "Arrancando el backend con el perfil demo (una sola vez)…"
    local desde; desde=$(date -u +%Y-%m-%dT%H:%M:%SZ)
    paso fase3 demo-arrancando
    PERFIL_DEMO_ACTIVO=1
    DEMO_PASSWORD=$(<"$CLAVE") docker compose -f docker-compose.yml -f "$override" up -d --no-deps --no-build backend
    paso fase3 demo-activo
    local sano=0; esperar_sano backend 300 && sano=1
    # Se guardan ya: al recrear el contenedor sin el perfil demo, Docker descarta sus logs.
    docker compose logs --since "$desde" backend | grep -E 'Seed de demostración terminada|Seed demo|ERROR|Exception' > "$EJEC/backend-demo.log" || true
    tail -40 "$EJEC/backend-demo.log"

    info "Volviendo a la configuración normal (sin perfil demo)…"
    paso fase3 volviendo-normal
    volver_perfil_normal || alto "El backend no quedó sano o conserva variables demo tras retirar el perfil demo."
    PERFIL_DEMO_ACTIVO=0
    paso fase3 normal
    ok "Backend sano y sin perfil demo ni DEMO_PASSWORD"

    huella_ajena > "$EJEC/ajeno-despues"
    if diff "$EJEC/ajeno-antes" "$EJEC/ajeno-despues"; then
        ok "Datos ajenos a la seed idénticos (contenido completo de $(grep -vc '^sin_empresa_id' "$EJEC/ajeno-despues") tablas)"
    else
        alto "¡Cambiaron datos ajenos a la seed (o hubo actividad real durante la ventana)! Respaldo previo: $RESPALDO_HECHO"
    fi
    (( sano )) || alto "Con el perfil demo el backend no llegó a estar sano: la seed falló. Ver README, «Fallo parcial»."
    grep -q 'Seed de demostración terminada' "$EJEC/backend-demo.log" || alto "No consta el fin de la seed (líneas del contenedor demo en $EJEC/backend-demo.log)."

    paso fase3 verificacion
    verificar_seed
    cerrar_operacion fase3 completada
}

# Exactamente las cinco empresas y diez cuentas previstas, los conteos por empresa y el kárdex.
verificar_seed() {
    sql <<SQL > "$EJEC/empresas-demo"
select e.codigo || '|' || e.nombre || '|' || e.activa from empresas e where e.demo order by e.codigo;
SQL
    sql <<SQL > "$EJEC/empresas-esperadas"
select codigo || '|' || nombre || '|' || activa from (values $SEED_EMPRESAS) s(codigo, nit, nombre, activa) order by codigo;
SQL
    diff "$EJEC/empresas-esperadas" "$EJEC/empresas-demo" || alto "Las empresas demo no son exactamente las cinco previstas."
    ok "Exactamente 5 empresas demo, con su nombre y estado"
    local cuentas; cuentas=$(sql <<SQL
select count(*) || '|' || count(*) filter (where u.rol = c.rol and e.codigo = c.codigo and u.estado = c.estado and e.demo
                                           and u.email = c.username || '@' || lower(c.codigo) || '.sgp-demo.test')
from usuarios u join empresas e on e.id = u.empresa_id
left join (values $SEED_CUENTAS) c(username, rol, codigo, estado) on c.username = u.username
where e.demo;
SQL
)
    [[ $cuentas == '10|10' ]] || alto "Cuentas demo inesperadas (total|correctas = $cuentas; se esperaba 10|10)."
    ok "Exactamente 10 cuentas demo con su rol, empresa, estado y correo"
    diff <(echo "$CONTEOS_ESPERADOS") <(conteos_demo) || alto "Los datos demo no tienen los conteos previstos."
    ok "Conteos de proveedores, productos, solicitudes, órdenes, recepciones, ventas y devoluciones"
    local kardex; kardex=$(sql <<'SQL'
select count(*) from (
  select m.saldo_resultante, sum(m.cantidad) over (partition by m.producto_id order by m.id) acumulado
  from movimientos_inventario m join empresas e on e.id = m.empresa_id and e.demo) k
where saldo_resultante <> acumulado or acumulado < 0;
SQL
)
    local existencias; existencias=$(sql <<'SQL'
select count(*) from existencias x join empresas e on e.id = x.empresa_id and e.demo
where x.cantidad <> (select coalesce(sum(m.cantidad), 0) from movimientos_inventario m where m.producto_id = x.producto_id);
SQL
)
    [[ $kardex == 0 && $existencias == 0 ]] || alto "Kárdex demo incoherente: $kardex movimientos y $existencias existencias."
    ok "Kárdex demo coherente (saldos y existencias)"
}

# -----------------------------------------------------------------------------
fase4() {
    fase "FASE 4 — Archivo privado de cuentas y pruebas por HTTP"
    exigir_sin_pendientes
    validar_clave
    sin_variables_demo || alto "El backend conserva variables demo."
    local cuentas_file="$PRIVADO/sgp_cuentas_demo_$MARCA.txt"
    local cuentas; cuentas=$(sql <<'SQL'
select u.username, u.rol, e.codigo, e.nombre, u.estado, case when e.activa then 'activa' else 'desactivada' end,
  case when not e.activa then 'Sin acceso: empresa desactivada (403 EMPRESA_INACTIVA)'
       when u.estado = 'PENDIENTE' then 'Sin acceso hasta que su administrador lo apruebe (403 PENDIENTE)'
       when u.estado = 'RECHAZADO' then 'Sin acceso: registro rechazado (403 RECHAZADO)'
       when u.rol = 'ADMINISTRADOR_COMERCIAL' then 'Administrador comercial: gestión de su empresa'
       else 'Trabajador: solicitudes propias, recepciones y punto de venta' end
from usuarios u join empresas e on e.id = u.empresa_id
where e.demo order by e.codigo, u.rol, u.username;
SQL
)
    local n; n=$(grep -c . <<<"$cuentas")
    (( n == 10 )) || alto "Hay $n cuentas demo en la base (se esperaban 10)."
    {
        echo "SGP (Unisen) — cuentas DEMO de prueba · generado $(date -Iseconds) en Aatenea"
        echo "Solo datos ficticios (empresas con demo = true). Ninguna cuenta real."
        echo
        echo "Contraseña PÚBLICA de demostración, común a las 10 cuentas demo (solo protege datos ficticios;"
        echo "no la uses en ninguna cuenta real):"
        echo "  $(<"$CLAVE")"
        echo
        echo "Inicio de sesión: abre la aplicación → usuario + contraseña."
        echo "Mercado Verde (MVSA01) tiene el flujo completo; Suministros del Caribe (SCA002) sirve para ver el aislamiento."
        echo
        echo "username | rol | código | empresa | estado | empresa activa | finalidad"
        echo "${cuentas//|/ | }"
    } > "$cuentas_file"
    chmod 600 "$cuentas_file"
    [[ $(stat -c '%a %U' "$cuentas_file") == "600 $PROPIETARIO" ]] || alto "Permisos inesperados en $cuentas_file"
    [[ $(stat -c '%a' "$PRIVADO") == 700 ]] || alto "Permisos inesperados en $PRIVADO"
    case "$cuentas_file" in "$REPO"/*) alto "El archivo está dentro del repositorio.";; esac
    [[ -z $(git status --porcelain) ]] || alto "El repositorio tiene cambios tras generar el archivo."
    [[ -z $(git log --all --oneline -- '*sgp_cuentas*' '*demo_password*') ]] || alto "Hay commits con archivos de credenciales."
    ok "Archivo $cuentas_file: 600 $PROPIETARIO, 10 cuentas, fuera del repositorio y de cualquier commit"

    pruebas_http

    esperar_sano backend 30 || alto "El backend no está sano al terminar."
    ok "Backend sano al terminar: $(readiness)"
    sin_variables_demo || alto "El backend conserva variables demo."
    ok "Configuración normal (sin perfil demo)"
    [[ -z $(pendientes) ]] || alto "Hay operaciones incompletas: $(pendientes | tr '\n' ' ')"
    ok "Sin operaciones incompletas"
}

# Login de las diez cuentas, permisos por rol y aislamiento entre MVSA01 y SCA002 (solo lectura).
pruebas_http() {
    TMP_HTTP=$EJEC/http                    # tokens: al_salir lo borra aunque la prueba se detenga
    mkdir -m 700 "$TMP_HTTP"
    local tmp=$TMP_HTTP
    login() { # $1 username → código HTTP; cuerpo en $tmp/r.json. El JSON va por stdin (no aparece en ps).
        local pw; pw=$(<"$CLAVE")
        printf '{"username":"%s","password":"%s"}' "$1" "$pw" \
            | curl -sS -o "$tmp/r.json" -w '%{http_code}' -H 'Content-Type: application/json' --data-binary @- "$(url_base)/api/auth/login"
    }
    probar() { # $1 username, $2 código esperado, $3 motivo o rol esperado
        local c; c=$(login "$1")
        if [[ $c == "$2" ]] && grep -q "\"${3%%=*}\":\"${3#*=}\"" "$tmp/r.json"; then ok "login $1 → $c ${3#*=}"
        else alto "login $1 → $c (se esperaba $2 ${3#*=})"; fi
    }
    cabecera() { # $1 username → archivo 600 con la cabecera Authorization (el token no va en argumentos)
        local h; h=$(mktemp -p "$tmp")
        login "$1" >/dev/null
        sed -n 's/.*"accessToken":"\([^"]*\)".*/Authorization: Bearer \1/p' "$tmp/r.json" > "$h"
        [[ -s $h ]] || alto "El login de $1 no trae token."
        echo "$h"
    }
    probar carlos.mendoza 200 rol=ADMINISTRADOR_COMERCIAL
    probar andres.rojas 200 rol=ADMINISTRADOR_COMERCIAL
    probar diego.martinez 200 rol=ADMINISTRADOR_COMERCIAL
    probar miguel.ortiz 200 rol=ADMINISTRADOR_COMERCIAL
    probar laura.torres 200 rol=TRABAJADOR
    probar maria.castro 200 rol=TRABAJADOR
    probar sofia.ramirez 403 motivo=PENDIENTE
    probar valentina.gomez 403 motivo=RECHAZADO
    probar sergio.vega 403 motivo=EMPRESA_INACTIVA
    probar paula.navarro 403 motivo=EMPRESA_INACTIVA

    local carlos andres laura maria
    carlos=$(cabecera carlos.mendoza); andres=$(cabecera andres.rojas)
    laura=$(cabecera laura.torres); maria=$(cabecera maria.castro)
    comprobar() { # $1 cabecera, $2 ruta, $3 código esperado, $4 descripción, [$5 totalElements esperado]
        local c; c=$(curl -sS -o "$tmp/g.json" -w '%{http_code}' -H @"$1" "$(url_base)$2")
        [[ $c == "$3" ]] || alto "$4 → $c (se esperaba $3)"
        if [[ -n ${5:-} ]]; then
            grep -q "\"totalElements\":$5[,}]" "$tmp/g.json" || alto "$4 → $(grep -o '"totalElements":[0-9]*' "$tmp/g.json") (se esperaba $5)"
            ok "$4 → $c, $5 elementos"
        else ok "$4 → $c"; fi
    }
    # Empresas de la seed por código y NIT (no por la marca demo, que «normalizar» retira).
    primero() { sql <<<"select min(x.id) from $1 x join empresas e on e.id = x.empresa_id join (values $SEED_EMPRESAS) s(codigo, nit, nombre, activa) on s.codigo = e.codigo and s.nit = e.nit where e.codigo = '$2';"; }
    local sol_sc ord_sc prod_sc ord_mv
    sol_sc=$(primero solicitudes SCA002); ord_sc=$(primero ordenes_compra SCA002)
    prod_sc=$(primero productos SCA002); ord_mv=$(primero ordenes_compra MVSA01)

    comprobar "$carlos" '/api/v1/solicitudes?size=50' 200 "carlos.mendoza ve las solicitudes de Mercado Verde" 3
    comprobar "$carlos" '/api/v1/ordenes-compra?size=50' 200 "carlos.mendoza ve sus órdenes" 3
    comprobar "$carlos" '/api/v1/productos?size=50' 200 "carlos.mendoza ve sus productos" 7
    comprobar "$carlos" '/api/v1/trabajadores?size=50' 200 "carlos.mendoza ve a su trabajadora" 1
    comprobar "$carlos" '/api/auth/invitaciones?size=50' 200 "carlos.mendoza gestiona invitaciones" 0
    comprobar "$carlos" /api/v1/empresas 403 "carlos.mendoza no accede a «Empresas» (SUPER_ADMIN)"
    comprobar "$carlos" "/api/v1/solicitudes/$sol_sc" 404 "carlos.mendoza no ve una solicitud de SCA002"
    comprobar "$carlos" "/api/v1/ordenes-compra/$ord_sc" 404 "carlos.mendoza no ve una orden de SCA002"
    comprobar "$carlos" "/api/v1/productos/$prod_sc" 404 "carlos.mendoza no ve un producto de SCA002"
    comprobar "$andres" '/api/v1/solicitudes?size=50' 200 "andres.rojas ve las solicitudes de Suministros del Caribe" 1
    comprobar "$andres" '/api/v1/ordenes-compra?size=50' 200 "andres.rojas ve sus órdenes" 2
    comprobar "$andres" '/api/v1/productos?size=50' 200 "andres.rojas ve sus productos" 4
    comprobar "$andres" "/api/v1/ordenes-compra/$ord_mv" 404 "andres.rojas no ve una orden de MVSA01"
    comprobar "$laura" '/api/v1/solicitudes?size=50' 200 "laura.torres ve sus solicitudes" 3
    comprobar "$maria" '/api/v1/solicitudes?size=50' 200 "maria.castro ve sus solicitudes" 1
    comprobar "$laura" "/api/v1/solicitudes/$sol_sc" 404 "laura.torres no ve una solicitud de SCA002"
    comprobar "$laura" /api/auth/invitaciones 403 "laura.torres no gestiona invitaciones"
    comprobar "$laura" /api/v1/trabajadores 403 "laura.torres no gestiona trabajadores"

    local cb p1 p2
    cb=$(sql <<<"select p.codigo_barras from productos p join empresas e on e.id = p.empresa_id join (values $SEED_EMPRESAS) s(codigo, nit, nombre, activa) on s.codigo = e.codigo and s.nit = e.nit where e.codigo = 'MVSA01' and p.sku = 'AGUA-600';")
    comprobar "$carlos" "/api/v1/productos/buscar?codigo=$cb" 200 "carlos.mendoza busca el código de barras compartido"
    p1=$(grep -o '"producto":{"id":[0-9]*' "$tmp/g.json" | grep -o '[0-9]*$')
    comprobar "$andres" "/api/v1/productos/buscar?codigo=$cb" 200 "andres.rojas busca el mismo código"
    p2=$(grep -o '"producto":{"id":[0-9]*' "$tmp/g.json" | grep -o '[0-9]*$')
    [[ -n $p1 && -n $p2 && $p1 != "$p2" ]] || alto "El código compartido no se resolvió por empresa ($p1 / $p2)."
    ok "Mismo código de barras → producto distinto en cada empresa ($p1 ≠ $p2)"
    rm -rf "$TMP_HTTP"; TMP_HTTP=
}

# ---- Normalización de los datos de prueba -----------------------------------------
# Las cinco empresas y diez cuentas de la seed pasan a verse como datos normales (siguen siendo
# ficticios): empresas.demo = false, correos @<empresa>.example.com (dominio reservado para pruebas,
# RFC 2606) y el catálogo de MVSA01 y SCA002 sin «(demo)». Solo cambian esas columnas de esas filas;
# las filas se localizan por su clave natural (código + NIT, username, empresa + NIT, empresa + SKU) y
# nunca se crean ni se borran. IDs, usernames, nombres, roles, estados, contraseñas, códigos, NIT,
# kárdex, órdenes, ventas e historial no se tocan. «revertir» devuelve exactamente los valores de la seed.
NORM_OBJETIVOS=$(cat <<'SQL'
create temp table norm_empresas (codigo text primary key, nit text not null unique);
insert into norm_empresas values
  ('MVSA01', '999100000001'), ('SCA002', '999100000002'), ('DNS003', '999100000003'),
  ('CHS004', '999100000004'), ('LAS005', '999100000005');
create temp table norm_usuarios (username text primary key, codigo text not null, rol text not null,
  email_original text not null unique, email_nuevo text not null unique);
insert into norm_usuarios values
  ('carlos.mendoza', 'MVSA01', 'ADMINISTRADOR_COMERCIAL', 'carlos.mendoza@mvsa01.sgp-demo.test', 'carlos.mendoza@mercadoverde.example.com'),
  ('laura.torres', 'MVSA01', 'TRABAJADOR', 'laura.torres@mvsa01.sgp-demo.test', 'laura.torres@mercadoverde.example.com'),
  ('andres.rojas', 'SCA002', 'ADMINISTRADOR_COMERCIAL', 'andres.rojas@sca002.sgp-demo.test', 'andres.rojas@suministroscaribe.example.com'),
  ('maria.castro', 'SCA002', 'TRABAJADOR', 'maria.castro@sca002.sgp-demo.test', 'maria.castro@suministroscaribe.example.com'),
  ('diego.martinez', 'DNS003', 'ADMINISTRADOR_COMERCIAL', 'diego.martinez@dns003.sgp-demo.test', 'diego.martinez@distribucionesnova.example.com'),
  ('sofia.ramirez', 'DNS003', 'TRABAJADOR', 'sofia.ramirez@dns003.sgp-demo.test', 'sofia.ramirez@distribucionesnova.example.com'),
  ('miguel.ortiz', 'CHS004', 'ADMINISTRADOR_COMERCIAL', 'miguel.ortiz@chs004.sgp-demo.test', 'miguel.ortiz@comercialhorizonte.example.com'),
  ('valentina.gomez', 'CHS004', 'TRABAJADOR', 'valentina.gomez@chs004.sgp-demo.test', 'valentina.gomez@comercialhorizonte.example.com'),
  ('sergio.vega', 'LAS005', 'ADMINISTRADOR_COMERCIAL', 'sergio.vega@las005.sgp-demo.test', 'sergio.vega@logisticaandina.example.com'),
  ('paula.navarro', 'LAS005', 'TRABAJADOR', 'paula.navarro@las005.sgp-demo.test', 'paula.navarro@logisticaandina.example.com');
create temp table norm_proveedores (codigo text, nit text, razon_original text not null, razon_nueva text not null,
  email_original text not null, email_nuevo text not null, primary key (codigo, nit));
insert into norm_proveedores values
  ('MVSA01', '999200000011', 'Granos del Valle S.A.S. (demo)', 'Granos del Valle S.A.S.',
   'ventas@granos-del-valle.sgp-demo.test', 'ventas@granosdelvalle.example.com'),
  ('MVSA01', '999200000012', 'Lácteos La Pradera S.A.S. (demo)', 'Lácteos La Pradera S.A.S.',
   'pedidos@lacteos-pradera.sgp-demo.test', 'pedidos@lacteoslapradera.example.com'),
  ('MVSA01', '999200000099', 'Bebidas del Río S.A.S. (demo)', 'Bebidas del Río S.A.S.',
   'ventas@bebidas-del-rio.sgp-demo.test', 'ventas@bebidasdelrio.example.com'),
  ('SCA002', '999200000021', 'Papelería Mayorista del Caribe (demo)', 'Papelería Mayorista del Caribe',
   'ventas@papeleria-caribe.sgp-demo.test', 'ventas@papeleriacaribe.example.com'),
  ('SCA002', '999200000099', 'Bebidas del Río S.A.S. (demo)', 'Bebidas del Río S.A.S.',
   'ventas@bebidas-del-rio.sgp-demo.test', 'ventas@bebidasdelrio.example.com');
create temp table norm_productos (codigo text, sku text, descripcion_original text not null, primary key (codigo, sku));
insert into norm_productos select v.codigo, v.sku, 'Producto ficticio de demostración.' from (values
  ('MVSA01', 'MV-ARR-500'), ('MVSA01', 'MV-ACE-1L'), ('MVSA01', 'MV-GAL-PQ'), ('MVSA01', 'MV-LEC-1L'),
  ('MVSA01', 'MV-HUE-30'), ('MVSA01', 'MV-CAF-250'), ('MVSA01', 'AGUA-600'),
  ('SCA002', 'SC-RES-CAR'), ('SCA002', 'SC-BOL-NEG'), ('SCA002', 'SC-CUA-100'), ('SCA002', 'AGUA-600')) v(codigo, sku);
-- Filas que se normalizan (tabla, id), localizadas por su clave natural.
create temp table norm_filas as
  select 'empresas'::text as tabla, e.id from empresas e join norm_empresas n on n.codigo = e.codigo and n.nit = e.nit
  union all
  select 'usuarios', u.id from usuarios u join norm_usuarios n on n.username = u.username
  union all
  select 'proveedores', p.id from proveedores p join empresas e on e.id = p.empresa_id
    join norm_empresas ne on ne.codigo = e.codigo and ne.nit = e.nit join norm_proveedores n on n.codigo = e.codigo and n.nit = p.nit
  union all
  select 'productos', p.id from productos p join empresas e on e.id = p.empresa_id
    join norm_empresas ne on ne.codigo = e.codigo and ne.nit = e.nit join norm_productos n on n.codigo = e.codigo and n.sku = p.sku;
SQL
)

# Huella para comprobar que solo cambia lo acordado. Líneas «sección|tabla|filas|md5»:
#   parcial  todas las tablas, todas las filas, sin las columnas que se normalizan
#   ajenas   filas completas de empresas, usuarios, proveedores y productos que NO se normalizan
#   objetivo filas completas que se normalizan
# $1 = función que ejecuta SQL por stdin.
huella_normalizacion() {
    "$1" <<SQL
$NORM_OBJETIVOS
create temp table norm_excluidas (tabla text primary key, columnas text[] not null);
insert into norm_excluidas values ('empresas', '{demo}'), ('usuarios', '{email}'),
  ('proveedores', '{razon_social,email}'), ('productos', '{descripcion}');
select format('select %L || ''|'' || count(*) || ''|'' || md5(coalesce(string_agg(x, E''\\n'' order by x), '''')) '
              'from (select (to_jsonb(t) - %L::text[])::text as x from %I t) s',
              'parcial|' || t.table_name, coalesce(x.columnas, '{}'), t.table_name)
from information_schema.tables t left join norm_excluidas x on x.tabla = t.table_name
where t.table_schema = 'public' and t.table_type = 'BASE TABLE' order by t.table_name
\gexec
select format('select %L || ''|'' || count(*) || ''|'' || md5(coalesce(string_agg(t::text, E''\\n'' order by t::text), '''')) '
              'from %I t where t.id %s (select f.id from norm_filas f where f.tabla = %L)',
              s.seccion || '|' || x.tabla, x.tabla, s.operador, x.tabla)
from norm_excluidas x cross join (values ('ajenas', 'not in'), ('objetivo', 'in')) s(seccion, operador)
order by s.seccion, x.tabla
\gexec
SQL
}

# SQL de la normalización: una sola transacción. Si una comprobación falla no se escribe nada.
# Idempotente: solo actualiza lo que aún no tiene el valor de destino. Salida: «tabla|filas cambiadas».
sql_normalizacion() { # $1 = aplicar | revertir
    printf '\\set direccion %s\n' "$1"
    printf '%s\n' 'begin;' "set local lock_timeout = '10s';" \
        'lock table empresas, usuarios, proveedores, productos in share row exclusive mode;' "$NORM_OBJETIVOS"
    cat <<'SQL'
create temp table norm_cfg as select :'direccion'::text = 'aplicar' as aplicar, :'direccion'::text as direccion;
do $$
declare
  aplicar boolean;
  n bigint;
  lista text;
  problemas text[] := '{}';
begin
  if (select direccion from norm_cfg) not in ('aplicar', 'revertir') then
    raise exception 'direccion debe ser aplicar o revertir';
  end if;
  select c.aplicar into aplicar from norm_cfg c;
  -- Cada clave natural localiza exactamente una fila.
  select count(*) into n from norm_filas where tabla = 'empresas';
  if n <> 5 then problemas := problemas || format('empresas localizadas por código y NIT: %s de 5', n); end if;
  select count(*) into n from norm_filas where tabla = 'usuarios';
  if n <> 10 then problemas := problemas || format('cuentas localizadas: %s de 10', n); end if;
  select count(*) into n from norm_filas where tabla = 'proveedores';
  if n <> 5 then problemas := problemas || format('proveedores localizados: %s de 5', n); end if;
  select count(*) into n from norm_filas where tabla = 'productos';
  if n <> 11 then problemas := problemas || format('productos localizados: %s de 11', n); end if;
  -- Cada cuenta sigue en su empresa y con su rol.
  select string_agg(n.username, ', ') into lista from norm_usuarios n
    where not exists (select 1 from usuarios u join empresas e on e.id = u.empresa_id join norm_empresas ne on ne.codigo = e.codigo and ne.nit = e.nit
                      where u.username = n.username and e.codigo = n.codigo and u.rol = n.rol);
  if lista is not null then problemas := problemas || ('cuentas fuera de su empresa o con otro rol: ' || lista); end if;
  -- Valor actual: el de la seed o el ya normalizado. Otro valor lo puso una persona y se respeta.
  select string_agg(u.username, ', ') into lista from usuarios u join norm_usuarios n on n.username = u.username
    where u.email not in (n.email_original, n.email_nuevo);
  if lista is not null then problemas := problemas || ('correo cambiado a mano en: ' || lista); end if;
  select string_agg(n.codigo || '/' || n.nit, ', ') into lista from proveedores p join norm_filas f on f.tabla = 'proveedores' and f.id = p.id
    join empresas e on e.id = p.empresa_id join norm_proveedores n on n.codigo = e.codigo and n.nit = p.nit
    where p.razon_social not in (n.razon_original, n.razon_nueva) or p.email not in (n.email_original, n.email_nuevo);
  if lista is not null then problemas := problemas || ('proveedor cambiado a mano: ' || lista); end if;
  select string_agg(n.codigo || '/' || n.sku, ', ') into lista from productos p join norm_filas f on f.tabla = 'productos' and f.id = p.id
    join empresas e on e.id = p.empresa_id join norm_productos n on n.codigo = e.codigo and n.sku = p.sku
    where p.descripcion is not null and p.descripcion <> n.descripcion_original;
  if lista is not null then problemas := problemas || ('descripción de producto escrita a mano: ' || lista); end if;
  -- Unicidad (uq_usuarios_email): ningún correo de destino lo usa otra cuenta.
  select string_agg(u.username || ' (' || u.email || ')', ', ') into lista from usuarios u join norm_usuarios n
    on u.email = case when aplicar then n.email_nuevo else n.email_original end
    where u.username <> n.username;
  if lista is not null then problemas := problemas || ('correo de destino en uso por otra cuenta: ' || lista); end if;
  if cardinality(problemas) > 0 then
    raise exception 'Normalización detenida sin escribir nada: %', array_to_string(problemas, '; ');
  end if;
end $$;
with c as (select aplicar from norm_cfg),
     cambio as (update empresas t set demo = not c.aplicar from c, norm_filas f
                where f.tabla = 'empresas' and f.id = t.id and t.demo is distinct from (not c.aplicar) returning t.id)
select 'empresas|' || count(*) from cambio;
with c as (select aplicar from norm_cfg),
     cambio as (update usuarios t set email = case when c.aplicar then n.email_nuevo else n.email_original end
                from c, norm_usuarios n where n.username = t.username
                  and t.email is distinct from case when c.aplicar then n.email_nuevo else n.email_original end returning t.id)
select 'usuarios|' || count(*) from cambio;
with c as (select aplicar from norm_cfg),
     cambio as (update proveedores t
                set razon_social = case when c.aplicar then n.razon_nueva else n.razon_original end,
                    email = case when c.aplicar then n.email_nuevo else n.email_original end
                from c, norm_filas f, empresas e, norm_proveedores n
                where f.tabla = 'proveedores' and f.id = t.id and e.id = t.empresa_id and n.codigo = e.codigo and n.nit = t.nit
                  and (t.razon_social, t.email) is distinct from
                      (case when c.aplicar then n.razon_nueva else n.razon_original end,
                       case when c.aplicar then n.email_nuevo else n.email_original end) returning t.id)
select 'proveedores|' || count(*) from cambio;
with c as (select aplicar from norm_cfg),
     cambio as (update productos t set descripcion = case when c.aplicar then null else n.descripcion_original end
                from c, norm_filas f, empresas e, norm_productos n
                where f.tabla = 'productos' and f.id = t.id and e.id = t.empresa_id and n.codigo = e.codigo and n.sku = t.sku
                  and t.descripcion is distinct from case when c.aplicar then null else n.descripcion_original end returning t.id)
select 'productos|' || count(*) from cambio;
do $$
declare
  aplicar boolean;
  n bigint;
begin
  select c.aplicar into aplicar from norm_cfg c;
  select count(*) into n from empresas t join norm_filas f on f.tabla = 'empresas' and f.id = t.id where t.demo = not aplicar;
  if n <> 5 then raise exception 'comprobación final: % de 5 empresas con el valor de destino', n; end if;
  select count(*) into n from usuarios t join norm_usuarios u on u.username = t.username
    where t.email = case when aplicar then u.email_nuevo else u.email_original end;
  if n <> 10 then raise exception 'comprobación final: % de 10 cuentas con el correo de destino', n; end if;
  select count(*) into n from proveedores t join norm_filas f on f.tabla = 'proveedores' and f.id = t.id
    join empresas e on e.id = t.empresa_id join norm_proveedores p on p.codigo = e.codigo and p.nit = t.nit
    where t.razon_social = case when aplicar then p.razon_nueva else p.razon_original end
      and t.email = case when aplicar then p.email_nuevo else p.email_original end;
  if n <> 5 then raise exception 'comprobación final: % de 5 proveedores con el valor de destino', n; end if;
  select count(*) into n from productos t join norm_filas f on f.tabla = 'productos' and f.id = t.id
    join empresas e on e.id = t.empresa_id join norm_productos p on p.codigo = e.codigo and p.sku = t.sku
    where t.descripcion is not distinct from case when aplicar then null else p.descripcion_original end;
  if n <> 11 then raise exception 'comprobación final: % de 11 productos con el valor de destino', n; end if;
end $$;
commit;
SQL
}

# Compara las secciones indicadas (expresión regular, p. ej. 'parcial|ajenas') de dos huellas.
huellas_iguales() { diff <(grep -E "^($1)\|" "$2") <(grep -E "^($1)\|" "$3"); }

# Sin cambios en producción: copia de la BD en un PostgreSQL desechable sin red; allí aplica la
# normalización, la repite (debe cambiar 0 filas), la revierte, la vuelve a aplicar y comprueba que una
# colisión la detiene sin escribir nada. La copia se elimina al terminar.
normalizar_probar() {
    fase "NORMALIZACIÓN — prueba en una copia desechable (producción no se modifica)"
    exigir_sin_pendientes
    COPIA_PRUEBA=$EJEC/copia-prueba.dump
    docker compose exec -T postgres sh -c 'pg_dump -U "$POSTGRES_USER" -d "$POSTGRES_DB" -Fc' > "$COPIA_PRUEBA"
    [[ -s $COPIA_PRUEBA ]] || alto "El volcado para la prueba está vacío."
    arrancar_contenedor_prueba
    docker exec -i "$CONTENEDOR_PRUEBA" pg_restore -U postgres -d restore_check --no-owner --no-privileges --exit-on-error \
        < "$COPIA_PRUEBA" || alto "No se pudo restaurar la copia de prueba."
    rm -f "$COPIA_PRUEBA"; COPIA_PRUEBA=
    ok "Copia de la BD en $CONTENEDOR_PRUEBA (sin red)"
    copia() { docker exec -i "$CONTENEDOR_PRUEBA" psql -U postgres -d restore_check -v ON_ERROR_STOP=1 -X -q -At -F "|"; }
    local h=$EJEC/huella-copia c=$EJEC/cambios-copia total

    huella_normalizacion copia > "$h-0"
    sql_normalizacion aplicar | copia > "$c-1" || alto "La normalización falló en la copia (motivo arriba)."
    total=$(awk -F'|' '{ s += $2 } END { print s + 0 }' "$c-1")
    info "1.ª aplicación: $(tr '\n' ' ' < "$c-1")"
    huella_normalizacion copia > "$h-1"
    huellas_iguales 'parcial|ajenas' "$h-0" "$h-1" || alto "La normalización cambió algo fuera de los campos acordados."
    ok "Fuera de los campos acordados no cambia nada ($(grep -c '^parcial|' "$h-1") tablas, filas ajenas incluidas)"

    sql_normalizacion aplicar | copia > "$c-2" || alto "La segunda aplicación falló."
    grep -qv '|0$' "$c-2" && alto "La segunda aplicación cambió filas: $(tr '\n' ' ' < "$c-2")"
    huella_normalizacion copia > "$h-2"
    huellas_iguales 'parcial|ajenas|objetivo' "$h-1" "$h-2" || alto "La segunda aplicación cambió datos."
    ok "Idempotente: la segunda aplicación cambia 0 filas y la huella completa es idéntica"

    sql_normalizacion revertir | copia > "$c-3" || alto "La reversión falló en la copia."
    huella_normalizacion copia > "$h-3"
    huellas_iguales 'parcial|ajenas' "$h-0" "$h-3" || alto "La reversión cambió algo fuera de los campos acordados."
    if (( total == 31 )); then
        huellas_iguales 'parcial|ajenas|objetivo' "$h-0" "$h-3" || alto "La reversión no devuelve exactamente el estado inicial."
        ok "Reversible: «revertir» devuelve la huella completa inicial"
    else
        info "La BD ya estaba normalizada (o en parte): «revertir» deja los valores de la seed"
    fi
    sql_normalizacion aplicar | copia > "$c-4" || alto "La nueva aplicación tras revertir falló."
    huella_normalizacion copia > "$h-4"
    huellas_iguales 'parcial|ajenas|objetivo' "$h-1" "$h-4" || alto "Aplicar → revertir → aplicar no llega al mismo estado."
    ok "Aplicar → revertir → aplicar llega exactamente al mismo estado"

    # Colisiones, solo en la copia: un correo de destino ocupado por otra cuenta y una descripción escrita a mano.
    sql_normalizacion revertir | copia > /dev/null || alto "No se pudo preparar la prueba de colisión."
    copia <<'SQL' || alto "No se pudo preparar la prueba de colisión."
update usuarios set email = 'carlos.mendoza@mercadoverde.example.com'
  where id = (select min(id) from usuarios where rol = 'SUPER_ADMIN');
update productos set descripcion = 'Descripción escrita a mano'
  where sku = 'MV-ARR-500' and empresa_id = (select id from empresas where codigo = 'MVSA01' and nit = '999100000001');
SQL
    huella_normalizacion copia > "$h-5"
    if sql_normalizacion aplicar | copia > "$c-6" 2> "$EJEC/error-colision"; then
        alto "La normalización no se detuvo ante una colisión."
    fi
    if ! grep -q 'correo de destino en uso por otra cuenta' "$EJEC/error-colision" \
            || ! grep -q 'descripción de producto escrita a mano' "$EJEC/error-colision"; then
        alto "La normalización se detuvo por otro motivo: $(cat "$EJEC/error-colision")"
    fi
    huella_normalizacion copia > "$h-6"
    huellas_iguales 'parcial|ajenas|objetivo' "$h-5" "$h-6" || alto "La normalización detenida escribió datos."
    ok "Una colisión (correo ocupado, descripción escrita a mano) la detiene sin escribir nada"

    limpiar_contenedor_prueba || alto "No se pudo eliminar la copia de prueba."
    ok "Copia de prueba eliminada. Producción no se ha modificado"
}

# Aplica (o, con DIRECCION=revertir, deshace) la normalización en producción. Sin reinicios ni compilación.
normalizar() {
    local direccion=${DIRECCION:-aplicar}
    fase "NORMALIZACIÓN DE LOS DATOS DE PRUEBA ($direccion)"
    [[ $direccion == aplicar || $direccion == revertir ]] || alto "DIRECCION debe ser aplicar o revertir."
    [[ ${CONFIRMAR_NORMALIZACION:-} == si ]] || alto "Solo se ejecuta con CONFIRMAR_NORMALIZACION=si (autorización expresa)."
    exigir_sin_pendientes
    validar_clave
    esperar_sano backend 30 || alto "El backend no está sano."
    sin_variables_demo || alto "El backend tiene variables demo: «bash $0 recuperar» antes de seguir."
    info "Pruebas HTTP previas: si fallan, no se toca nada"
    pruebas_http
    respaldar sgp_pre_normalizacion
    huella_normalizacion sql > "$EJEC/huella-antes"
    sql_normalizacion "$direccion" | sql > "$EJEC/cambios" \
        || alto "Normalización detenida: la transacción se deshizo y no se escribió nada (motivo arriba)."
    ok "Filas cambiadas: $(tr '\n' ' ' < "$EJEC/cambios")"
    huella_normalizacion sql > "$EJEC/huella-despues"
    huellas_iguales 'parcial|ajenas' "$EJEC/huella-antes" "$EJEC/huella-despues" \
        || alto "Cambió algo fuera de los campos acordados (¿actividad durante la ventana?). Revísalo antes de seguir; respaldo previo: $RESPALDO_HECHO"
    ok "Fuera de los campos acordados no cambió nada ($(grep -c '^parcial|' "$EJEC/huella-despues") tablas; empresas reales y su historial incluidos)"
    pruebas_http
    esperar_sano backend 30 || alto "El backend no está sano al terminar."
    ok "Normalización ($direccion) terminada. Respaldo previo: $RESPALDO_HECHO"
}

# -----------------------------------------------------------------------------
# DESTRUCTIVO. Orden: integridad y restauración de prueba del volcado → imágenes acordes con su esquema
# (se decide antes de tocar nada) → servicios detenidos → respaldo verificado de la BD actual →
# borrado y restauración → mismas filas y mismo Flyway que la copia de prueba → servicios en marcha.
restaurar() {
    fase "RESTAURACIÓN DE LA BASE DE DATOS (destructiva)"
    exigir_sin_pendientes fase2 fase3 rollback restauracion
    cargar_despliegue
    local r=${RESPALDO:-} nombre
    [[ $r == "$RESPALDOS"/*.dump && -f $r && ! -L $r ]] || alto "Indica RESPALDO=<ruta de un .dump definitivo de $RESPALDOS> (nunca un .parcial)."
    nombre=${r##*/}
    [[ ${CONFIRMAR_RESTAURACION:-} == "$nombre" ]] || alto "Para confirmar, repite el nombre del volcado: CONFIRMAR_RESTAURACION=$nombre"
    [[ -f $r.sha256 ]] || alto "Falta $r.sha256: sin suma de verificación no se restaura."
    (cd "$RESPALDOS" && sha256sum -c --quiet "$nombre.sha256") || alto "El SHA-256 de $r no coincide."
    ok "Integridad de $nombre (SHA-256)"
    docker compose exec -T postgres sh -c 'test "$POSTGRES_DB" != postgres' \
        || alto "POSTGRES_DB es «postgres»: este procedimiento no borra la base de mantenimiento."

    iniciar_operacion restauracion "respaldo=$r"
    if (( REANUDANDO )); then
        if [[ $r == "$(dato restauracion respaldo)" ]]; then
            info "Se repite la restauración pendiente de $nombre"
        elif [[ -n $(dato restauracion respaldo_previo) && $r == "$(dato restauracion respaldo_previo)" ]]; then
            anotar restauracion "respaldo=$r"
            info "Se aborta la restauración pendiente devolviendo la BD a su respaldo previo ($nombre)"
        else
            alto "La restauración pendiente es de $(dato restauracion respaldo); con REANUDAR=1 solo se admite ese volcado o su respaldo previo ($(dato restauracion respaldo_previo))."
        fi
    fi
    paso restauracion verificando
    verificar_restauracion "$r"
    local copia=$EJEC/conteos-restaurados-$nombre flyway_copia=$EJEC/flyway-restaurado-$nombre
    if [[ -f $r.conteos ]]; then
        diff "$r.conteos" "$copia" || alto "La copia de prueba no tiene las filas anotadas al crear el respaldo."
        ok "La copia de prueba coincide con los conteos anotados al respaldar"
    fi

    # Punto de partida, solo la primera vez: esquema de la BD actual e imágenes en marcha (etiquetadas).
    local servicio etiqueta
    if [[ -z $(dato restauracion flyway_original) ]]; then
        flyway > "$EJEC/flyway-original"
        for servicio in backend frontend; do
            etiqueta="$IMAGENES-$servicio:pre-restore-$MARCA"
            docker image tag "$(docker inspect -f '{{.Image}}' "$(id_de "$servicio")")" "$etiqueta"
            anotar restauracion "imagen_original_$servicio=$etiqueta"
        done
        anotar restauracion "flyway_original=$EJEC/flyway-original"
    fi
    local origen
    if cmp -s "$flyway_copia" "$(dato restauracion flyway_original)"; then origen=original
    elif cmp -s "$flyway_copia" "$DESP/flyway-previo"; then origen=fase1
    else alto "El esquema (Flyway) de $nombre no coincide con el de la BD actual ni con el de la fase 1: elegir la imagen exige una decisión manual (README)."; fi
    info "Imágenes acordes con el esquema del volcado: $( [[ $origen == original ]] && echo 'las que estaban en marcha' || echo 'las de la fase 1')"

    paso restauracion deteniendo
    docker compose stop frontend backend
    if ya_paso restauracion bd-borrada; then
        local previo; previo=$(dato restauracion respaldo_previo)
        [[ -f $previo ]] || alto "Falta el respaldo previo $previo de la primera ejecución."
        ok "La BD actual es una restauración incompleta: el respaldo de su estado original sigue siendo $previo"
    else
        paso restauracion respaldo-previo
        respaldar sgp_pre_restore
        anotar restauracion "respaldo_previo=$RESPALDO_HECHO"
    fi

    paso restauracion bd-borrada
    docker compose exec -T postgres sh -c 'dropdb -U "$POSTGRES_USER" --maintenance-db=postgres --if-exists "$POSTGRES_DB" && createdb -U "$POSTGRES_USER" --maintenance-db=postgres "$POSTGRES_DB"'
    docker compose exec -T postgres sh -c 'pg_restore -U "$POSTGRES_USER" -d "$POSTGRES_DB" --no-owner --exit-on-error' < "$r" \
        || alto "pg_restore falló: la BD está incompleta y los servicios siguen detenidos (README, «Recuperación»)."
    paso restauracion restaurada
    conteos_tablas sql > "$EJEC/conteos-tras-restaurar"
    diff "$copia" "$EJEC/conteos-tras-restaurar" || alto "La BD restaurada no tiene las filas de la copia de prueba (servicios detenidos)."
    flyway > "$EJEC/flyway-tras-restaurar"
    cmp -s "$flyway_copia" "$EJEC/flyway-tras-restaurar" || alto "Flyway de la BD restaurada no coincide con el del volcado (servicios detenidos)."
    ok "BD restaurada: mismas filas que la copia de prueba en $(wc -l < "$copia") tablas y mismo Flyway"

    for servicio in backend frontend; do
        if [[ $origen == original ]]; then etiqueta=$(dato restauracion "imagen_original_$servicio")
        else etiqueta=$(<"$DESP/imagen-previa-$servicio"); fi
        docker image tag "$etiqueta" "$IMAGENES-$servicio:latest"
    done
    paso restauracion arrancando
    arrancar_servicios || alto "La BD está restaurada, pero los servicios no quedaron sanos: «bash $0 recuperar» y docker compose logs."
    ok "Backend y frontend sanos, sin perfil demo"
    printf '%s restaurado=%s respaldo_previo=%s\n' "$(date -Iseconds)" "$r" "$(dato restauracion respaldo_previo)" >> "$DESP/restauraciones"
    cerrar_operacion restauracion completada
    local op
    for op in fase2 fase3 rollback; do
        if [[ -e $DIARIO/$op ]]; then cerrar_operacion "$op" abortada-por-restauracion; fi
    done
    ok "Restaurado $nombre. Si se volvió al esquema de la fase 1, el código vuelve a commit-previo aparte (README)."
}

# -----------------------------------------------------------------------------
# Tras cualquier interrupción, incluso sin diario (reinicio del VPS, kill -9): devuelve el backend al
# perfil normal y deja todo lo necesario para reanudar o abortar. Nunca borra respaldos, diarios,
# etiquetas de imagen ni la contraseña demo, y nunca arranca el backend sobre una BD a medio restaurar.
recuperar() {
    fase "RECUPERACIÓN — operaciones incompletas y vuelta al perfil normal"
    if [[ -e $OVERRIDE_HEREDADO ]]; then
        mv "$OVERRIDE_HEREDADO" "$EJEC/override-heredado.yml"
        info "Override de la versión anterior del script trasladado a $EJEC (no contiene secretos)"
    fi
    local bd_en_riesgo=0 p
    if [[ -e $DIARIO/restauracion ]]; then
        p=$(dato restauracion paso)
        if [[ $p == arrancando ]]; then
            info "La BD ya estaba restaurada y verificada: se arrancan los servicios"
            arrancar_servicios || alto "Los servicios no quedaron sanos: docker compose logs backend"
            cerrar_operacion restauracion completada-en-recuperacion
        elif ya_paso restauracion bd-borrada; then
            bd_en_riesgo=1
            aviso "Restauración interrumpida con la BD borrada o a medio restaurar: NO se arranca el backend"
            aviso "(sobre una BD vacía, Flyway crearía un esquema nuevo y la aplicación arrancaría sin datos)."
            info "Reanudar: REANUDAR=1 RESPALDO=$(dato restauracion respaldo) CONFIRMAR_RESTAURACION=$(basename "$(dato restauracion respaldo)") bash $0 restaurar"
            info "Abortar (estado original): REANUDAR=1 RESPALDO=$(dato restauracion respaldo_previo) CONFIRMAR_RESTAURACION=$(basename "$(dato restauracion respaldo_previo)") bash $0 restaurar"
        else
            info "La restauración se interrumpió antes de tocar la BD (paso $p): se arrancan los servicios tal como estaban"
            arrancar_servicios || alto "Los servicios no quedaron sanos: docker compose logs backend"
            cerrar_operacion restauracion abortada-bd-intacta
        fi
    fi

    if (( ! bd_en_riesgo )); then
        if sin_variables_demo; then
            ok "El backend no tiene el perfil demo"
        else
            info "El backend tiene el perfil demo (fase 3 interrumpida o reinicio del VPS): se recrea sin él"
            volver_perfil_normal || alto "El backend no quedó sano o conserva variables demo: docker compose logs backend"
            ok "Backend sano y sin perfil demo"
        fi
        if [[ -e $DIARIO/fase3 ]]; then
            anotar fase3 "recuperado=$MARCA"
            local antes; antes=$(dato fase3 huella)
            if [[ -f $antes ]]; then
                huella_ajena > "$EJEC/ajeno-recuperacion"
                if diff -q "$antes" "$EJEC/ajeno-recuperacion" >/dev/null; then ok "Datos ajenos a la seed idénticos a los de antes de la seed"
                else aviso "Los datos ajenos a la seed cambiaron desde $antes: no reanudes sin investigar (diff de ambos archivos)."; fi
            fi
            info "Fase 3 incompleta (paso $(dato fase3 paso)). Reanudar: REANUDAR=1 CONFIRMAR_SEED=si bash $0 fase3"
            info "Abortar: restaurar $(dato fase3 respaldo_pre_seed) con «restaurar» (README)"
        fi
    fi
    if [[ -e $DIARIO/fase2 ]]; then
        info "Fase 2 incompleta (paso $(dato fase2 paso)); backend $(docker inspect -f '{{.State.Health.Status}}' "$(id_de backend)" 2>/dev/null || echo 'sin contenedor')."
        info "Reanudar: REANUDAR=1 bash $0 fase2 · Abortar: bash $0 rollback (si Flyway no cambió) o «restaurar»"
    fi
    if [[ -e $DIARIO/rollback ]]; then
        info "Rollback incompleto (paso $(dato rollback paso)). Reanudar: REANUDAR=1 bash $0 rollback"
    fi
    if [[ -z $(pendientes) ]]; then ok "No queda ninguna operación incompleta"
    else info "Siguen abiertas: $(pendientes | tr '\n' ' ')(los diarios y respaldos se conservan)"; fi
}

estado() {
    fase "ESTADO (solo lectura)"
    local indicios=0 op lista actual
    actual=$(cat "$ESTADO/actual" 2>/dev/null || echo ninguno)
    info "HEAD $(git rev-parse HEAD) · despliegue vigente $actual"
    if [[ -d $ESTADO/despliegues/$actual ]]; then
        local d=$ESTADO/despliegues/$actual
        info "  objetivo $(cat "$d/objetivo" 2>/dev/null) · commit previo $(cat "$d/commit-previo" 2>/dev/null)"
        info "  fase 1 $( [[ -e $d/fase1.ok ]] && echo hecha || echo incompleta) · fase 2 $( [[ -e $d/fase2.ok ]] && echo hecha || echo pendiente)$( [[ -e $d/rollback.hecho ]] && echo ' · ROLLBACK hecho')"
    fi
    for op in $(pendientes); do
        indicios=$(( indicios + 1 )); aviso "Operación INCOMPLETA «$op», último paso $(dato "$op" paso):"; mostrar_diario "$op"
    done
    lista=$(contenedores_prueba || true)
    [[ -z $lista ]] || { indicios=$(( indicios + 1 )); aviso "Restauraciones de prueba que siguen existiendo: $(tr '\n' ' ' <<<"$lista")"; }
    lista=$(respaldos_parciales)
    [[ -z $lista ]] || { indicios=$(( indicios + 1 )); aviso "Respaldos parciales (nunca válidos):"$'\n'"$lista"; }
    [[ ! -e $OVERRIDE_HEREDADO ]] || { indicios=$(( indicios + 1 )); aviso "Existe $OVERRIDE_HEREDADO (versión anterior del script)"; }
    info "Backend: $(docker inspect -f '{{.State.Status}} {{.State.Health.Status}}' "$(id_de backend)" 2>/dev/null || echo 'sin contenedor')"
    if sin_variables_demo; then ok "Backend sin perfil demo"
    else indicios=$(( indicios + 1 )); aviso "El backend tiene variables demo"; fi
    if (( indicios )); then aviso "$indicios indicio(s) de una operación incompleta: bash $0 recuperar (README, «Recuperación»)"
    else ok "Sin operaciones incompletas"; fi
    if [[ -e $DIARIO/restauracion ]] && ya_paso restauracion bd-borrada; then
        aviso "La BD puede estar vacía o a medio restaurar: no se consulta."; return 0
    fi
    if [[ -s $CLAVE ]]; then info "Contraseña demo: definida en $CLAVE (no se imprime)"
    else info "Contraseña demo: sin definir (bash $0 clave-demo)"; fi
    info "Empresas demo (código|proveedores|productos|solicitudes|órdenes|recepciones|ventas|devoluciones):"
    conteos_demo || aviso "No se pudo consultar la BD"
    info "Cuentas demo:"
    sql <<<"select u.username || '|' || u.rol || '|' || e.codigo || '|' || u.estado from usuarios u join empresas e on e.id = u.empresa_id where e.demo order by e.codigo, u.rol;" \
        || aviso "No se pudo consultar la BD"
}

# -----------------------------------------------------------------------------
# Limpieza de salida (error, alto, Ctrl-C, TERM). Es una ayuda, no la garantía: si no llega a ejecutarse
# (kill -9, corte de luz), el diario y «recuperar» cubren lo mismo. No hace nada destructivo.
# shellcheck disable=SC2329  # se invoca desde el trap EXIT
al_salir() {
    local rc=$? op
    trap - ERR EXIT
    trap '' INT TERM                       # que un segundo Ctrl-C no corte la limpieza a medias
    set +e
    limpiar_contenedor_prueba
    [[ -z $TMP_HTTP ]] || rm -rf "$TMP_HTTP"
    [[ -z $COPIA_PRUEBA ]] || rm -f "$COPIA_PRUEBA"
    for op in "${PARCIALES[@]}"; do         # solo los parciales de esta ejecución: nunca fueron válidos
        if [[ -e $op ]]; then rm -f "$op"; info "Eliminado el respaldo parcial $op (incompleto)"; fi
    done
    if (( PERFIL_DEMO_ACTIVO )) && ! sin_variables_demo; then
        aviso "Salida con el perfil demo activo: se intenta volver al perfil normal (espera; no interrumpas)…"
        if volver_perfil_normal; then
            paso fase3 normal-tras-interrupcion
            ok "Backend de nuevo sin perfil demo. La fase 3 sigue incompleta: «bash $0 recuperar»"
        else
            aviso "No se pudo volver al perfil normal: «bash $0 recuperar» en cuanto sea posible"
        fi
    fi
    if (( rc != 0 )); then
        for op in $(pendientes); do
            aviso "Queda incompleta la operación «$op» (paso $(dato "$op" paso)): «bash $0 estado» y README, «Recuperación»"
        done
        [[ -z $EJEC ]] || info "Registro de esta ejecución: $EJEC/registro.log"
    fi
    exit "$rc"
}

main() {
    local orden=${1:-}
    case $orden in
        fase1|fase2|clave-demo|fase3|fase4|estado|recuperar|rollback|restaurar|normalizar-probar|normalizar) ;;
        *) echo "Uso: $0 fase1|fase2|clave-demo|fase3|fase4|estado|recuperar|rollback|restaurar|normalizar-probar|normalizar"; exit 2 ;;
    esac
    command -v flock >/dev/null || { echo "  [ALTO] Falta flock (util-linux): sin él no se puede impedir una ejecución simultánea."; exit 1; }
    mkdir -p "$PRIVADO" "$ESTADO" "$DIARIO" "$HISTORIAL" "$ESTADO/despliegues" "$ESTADO/ejecuciones"
    chmod 700 "$PRIVADO" "$ESTADO"
    EJEC=$ESTADO/ejecuciones/$MARCA-$orden
    mkdir -m 700 "$EJEC"

    # Un corte de la sesión SSH (SIGHUP) no interrumpe la ejecución: se ignora aquí y en los procesos
    # hijos, y tee sigue escribiendo el registro aunque la terminal desaparezca. tee ignora además
    # Ctrl-C (-i): si muriera, la limpieza de salida moriría por SIGPIPE al escribir.
    trap '' HUP
    local tee_opc=(-i)
    if tee --output-error=warn </dev/null >/dev/null 2>&1; then tee_opc+=(--output-error=warn); fi
    exec > >(tee "${tee_opc[@]}" -a "$EJEC/registro.log") 2>&1
    trap al_salir EXIT
    trap 'exit 130' INT
    trap 'exit 143' TERM

    # Cerrojo exclusivo; el núcleo lo libera al morir el proceso (también con kill -9), así que nunca
    # queda un cerrojo huérfano. «estado» solo pide uno compartido y, si no lo obtiene, avisa.
    exec 9<>"$CERROJO"
    if [[ $orden == estado ]]; then
        flock -n -s 9 || aviso "Otra ejecución en curso ($(cat "$CERROJO")): los datos pueden estar cambiando."
    else
        flock -n 9 || alto "Otra ejecución en curso (PID, marca y orden: $(cat "$CERROJO")). No se espera ni se fuerza."
        printf '%s %s %s\n' "$$" "$MARCA" "$orden" > "$CERROJO"
    fi
    info "Ejecución $MARCA ($orden) · registro y datos en $EJEC"
    cd "$REPO"
    [[ $orden == estado || $orden == clave-demo ]] || limpiar_huerfanos
    case $orden in
        fase1) fase1 ;;
        fase2) fase2 ;;
        clave-demo) clave_demo ;;
        fase3) fase3 ;;
        fase4) fase4 ;;
        estado) estado ;;
        recuperar) recuperar ;;
        rollback) rollback ;;
        restaurar) restaurar ;;
        normalizar-probar) normalizar_probar ;;
        normalizar) normalizar ;;
    esac
}

# Todo el archivo se lee antes de ejecutar nada: cambiarlo a mitad no altera esta ejecución.
main "$@"
exit $?
