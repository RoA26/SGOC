# Auditoría arquitectónica del SGP

Documento vivo de la actualización arquitectónica (plataforma multiempresa de compras, inventario
y, más adelante, ventas). Cada fase añade su informe al final. Fecha de la auditoría: 16/10/2026.

## Fase 0: diagnóstico

### 0.1 Estado de Git

| Elemento | Estado |
| --- | --- |
| Rama de trabajo | `claude/happy-mayer-46ds84`, sin cambios pendientes ni `stash` |
| `main` (remoto) | `51dd80d` (gestión centralizada de empresas), igual que la rama |
| Otras ramas remotas | `claude/purchase-order-hito-1-7jpnkw`, en `c8886f3` y sin commits propios: **otra sesión parece preparar las órdenes de compra**. La Fase 2 debe coordinarse con ella para no duplicar modelos |

### 0.2 Arquitectura real

- **Monolito modular** Spring Boot 3.5 (Java 21) + React 18 (Vite, Zustand, React Hook Form + Zod).
  PostgreSQL 16 con Flyway (V1–V7) y `ddl-auto: validate`. Sin microservicios ni gateway.
- **Autenticación:** JWT HS256 sin estado. El filtro JWT **carga el usuario de la BD en cada
  petición**: el rol, el estado de la cuenta y la empresa salen de la BD, no del token (el claim
  `rol` es informativo para el cliente).
- **Autorización:** `@PreAuthorize` en los servicios con las expresiones de `Permisos`.
- **Multiempresa:** esquema compartido con `@TenantId` de Hibernate 6 en `EntidadDeEmpresa`
  (proveedores, productos, solicitudes, códigos de invitación). `TenantFilter` fija el tenant: el
  de la cuenta para GERENTE/USUARIO (la cabecera se ignora) y el de `X-Tenant-ID` para el
  SUPER_ADMIN. `Usuario` y `Empresa` son tablas globales con filtrado explícito.
- **Módulos backend:** autenticación e invitaciones, empresas, trabajadores, proveedores,
  productos, solicitudes de compra (maestro-detalle).

### 0.3 Reglas multiempresa: verificación

| # | Regla | Estado | Evidencia / hueco |
| --- | --- | --- | --- |
| 1 | Admin de empresa y trabajador solo ven su empresa | ✅ Correcto | `TenantFilter` impone la empresa de la cuenta; tests de aislamiento |
| 2 | SUPER_ADMIN administra empresas y usa un modo global | ✅ Correcto | `/api/v1/empresas/**` restringido a SUPER_ADMIN |
| 3 | El modo global no es una omisión indiscriminada de filtros | ⚠️ Parcial | Sin cabecera, el SUPER_ADMIN cae en el tenant raíz: **todas** las entidades de negocio sin filtro, de forma implícita y sin registro |
| 4 | La supervisión aplica el alcance de esa empresa | ✅ Correcto | `X-Tenant-ID` validado contra la BD; el filtro `@TenantId` lo aplica |
| 5 | El id de empresa del frontend no es autoridad | ✅ Correcto | Ignorado para GERENTE/USUARIO; para el SUPER_ADMIN solo elige contexto, autorizado por su rol |
| 6 | Consultas y relaciones validan el aislamiento | ✅ Correcto | `@TenantId` en consultas y en la carga por id; FK compuestas producto→proveedor y solicitud→usuario |
| 7 | Cuentas desactivadas no conservan acceso con un JWT vigente | ✅ Correcto | Estado leído de la BD en cada petición (403 con `motivo`) |
| 8 | Relaciones que impiden asociar registros a otro tenant | ⚠️ Parcial | `detalles_solicitud` no lleva `empresa_id` (el servicio solo resuelve productos del tenant, pero la BD no lo impide). `codigos_invitacion.usado_por_id` tiene una FK simple |

### 0.4 Requerimientos clasificados

| Requerimiento | Clasificación | Observación |
| --- | --- | --- |
| Nombres y apellidos separados | ❌ No implementado · Requiere migración | Solo `usuarios.nombre` (texto libre, 150); en el registro, por defecto, el username |
| Rol `ADMIN_EMPRESA` | ❌ No implementado · Requiere migración | Se llama `GERENTE` en el enum, en la BD (`ck_usuarios_rol`), en `Permisos`, en el JWT y en el frontend |
| Rol `TRABAJADOR` | 🟡 Parcial · Requiere migración | Existe como `USUARIO` (documentado como equivalente) |
| Alta atómica de empresa + administrador | ✅ Correcto | Transacción única con reintento ante carrera por código; falta pedir nombres y apellidos y usar el nuevo rol |
| Código de empresa único y estático | ✅ Correcto | `empresas.codigo` (V7) |
| Nombre comercial en la interfaz | 🟡 Parcial | El SUPER_ADMIN ya ve nombre y código; GERENTE y USUARIO ven «Empresa N.º X» porque la sesión solo trae el id |
| Perfil con nombre real | ❌ No implementado | No hay página de perfil; el saludo usa `nombre`, que puede ser el username |
| Invitaciones | 🟡 Parcial | Correcto: un solo uso, 72 h, validación al canjear con bloqueo de fila, empresa y rol fijados por el servidor, creador auditado. Falta: **revocación**, **listado** para el administrador y FK compuesta de `usado_por` |
| Modo global explícito y auditable | 🟡 Parcial | Ver regla 3 |
| Productos | 🟡 Parcial | Empresa, SKU, nombre, descripción, un único `precio` (COP entero, referencia de compra) y estado lógico. Sin unidad de medida, sin distinción costo/venta y sin existencias |
| Proveedores | ✅ Correcto | Por empresa, NIT único por empresa, baja lógica bloqueada si tiene productos activos |
| Solicitudes de compra | ✅ Correcto | PENDIENTE → APROBADA / RECHAZADA, revisor y comentario, bloqueo ante revisiones simultáneas |
| Órdenes de compra · Recepciones · Inventario | ❌ No implementado | Solo aparecen como «Próximamente» en la interfaz; sin entidades ni endpoints |
| Clientes · Ventas · Facturación | ❌ No implementado · Requiere decisión funcional | Fuera del código actual; Fase 3 (diseño) |
| Cambio y recuperación de contraseña | ❌ No implementado | El administrador inicial recibe una contraseña que no puede cambiar |

### 0.5 Matriz de permisos actual (verificada en el backend)

| Operación | SUPER_ADMIN | GERENTE (→ ADMIN_EMPRESA) | USUARIO (→ TRABAJADOR) |
| --- | --- | --- | --- |
| Crear, listar, ver y activar/desactivar empresas; estado de sus administradores | ✅ | ❌ 403 | ❌ 403 |
| Ver la empresa actual | Con contexto de empresa | ✅ la suya | ❌ 403 |
| Listar trabajadores y aprobarlos, rechazarlos o desactivarlos | Con contexto de empresa | ✅ los de su empresa | ❌ 403 |
| Generar invitaciones | Con contexto de empresa (solo por API) | ✅ para su empresa | ❌ 403 |
| Consultar proveedores y productos | Global o con contexto | ✅ su empresa | ✅ su empresa |
| Crear, editar y dar de baja proveedores y productos | Con contexto de empresa | ✅ su empresa | ❌ 403 |
| Crear solicitudes | ❌ 403 | ✅ | ✅ |
| Ver solicitudes | Global o con contexto | ✅ las de su empresa | ✅ solo las suyas (las ajenas → 404) |
| Aprobar o rechazar solicitudes | Con contexto de empresa (solo por API) | ✅ su empresa | ❌ 403 |
| Ver su perfil (`/api/auth/me`) | ✅ | ✅ | ✅ |

La interfaz oculta al SUPER_ADMIN las escrituras dentro de una empresa (supervisión de solo
lectura), pero la API conserva el «modo soporte».

### 0.6 Riesgos identificados

1. **Modo global implícito** (regla 3): cualquier endpoint de negocio presente o futuro (p. ej.
   ventas) quedaría sin filtro de empresa para el SUPER_ADMIN si no envía cabecera, sin registro.
   **Se corrige en la Fase 1.**
2. **Renombrado de roles:** el valor persistido cambia. El backend y la BD deben desplegarse juntos
   (Flyway migra al arrancar). Volver atrás exige una migración inversa. Los tokens emitidos
   siguen siendo válidos porque el rol se lee de la BD.
3. **Nombres existentes:** `nombre` es texto libre; partirlo en nombres y apellidos de forma
   automática sería frágil ("María del Pilar De la Torre"). **No se parte.**
4. **Precio único visible para todos los roles:** hoy `precio` es una referencia de compra que
   también ve el trabajador. Con ventas habrá que separar costo y precio de venta y restringir el
   costo.
5. **Sin cambio de contraseña:** la contraseña inicial que define el SUPER_ADMIN no puede
   cambiarse.
6. **Sin límite de intentos** en el login ni en el registro.
7. **Rama paralela de órdenes de compra:** riesgo de modelos duplicados en la Fase 2.

### 0.7 Plan de la Fase 1 y decisiones que afectan a datos o permisos

