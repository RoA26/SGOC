# Despliegue en Aatenea (VPS de rrtf.duckdns.org)

Procedimiento por etapas, cada una con su punto de control. **Ninguna etapa se ejecuta sin
autorización expresa**, y la seed demo tiene una autorización propia (`CONFIRMAR_SEED=si`). La base
de datos de Aatenea contiene datos reales: el script se detiene ante cualquier condición insegura y
nunca borra datos, volúmenes ni imágenes. Lo único que elimina son sus propias restauraciones de
prueba (contenedores desechables sin red) y los respaldos `.parcial` que la ejecución en curso no
llegó a completar.

| Ruta en el VPS | Contenido |
| --- | --- |
| `/root/sgp-unisen` | Repositorio (`REPO`) |
| `/root/sgp-private` (700) | Copia del script, `estado/`, contraseña demo (`demo_password`, 600, la define `clave-demo`), archivo de cuentas (600) y cerrojo `sgp-aatenea.lock` |
| `/root/sgp-private/estado/despliegues/<marca>/` | Datos de cada despliegue, escritos una sola vez: commit previo, objetivo, imágenes de retorno (etiqueta e ID), Flyway, respaldo previo, marcas `fase1.ok`, `fase2.ok`, `rollback.hecho` |
| `/root/sgp-private/estado/actual` | Marca del despliegue vigente (los anteriores se conservan) |
| `/root/sgp-private/estado/en-curso/` | Diario de cada operación **abierta** (`fase2`, `fase3`, `rollback`, `restauracion`) |
| `/root/sgp-private/estado/historial/` | Diarios cerrados, con su resultado (nunca se borran) |
| `/root/sgp-private/estado/ejecuciones/<marca>-<orden>/` | Registro (`registro.log`) y archivos de trabajo de **cada ejecución**: conteos, huellas, override demo |
| `/root/backups` (700) | Volcados `pg_dump -Fc` (600) con su `.sha256` y `.conteos` (600) |

Las rutas se pueden cambiar con `REPO`, `PRIVADO`, `RESPALDOS` y `RAMA` (por defecto `main`).
`PROPIETARIO` (por defecto `root`) es el dueño que se exige a respaldos y archivos privados, e
`IMAGENES` (por defecto `unisen/sgp`) el prefijo de las imágenes de `docker-compose.yml`
(`<prefijo>-backend`, `<prefijo>-frontend`): si no coincide con el compose, las etiquetas
`pre-deploy-<marca>` y el rollback apuntarían a otras imágenes. En Aatenea no se cambian. La
marca (`AAAAMMDD-HHMMSS-PID`) identifica cada ejecución; la de la fase 1 identifica además el
despliegue y sus etiquetas de imagen `pre-deploy-<marca>`.

## 0. En local: commit y publicación

```bash
git status && git diff --stat                        # revisar lo que entra
git add -A backend-spring docs README.md deploy && git commit   # mensaje propuesto en el informe
git push origin main
git fetch origin && git rev-parse HEAD origin/main   # deben coincidir: es el OBJETIVO
```

## 1. En el VPS: requisitos y script del commit aprobado

```bash
flock --version                                      # util-linux; sin flock el script no arranca
tmux new -s sgp                                      # recomendado (el script ya ignora SIGHUP)
cd /root/sgp-unisen
git status --short                                   # debe estar vacío
git fetch origin                                     # no toca el árbol de trabajo
OBJETIVO=<sha completo aprobado>
git rev-parse origin/main                            # debe ser $OBJETIVO
install -d -m 700 /root/sgp-private
git show "$OBJETIVO:deploy/aatenea/sgp-aatenea.sh" > /root/sgp-private/sgp-aatenea.sh
sha256sum /root/sgp-private/sgp-aatenea.sh           # compárala con la del mismo archivo en local
bash /root/sgp-private/sgp-aatenea.sh estado         # debe decir «Sin operaciones incompletas»
```

`/root/sgp-private` debe estar en un sistema de archivos local (flock no es fiable en NFS).

## 2. Etapas

