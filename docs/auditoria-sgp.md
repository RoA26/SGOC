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