| Cambio | Migración | Decisión |
| --- | --- | --- |
| Roles `ADMIN_EMPRESA` y `TRABAJADOR` | **V8**: `UPDATE` de `usuarios.rol` y nueva `ck_usuarios_rol` | Se renombran los valores internos, las autoridades (`ROLE_ADMIN_EMPRESA`), `Permisos` y el contrato JSON (`gerente` → `administrador`). La sesión guardada en el navegador se traduce sin cerrar la sesión |
| Nombres y apellidos | **V9**: columnas `nombres` y `apellidos` (nulas), `nombre` ampliado a 201 | Las cuentas nuevas los exigen. Las existentes **no se parten**: conservan su nombre completo en `nombre` (nombre visible) y cada persona completa sus datos desde su perfil (`PATCH /api/auth/me`). `nombre` se conserva como nombre visible derivado |
| Empresa en la sesión | — | `empresaNombre` y `empresaCodigo` en el login y en `/api/auth/me`, leídos de la BD en cada petición; la interfaz refresca el perfil al cargar |
| Invitaciones | **V10**: `fecha_revocacion` y `revocado_por_id` + FK compuesta de `usado_por` | Revocar es una baja lógica (se conserva el historial). El listado no expone el código completo |
| Modo global explícito | — | El SUPER_ADMIN debe elegir contexto en los endpoints de negocio: `X-Tenant-ID: <id>` (supervisión) o `X-Tenant-ID: global` (modo global, registrado). Sin cabecera → 400 |

Se aplazan a la Fase 2 la FK compuesta de `detalles_solicitud` y el resto de compras e inventario.
Las ventas se reservan para después de validar el diseño de la Fase 3.

## Fase 1: informe (identidad y administración)

### 1.1 Cambios implementados

| Requisito | Estado | Cómo |
| --- | --- | --- |
| Roles `ADMIN_EMPRESA` y `TRABAJADOR` | ✅ | V8 traduce los valores; enum, autoridades, `Permisos`, contrato JSON (`administrador`) y frontend actualizados. Etiquetas visibles: «Administrador», «Administrador de empresa», «Trabajador» |
| Nombres y apellidos | ✅ | V9 sin partir datos; obligatorios en el alta de empresa y en el registro; `PATCH /api/auth/me` y página *Mi perfil*; saludo con los nombres |
| Empresa real en la sesión | ✅ | `empresaNombre`/`empresaCodigo` en el login y en `/api/auth/me`, leídos de la BD; refresco al cargar y al volver a la pestaña |
| Alta atómica de empresa + administrador | ✅ (se conserva) | Ahora con nombres, apellidos y rol `ADMIN_EMPRESA` |
| Invitaciones | ✅ | Listado sin el código completo, revocación auditada con bloqueo de fila, FK compuesta de quien canjea (V10) |
| Modo global explícito y auditable | ✅ | `X-Tenant-ID: global` obligatorio en rutas de negocio para el SUPER_ADMIN; sin cabecera, 400; registrado en el log |
| Usuarios desactivados con JWT vigente | ✅ (se conserva) | Sin cambios: estado leído de la BD en cada petición |

### 1.2 Matriz de permisos resultante (verificada en el backend)

| Operación | SUPER_ADMIN | ADMIN_EMPRESA | TRABAJADOR |
| --- | --- | --- | --- |
| Empresas: crear, listar, ficha, activar/desactivar; estado de sus administradores | ✅ | ❌ 403 | ❌ 403 |
| Datos de negocio de varias empresas | Solo con `X-Tenant-ID: global` (lectura, registrado) | ❌ (solo la suya) | ❌ (solo la suya) |
| Datos de negocio de una empresa | Con `X-Tenant-ID: <id>` (supervisión, registrada) | ✅ la suya | ✅ la suya, según su rol |
| Trabajadores: listar, aprobar, rechazar, desactivar | Con `X-Tenant-ID: <id>` | ✅ su empresa | ❌ 403 |
| Invitaciones: generar, listar, revocar | Con `X-Tenant-ID: <id>` (solo por API) | ✅ su empresa | ❌ 403 |
| Proveedores y productos: consultar / modificar | Consultar | ✅ / ✅ | ✅ / ❌ 403 |
| Solicitudes: crear / ver / revisar | ❌ / ✅ / con `<id>` | ✅ / su empresa / ✅ | ✅ / solo las suyas / ❌ |
| Perfil: ver y corregir nombres y apellidos propios | ✅ | ✅ | ✅ |

La interfaz del SUPER_ADMIN sigue siendo de solo lectura dentro de una empresa; la API conserva el
modo soporte (escrituras con `X-Tenant-ID: <id>`, firmadas con su identidad).

### 1.3 Migraciones y estrategia de aplicación

| Migración | Qué hace | Datos existentes | Vuelta atrás |
| --- | --- | --- | --- |
| V8 | `GERENTE` → `ADMIN_EMPRESA`, `USUARIO` → `TRABAJADOR`; nueva `ck_usuarios_rol` | Nadie pierde acceso ni empresa | `UPDATE` inversos + restricción anterior |
| V9 | `nombres`, `apellidos` (nulas), `nombre` → 201, `ck_usuarios_nombres` | No se tocan; `nombre` se conserva | Quitar columnas (sin pérdida: `nombre` sigue completo) |
| V10 | `fecha_revocacion`, `revocado_por_id`, `ck_codigos_invitacion_revocacion`, FK `(empresa_id, usado_por_id)`, índice | Ninguna revocada | Quitar columnas y restricciones |

Se aplican solas al arrancar el backend (Flyway, transaccional en PostgreSQL). Backend y BD se
despliegan juntos. Probado sobre datos reales: el backend de `main` (V7) creó empresa,
administrador, trabajadores, invitaciones y un proveedor en PostgreSQL 16; la versión nueva migró
V8–V10, conservó todo y aceptó el token emitido por la versión anterior.

### 1.4 Pruebas ejecutadas (resultados reales)

| Prueba | Resultado |
| --- | --- |
| Suite del backend en H2 (`./mvnw test`) | 185 tests, 0 fallos (172 adaptados + 13 nuevos) |
| Misma suite contra PostgreSQL 16 | 185 tests, 0 fallos |
| Actualización V7 → V10 en PostgreSQL con datos de la versión anterior | Correcta (roles, nombres, invitaciones, restricciones, token antiguo) |
| Frontend `npm run lint` y `npm run build` | Sin avisos ni errores |
| Recorrido en navegador (Playwright) sobre los datos migrados | 9/9 comprobaciones, sin errores de consola |

Los tests nuevos cubren, entre otros: invitación expirada, revocada o usada que no permite
registrarse; revocación y canje simultáneos (siempre gana uno y la BD queda coherente);
aislamiento de invitaciones entre empresas; modo global obligatorio, registrado e inexistente para
ADMIN_EMPRESA; nombres conservados en la migración; perfil limitado a la propia cuenta; cambio de
nombre comercial visible sin volver a entrar; token antiguo válido con los permisos de la BD.

### 1.5 Riesgos pendientes

1. **Despliegue acoplado:** V8 exige desplegar backend y BD juntos (como siempre con Flyway).
2. **Cuentas antiguas sin nombres y apellidos** hasta que cada persona los complete (se muestra su
   nombre completo y un aviso).
3. **Sin cambio ni recuperación de contraseña** (la inicial la define el SUPER_ADMIN).
4. **Modo soporte de la API** para el SUPER_ADMIN (escrituras dentro de una empresa): autorizado y
   registrado, pero la interfaz no lo usa. Decidir si se restringe a solo lectura en el backend.
5. **Sin límite de intentos** en el login ni en el registro.
6. **`detalles_solicitud` sin `empresa_id`** (defensa en profundidad en la BD; el servicio ya lo
   impide): se aborda en la Fase 2.
7. **Rama paralela de órdenes de compra** (`claude/purchase-order-hito-1-7jpnkw`): coordinar antes
   de la Fase 2.

### 1.6 Requerimientos todavía no implementados

Órdenes de compra, recepciones, inventario (Fase 2); clientes, ventas, documentos y facturación
(Fases 3–4, previa validación del diseño); separación de costo y precio de venta en productos;
cambio de contraseña; gestión de varios administradores por empresa.

### 1.7 Próximo paso recomendado

Fase 2: auditar con la otra sesión el modelo de órdenes de compra y definir el de recepciones e
inventario (movimientos con bloqueo por producto), incluida la FK compuesta de
`detalles_solicitud`. Después, el diseño de ventas de la Fase 3 para revisión.

## Fase 2: analítica, compras, inventario y preparación del POS

### 2.0 Diagnóstico (Fase 0)

**Git.** Rama de trabajo = `main` = `6fc198c`, sin cambios pendientes. La rama
`claude/purchase-order-hito-1-7jpnkw` sigue en `c8886f3` sin commits propios: no existe un modelo
previo de órdenes que reutilizar ni con el que pueda haber duplicados.