| Etapa | Comando | Qué hace | Punto de control |
| --- | --- | --- | --- |
| Fase 1 | `OBJETIVO=$OBJETIVO bash /root/sgp-private/sgp-aatenea.sh fase1` | Rama, cambios locales, `origin/main = OBJETIVO`, avance en rápido posible, migraciones nuevas (y ninguna modificada), si cambia el frontend, salud y variables (solo nombres) del backend, Flyway. Respaldo nuevo con SHA-256 y **restauración de prueba** en un PostgreSQL desechable sin red (misma imagen; compara filas de todas las tablas). Etiqueta las imágenes actuales `unisen/sgp-{backend,frontend}:pre-deploy-<marca>`. Crea un despliegue nuevo y solo al final lo marca como vigente | Revisar la salida antes de seguir. Con `RESPALDO_PREVIO=<ruta> SHA_RESPALDO_PREVIO=<sha>` verifica además un respaldo anterior |
| Fase 2 | `bash /root/sgp-private/sgp-aatenea.sh fase2` | `git merge --ff-only OBJETIVO`, compila y despliega solo el backend (y el frontend si cambió), espera `healthy`, comprueba que no hay perfil demo, que la seed no se ejecutó, Flyway y un login por Nginx | Probar la aplicación con una cuenta real. Si algo va mal: `rollback` |
| — | `bash /root/sgp-private/sgp-aatenea.sh clave-demo` | Pide dos veces, sin eco, la contraseña **común y pública** de las cuentas demo y la guarda en `/root/sgp-private/demo_password` (600). Nunca sobrescribe una existente. Ver «Contraseña demo» | Antes de la fase 3, con la contraseña ya decidida |
| Fase 3 | `CONFIRMAR_SEED=si bash /root/sgp-private/sgp-aatenea.sh fase3` | Ver «Seed demo» | Solo con autorización expresa |
| Fase 4 | `bash /root/sgp-private/sgp-aatenea.sh fase4` | Archivo privado de cuentas y pruebas de login, permisos y aislamiento por HTTP (solo lectura) | Entregar el archivo de cuentas por un canal privado |
| — | `bash /root/sgp-private/sgp-aatenea.sh estado` | Solo lectura: despliegue vigente, operaciones incompletas, restos de ejecuciones interrumpidas, perfil del backend, empresas y cuentas demo | — |
| — | `bash /root/sgp-private/sgp-aatenea.sh recuperar` | Tras una interrupción: ver «Recuperación» | — |
| — | `bash /root/sgp-private/sgp-aatenea.sh rollback` | Vuelve a las imágenes de la fase 1 sin tocar la BD | Solo si Flyway no cambió (lo comprueba) |
| — | `RESPALDO=… CONFIRMAR_RESTAURACION=… bash … restaurar` | **Destructivo**: ver «Vuelta atrás» | Solo con autorización |
| — | `bash … normalizar-probar` / `CONFIRMAR_NORMALIZACION=si bash … normalizar` | Quita la marca y los textos «demo» de las empresas de la seed: ver «Normalización» | Solo con autorización |

Reglas comunes:

- **Una ejecución a la vez**: cada orden toma un cerrojo exclusivo (`flock`) sobre
  `/root/sgp-private/sgp-aatenea.lock`. Si otra está en curso se detiene en el acto, indicando su
  PID, marca y orden; nunca espera ni fuerza. El núcleo libera el cerrojo cuando el proceso muere,
  incluso con `kill -9`, así que no puede quedar un cerrojo huérfano (mientras siga vivo algún
  proceso hijo, p. ej. un `docker compose up`, el cerrojo sigue tomado, como debe ser). `estado`
  solo pide un cerrojo compartido y, si no lo obtiene, avisa y sigue.
- **Respaldos atómicos**: el volcado, su `.sha256` y su `.conteos` se escriben como `*.parcial`. Solo
  después de comprobar que no hubo escrituras durante el volcado, calcular la suma y completar la
  restauración de prueba se renombran; el `.dump` es lo último. Un `.dump` con nombre definitivo está
  siempre completo y verificado; un `.parcial` **nunca** es un respaldo válido.
- **Sesión SSH**: un corte (SIGHUP) no interrumpe la ejecución; la salida sigue en el registro de la
  ejecución. Aun así, conviene usar `tmux`.
- Cada ejecución deja su registro en `estado/ejecuciones/<marca>-<orden>/registro.log` (sin
  contraseñas, tokens ni valores de variables).

## 3. Seed demo (fase 3)

### Contraseña demo

Las diez cuentas demo comparten **una contraseña pública de demostración**, fácil de comunicar,
que decide una persona: solo protege datos ficticios y nunca debe usarse en una cuenta real ni
proteger información sensible. Su valor no se escribe en el repositorio, la documentación, los
registros ni la salida del script.

- `bash /root/sgp-private/sgp-aatenea.sh clave-demo` la pide dos veces sin eco (escríbela cuando
  aparezca «Contraseña demo:», no antes) y la guarda en `/root/sgp-private/demo_password` (600).
