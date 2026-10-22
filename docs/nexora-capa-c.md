# Nexora · Capa C: página pública, sistema visual y animaciones

Rama de trabajo: `claude/ecstatic-lovelace-p1buxv`, continuación directa de
`claude/loving-edison-5cogo9` (`220738e`, capas A y B). Solo frontend: ningún cambio de backend,
esquema, permisos, Docker ni Nginx activo. Sin merge, PR ni despliegue.

## 1. Qué se implementó

### Página pública (`/` sin sesión)

- **Encaje de rutas.** `RutaProtegida` recibe `publicaEnRaiz`: en `/`, sin sesión vigente, muestra
  `pages/Bienvenida.tsx`; con sesión, `/` sigue siendo el panel de inicio. Cualquier otra ruta privada
  sigue redirigiendo a `/login` recordando la ruta pedida; `/login` y `/registro` no cambian. Una
  sesión que caduca estando en `/` vuelve a la página pública. Sin endpoints nuevos: contenido estático.
- **Secciones.** Encabezado con enlaces a las secciones y las dos acciones; portada con la propuesta,
  «Iniciar sesión» y «Tengo un código de invitación»; recorrido interactivo de una compra (control
  deslizante accesible con teclado); documentos que genera el sistema (orden en PDF/Word, comprobante
  interno, historial); módulos (punto de venta, calendario de entregas, existencias y kárdex, personal
  e invitaciones); roles y aislamiento entre empresas; preguntas frecuentes; cierre con el primer paso
  del registro, que lleva la vía y el código a `/registro?via=empresa&codigo=…`; pie.
- **Contenido verificado contra el código** (A.6 de `docs/auditoria-sgp.md`): sin clientes, cifras,
  testimonios ni certificaciones. Se dice lo que no existe (factura electrónica, recuperación de
  contraseña, alta de empresas por su cuenta). Los fragmentos de interfaz usan los componentes reales
  (insignias de estado, formato COP) con los datos de la seed demo de Mercado Verde, rotulados como
  «Datos de ejemplo de una empresa de demostración»; los totales se calculan de las líneas
  (`features/bienvenida/datosDemo.ts`, con prueba).
- **Registro.** `/registro` acepta `?via=empresa` (abre la vía del código de la empresa con el código
  del enlace). El resto del formulario y sus validaciones no cambian.

### Sistema visual

Se conservó la identidad Nexora existente (borgoña `#6B1E2E`, crema, dorado, Geist e Instrument Serif,
tokens en `src/brand/nexora/`) y se corrigió lo que la alejaba de una aplicación empresarial:

- **Tokens nuevos** (`tokens.css`): movimiento (`--nexora-ease-out`, `--nexora-dur-*`), color de
  selección, borgoña como tono de datos de las gráficas (antes azul) y un campo borgoña fijo para el
  cierre de la página pública (en oscuro, el primario es rosado).
- **Componentes base** (`componentes.css`): estados completos (hover, active, focus-visible, disabled,
  `aria-busy`), tarjetas planas (borde, sin sombra en reposo; la sombra queda para diálogos y menús),
  `u-link`, `u-card-interactiva`, `u-skeleton`, anillo de foco global y selección con la marca.
- **Jerarquía.** Títulos de página en la sans de la interfaz a 28 px con migas «Inicio › sección»
  (antes: etiqueta en mayúsculas espaciadas y serif de 48 px). La serif queda para la página pública,
  login/registro y el saludo del inicio. Cabeceras de tabla en minúscula sin banda; cifras tabulares.
- **Responsive.** Tablas con ancho mínimo: en el teléfono se desplazan dentro de su marco en lugar de
  partir nombres palabra por palabra. Página pública sin desbordamiento horizontal a 390 px (medido).

### Vistas revisadas (capturas en escritorio y móvil con datos demo reales)

Login, registro (incluida la cuenta pendiente), shell y navegación (escritorio y menú móvil), inicio de
los tres roles, solicitudes (y su diálogo de revisión), órdenes de compra y su ficha, entregas,
recepciones, recepción de una orden, recibir mercancía (trabajador), punto de venta, ventas, reportes,
gestión comercial, calendario, existencias, productos (y su diálogo), proveedores, personal,
invitaciones, perfil, empresas y ficha de empresa (SUPER_ADMIN). La mayoría heredan los cambios por los
componentes comunes (`PageHeader`, `DataTable`, `Modal`, `StatTile`, avisos); las fichas de orden,
recepción y venta llevan el tipo de objeto en el título.

### Animaciones (todas con alternativa en `prefers-reduced-motion`)