| Área | Estado real | Evidencia |
| --- | --- | --- |
| Dashboard del SUPER_ADMIN | Pendiente | `Inicio` solo muestra conteos de solicitudes; no hay ningún endpoint de métricas |
| Dashboard del ADMIN_EMPRESA | Parcial | Conteos de solicitudes calculados en el cliente (tres peticiones `size=1` al listado); nada de productos, stock ni órdenes |
| Dashboard del TRABAJADOR | Parcial | Las mismas tarjetas, con sus solicitudes |
| Biblioteca de gráficas | No existe | `package.json` no incluye ninguna |
| Órdenes de compra | Pendiente (solo avisos en la interfaz) | Menú «Próximamente» y botón deshabilitado «Generar orden de compra»; sin tablas ni endpoints |
| Recepciones | Pendiente | Ídem |
| Existencias y movimientos | Pendiente | Sin tabla de stock, de movimientos ni stock mínimo |
| Código de barras | Pendiente | Los productos solo tienen SKU (único por empresa) |
| Precio de venta | Pendiente | `productos.precio` es único: hoy actúa como precio de referencia de compra (estimado de las solicitudes) |
| POS | Pendiente (fuera del alcance de esta fase) | — |

Dependencias y limitaciones técnicas que condicionan el diseño:

1. `productos` y `solicitudes` no tienen `UNIQUE (empresa_id, id)`: no pueden ser destino de las FK
   compuestas que impiden cruzar empresas en la BD. `detalles_solicitud` no tiene `empresa_id`
   (riesgo 6 de la Fase 1).
2. Proveedores y productos usan borrado lógico con `@SQLRestriction`: los documentos históricos
   (órdenes, recepciones, movimientos) necesitan vistas de solo lectura, como `ProductoReferencia`.
3. **Zona horaria.** Los instantes se guardan en UTC; los «días» del negocio son de Colombia
   (UTC−5, sin horario de verano). H2, la BD de los tests, no aplica bien `AT TIME ZONE`
   (comprobado: `03:00Z` del 16/10 da 16/10 en vez de 15/10), así que agrupar por día no puede
   depender de las funciones de zona de la BD.
4. La analítica de compras depende de las Entregas B y C: primero se crea el modelo (V11) y la
   analítica se calcula sobre él; mientras no haya órdenes responde «sin datos», no ceros inventados.
5. El modo soporte del SUPER_ADMIN (escrituras con `X-Tenant-ID: <id>`) sigue sin decidir: en los
   módulos nuevos las escrituras quedan reservadas al ADMIN_EMPRESA y el SUPER_ADMIN solo consulta.

Orden de trabajo: modelo (V11, V12) → A → B → C → D → interfaz → diseño del POS (E).

### 2.1 Modelo de datos común (V11 y V12)

- **V11 — compras e inventario** (no destructiva): `UNIQUE (empresa_id, id)` en productos y
  solicitudes; `detalles_solicitud.empresa_id` rellenada desde su solicitud, con FK compuestas
  (cierra el riesgo 6 de la Fase 1); `productos.stock_minimo` (0 por defecto); tablas
  `ordenes_compra`, `detalles_orden_compra`, `recepciones`, `detalles_recepcion`, `existencias`
  (una fila por producto, rellenada a 0 para los existentes) y `movimientos_inventario`. Todas con
  `empresa_id`, FK compuestas `(empresa_id, x_id)` —también la de autoría hacia `usuarios`— y
  CHECK de estados, cantidades y coherencia (emitida ⇒ quién y cuándo; cancelada ⇒ motivo;
  recibido ≤ pedido; existencias ≥ 0).
- **V12 — código de barras:** `productos.codigo_barras VARCHAR(64)` opcional, `UNIQUE
  (empresa_id, codigo_barras)` y CHECK de formato.
- **Verificado:** prueba de migración V10 → V12 en H2 con datos de dos empresas; en PostgreSQL,
  actualización real de la BD de la Fase 1 (V10 con usuarios, invitaciones, proveedor, productos y
  una solicitud): 2 migraciones aplicadas en 0,16 s, existencias creadas a 0 y líneas de solicitud
  con la empresa correcta. Ningún dato se perdió.

### 2.2 Matriz de permisos de los módulos nuevos (verificada en el backend)

| Recurso | SUPER_ADMIN | ADMIN_EMPRESA | TRABAJADOR |
| --- | --- | --- | --- |
| `GET /analitica/global[/empresas]` | Sí, solo con `X-Tenant-ID: global` (con `<id>` → 400) | 403 | 403 |
| `GET /analitica/empresa` | Supervisando (`<id>`) | Su empresa | 403 |
| `GET /analitica/personal` | 403 | Sí | Sí (sus solicitudes) |
| Órdenes y recepciones: consultar | Supervisión o global | Su empresa | 403 |
| Órdenes: crear, editar, emitir, cancelar; registrar recepciones | 403 | Su empresa | 403 |
| Existencias: consultar | Supervisión o global | Su empresa | Su empresa (sin costos) |
| Kárdex | Supervisión o global | Su empresa | 403 |
| Ajustes de inventario | 403 | Su empresa | 403 |
| `GET /productos/buscar?codigo=` | Solo supervisando (global → 400) | Su empresa | Su empresa |

Las escrituras de compras e inventario quedan reservadas al ADMIN_EMPRESA (nuevo
`Permisos.ADMIN_EMPRESA`); las FK de autoría lo impiden también en la BD. El modo soporte de
escritura del SUPER_ADMIN en los módulos antiguos sigue pendiente de decisión.

### 2.3 Entrega A — analítica

1. **Implementado:** `/api/v1/analitica/global`, `/global/empresas` (paginado), `/empresa` y
   `/personal`, con agregaciones COUNT/SUM/GROUP BY en la BD (número fijo de consultas,
   comprobado con estadísticas de Hibernate), período `desde`/`hasta` validado (por defecto 30
   días, máx. 366, nunca futuro), días contados en hora de Colombia y `totalHistorico` para
   distinguir "sin datos" de "cero". Dashboards por rol en *Inicio* con indicadores, columnas por
   día/mes, barras horizontales, lista de productos en alerta, vista de tabla, teclado y estados
   de carga, error y vacío. Sin indicadores de ventas.
2. **Reutilizado:** filtro de empresa (`@TenantId`), `TenantFilter` (modo global explícito),
   `Permisos`, `DataTable`, `usePaginatedResource`, tokens de la marca.
3. **Archivos:** `AnaliticaController`, `AnaliticaService`, `AnaliticaRepository`,
   `CalendarioNegocio`, `NegocioProperties`, `AnaliticaDTO`, `ModoGlobalRequeridoException`;
   frontend `features/analitica/*`, `components/charts/*`, `StatTile`, `useRecurso`,
   `lib/periodo.ts`, `lib/numeros.ts`, `pages/Inicio.tsx`.
4. **Migraciones:** ninguna propia (usa V11).
5. **Pruebas:** `AnaliticaTest` (8): autorización global/empresa/personal, modo global
   obligatorio, validación del período y granularidad, ceros reales vs. sin datos, aislamiento
   entre dos empresas con valores conocidos, días de Colombia (22:00 en Bogotá cuenta en su día) y
   consultas constantes con más datos.
6. **Limitaciones:** el agrupamiento por hora UTC exige zonas con desfases de horas completas (se
   valida al arrancar); sin caché (evaluado: consultas indexadas y acotadas a 366 días). No existe
   estado "pendiente" de empresa en el modelo, así que no se muestra.
7. **Pendiente:** ventas del día (requiere el POS).
8. **Siguiente paso:** medir los tiempos de las consultas con volumen real antes de plantear caché.

Gráficas sin dependencias nuevas (SVG propio). Color de la serie validado con el validador de
paletas contra la superficie clara y oscura de la marca (banda, croma, contraste ≥ 3:1).

### 2.4 Entrega B — órdenes de compra

1. **Implementado:** CRUD de borradores, número correlativo por empresa (fila de la empresa
   bloqueada), emisión, cancelación con motivo, transiciones validadas en la entidad con la orden
   bloqueada, generación desde una solicitud APROBADA (un borrador por proveedor, sin duplicados
   aunque se pida a la vez), proveedor y productos validados en la empresa y del mismo proveedor,
   re-validación del catálogo al emitir, bajas de productos y proveedores bloqueadas con órdenes
   abiertas. Interfaz: listado con filtros, ficha, formulario, emisión y cancelación; botón
   "Generar órdenes de compra" en la solicitud aprobada.
2. **Reutilizado:** patrón de `ProductoReferencia` (nuevo `ProveedorReferencia` para órdenes
   históricas), bloqueo pesimista de `SolicitudRepository`, `EntidadAuditable`.
3. **Archivos:** `OrdenCompra`, `DetalleOrdenCompra`, `EstadoOrdenCompra`, `OrdenCompraService`,
   `OrdenCompraController`, DTO de orden, repositorios; `ProductoService`/`ProveedorService`
   (bajas); frontend `features/ordenes/*`, `pages/OrdenesCompra.tsx`, `OrdenCompraDetalle.tsx`.
4. **Migraciones:** V11.
5. **Pruebas:** `OrdenCompraTest` (11), incluidas la carrera de 4 generaciones simultáneas de la
   misma solicitud (una sola orden) y 6 altas simultáneas (números 1–6 sin huecos ni repetidos),
   e ids manipulados de otra empresa en cada endpoint (404 o campo inválido, sin efectos).