- Formato: 8-72 caracteres, solo letras sin tilde, dígitos, `.`, `_` y `-` (la política de SGP y
  nada que haya que escapar en JSON o YAML). Una contraseña distinta en la confirmación o con otro
  formato no se guarda.
- No sobrescribe una existente: las cuentas demo ya creadas tienen esa contraseña (la seed nunca
  cambia la de una cuenta que exista). Cambiarla antes de la fase 3 exige retirar el archivo a mano.
- Las cuentas reales no se tocan: la seed solo usa esta contraseña al **crear** sus propias cuentas.

### Pasos

1. **Colisiones**: se detiene si algún código, NIT, username o correo de la seed lo usa un registro
   ajeno, si hay cuentas ajenas con correo `*.sgp-demo.test` o si existe **cualquier** otra empresa
   demo (por ejemplo, `DEMO01`…`DEMO06` de la seed anterior). Qué hacer con ellas lo decide una persona.
2. Si ya existen empresas de esta seed (una ejecución anterior), exige `REANUDAR=1`.
3. Abre el diario `en-curso/fase3`. Respaldo inmediato con SHA-256 y restauración de prueba aislada.
4. **Huella de los datos ajenos**: por cada tabla con `empresa_id` (todas las de negocio y
   `usuarios`), más `empresas` y `flyway_schema_history`, el número de filas y el MD5 del contenido
   completo de las filas que **no** son de las cinco empresas de la seed. Así se compara todo, no solo
   unos conteos.
5. Usa la contraseña demo de `clave-demo` (se detiene al principio, antes de respaldar, si falta, no
   tiene permisos 600 o no cumple el formato); con `REANUDAR=1`, la misma de la ejecución anterior.
6. Arranca el backend **una vez** con un override temporal (`SPRING_PROFILES_ACTIVE=demo` y
   `DEMO_PASSWORD`, sin secretos en el archivo, que queda en la carpeta de la ejecución), espera y lo
   vuelve a arrancar **sin** el override. Comprueba que el contenedor no conserva esas variables.
7. Compara la huella: cualquier diferencia detiene el script (puede deberse también a actividad real
   durante la ventana: hay que ejecutarlo sin usuarios trabajando).
8. Comprueba exactamente 5 empresas demo, 10 cuentas con su rol, empresa, estado y correo, los conteos
   previstos por empresa y la coherencia del kárdex. Cierra el diario.

La seed **nunca** se ejecuta en un despliegue normal: `docker-compose.yml` no define el perfil ni
la contraseña (la fase 2 lo comprueba) y el `Dockerfile` tampoco.

### Fallo parcial

Por empresa (en orden `MVSA01`, `SCA002`, `DNS003`, `CHS004`, `LAS005`) hay dos transacciones: la
empresa y sus cuentas, y sus datos de negocio. Si falla una, se deshace entera; lo confirmado antes
se conserva y las empresas siguientes no se crean. Se detecta porque la fase 3 se detiene («la seed
falló» o «No consta el fin de la seed»), porque su diario sigue abierto y con `estado` (faltan
empresas, o una empresa tiene cuentas pero no datos). Opciones:

- **Reanudar**: corregir la causa y `REANUDAR=1 CONFIRMAR_SEED=si bash … fase3` (misma contraseña;
  la seed solo crea lo que falta, sin duplicar).
- **Deshacer**: restaurar el respaldo `sgp_pre_seed_*` con `restaurar` (ver «Vuelta atrás»). Se pierde
  lo escrito después.

No hay un borrado automático de los datos demo: retirarlos más adelante exige restaurar el respaldo
previo a la seed o una operación nueva y revisada.

### Normalización (las empresas de la seed sin marca «demo»)

Tras la seed, las cinco empresas y sus diez cuentas pueden pasar a verse como datos normales.
**Siguen siendo ficticios**: solo desaparecen la marca y las etiquetas «demo». Cambian exclusivamente
estas columnas de estas filas, localizadas por su clave natural (código + NIT, username, empresa + NIT,
empresa + SKU):

| Qué | Antes | Después |
| --- | --- | --- |
| `empresas.demo` de las 5 empresas | `true` | `false`: sin insignia «Demo»; la analítica las cuenta como empresas normales |
| `usuarios.email` de las 10 cuentas | `usuario@<código>.sgp-demo.test` | `usuario@<empresa>.example.com` (dominio reservado para pruebas, RFC 2606) |
| `proveedores.razon_social` y `email` (MVSA01, SCA002) | `… (demo)`, `@….sgp-demo.test` | sin «(demo)», `@<proveedor>.example.com` |
| `productos.descripcion` (11 productos) | `Producto ficticio de demostración.` | vacía |