| Dónde | Qué | Con movimiento reducido |
| --- | --- | --- |
| Cambio de ruta | Ascenso corto de la vista (320 ms) | Fundido de 120 ms |
| Diálogos y menú móvil | Entrada con escala/desplazamiento; fondo con fundido | Fundido |
| Botones y tarjetas-enlace | Cesión al pulsar (escala 0,98), transiciones de color | Sin escala |
| Avisos | Entrada; el error se sacude una vez; el icono de confirmación hace un impulso | Fundido, sin sacudida |
| Errores de campo | Entrada corta junto al campo | Fundido |
| Gráficas | Las barras crecen desde su base al cargar | Sin crecimiento |
| Carga | Esqueletos del tamaño final (tablas, indicadores, recientes, módulos diferidos) | Sin pulso |
| Página pública | Fundido lento del escenario (pausable, con botones), entrada escalonada de secciones, cambio de etapa y de documento | Escenario quieto, secciones sin desplazamiento |

Solo `transform` y `opacity` (más color en los estados). El contenido nunca queda oculto si el script
falla: la entrada escalonada solo oculta lo que el propio script marca.

### Skills y bibliotecas

- Skills leídas y aplicadas: **design-taste-frontend** (dirección `split-stage`, semilla 89513969;
  inspector y linter), **impeccable** (craft floor, modo Operate para la app; detector),
  y sus referencias de movimiento. No se escribió `PRODUCT.md` de Impeccable: el brief de esta tarea
  fijaba la marca y pedía no detenerse a preguntar.
- Redibujos de la dirección: la sección `about` («carta del fundador») se cambió por «datos al margen
  y párrafo amplio» porque exigía inventar una persona; se descartaron las fotografías de archivo
  (no hay imágenes propias y serían decoración ajena al producto) y la franja decorativa se convirtió
  en una etiqueta de código de barras EAN-13 real del producto de ejemplo.
- **Ninguna dependencia nueva**: CSS (Tailwind v4 + capas propias), React y `lucide-react` existentes.

## 2. Verificación ejecutada (resultados reales)

| Comprobación | Resultado |
| --- | --- |
| `npm run lint` | Sin avisos ni errores |
| `npx tsc -b` | Sin errores |
| `npm test` | 89/89 (antes 76; nuevas: página pública, guardas de ruta, recorrido, acceso con código, `?via=empresa`, EAN-13, totales de ejemplo) |
| `npm run build` | Correcto |
| `mvn test` del backend (H2; sin cambios de backend en esta capa) | 336/336, 0 fallos |
| Recorrido de integración (`scripts/validacion-local/integracion.mjs`) con PostgreSQL 16 local vacío | 49/49 (incluye 429 de login y registro en la interfaz y «sin errores de consola») |
| 429 real del backend en el login (navegador) | Mensaje del backend, contraseña vaciada, botón habilitado, sin errores de consola |
| Capturas 1440/768/390 px, claro y oscuro | Revisadas: página pública, login, registro y todas las vistas privadas de los tres roles |
| Desbordamiento horizontal a 390 px | Ninguno en la página pública ni en login |
| Movimiento reducido (Chromium emulado) | Vista con fundido sin desplazamiento; escenario sin avance automático |
| Teclado | «Saltar al contenido» primero, orden lógico, anillo de foco de 2 px con el color de la marca |
| Inspector de tasteskill (página pública) | 0 FAIL; avisos defendidos en la sección 3 |
| Detector de Impeccable | Sin hallazgos |

## 3. Limitaciones y pendientes

- Avisos del inspector que se mantienen a propósito: fondo crema (es el del paquete de marca), línea
  bajo la cabecera fija (separa el contenido que pasa por debajo), botón secundario con contorno, sin
  fotografías ni vídeo (los fragmentos de interfaz son las imágenes), fila deslizable de documentos en
  el teléfono.
- No probado en Safari/Firefox ni en dispositivos físicos (solo Chromium emulado).
- No se hizo una auditoría de accesibilidad con lector de pantalla real; sí roles, nombres accesibles,
  foco y contraste de los tokens.
- Vistas de alta densidad (gestión comercial, reportes) siguen siendo tablas anchas que en el teléfono
  se desplazan dentro de su marco: una versión en filas de dos líneas queda como mejora.
- `README.md` describe la marca y la estructura; la capa C se resume en este documento.

## 4. Checklist para activar la capa B (no hecho; requiere autorización)

Detalle en `deploy/propuesta-capa-b/README.md` y en la sección B6 de `docs/auditoria-sgp.md`.

1. [ ] Verificar en Aatenea, en solo lectura: qué escucha en 80/443; `HTTP_PORT`; si el proxy TLS
       añade o sustituye `X-Forwarded-For`; la dirección con la que el proxy llega a Nginx; si Docker
       usa el *userland proxy*; la IP y el número de logins de la verificación de `sgp-aatenea.sh`.
2. [ ] Fijar `NGINX_PROXY_CONFIANZA` con esos datos (no tiene valor por defecto) y, si hay proxy,
       `HTTP_PORT=127.0.0.1:…` en el `.env` del VPS.