6. **Limitaciones:** sin descuentos ni impuestos en las líneas (costo unitario pactado); la
   cantidad solicitada no se duplica en la orden (está en la solicitud enlazada); una orden
   recibida en parte no se puede cerrar con saldo pendiente (decisión pendiente: estado de cierre).
7. **Pendiente:** impuestos/descuentos, envío de la orden al proveedor (PDF/correo).
8. **Siguiente paso:** decidir la política de cierre de saldos y la de impuestos.

### 2.5 Entrega C — recepciones e inventario

1. **Implementado:** recepciones parciales y totales (solo lo efectivamente recibido; nunca más
   de lo pendiente), existencias por producto, kárdex inmutable con saldo resultante, usuario,
   fecha, origen y costo factual, ajustes manuales con motivo (inventario inicial, conteo, merma),
   idempotencia por clave del cliente (mismos datos → 200 con lo ya registrado; otros → 409) y
   todo en una transacción. Interfaz: registro de recepción, historial, página de existencias con
   filtros, ajustes y kárdex.
2. **Reutilizado:** bloqueo pesimista, `GlobalExceptionHandler` (nuevas restricciones mapeadas).
3. **Archivos:** `Recepcion`, `DetalleRecepcion`, `Existencia`, `MovimientoInventario`,
   `TipoMovimiento`, `EstadoStock`, `RecepcionService`, `InventarioService`, controladores y DTO;
   frontend `features/inventario/*`, `RecepcionModal`, `pages/Inventario.tsx`, `Recepciones.tsx`.
4. **Migraciones:** V11.
5. **Pruebas:** `RecepcionInventarioTest` (10): 4 recepciones simultáneas sobre lo pendiente
   (solo una entra, 3 rondas), la misma clave enviada 4 veces a la vez (una sola recepción), 5
   retiros simultáneos sobre 10 unidades (3 entran, saldos 10→7→4→1), todo o nada ante errores,
   aislamiento y permisos. En PostgreSQL con bloqueos reales.
6. **Valoración (decisión documentada, sin cálculos financieros nuevos):** el costo del catálogo
   no se usa como costo de las existencias. Cada entrada guarda su costo factual (el de la orden o
   el del ajuste). Propuesta pendiente de validación contable: costo promedio ponderado
   (`costo_promedio` en existencias, recalculado en cada entrada). Detalle en `docs/pos-diseno.md` §8.
7. **Pendiente:** cantidades reservadas (POS), unidades de medida y cantidades fraccionarias,
   entregas excedentes (hoy rechazadas de forma explícita), devoluciones a proveedor.
8. **Siguiente paso:** aprobar la estrategia de valoración.

### 2.6 Entrega D — código de barras

1. **Implementado:** campo opcional (vacío = sin código), texto que conserva ceros a la
   izquierda, único por empresa (repetible entre empresas), `GET /productos/buscar?codigo=` con la
   empresa del contexto (nunca un `empresa_id` del cliente; en modo global, 400), coincidencia
   exacta por código de barras y después por SKU, preferencia por productos activos, 409 si solo
   coincide uno dado de baja, 404 si no existe. Interfaz: campo en el formulario (el Enter del
   lector pasa al campo siguiente) y buscador por escáner en *Existencias*.
2. **Reutilizado:** `ProductoReferencia`, convención de restricciones únicas del SKU.
3. **Archivos:** `Producto`, `ProductoRequestDTO`/`ResponseDTO`, `ProductoService`,
   `ProductoController`, `Patrones`; frontend `BuscadorCodigo`, `ProductoFormModal`, esquemas.
4. **Migraciones:** V12.
5. **Pruebas:** `CodigoBarrasTest` (5) y la prueba de migración.
6. **Limitaciones:** un código por producto; no se normalizan mayúsculas (Code 128 distingue).
7. **Pendiente:** varios códigos por producto (presentaciones, códigos del proveedor) en una tabla propia.
8. **Siguiente paso:** decidirlo junto con el POS.

### 2.7 Entrega E — diseño del POS

Solo diseño, en `docs/pos-diseno.md`: entidades, contratos, flujo, permisos, concurrencia,
comprobante interno sin valor fiscal (la facturación electrónica no está implementada ni se
afirma su cumplimiento) y diez decisiones pendientes.

### 2.8 Validación ejecutada (resultados reales)

| Verificación | Resultado |
| --- | --- |
| Backend en H2 (`mvn test`) | 220 tests, 0 fallos (185 anteriores + 35 nuevos) |
| Misma suite contra PostgreSQL 16 | 220 tests, 0 fallos |
| Actualización V10 → V12 en PostgreSQL con los datos de la Fase 1 | Correcta |
| Frontend `npm run lint`, `tsc -b` y `npm run build` | Sin avisos ni errores |
| Recorrido en navegador (Playwright) sobre los datos migrados | 12/12 comprobaciones, sin errores de consola (modo oscuro, claro y móvil de 390 px) |

## Fase 3: documento Word, entregas, recepción operativa y calendario

Fecha: 19/10/2026. Base: `main` = `fec3689` (Fase 2), sin cambios pendientes.

### 3.0 Diagnóstico

| Requisito | Estado encontrado | Decisión |
| --- | --- | --- |
| Documento Word de la orden emitida | No existía (ni biblioteca ni endpoint) | Apache POI `poi-ooxml` 5.5.1; instantánea inmutable al emitir |
| Modificación de órdenes emitidas | No existe: las líneas y observaciones se congelan al emitir | Una versión documental por emisión; la tabla admite versiones futuras |
| Datos de empresa para el documento | Solo nombre, NIT y código; sin dirección ni logo (la marca Unisen es de la plataforma) | No se inventan: el documento muestra lo que hay |
| Unidad de medida, impuestos, descuentos, cargo | No existen en el modelo | No se muestran; el total es la suma de líneas (regla vigente) |
| Programación de entregas | No existía | Historial de solo inserción (`programaciones_entrega`), la orden apunta a la vigente |
| Auditoría reutilizable | Solo columnas de autoría (quién/cuándo) y registro en el log; no hay tabla de eventos | El historial se deriva de los datos; solo se añade la tabla de programaciones (no hay otra fuente para los cambios de fecha) |
| Recepción por el trabajador | `POST …/recepciones` exigía ADMIN_EMPRESA; las respuestas llevaban costos | `MIEMBRO_EMPRESA` (pedido explícito del requerimiento) y vistas sin costos |
| Faltantes, daños, productos incorrectos | Solo observaciones de texto | `incidencias_recepcion`, informativas (no mueven inventario) |
| Exceso de entrega | Rechazado (CHECK recibido ≤ pedido) | Se mantiene; el excedente se informa como incidencia, sin entrar al inventario |
| Calendario | No existía | Eventos derivados; sin tabla de calendario |
| Pruebas de frontend | No había framework | Vitest + Testing Library (jsdom) |
| Entorno de esta sesión | Java 17 en la máquina (el proyecto exige 21); el PostgreSQL local en ejecución es de otro proyecto | JDK 21 temporal y un contenedor PostgreSQL 16 propio y efímero |

### 3.1 Migración V13 (no destructiva)

- `documentos_orden_compra`: instantánea JSON (`contenido`), versión, motivo (`EMISION`), huella
  SHA-256, quién y cuándo. Única por (orden, versión). FK compuestas a orden y usuario.
- `programaciones_entrega`: versión, `fecha_estimada DATE`, franja opcional (`hora_desde` <
  `hora_hasta`, ambas o ninguna), `confirmada_proveedor`, referencia, observaciones, motivo
  (obligatorio desde la versión 2), quién y cuándo. `ordenes_compra.programacion_entrega_id`
  apunta a la vigente con una FK `(empresa_id, id, programacion_entrega_id)` que impide apuntar a la
  programación de otra orden o de otra empresa.
- `incidencias_recepcion`: tipo (FALTANTE, DANADO, EXCEDENTE, PRODUCTO_INCORRECTO, OTRO), línea y
  cantidad (obligatorias en los tres primeros) o descripción (en los dos últimos).
- Datos existentes: ninguno cambia. Las órdenes emitidas antes de V13 no tienen instantánea; su Word
  se reconstruye al descargar (sin escribir en la BD) y lo indica en el propio documento.

### 3.2 Matriz de permisos resultante (verificada en el backend)

| Recurso | SUPER_ADMIN | ADMIN_EMPRESA | TRABAJADOR |
| --- | --- | --- | --- |
| Descargar el Word y ver versiones documentales | Supervisando o global | Su empresa | 403 (lleva costos) |
| Programar / reprogramar entrega | 403 | Su empresa | 403 |
| Historial de programación | Supervisando | Su empresa | Órdenes emitidas de su empresa |
| Historial completo de la orden | Supervisando o global | Su empresa | 403 |
| Entregas: listado y detalle (sin costos) | Supervisando (global → 400) | Todas las vistas | Solo vistas de lo pendiente (otras → 403); borradores y canceladas → 404 |
| Resumen de entregas | Supervisando | Sí | 403 |
| Registrar recepción | 403 | Sí | Sí (respuesta sin costos) |
| Recepciones: listado con costos y filtros | Supervisando o global | Sí | 403 |
| Mis recepciones · detalle de una recepción | — · supervisando | Sí · sí | Sí · sí (sin costos) |
| Calendario | Supervisando (global → 400) | Todos los tipos | Entregas, recepciones y sus solicitudes pendientes |