No cambian IDs, usernames, nombres, roles, estados, contraseñas, códigos, NIT, existencias, kárdex,
órdenes, ventas, devoluciones ni historial (los textos «(demo)» del historial y los Word ya generados
se conservan). No hay migración, compilación ni reinicio.

1. `bash /root/sgp-private/sgp-aatenea.sh normalizar-probar` — sin tocar producción: copia de la BD en
   un PostgreSQL desechable sin red; allí aplica, repite (0 filas), revierte, vuelve a aplicar y
   comprueba que una colisión la detiene sin escribir nada. Elimina la copia al terminar.
2. `CONFIRMAR_NORMALIZACION=si bash /root/sgp-private/sgp-aatenea.sh normalizar` — en una ventana sin
   actividad: pruebas HTTP previas (si fallan, no toca nada), respaldo `sgp_pre_normalizacion_*`
   verificado, huella, **una transacción** con comprobaciones previas (cada fila localizada, cuentas en
   su empresa y con su rol, valores actuales de la seed o ya normalizados, ningún correo de destino en
   uso por otra cuenta) y comprobación final, huella de nuevo (fuera de los campos acordados no puede
   cambiar nada: todas las tablas, empresas reales incluidas) y pruebas HTTP de login y aislamiento.
3. Vuelta atrás: `CONFIRMAR_NORMALIZACION=si DIRECCION=revertir bash … normalizar` devuelve exactamente
   los valores de la seed (o `restaurar` el respaldo `sgp_pre_normalizacion_*`).

Es idempotente: repetirla cambia 0 filas. Si alguien cambió a mano uno de esos valores, se detiene sin
escribir y lo indica. Después de normalizar, la seed y las órdenes que buscan empresas con
`demo = true` (`fase3`, `fase4`, la lista de `estado`) ya no las reconocen: `fase3` se detendría por
colisión de códigos sin escribir nada, y `fase4` por no encontrar las 10 cuentas. Las pruebas HTTP
localizan las empresas por código y NIT, así que siguen funcionando.

## 4. Recuperación tras una interrupción

La detección **no depende de traps**. Antes de cada paso con efectos, el script anota (y sincroniza
en disco) el paso en `estado/en-curso/<operación>`; el diario solo se cierra cuando la operación
termina o se aborta, y entonces pasa a `historial/`. Un corte de luz, un `kill -9` o un OOM dejan el
diario abierto en el último paso iniciado. Además, `estado` y `recuperar` miran el estado real, con o
sin diario: variables demo en el contenedor del backend (p. ej. tras un reinicio del VPS en plena
seed, porque `restart: unless-stopped` lo vuelve a levantar con su configuración), restauraciones de
prueba que siguen existiendo, respaldos `.parcial` y el override de la versión anterior del script.

Los traps son una ayuda adicional: ante un error, Ctrl-C o `kill` (TERM), eliminan la restauración de
prueba, los `.parcial` y los tokens de esa ejecución y, si el backend estaba con el perfil demo,
intentan devolverlo al normal. El diario sigue abierto igualmente.

**Mientras haya un diario abierto, las demás órdenes se niegan** (salvo `estado`, `recuperar` y las
que reanudan o abortan esa operación, indicadas abajo).

### Procedimiento

1. `bash /root/sgp-private/sgp-aatenea.sh estado` — muestra cada operación incompleta con su diario y
   cada indicio. No cambia nada.
2. `bash /root/sgp-private/sgp-aatenea.sh recuperar` — es seguro repetirlo:
   - elimina las restauraciones de prueba huérfanas (solo contenedores llamados
     `sgp-restore-check-<marca>` y sin red; nunca otros) con su volumen anónimo;
   - lista los `.parcial` sin borrarlos (se borran a mano tras revisarlos: nunca sirven);
   - si el backend tiene el perfil demo, lo recrea con la configuración normal y lo verifica;
   - con la fase 3 abierta, compara la huella de datos ajenos con la anotada antes de la seed;
   - según el paso de una restauración, arranca los servicios **o se niega** (tabla siguiente);
   - nunca borra diarios, respaldos, etiquetas de imagen, despliegues ni la contraseña demo.