3. [ ] Autorizar y aplicar `activacion.diff` (Nginx, Dockerfile, `docker-compose.yml`).
4. [ ] Desplegar con `NGINX_LIMITE_MODO_PRUEBA=on`, revisar `limiting requests, dry run` unos días y
       pasar a `off` si no afecta a usuarios legítimos.
5. [ ] Si el dominio cambia (sección 5), `server_name` de la plantilla de la capa B y del
       `frontend/nginx.conf` deben incluir el nombre nuevo (ver también la IP del proxy del nuevo sitio).

## 5. Checklist para migrar al dominio `n3xora.duckdns.org` (no hecho; requiere autorización)

El subdominio creado por el propietario es **`n3xora.duckdns.org`** (la auditoría A.7 hablaba de un
hipotético `nexora.duckdns.org`; las referencias son las mismas). Nada de esto se ha verificado ni
cambiado: DNS, certificado, proxy y VPS quedan fuera de esta tarea.

Infraestructura (fuera del repositorio):

1. [ ] DNS: que `n3xora.duckdns.org` resuelva a la IP del VPS (`dig +short n3xora.duckdns.org`) y que
       el token de DuckDNS lo actualice igual que `rrtf.duckdns.org`. **No eliminar** `rrtf.duckdns.org`.
2. [ ] Proxy TLS del host: un bloque para `n3xora.duckdns.org` que reenvíe al mismo `HTTP_PORT`, con
       su certificado (emisión y renovación comprobadas) y las mismas cabeceras `X-Forwarded-*`.
3. [ ] Periodo de transición con los dos nombres activos; decidir después si `rrtf` redirige (301) al
       nuevo.

Configuración (en el repositorio, en un cambio propio):

4. [ ] `CORS_ALLOWED_ORIGINS`: admitir los dos orígenes durante la transición. En `docker-compose.yml`
       hoy deriva de `APP_DOMAIN`; habría que permitir una lista (`https://n3xora.duckdns.org,https://rrtf.duckdns.org,…`).
5. [ ] `.env.example`/`.env` del VPS (`APP_DOMAIN`), valor por defecto en `application.yml`,
       `backend-spring/.env.example`, `frontend/nginx.conf` (`server_name`) y la plantilla de la capa B.
6. [ ] Pruebas de CORS: `AuthControllerTest` y `application-test.yml`.
7. [ ] Documentación: `README.md`, `backend-spring/README.md`, `deploy/aatenea/README.md`,
       `CLAUDE.md`, `frontend/.env.example`, comentario de `lib/axios.ts`.

Efectos a tener en cuenta:

8. [ ] **Las sesiones no pasan de un dominio a otro**: el token vive en el `localStorage` de cada
       origen (`sgp.session`), así que en `n3xora` todos inician sesión de nuevo. No cambiar la clave ni
       el emisor JWT `unisen-sgp` (cerraría todas las sesiones en ambos).
9. [ ] Comprobar tras el cambio un login real, el registro, la descarga de documentos (PDF/Word) y
       que `forward-headers-strategy` reconoce el nuevo host (mismo origen).
10. [ ] El frontend usa rutas relativas (`/api`): no hay URLs de producción escritas en el código que
        cambiar.

## 6. Cómo demostrar Nexora en local

Sin Docker (como en esta sesión) o con el PostgreSQL de `docker compose up -d postgres`:

```bash
# 1. Base de datos vacía y desechable (PostgreSQL 16 local), p. ej. sgp_demo.
# 2. Backend con datos de demostración (solo desarrollo; contraseñas solo locales):
cd backend-spring
SPRING_PROFILES_ACTIVE=demo DEMO_PASSWORD=<clave-local> \
DB_URL=jdbc:postgresql://127.0.0.1:<puerto>/sgp_demo DB_USERNAME=<usuario> DB_PASSWORD=<clave> \
JWT_SECRET="$(openssl rand -base64 64 | tr -d '\n')" \
ADMIN_USERNAME=superadmin.local ADMIN_EMAIL=superadmin@ejemplo.test ADMIN_PASSWORD=<clave-local> \
./mvnw spring-boot:run
# 3. Frontend:
cd frontend && npm install && npm run dev   # http://localhost:5173 (no 127.0.0.1: CORS)
```

Recorrido sugerido: la página pública en `/` (mover el recorrido de una compra, elegir documentos,
probar el cierre con un código), «Iniciar sesión» con `carlos.mendoza` (administrador comercial de
Mercado Verde, contraseña `DEMO_PASSWORD`), su panel, solicitudes → aprobar la pendiente, la ficha de
`OC-00003` y su recepción parcial, punto de venta; después `laura.torres` (trabajadora: menú reducido,
«Recibir mercancía») y `superadmin.local` (empresas y supervisión). Para ver el 429: diez intentos
fallidos con un mismo usuario.