### 3.3 Entrega A — auditoría y documento Word

1. **Implementado:** instantánea al emitir (misma transacción que la emisión), `.docx` generado en
   cada descarga desde la instantánea (empresa compradora, número, fecha, proveedor con sus datos
   disponibles, entrega vigente al emitir, líneas con código, código de barras, descripción,
   cantidad, precio unitario y subtotal, total en COP, observaciones, quién emitió y con qué rol,
   pie con versión, huella y paginación), versiones documentales y botón *Descargar Word* en la ficha.
2. **Reutilizado:** `ProveedorReferencia`/`ProductoReferencia` (documentos históricos), el filtro de
   empresa (`@TenantId`), `NegocioProperties` (zona horaria) y el bloqueo de la orden al emitir.
3. **Almacenamiento:** sin binarios ni infraestructura nueva: la instantánea (≤ 200 000 caracteres)
   basta para regenerar el mismo documento.
4. **Pruebas:** `DocumentoOrdenTest` (5): .docx válido (ZIP con `word/document.xml`, legible por POI)
   con los datos correctos y sin datos inventados, inmutabilidad frente a cambios del catálogo,
   solo órdenes emitidas, reconstrucción sin escritura, autorización por rol y aislamiento (404).
5. **Limitaciones:** sin PDF (se exporta desde Word o LibreOffice); sin logo ni dirección de la
   empresa (no existen en el modelo).

### 3.4 Entrega B — programación de entregas y calendario

1. **Implementado:** `PUT /ordenes-compra/{id}/entrega` (versión nueva por cambio, motivo al
   reprogramar, fecha no pasada salvo la vigente, máximo dos años, idempotente si no cambia nada,
   orden bloqueada), historial, `GET /calendario` (máx. 62 días, eventos ordenados, todo el día
   cuando no hay hora) y la página *Calendario* (mes, semana y día, navegación, filtros por tipo y
   estado, detalle con acciones según el rol, estados vacío y de error, móvil).
2. **Qué fecha representa cada evento:** emisión = instante de emisión; entrega = fecha estimada
   vigente (con franja si se acordó); recepción = instante real del registro; solicitud = creación.
3. **Pruebas:** `EntregaTest` (7) y `CalendarioHistorialTest` (4), incluidas 4 reprogramaciones
   simultáneas (versiones 1–5 sin huecos ni repetidas) y el aislamiento entre empresas.

### 3.5 Entrega C — recepción del trabajador y supervisión del gerente

1. **Implementado:** *Recibir mercancía* (pendientes por fecha estimada, retrasadas primero; filtros;
   ficha con lo pendiente; registro por producto de recibidas, dañadas y faltantes; excedentes,
   productos incorrectos y otras novedades; resumen antes de confirmar; resultado; *Mis
   recepciones*), *Entregas* (cifras, vistas, filtros por fecha estimada, proveedor y número,
   programar y reprogramar, alertas) y *Recepciones* (filtros, incidencias, quién registró, detalle).
2. **Reglas:** aceptado ≤ pendiente; aceptado + dañado + faltante ≤ pendiente; el excedente no entra
   al inventario; una entrega solo con incidencias no cambia el estado de la orden; quién, la
   empresa y la hora salen del servidor (los campos enviados por el cliente se ignoran).
3. **Pruebas:** `RecepcionOperativaTest` (7): identidad del servidor, sin costos, incidencias y
   validaciones, idempotencia con incidencias, trabajador y administrador recibiendo a la vez (3
   rondas, una sola recepción), trazabilidad movimiento → recepción → orden, inmutabilidad (405) y
   aislamiento.

### 3.6 Entrega D — historial, inventario y extremo a extremo

1. **Historial:** `GET /ordenes-compra/{id}/historial` reconstruye solicitud, aprobación, creación,
   emisión, documento, programaciones, recepciones (parciales, final o solo incidencias) con sus
   movimientos de kárdex y la cancelación, en un número fijo de consultas.
2. **Inventario:** sin cambios en la valoración ni en las reglas; cada movimiento de recepción sigue
   enlazado a su línea de recepción (y por ella a la orden).

### 3.7 Validación ejecutada en esta sesión (resultados reales)

| Verificación | Resultado |
| --- | --- |
| Backend antes de los cambios, H2 | 220 tests, 0 fallos |
| Backend final, H2 (`./mvnw test`) | 244 tests, 0 fallos (220 anteriores, 2 adaptados al nuevo permiso y mensaje, 24 nuevos) |
| Backend final, PostgreSQL 16.15 (contenedor efímero) | 244 tests, 0 fallos |
| Actualización real V12 → V13 en PostgreSQL con datos de la Fase 2 | Correcta: solo V13 aplicada, orden, recepción, existencias y kárdex intactos; el backend arrancó sobre esa BD (validación del esquema), descargó el Word reconstruido de la orden antigua (`X-Documento-Reconstruido: true`), programó la entrega y un trabajador recibió el saldo (orden RECIBIDA, existencias 4 → 10) |
| Frontend `npm run lint`, `tsc -b`, `npm run build` | Sin avisos ni errores |
| Frontend `npm test` (Vitest) | 26 pruebas, 0 fallos (fechas, filtros, validación de la recepción, menú por rol, calendario, recepción del trabajador, entregas y guardas de ruta) |
| Navegador (Playwright + Chromium) contra backend y PostgreSQL reales | 23/23 comprobaciones, sin errores de consola (escritorio, móvil 390 px claro y oscuro) |
| Documento generado, abierto con LibreOffice y convertido a PDF | Correcto (revisión visual) |

### 3.8 Decisiones pendientes

1. **Excedentes:** aceptar unidades de más (con qué tope, quién lo autoriza y cómo se valora).
2. **Corrección de una recepción confirmada:** hoy se corrige con un ajuste de inventario; un
   proceso de anulación o nota de corrección con auditoría propia está por definir.
3. **Cierre de órdenes con saldo pendiente** (ya pendiente desde la Fase 2), relevante para los
   faltantes que el proveedor no repondrá.
4. **Impuestos y descuentos** en la orden y en el documento.
5. **Identidad documental de la empresa:** dirección, teléfono, logo y, si se requiere, cargo del
   firmante; y unidad de medida de los productos.
6. **PDF** y envío del documento al proveedor.
7. **Programación por el SUPER_ADMIN:** sigue sin escritura (decisión vigente); confirmar si debe
   cambiar.

## Módulo comercial (V14–V16): roles, precios, POS, comprobantes, devoluciones, reportes y seed

Fecha: 20/10/2026. Base: `main` = `c1e6121`, sin cambios pendientes (copia en la etiqueta
`backup/pre-modulo-comercial-2026-10-19`). Reglas, fórmulas, decisiones y cuentas de demostración en
[modulo-comercial.md](modulo-comercial.md).