3. Decidir **reanudar** o **abortar** según la tabla.

### Estados intermedios y condiciones

| Diario abierto (último paso) | Situación | Reanudar | Abortar |
| --- | --- | --- | --- |
| `fase2` (`codigo`, `backend`, `frontend`, `comprobacion`) | El código puede estar ya en el objetivo; el backend o el frontend pueden ser nuevos o viejos | `REANUDAR=1 bash … fase2` (si HEAD ya es el objetivo, no repite el merge). Condición: árbol limpio y la causa del fallo corregida | `bash … rollback` (cierra el diario de la fase 2 como abortado), si Flyway no cambió; si cambió, `restaurar` con el respaldo `sgp_pre_deploy` |
| `fase3` (`inicio`, `respaldo`, `huella`) | Aún no se tocó el backend | `REANUDAR=1 CONFIRMAR_SEED=si bash … fase3` | Basta con no reanudar; para cerrar el diario, `restaurar` el `sgp_pre_seed` (o pedir una decisión) |
| `fase3` (`demo-arrancando`, `demo-activo`, `volviendo-normal`) | El backend puede estar **con el perfil demo** | Primero `recuperar` (vuelve al normal); después `REANUDAR=1 CONFIRMAR_SEED=si bash … fase3`. Condición: que `recuperar` diga «Datos ajenos a la seed idénticos» | `recuperar` y después `restaurar` el respaldo `sgp_pre_seed_*` anotado en el diario (`respaldo_pre_seed=`) |
| `fase3` (`normal`, `normal-tras-interrupcion`, `verificacion`) | Backend normal; la seed pudo terminar o no | `REANUDAR=1 CONFIRMAR_SEED=si bash … fase3` (idempotente: verifica y cierra) | `restaurar` el `sgp_pre_seed_*` |
| `rollback` (`imagenes`, `backend`, `frontend`) | `:latest` puede apuntar ya a las imágenes viejas | `REANUDAR=1 bash … rollback` | — (repetir es la única salida segura) |
| `restauracion` (`inicio`, `verificando`, `deteniendo`, `respaldo-previo`) | **La BD no se ha tocado**; los servicios pueden estar detenidos | `REANUDAR=1 RESPALDO=… CONFIRMAR_RESTAURACION=… bash … restaurar` | `recuperar`: arranca los servicios como estaban y cierra el diario como `abortada-bd-intacta` |
| `restauracion` (`bd-borrada`, `restaurada`) | **BD borrada o a medio restaurar**; servicios detenidos. `recuperar` y `estado` **no** arrancan el backend ni consultan la BD: sobre una BD vacía, Flyway crearía un esquema nuevo y la aplicación arrancaría sin datos | `REANUDAR=1` con el mismo `RESPALDO` (no vuelve a respaldar la BD incompleta: el estado original ya está en `respaldo_previo=`) | `REANUDAR=1 RESPALDO=<respaldo_previo del diario> CONFIRMAR_RESTAURACION=<su nombre> bash … restaurar`: vuelve al estado anterior a la restauración, con las imágenes que estaban en marcha |
| `restauracion` (`arrancando`) | BD restaurada y verificada; faltaba arrancar | `recuperar` (arranca y cierra el diario) | — |

Condiciones para **no** reanudar y pedir una decisión: la huella de datos ajenos cambió, una
comprobación de filas o Flyway no coincide, o el diario pertenece a otro despliegue (el script lo
detecta). En esos casos no se ejecuta nada más hasta revisarlo.

## 5. Vuelta atrás

**Imagen** (sin migraciones nuevas): `bash /root/sgp-private/sgp-aatenea.sh rollback`. Comprueba que
las etiquetas `pre-deploy-<marca>` siguen apuntando a las imágenes anotadas en la fase 1. El código
del repositorio sigue en el commit nuevo; para devolverlo, con `git status` vacío:

```bash
git -C /root/sgp-unisen reset --hard "$(cat /root/sgp-private/estado/despliegues/$(cat /root/sgp-private/estado/actual)/commit-previo)"
```

Tras un rollback, el despliegue no admite más fases 2 ni 3: el siguiente intento empieza con una fase 1.
Para volver a un despliegue anterior al vigente: `DESPLIEGUE=<marca> bash … rollback`.

**Base de datos** (destructivo: se pierde todo lo escrito después del respaldo). Solo con autorización:

```bash
R=/root/backups/<respaldo>.dump
RESPALDO=$R CONFIRMAR_RESTAURACION=$(basename "$R") bash /root/sgp-private/sgp-aatenea.sh restaurar
```

En este orden, y con el diario `restauracion` anotando cada paso:

1. Integridad del volcado (`.sha256`) y restauración de prueba aislada; si existe `.conteos`, la copia
   debe tener esas filas. `POSTGRES_DB` no puede ser `postgres`.
2. Imágenes acordes con el esquema del volcado, decididas **antes de tocar nada**: si su Flyway
   coincide con el de la BD actual, las que están en marcha (se etiquetan `pre-restore-<marca>`); si
   coincide con el de la fase 1, las `pre-deploy-<marca>`; si no, se detiene.
3. Detiene frontend y backend.
4. **Respaldo verificado del estado actual** (`sgp_pre_restore_<marca>.dump`, mismo procedimiento
   atómico que los demás). Sin él no se borra nada.
5. `dropdb` + `createdb` + `pg_restore --exit-on-error`.
6. La BD restaurada debe tener las mismas filas que la copia de prueba y el mismo Flyway; si no, se
   detiene con los servicios parados.
7. Etiqueta las imágenes elegidas como `:latest`, arranca backend y frontend y comprueba que están
   sanos y sin perfil demo. Cierra su diario y, como abortados, los de `fase2`, `fase3` o `rollback`
   que siguieran abiertos.

Si se restaura el respaldo `sgp_pre_deploy` (esquema de la fase 1), el código vuelve a
`commit-previo` aparte (comando de arriba).

**Procedimiento manual de emergencia** (solo si el script no puede usarse). El respaldo previo
(`pg_dump` antes de `dropdb`) es obligatorio:

```bash
cd /root/sgp-unisen
R=/root/backups/<respaldo>.dump
(cd /root/backups && sha256sum -c "$(basename "$R").sha256")          # integridad
docker compose stop frontend backend
M=$(date +%Y%m%d-%H%M%S)
P=/root/backups/sgp_pre_restore_manual_$M.dump
docker compose exec -T postgres sh -c 'pg_dump -U "$POSTGRES_USER" -d "$POSTGRES_DB" -Fc' > "$P.parcial"
docker compose exec -T postgres pg_restore --list < "$P.parcial" > /dev/null    # legible (no prueba que esté completo; el script sí lo restaura a prueba)
(cd /root/backups && sha256sum "$(basename "$P").parcial" | sed 's/\.parcial$//' > "$(basename "$P").sha256")
mv "$P.parcial" "$P"                                                   # solo ahora tiene nombre definitivo
docker compose exec -T postgres sh -c 'dropdb -U "$POSTGRES_USER" --maintenance-db=postgres "$POSTGRES_DB" && createdb -U "$POSTGRES_USER" --maintenance-db=postgres "$POSTGRES_DB"'
docker compose exec -T postgres sh -c 'pg_restore -U "$POSTGRES_USER" -d "$POSTGRES_DB" --no-owner --exit-on-error' < "$R"
# Imagen del backend acorde con el esquema del respaldo (la de la fase 1 si se restaura el previo al despliegue):
docker image tag "$(cat /root/sgp-private/estado/despliegues/<marca>/imagen-previa-backend)" unisen/sgp-backend:latest
docker compose up -d --no-deps --no-build backend && docker compose up -d --no-deps --no-build frontend
docker compose ps                                                      # backend healthy
```

Si `pg_restore` falla a medias, **no** arrancar el backend sobre esa BD (Flyway crearía un esquema
vacío): repetir desde `dropdb` con el mismo volcado o con el `sgp_pre_restore_manual_*`.

## 6. Riesgos conocidos

- Mientras dura la fase 3, root puede ver `DEMO_PASSWORD` con `docker inspect` del contenedor demo.
- Si el VPS se reinicia en plena fase 3, el contenedor demo vuelve con el perfil demo (la seed es
  idempotente): `estado` lo señala y `recuperar` lo devuelve al perfil normal.
- La huella de datos ajenos no distingue un cambio de la seed de la actividad real: ventana sin uso.
- El archivo de cuentas contiene la contraseña demo en claro (600, fuera del repositorio): entrégalo
  por un canal privado y bórralo cuando no haga falta.
- Una restauración (`restaurar`) deja la aplicación detenida mientras respalda y restaura; el respaldo
  previo exige espacio libre (tres veces el tamaño de la BD, lo comprueba).
- Las etiquetas `pre-deploy-*` y `pre-restore-*` y los respaldos se acumulan: retirarlos es una tarea
  manual y deliberada.