| Entrega | Resultado |
| --- | --- |
| A — Rol y PDF | V14 `ADMIN_EMPRESA → ADMINISTRADOR_COMERCIAL` (sin pérdida de cuentas; tokens antiguos con permisos de la BD). PDF de la orden con OpenPDF desde la instantánea (misma huella que el Word) |
| B — Modelo comercial | V15: precio de venta sin IVA y tratamiento del IVA por producto, historial de precios, política, autorizaciones de un solo uso; V16: costo promedio inicial desde el kárdex (método provisional) |
| C — POS | Cotización y venta transaccional, idempotente, con existencias bloqueadas en orden de producto, pagos mixtos, límites de descuento por rol |
| D — Documentos e historial | Comprobante PDF interno (no es factura electrónica), historial, devoluciones parciales/totales con reingreso opcional, anulación del mismo día, reportes con datos históricos |
| E — Seed | Empresas ficticias (perfil `demo`), idempotente, bloqueada con perfil de producción, contraseña solo desde `DEMO_PASSWORD`. El 21/10/2026 se simplificó a cinco empresas y diez cuentas (ver [modulo-comercial.md §10](modulo-comercial.md#10-datos-de-demostración-seed)) |

Validación ejecutada en esta sesión: backend 303 tests en H2 y la misma suite en PostgreSQL 16
(contenedor efímero); frontend lint, `tsc -b`, 46 pruebas Vitest y build. Detalle de resultados y
pasos de prueba en el informe final de la sesión y en `modulo-comercial.md`.


## Fase A (Nexora): auditoría, inventario de marca y preparación de B.6, B.7 y B.8

Fecha: 21/10/2026. Base: rama `claude/loving-edison-5cogo9` = `main` = `63df7d6`, sin cambios
pendientes. **Alcance autorizado: solo la Fase A** (diagnóstico, inventario y plan). Este
apartado no implementa nada de las Fases B (B.6) y C (B.7, B.8); todo lo de esas fases se describe
como propuesta.

**Identidad comercial (decisión del propietario, 21/10/2026):** el producto se llama **Nexora**.
«SGOC», «SGP» y «Unisen SGP» son nombres históricos o identificadores técnicos.

**Dominio:** el dominio **actual de producción es `https://rrtf.duckdns.org/`** y sigue siéndolo.
`nexora.duckdns.org` es solo un destino **futuro** propuesto: no se ha comprobado su DNS, su
certificado ni el proxy del host, no existe ninguna referencia a él en el código y nada se migra
sin autorización expresa.

### A.1 Hitos de referencia frente al código real

El repositorio no usa la numeración H1–H8: sus commits y este documento hablan de «Hitos 1–11»,
«Fases 0–3» y «Módulo comercial». La correspondencia, verificada en el código:

| Hito | Estado real | Evidencia | Huecos |
| --- | --- | --- | --- |
| H1 Multiempresa | ✅ | `@TenantId` en `EntidadDeEmpresa`, `TenantFilter`, FK compuestas `(empresa_id, x_id)`, `AislamientoMultiEmpresaTest`, `AislamientoTrabajadoresTest` | Modo soporte de escritura del SUPER_ADMIN por API sin decidir (riesgo 4 de la Fase 1) |
| H2 Identidad y onboarding | 🟡 | Alta atómica empresa + administrador, invitaciones, registro, `/api/v1/trabajadores` | Sin interfaz para registrarse con código de empresa ni para aprobar trabajadores (ver A.4); sin cambio ni recuperación de contraseña; sin límite de intentos en login/registro |
| H3 Catálogo | ✅ | Proveedores y productos por empresa, código de barras (V12), precios (V15) | Unidad de medida |
| H4 Inventario | ✅ | Existencias, kárdex, ajustes (V11), costo promedio (V16) | Valoración **provisional** |
| H5 Solicitudes | ✅ | Maestro-detalle con revisión | `empresaNombre` en modo global |
| H6 Órdenes de compra | ✅ | Borradores, emisión, Word/PDF, entregas (V11, V13) | Impuestos, descuentos, cierre con saldo |
| H7 Recepción | ✅ | Parciales, incidencias, idempotencia | Excedentes, corrección de recepciones |
| H8 Dashboards | ✅ | `/api/v1/analitica/*` y `Inicio` por rol | Sin indicadores de personal (B.6) |

### A.2 Autenticación, roles y rutas del frontend (para B.6 y B.7)

- **Roles reales:** `SUPER_ADMIN`, `ADMINISTRADOR_COMERCIAL` (el antiguo `GERENTE`, luego
  `ADMIN_EMPRESA`; V8 y V14) y `TRABAJADOR`. No existe un rol `GERENTE` en el código actual.
- **Autorización:** `@PreAuthorize` con `Permisos`; el filtro JWT carga rol, estado y empresa de la
  BD en cada petición. Las guardas del frontend (`RutaProtegida`, `RutaConRol`, `RutaInvitado`) son
  solo de interfaz.
- **Rutas públicas:** `/login` y `/registro` (solo con **código de invitación**). No hay
  recuperación de contraseña (no existe el mecanismo).
- **Ruta inicial:** `/` está dentro de `RutaProtegida`: un visitante sin sesión va a `/login`
  (recordando la ruta pedida en `state.from`); con sesión, `Inicio` muestra el dashboard según el
  rol (no hay rutas de dashboard distintas por rol). Cualquier otra ruta desconocida vuelve a `/`.
- **Nginx:** `try_files $uri $uri/ /index.html` y `listen 80 default_server`: el acceso directo a
  cualquier ruta de la SPA ya funciona y una ruta pública nueva no requiere cambios en Nginx.

### A.3 Paquete de marca: `nexora-brand-assets.zip`

Ubicación inspeccionada: el adjunto de la sesión `c416469a-nexora-brand-assets.zip` (9 330 bytes,
SHA-256 `8978f9d5397031d171aaedfe5f94d4fd53ad967abdaef1dc7608a8b293aa32cc`). `unzip -t` sin
errores; extraído en un directorio temporal, **no** copiado al repositorio.

| Archivo | Lienzo | Contenido | Uso propuesto |
| --- | --- | --- | --- |
| `nexora-logo-claro.svg` | 624,25 × 110 | Isotipo `#6B1E2E` + punto `#C9A86A` + palabra en trazos `#1C1416` | Logotipo sobre fondos claros (cabecera, login móvil, página pública) |
| `nexora-logo-oscuro.svg` | 624,25 × 110 | Igual con trazo `#B2475C` y palabra `#F5EFE6` | Fondos oscuros (panel de marca del login, tema oscuro) |
| `nexora-isotipo.svg` | 140 × 68 | «Bucle Operativo» solo | Barra lateral contraída, cargas |
| `nexora-isotipo-oscuro.svg` | 140 × 68 | Isotipo con trazo `#B2475C` | Ídem en tema oscuro |
| `nexora-isotipo-monocromo.svg` | 140 × 68 | Una tinta `#1C1416`, trazo abierto alrededor del punto | Comprobantes y documentos en blanco y negro (PDF/Word), si se aprueba |
| `nexora-favicon.svg` | 150 × 150 | Isotipo con `<style>` y `prefers-color-scheme` | Sustituir `frontend/public/favicon.svg` |
| `nexora-masterbrand.svg` | 1200 × 940 | Lámina de identidad (construcción, tokens, aplicación) | Solo referencia de diseño: **no** se sirve en la aplicación |
| `README.md` | — | Paleta, usos y fragmentos de React/HTML | Referencia; sus fragmentos no se ejecutan |

- **Seguridad:** XML bien formado en los siete SVG; sin `<script>`, manejadores `on*`, `href`,
  `<image>`, `<foreignObject>` ni recursos externos. El único `<style>` es el del favicon.
- **Dependencias:** ninguna. No trae bibliotecas, componentes, animaciones, `package.json` ni
  scripts: **no hay nada que instalar**. Encaja con lo que ya hay (Vite importa SVG como URL, igual
  que `BrandLogo.tsx`; Geist ya se sirve con `@fontsource-variable/geist`).
- **Licencia:** el paquete no incluye licencia; se asume material propio del propietario (confirmar).
- **Duplicados:** ninguno exacto; las variantes claro/oscuro difieren solo en los colores.
- **Paleta (`--brand #6B1E2E`, `--brand-dark #B2475C`, `--gold #C9A86A`, `--text #1C1416`,
  `--text-dark #F5EFE6`, `--surface-dark #1E181A`).** Los tokens actuales de
  `src/brand/unisen/tokens.css` (azul `#1d2b4f` y terracota `#b5552d`) están marcados como
  **marcadores de posición** desde el inicio, así que adoptar la paleta Nexora es sustituir
  valores, no rediseñar. Contraste WCAG calculado:

  | Par | Contraste | Lectura |
  | --- | --- | --- |
  | `#6B1E2E` sobre `#FFFFFF` / `#F6F5F1` | 11,35 / 10,40 : 1 | Válido para texto y botones primarios |
  | `#FFFFFF` sobre `#6B1E2E` | 11,35 : 1 | Texto de botón primario válido |
  | `#C9A86A` sobre `#FFFFFF` / `#F6F5F1` | 2,26 / 2,07 : 1 | **No** apto para texto ni para elementos gráficos informativos en tema claro: solo decorativo |
  | `#C9A86A` sobre `#1E181A` / `#6B1E2E` | 7,74 / 5,02 : 1 | Válido como acento en superficies oscuras o de marca |
  | `#B2475C` sobre `#1E181A` | 3,29 : 1 | Solo texto grande, iconos y bordes (≥ 3:1); **no** texto normal (< 4,5:1) |
  | `#F5EFE6` sobre `#1E181A` | 15,30 : 1 | Texto principal del tema oscuro |

  Consecuencia: en el tema oscuro el color primario de los textos y enlaces no puede ser
  `#B2475C` tal cual; hará falta un tono más claro derivado (decisión de diseño de la Fase C).
- **Adaptaciones necesarias:** los SVG traen `width`/`height` fijos (se escalan con
  `height: …; width: auto`); el favicon hereda el tema del sistema, no un `data-theme` forzado.

### A.4 B.6: datos disponibles para la vista de personal

| Pregunta | Respuesta verificada |
| --- | --- |
| Relación usuario ↔ empresa | `usuarios.empresa_id` (nula solo para SUPER_ADMIN); FK compuestas `(empresa_id, usuario_id)` en todo lo que un usuario firma |
| Roles y estados | `Rol` y `EstadoUsuario` (`PENDIENTE`, `ACTIVO`, `RECHAZADO`, `INACTIVO`), leídos de la BD en cada petición |
| Endpoint existente | `GET /api/v1/trabajadores[?estado=]` (paginado, `Permisos.GESTION`, empresa de `TenantContextHolder`, solo rol TRABAJADOR) y `PATCH /{id}/estado` (aprobar, rechazar, desactivar, reactivar, con bloqueo de fila). Probado en `OnboardingTrabajadoresTest` y `AislamientoTrabajadoresTest` |
| Vista equivalente | **No hay ninguna para el ADMINISTRADOR_COMERCIAL**: `/admin/invitaciones` solo gestiona códigos. El SUPER_ADMIN tiene `UsuariosEmpresa` en `EmpresaDetalle.tsx` (tabla de solo lectura reutilizable como patrón) |
| Última conexión real | **No existe.** `AuthService.login` no persiste ni registra el inicio de sesión; `usuarios` no tiene columna de acceso |
| Última operación registrada | Derivable, sin tablas nuevas: `solicitudes.usuario_id` (índice `ix_solicitudes_usuario_fecha`), `recepciones.recibida_por_id`, `ventas.vendida_por_id` (índice `ix_ventas_vendedor`), `movimientos_inventario.usuario_id`, `devoluciones_venta.registrada_por_id`. `recepciones` y `movimientos_inventario` no tienen índice por usuario |
| Auditoría general | No hay tabla de eventos; solo columnas de autoría y el log |

Consecuencia importante: los trabajadores que se registran con el **código de empresa** quedan
`PENDIENTE` y hoy **ningún administrador puede aprobarlos desde la interfaz** (solo por API); además
`/registro` no ofrece esa vía. El README ya lo lista en «Pendiente en el backend».

**Propuesta para la Fase B (no implementada):** página `/admin/personal` (ADMINISTRADOR_COMERCIAL)
con `DataTable`, `usePaginatedResource` y `EstadosBadges`, filtro por estado y columnas nombre,
usuario, estado y alta. Actividad: un endpoint nuevo de solo lectura, `Permisos.ADMINISTRADOR_COMERCIAL`,
que agregue por trabajador de la página (una consulta por fuente, sin N+1) la **última operación
registrada** con su tipo (p. ej. «Solicitud · 20/10/2026») y conteos del período; rotulado siempre
como «Última operación», nunca como «Última conexión». Sin datos → «Sin actividad registrada».
La «última conexión» exigiría una migración (`usuarios.ultimo_acceso_en`, escrita en el login) y
**no** se propone sin autorización. Decidir si la vista incluye las acciones de aprobar, rechazar,
desactivar y reactivar (ya implementadas y autorizadas en el backend) o si es solo de consulta.

### A.5 B.6.2: tarjeta del administrador para el trabajador

- **Cómo se determina:** «administrador principal» = el `ADMINISTRADOR_COMERCIAL` de menor id de la
  empresa (`UsuarioRepository.findPrimeroConRolPorEmpresa`). Hoy solo se crea uno por empresa (con
  la empresa), pero **la BD no lo impide** y la gestión de varios administradores está sin decidir:
  la relación es inequívoca por convención, no por esquema. Puede estar `INACTIVO`.
- **Qué ve ya el trabajador:** el nombre de quien revisó cada solicitud suya (`revisadoPor` en
  `SolicitudResponseDTO`) y, en *Mi perfil*, su empresa (nombre y código).
- **Valor práctico:** bajo. Quien más lo necesitaría (cuenta `PENDIENTE`, `RECHAZADA` o `INACTIVA`)
  **no puede iniciar sesión** (403 con `motivo`), así que una tarjeta dentro de la aplicación no le
  llega. Un trabajador activo ya ve a su revisor.
- **Recomendación:** **posponer** hasta decidir si habrá varios administradores. Si se aprueba:
  campo opcional en `GET /api/auth/me` solo para TRABAJADOR, con el nombre visible de los
  administradores **activos** (sin correo ni username), calculado al vuelo (sin duplicar datos), y una
  tarjeta discreta en *Mi perfil*.

### A.6 B.7: página pública de bienvenida

- **Encaje propuesto (Fase C, no implementado):** en `/`, un componente que muestre la página
  pública sin sesión y `Inicio` con sesión; `/login` y `/registro` se conservan; `RutaProtegida`
  sigue llevando a `/login` cualquier ruta privada (los enlaces directos no cambian). Un usuario con
  sesión nunca ve la página pública. Sin endpoints nuevos: el contenido es estático.
- **Contenido verificable:** solicitudes y aprobación, órdenes de compra con documento PDF/Word,
  entregas y recepción con incidencias, existencias y kárdex con código de barras, punto de venta
  con comprobante interno (no es factura electrónica), dashboards por rol, multiempresa con
  aislamiento. Botón principal «Iniciar sesión»; enlace secundario «Tengo un código de invitación»
  (`/registro`), que es el único registro habilitado. Sin clientes, cifras ni testimonios.
- **A decidir:** si la página vive en `/` (recomendado) o en una ruta propia; si se menciona Unisen
  (p. ej. en el pie) o solo Nexora.

### A.7 Referencias técnicas al nombre y al dominio

**Dominio actual `rrtf.duckdns.org`** (14 apariciones, todas vigentes y correctas hoy). Qué habría
que tocar **si algún día** se migra:

| Dónde | Tipo | Para una futura migración a `nexora.duckdns.org` |
| --- | --- | --- |
| `.env.example` (`APP_DOMAIN`), `docker-compose.yml` (`CORS_ALLOWED_ORIGINS` deriva de `APP_DOMAIN`) | Configuración | Cambiar `APP_DOMAIN` en el `.env` del VPS (durante la transición, admitir ambos orígenes) |
| `application.yml` (valor por defecto de `CORS_ALLOWED_ORIGINS`), `backend-spring/.env.example` | Configuración | Actualizar el valor por defecto |
| `frontend/nginx.conf` (`server_name`, comentario) | Configuración | Añadir el nombre; como es `default_server`, ya atiende cualquier `Host` |
| `AuthControllerTest` (3), `application-test.yml` | Pruebas de CORS | Actualizar junto con lo anterior |
| `README.md` (6), `backend-spring/README.md` (2), `deploy/aatenea/README.md`, `CLAUDE.md`, `frontend/.env.example`, comentario en `lib/axios.ts` | Documentación | Actualizar al migrar |
| Fuera del repositorio: DNS de DuckDNS, certificado y proxy del host (Caddy u otro), `.env` del VPS | Infraestructura | **No verificado**; requiere autorización |

La SPA usa `/api` en el mismo origen y Spring usa `forward-headers-strategy: framework` con
`X-Forwarded-Host` de Nginx, así que las peticiones del propio dominio deberían reconocerse como
mismo origen; debe comprobarse con un login real tras el cambio.

**Nombre:** 247 clases Java en `com.unisen.sgp`; `sgp-backend`/`sgp-frontend` (Maven y npm);
proyecto Compose `sgp`, BD y usuario `sgp`, imágenes `unisen/sgp-*`; `src/brand/unisen/` y las
variables `--unisen-*`; script y rutas del VPS (`/root/sgp-unisen`, `/root/sgp-private`).
**Se conservan**: renombrarlos no aporta nada visible y rompe despliegue, scripts o datos. Tres
identificadores **no deben cambiarse** sin un plan propio: el emisor JWT `unisen-sgp` (cerraría todas
las sesiones), la clave `sgp.session` del `localStorage` (ídem) y la BD `sgp` (datos de producción).

Textos **visibles** con «Unisen» que pasarían a Nexora en la Fase C: `<title>` de `index.html`,
`alt` de `BrandLogo.tsx`, `AuthLayout.tsx` (lema y pie), `Login.tsx` («usuario de Unisen»),
`pages/admin/Empresas.tsx` (descripción), título de la API en `OpenApiConfig.java` y la línea de
cabecera del archivo de cuentas demo de `sgp-aatenea.sh`.

### A.8 Verificación ejecutada en esta sesión

| Comando | Resultado |
| --- | --- |
| `cd frontend && npm run lint` | Sin avisos ni errores (código de salida 0) |
| `cd frontend && npm test` | 51 pruebas, 0 fallos |
| `cd frontend && npm run build` | Correcto (`tsc -b` + Vite) |
| `cd backend-spring && ./mvnw test` (H2) | 312 pruebas, 0 fallos, 0 errores (el `README.md` dice 303: discrepancia ya registrada como pendiente en `CLAUDE.md`, no se corrige aquí) |

No se ejecutó la suite contra PostgreSQL ni un recorrido en navegador (no hay cambios funcionales
que verificar). No se ejecutó Docker.

### A.9 Riesgos y pendientes detectados

1. Trabajadores `PENDIENTE` sin interfaz de aprobación (A.4): el flujo «código de empresa» está a
   medias para el usuario final.
2. Sin límite de intentos en login y registro (pendiente desde la Fase 0).
3. Sin cambio ni recuperación de contraseña (pendiente desde la Fase 0).
4. El acento dorado no es apto para texto en tema claro y el borgoña oscuro no lo es para texto
   normal en tema oscuro (A.3).
5. La migración de dominio depende de DNS, TLS y proxy fuera del repositorio, no verificados.

### A.10 Decisiones que necesita el propietario antes de seguir

1. Autorizar la Fase B y, en ella, si la vista de personal incluye las acciones de estado ya
   existentes o solo consulta (A.4).
2. Aceptar que el resumen de actividad muestre «última operación registrada» y no «última conexión»,
   o autorizar la migración para registrar accesos.
3. Posponer la tarjeta del administrador (recomendado) o implementarla con el diseño de A.5.
4. Autorizar la Fase C: página pública en `/`, sustitución de los tokens por la paleta Nexora (con
   el ajuste de contraste del tema oscuro) y de los logotipos y el favicon.
5. Papel de Unisen frente a Nexora en los textos (pie, documentos PDF/Word) y licencia del paquete.
6. Fecha y procedimiento de la migración al dominio `nexora.duckdns.org`.

## Fase B (Nexora): vista de personal (B.6)

Fecha: 21/10/2026. Autorizada por el propietario tras la Fase A. Decisiones aplicadas, las
recomendadas en A.4 y A.5: la vista incluye las acciones de estado que **ya existían** en el backend
(no se crea ningún permiso nuevo); la actividad es la **última operación registrada**, nunca la
última conexión; la tarjeta del administrador para el trabajador (B.6.2) **se pospone**.

### B.1 Cambios

| Capa | Archivo | Qué |
| --- | --- | --- |
| Backend | `TrabajadorController` | `GET /api/v1/trabajadores/actividad[?estado=]` (paginado, mismos filtros y orden que el listado) |
| Backend | `PersonalService` (nuevo) | Reutiliza `UsuarioService.listarTrabajadores` (misma autorización `Permisos.GESTION` y misma empresa) y añade la actividad: una consulta agregada por fuente para toda la página (3 consultas fijas, sin N+1) |
| Backend | `SolicitudRepository`, `RecepcionRepository`, `VentaRepository`, `ActividadPorUsuario` (nuevo) | `actividadPorUsuario(ids, desde)`: `MAX(fecha)` y `SUM(CASE fecha >= desde)` por usuario, filtrado además por `@TenantId` |
| Backend | `PersonalDTO` (nuevo) | id, username, nombre, estado, alta y `actividad` (`ultimaOperacion {tipo, en}` u omitida, `dias`, `solicitudes`, `recepciones`, `ventas`). **Sin correo** |
| Frontend | `pages/admin/Personal.tsx`, `features/personal/*` | Página `/admin/personal`: filtro por estado en la URL, tabla con estado, actividad y alta, acciones aprobar/rechazar/desactivar/reactivar (rechazar y desactivar con confirmación), estados de carga, error con reintento, lista vacía y «Sin actividad registrada» |
| Frontend | `App.tsx`, `permisos.ts`, `navegacion.ts` | Ruta bajo `RutaConRol(ROLES_INVITACION)`, permiso de interfaz `gestionarPersonal` y entrada «Personal» en *Administración* |

Sin migraciones, sin dependencias nuevas y sin cambios en seguridad, JWT, CORS, Nginx o Docker.

**Qué cuenta como actividad:** solicitudes creadas (`solicitudes.usuario_id`), recepciones
registradas (`recepciones.recibida_por_id`) y ventas realizadas (`ventas.vendida_por_id`, también
las anuladas después). Las devoluciones no cuentan porque solo las registra el administrador. La
ventana de los conteos son los últimos 30 días hasta el instante de la consulta.

### B.2 Seguridad

- La empresa la fija el servidor (`TenantContextHolder`): un ADMINISTRADOR_COMERCIAL solo ve su
  empresa aunque envíe `X-Tenant-ID`; el TRABAJADOR recibe 403; el SUPER_ADMIN, 400 en modo global y
  la empresa supervisada con `X-Tenant-ID: <id>` (como el listado existente). La interfaz solo
  muestra la página al ADMINISTRADOR_COMERCIAL.
- Los ids cuya actividad se consulta salen del listado del servidor, nunca del cliente, y las
  consultas de actividad se filtran también por `@TenantId`.
- Las acciones de estado usan el `PATCH` existente (fila bloqueada, 404 fuera de la empresa).

### B.3 Verificación ejecutada

| Comando | Resultado |
| --- | --- |
| `./mvnw test` (H2, `-Xlint:all -Werror`) | 317 pruebas, 0 fallos (312 anteriores + 5 de `PersonalActividadTest`: sin actividad y sin correo; atribución, operación más reciente y ventana de 30 días; aislamiento entre dos empresas y cabecera ignorada; filtro por estado; 403 del trabajador, 400 del SUPER_ADMIN global y 200 supervisando) |
| `npm run lint` · `tsc -b` · `npm test` · `npm run build` | Sin avisos ni errores · 60 pruebas, 0 fallos (51 + 9 nuevas: actividad, página y menú) · build correcto |
| Navegador (Playwright + Chromium) contra `vite preview` con la API **simulada** | Escritorio 1366 px claro y móvil 390 px oscuro: sin desbordamiento de página, tabla completa en escritorio, aprobar, desactivar con confirmación, filtro en la URL, un TRABAJADOR que abre la ruta vuelve a `/`, sin errores de consola |

No se ejecutó la suite contra PostgreSQL ni un recorrido con backend y BD reales.

### B.4 Limitaciones y pendientes

1. **Última conexión:** no existe; registrarla exige una migración (`usuarios.ultimo_acceso_en`
   escrita en el login) y una decisión del propietario.
2. En móvil la tabla se desplaza en horizontal dentro de su tarjeta, como el resto de `DataTable`.
3. `recepciones` no tiene índice por `recibida_por_id`; con volúmenes grandes, valorar uno (migración).
4. El registro con código de empresa sigue sin campo en `/registro` (la aprobación ya está en *Personal*).
5. B.6.2 (tarjeta del administrador) pospuesta, por los motivos de A.5.

## Cambio de marca: Unisen → Nexora

Fecha: 21/10/2026. Instrucción del propietario: todo lo que diga «Unisen» pasa a «Nexora». Este
documento conserva las menciones a Unisen de las fases anteriores porque es un registro histórico.

### Qué cambió

| Ámbito | Cambio |
| --- | --- |
| Marca del frontend | `src/brand/unisen/` → `src/brand/nexora/`; variables `--unisen-*` → `--nexora-*`; logotipos y favicon del paquete `nexora-brand-assets` (sin modificar). En el paquete, `nexora-logo-claro.svg` va sobre fondos claros y `nexora-logo-oscuro.svg` sobre oscuros: `BrandLogo` lo traduce a su convenio (`variant="claro"` = logotipo claro para fondo oscuro) |
| Paleta (`tokens.css`) | Borgoña `#6B1E2E` como primario en claro, superficies crema, panel de marca `#1E181A`, acento dorado. Tonos derivados donde la marca no llega al contraste de texto: dorado `#7A5C24` en claro (≥ 5,1:1), primario `#E09AA8` en oscuro (7,8:1), rojo de peligro oscuro `#F97066` para no confundirlo con el primario rosado. Nuevo token `--nexora-color-brand-panel-accent` (dorado puro, 7,7:1 sobre el panel). La serie de las gráficas (azul validado) no cambia |
| Textos visibles | Título de la página, `alt` del logotipo, login, panel de marca (el lema ya no nombra a la empresa cliente), descripción de *Empresas*, ejemplo de correo del registro (`nombre@empresa.com`), título de la API (`Nexora API`) y ejemplo de correo de la API |
| Configuración y comentarios | Cabeceras de `application.yml`, `docker-compose.yml`, Dockerfiles y `nginx.conf`; `<name>`/`<description>` del `pom.xml`; `ADMIN_EMAIL` de ejemplo en los `.env.example` (no afecta al administrador ya creado); texto del archivo de cuentas demo del script de Aatenea |
| Pruebas | Empresa, correos y variables de las pruebas; emisor JWT de las pruebas (`nexora-test`) |
| Documentación | `README.md`, `backend-spring/README.md`, `CLAUDE.md` |

### Qué no cambió (requiere decisión del propietario)

| Identificador | Motivo |
| --- | --- |
| Comentario de `V3__refactor_usuarios_e_invitaciones.sql` | Migración aplicada: cambiar un byte altera su checksum y Flyway detendría el arranque en producción. No se edita nunca |
| Emisor JWT `unisen-sgp` (`application.yml`, `.env.example`) | Cambiarlo invalida todas las sesiones abiertas (todos deben volver a entrar). Si el `.env` del VPS define `JWT_ISSUER`, además habría que cambiarlo allí |
| Imágenes `unisen/sgp-*` (`docker-compose.yml`, Dockerfiles, script y README de Aatenea) | El script de despliegue etiqueta y restaura las imágenes de retorno con ese prefijo: cambiarlo sin migrar el procedimiento rompería el *rollback* |
| Ruta `/root/sgp-unisen` del VPS | Es un directorio real del servidor |
| Paquete Java `com.unisen.sgp` y `groupId` `com.unisen` | 247 archivos; invisible para el usuario; el propietario pidió no renombrar paquetes automáticamente |
| Prefijo `u-` de las clases CSS (`u-btn`, `u-card`…) | No contiene la palabra; renombrarlo tocaría casi todos los componentes |

### Verificación

| Comando | Resultado |
| --- | --- |
| `./mvnw test` (H2) | 317 pruebas, 0 fallos. Las 5 que fallaron en la primera ejecución esperaban el código `UNIS01`, que la aplicación deriva del nombre de la empresa de prueba; con «Nexora» es `NEXO01` y se actualizaron |
| `npm run lint` · `tsc -b` · `npm test` · `npm run build` | Sin avisos ni errores · 60 pruebas, 0 fallos · build correcto |
| Navegador (Playwright + Chromium, API simulada) | Login y *Personal* en escritorio claro, escritorio oscuro y móvil: título «Nexora · Órdenes de Compra», logotipo correcto para cada fondo y cargado, favicon de Nexora, sin desbordamiento ni errores de consola |
