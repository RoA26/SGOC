# Módulo comercial del SGP

Precios de venta, IVA, rentabilidad, punto de venta (POS), comprobantes, devoluciones, reportes y
datos de demostración. Fecha: 20/10/2026. Base: `main` = `c1e6121` (Fase 3).

> **Facturación electrónica: no incluida.** El PDF de una venta es un **comprobante comercial
> interno**: no es factura electrónica ni documento equivalente, no lleva numeración autorizada,
> CUFE ni validación de la DIAN, y lo dice en el propio documento. La facturación electrónica es una
> fase independiente (habilitación, proveedor tecnológico o modalidad propia, revisión normativa).

## 1. Roles

| Rol | Qué hace en el módulo comercial |
| --- | --- |
| `SUPER_ADMIN` | Administra la plataforma. **No vende ni cambia precios, costos o políticas** de ninguna empresa. Puede *consultar* ventas, gestión comercial y reportes de una empresa solo supervisándola (`X-Tenant-ID: <id>`); en modo global, los reportes responden 400 |
| `ADMINISTRADOR_COMERCIAL` | Uno o más por empresa (antes `ADMIN_EMPRESA`). Precios, IVA, costos, rentabilidad, historial de precios, política de descuentos, autorizaciones de excepción, ventas, devoluciones, anulaciones y reportes **de su empresa** |
| `TRABAJADOR` | Busca productos, ve precios vigentes y existencias (sin costos), vende con descuentos dentro de su límite (o con un código de autorización), registra pagos, consulta **sus** ventas y comprobantes y sus ventas del día |

Todo se valida en el backend (`@PreAuthorize` + filtro de empresa `@TenantId` + FK compuestas
`(empresa_id, x_id)` en la BD). Ocultar un botón no protege nada.

### Migración `ADMIN_EMPRESA → ADMINISTRADOR_COMERCIAL` (V14)

- Solo cambia el valor de `usuarios.rol` y la restricción `ck_usuarios_rol`. Contraseñas,
  empresas, estados y relaciones (todas apuntan al id del usuario) no cambian.
- **Tokens antiguos:** el JWT solo identifica al usuario; el filtro JWT lee rol, estado y empresa
  de la BD en **cada** petición (el claim `rol` es informativo). Un token emitido antes de V14 (con
  `rol: ADMIN_EMPRESA`) sigue valiendo con los permisos nuevos, y un claim manipulado
  (`rol: ADMINISTRADOR_COMERCIAL` en el token de un trabajador) no eleva nada. Probado en
  `GestionComercialTest.unTokenAntiguoRecibeLosPermisosDeLaBaseDeDatos`.
- La sesión guardada en el navegador (versión 3 de `sgp.session`) traduce `ADMIN_EMPRESA` sin
  cerrar la sesión; `GET /api/auth/me` devuelve el rol nuevo.
- No hay un cuarto rol ni dos roles equivalentes conviviendo. Vuelta atrás: el `UPDATE` inverso y
  la restricción anterior (y desplegar el backend anterior).

## 2. Modelo comercial

Los productos ya son de una sola empresa (`productos.empresa_id`): el precio de venta vive en el
propio producto, sin catálogos paralelos ni tablas de precios por empresa.

| Dato | Dónde | Notas |
| --- | --- | --- |
| Precio de referencia de compra | `productos.precio` (sin cambios) | Estimado de solicitudes y costo propuesto en órdenes |
| Precio de venta **sin IVA** | `productos.precio_venta` | `NULL` = no está a la venta. Los productos existentes quedan sin precio: no se presume nada |
| Tratamiento del IVA | `productos.tratamiento_iva` + `tarifa_iva` | `GRAVADO` (tarifa > 0), `EXENTO` (gravado al 0 %), `EXCLUIDO` (no causa IVA). Una tarifa cero no convierte un producto en exento ni excluido. Sin clasificar no se puede vender |
| Historial de precios | `cambios_precio` (solo inserción) | Precio, tratamiento y tarifa anteriores y nuevos, costo de referencia, motivo, usuario y fecha |
| Política comercial | `politicas_comerciales` (una por empresa) | Límites de descuento por rol, ventas bajo el costo (`PERMITIR` / `AUTORIZACION`), margen objetivo para sugerir precios |
| Costo promedio | `existencias.costo_promedio` (4 decimales) | `NULL` = desconocido |

Cambiar un precio (`PUT /api/v1/comercial/productos/{id}/condiciones`) bloquea la fila del
producto: dos cambios simultáneos quedan en el historial uno tras otro, cada uno con el valor
anterior exacto. Repetir los mismos valores no registra nada. Un precio por debajo del costo se
permite y se advierte (`bajoCosto`). El margen objetivo **solo sugiere** un precio; nunca lo cambia.

## 3. Costo y valoración del inventario — decisión provisional

**Método aplicado: costo promedio ponderado, marcado como provisional** (no hay una política
aprobada por contabilidad). Alternativa documentada: **FIFO** (las salidas toman primero el costo
de las unidades más antiguas; exige capas de costo por entrada).

| Movimiento | Efecto en el promedio |
| --- | --- |
| Entrada con costo (recepción, ajuste con costo, devolución de venta) | Sin unidades previas: el costo de la entrada. Con unidades de promedio conocido: `(saldo × promedio + cantidad × costo) / (saldo + cantidad)`. Con unidades de costo desconocido: sigue desconocido |
| Entrada sin costo (ajuste) | Se valora al promedio vigente (no cambia); sin promedio, sigue desconocido |
| Salida (venta, ajuste negativo) | No cambia el promedio |

- **V16** calculó el promedio inicial recorriendo el kárdex existente con estas mismas reglas; no
  cambió ningún movimiento ni saldo.
- Cada línea de venta guarda `costo_unitario` y `metodo_costo` (`PROMEDIO_PONDERADO` o
  `DESCONOCIDO`). Cambiar de método más adelante **no recalcula** ninguna venta histórica.
- Si se adopta FIFO: nueva tabla de capas, migración que las construya desde el kárdex y el mismo
  campo `metodo_costo` distingue las ventas antiguas de las nuevas.

## 4. Fórmulas

Precisión y redondeo en un solo lugar (`service/comercial/Dinero`): importes con 2 decimales
`HALF_UP`, costos unitarios con 4, porcentajes con 2. Siempre `BigDecimal`.

### 4.1 Orden de cálculo de una venta (`CalculadoraVenta`)

1. Subtotal de línea = precio sin IVA × cantidad.
2. Descuento de línea: porcentaje del subtotal (redondeado al centavo) o **valor fijo por línea**
   (no por unidad); nunca mayor que el subtotal.
3. Neto de línea = subtotal − descuento de línea.
4. Descuento general: porcentaje de la suma de netos o valor fijo, **repartido entre las líneas en
   proporción a su neto**, exacto al centavo por el método del mayor resto (a igual resto, la línea
   de menor número). La suma de las partes es siempre el descuento general.
5. Base gravable = neto − parte del descuento general (los descuentos se aplican antes del IVA).
6. IVA de la línea = base × tarifa / 100, redondeado al centavo (exentos y excluidos: 0).
7. Total de la línea = base + IVA. Totales de la venta = suma de las líneas (la BD lo comprueba con
   `CHECK`).

El cliente **nunca** envía precios, impuestos ni totales; si los envía, se ignoran.

### 4.2 Rentabilidad (`Rentabilidad`) — la misma en detalle, gestión comercial y reportes

| Indicador | Fórmula | No definido |
| --- | --- | --- |
| Utilidad bruta | ingreso neto sin IVA − costo histórico de lo vendido | Costo desconocido → `null` (no 0) |
| Margen bruto | utilidad / ingreso neto × 100 | Ingreso 0 → `null` |
| Recargo sobre el costo | utilidad / costo × 100 | Costo 0 o desconocido → `null` |

Es utilidad **bruta**: no descuenta gastos operativos (el sistema no los registra).

## 5. Descuentos y autorizaciones

- **Límite**: se aplica al **descuento efectivo de cada línea** (el de la línea más su parte del
  descuento general) sobre su subtotal. Así no se puede esquivar combinando descuentos.
- Sin política definida: trabajador **0 %**, administrador comercial 100 %, ventas bajo el costo
  permitidas con advertencia. Los valores de la seed (trabajador 10 %, administrador 30 %, margen
  objetivo 30 %) **son solo de demostración**, no una recomendación.
- **Autorización de excepción**: el administrador comercial genera un código de un solo uso
  (`ABCD-2345`, 5 min a 24 h, por defecto 30 min) que cubre un descuento efectivo máximo (nunca
  mayor que su propio límite) y, si se indica, vender bajo el costo. Al confirmar la venta se
  bloquea la autorización, se comprueba (no usada, vigente, suficiente) y queda enlazada a la venta
  (quién la emitió, quién la usó, cuándo).
- Rechazos: descuento negativo, cero, > 100 % o mayor que el importe → 400; por encima del límite
  sin autorización válida → 403 (`requiereAutorizacion: true`, la interfaz no cierra la sesión).

## 6. Venta transaccional (`POST /api/v1/ventas`)

En **una** transacción: (1) bloqueo de la fila de la empresa (numeración `V-00001` correlativa y
claves de idempotencia sin carreras); (2) si la clave ya existe: misma huella SHA-256 de la petición
→ 200 con la venta registrada, otra huella → 409; (3) productos activos de la empresa del contexto
(un id ajeno no existe) con precio e IVA del catálogo; (4) cálculo completo; (5) existencias
bloqueadas **en orden de producto** (mismo orden que recepciones y ajustes: sin interbloqueos) y
409 con `faltantes` si no alcanzan; (6) política y autorización; (7) pagos que suman exactamente el
total (efectivo con lo recibido y el cambio); (8) cabecera, líneas con su copia histórica, pagos,
salida de existencias y un movimiento `VENTA` por línea con su costo. Si algo falla, no queda nada.

Probado: 5 ventas simultáneas de 3 unidades sobre 10 → 3 entran, saldos 7/4/1, números 1–3; la
misma clave enviada 4 veces a la vez → una venta y un solo descuento de existencias.

## 7. Devoluciones y anulaciones

Nada se borra ni se edita: cada operación deja un registro (`devoluciones_venta` y sus líneas) con
quién, cuándo, motivo, unidades e importes.

| | Devolución | Anulación |
| --- | --- | --- |
| Quién | Administrador comercial | Administrador comercial |
| Cuándo | Cualquier día | **Solo el mismo día** y sin devoluciones previas (corrige un error de registro) |
| Unidades | Parcial o total, nunca más de lo que queda por devolver | Todas |
| Inventario | Por línea: vuelve al inventario vendible (movimiento `DEVOLUCION_VENTA` al costo histórico) o no (p. ej. dañada: no se mueve inventario) | Todo vuelve |
| Reportes | Resta en el período en que se registra | La venta deja de contarse |

Importes devueltos: reparto **telescópico** (lo devuelto acumulado hasta *k* unidades es siempre
`round(importe × k / n)`), así la suma de todas las devoluciones de una línea es exactamente lo
cobrado y nunca negativa. La venta se bloquea: devoluciones simultáneas se serializan; la clave de
idempotencia impide registrar dos veces la misma.

## 8. Documentos PDF (OpenPDF 2.0.3)

- **Orden de compra** (`GET /api/v1/ordenes-compra/{id}/documento/pdf`): se genera desde la misma
  instantánea inmutable que el Word (misma versión y huella SHA-256): no se guardan binarios y el
  documento no cambia aunque cambien después el catálogo o el proveedor. Una orden emitida antes de
  V13 se reconstruye y el documento lo advierte. Tabla con cabecera repetida en cada página, filas
  que no se parten, pie con «Página X de Y». Sin logo (no existe en el modelo; la cabecera está
  preparada para incluirlo). El Word sigue disponible.
- **Comprobante de venta** (`GET /api/v1/ventas/{id}/comprobante`): desde la venta persistida
  (copia histórica); refleja devoluciones y anulación. Mismas reglas de visibilidad que el detalle.

## 9. Reportes (`GET /api/v1/reportes/ventas?desde=&hasta=`)

Días en `America/Bogota`, máximo 366, nunca futuros (por defecto 30). Ventas del período = no
anuladas; ingreso neto = base sin IVA − devoluciones del período; costo y utilidad con costos
históricos (si falta alguno: «no disponible» e importe afectado); descuentos concedidos; por día;
más vendidos; mayor contribución a la utilidad; por persona; bajo inventario; ventas bajo el costo.
El trabajador solo tiene `GET /api/v1/reportes/ventas/mias` (sus ventas del día, sin costos).

## 10. Datos de demostración (seed)

**Activación explícita**: solo con el perfil `demo`. Con un perfil de producción (`prod`,
`production`, `produccion`) activo a la vez, el arranque se detiene sin sembrar.

```bash
cd backend-spring
SPRING_PROFILES_ACTIVE=demo ./mvnw spring-boot:run      # siembra al arrancar y sigue sirviendo
```

Crea cinco empresas ficticias (`DEMO01`…`DEMO05`, marcadas `empresas.demo = true`) con su
administrador comercial, 2–3 trabajadores, proveedores, productos con código de barras EAN-13
ficticio (prefijo 20, reservado a uso interno), precios e IVA **de demostración** (no son una
clasificación tributaria validada), inventario inicial con costo, una orden recibida por completo,
otra en parte y otra pendiente, ventas (sin descuento, con descuento, con descuento general, bajo
el costo, pagos mixtos), productos agotados y una devolución parcial. Todo pasa por los servicios
reales (mismas reglas, kárdex y costos).

**Idempotente**: empresas por código + marca demo; usuarios por username (nunca se cambia una
contraseña existente); proveedores por NIT; productos por SKU; inventario, recepciones, ventas y
devoluciones con claves estables; órdenes solo si la empresa demo no tiene ninguna. Una empresa
real con el mismo código o NIT, o un username ocupado en otra empresa, hace que esa empresa demo
se omita sin tocar nada. No crea ninguna cuenta `SUPER_ADMIN`.

### Cuentas de demostración

Contraseña común, **solo para desarrollo y pruebas**: `abcde123!` (se guarda cifrada con el
`PasswordEncoder` BCrypt configurado; nunca en texto plano ni en los logs).

| Empresa | Administrador comercial | Trabajadores |
| --- | --- | --- |
| DEMO01 · Ferretería El Tornillo Feliz (demo) | `demo1.admin` | `demo1.ana`, `demo1.luis`, `demo1.marta` |
| DEMO02 · Minimercado La Canasta (demo) | `demo2.admin` | `demo2.sara`, `demo2.pablo`, `demo2.nora` |
| DEMO03 · Papelería Punto y Coma (demo) | `demo3.admin` | `demo3.diego`, `demo3.laura` |
| DEMO04 · Droguería Bienestar Total (demo) | `demo4.admin` | `demo4.julia`, `demo4.mateo`, `demo4.elena` |
| DEMO05 · Deportes Gol de Oro (demo) | `demo5.admin` | `demo5.tomas`, `demo5.isabel` |

## 11. Endpoints nuevos

| Método | Ruta | Quién |
| --- | --- | --- |
| GET | `/api/v1/ordenes-compra/{id}/documento/pdf` | Gestión |
| GET | `/api/v1/comercial/productos[?q=&filtro=]` · `/productos/{id}` | Gestión |
| PUT | `/api/v1/comercial/productos/{id}/condiciones` | Administrador comercial |
| GET | `/api/v1/comercial/historial-precios[?productoId=]` | Gestión |
| GET · PUT | `/api/v1/comercial/politica` | Gestión · administrador comercial |
| GET · POST | `/api/v1/comercial/autorizaciones` | Gestión · administrador comercial |
| GET | `/api/v1/ventas/productos[?q=&disponibles=]` · `/productos/codigo?codigo=` | Toda la empresa (sin costos) |
| POST | `/api/v1/ventas/cotizacion` · `/api/v1/ventas` | Administrador comercial y trabajador |
| GET | `/api/v1/ventas[?desde=&hasta=&estado=&vendedorId=&numero=]` · `/{id}` · `/{id}/comprobante` | Trabajador: las suyas; gestión: todas |
| POST | `/api/v1/ventas/{id}/devoluciones` · `/{id}/anulacion` | Administrador comercial |
| GET | `/api/v1/reportes/ventas` · `/ventas/mias` | Gestión · personas de la empresa |

## 12. Interfaz

| Ruta | Para | Contenido |
| --- | --- | --- |
| `/pos` | Administrador comercial y trabajador | Escáner/SKU, catálogo con precio con IVA y disponibilidad, carrito, descuentos de línea y general, código de autorización, total calculado por el backend, pagos (mixtos, efectivo con cambio), cliente opcional, cobro idempotente, resultado y comprobante |
| `/ventas`, `/ventas/:id` | Toda la empresa (el trabajador, las suyas) | Historial con filtros; detalle con IVA por tarifa, pagos, autorización, devoluciones; costos y rentabilidad solo para la gestión; devolución y anulación |
| `/comercial` | Gestión (cambios: administrador comercial) | Precios y rentabilidad (filtros: a la venta, sin precio, bajo el costo, costo desconocido), cambio de precio con confirmación y advertencia, historial, política y autorizaciones |
| `/reportes` | Gestión | Período (hoy, 7/30/90 días, 12 meses o personalizado), indicadores, gráficas con vista de tabla, por persona, bajo inventario, ventas bajo el costo |
| `/ordenes-compra/:id` | Gestión | *Descargar PDF* (además de *Word*) |

## 13. Decisiones pendientes (requieren confirmación)

1. **Valoración del inventario**: promedio ponderado aplicado como provisional; validar con
   contabilidad o adoptar FIFO (sección 3). Afecta a la rentabilidad oficial.
2. **Precio con o sin IVA**: se captura sin IVA (el POS muestra ambos). Si el negocio fija precios
   finales con IVA incluido, habría que derivar la base (y su redondeo).
3. **Clasificación tributaria** real de cada producto y otras tarifas o impuestos (INC, bolsas…):
   el modelo admite cualquier tarifa, pero la clasificación debe validarla asesoría tributaria.
4. **Límites de descuento y autorizaciones**: valores por defecto conservadores; ¿límites por
   producto o categoría? ¿Autorización presencial en lugar de código?
5. **Devoluciones**: ¿puede registrarlas el trabajador? ¿Plazo máximo? ¿Medio del reembolso? Hoy:
   solo el administrador comercial, sin plazo, sin registrar el medio del reembolso.
6. **Anulación** solo el mismo día: confirmar si debe ser el mismo turno o admitir más tiempo.
7. **Numeración comercial** por empresa (`V-00001`), sin cajas ni turnos. Si se requiere una
   numeración por caja o la de un documento equivalente, es un cambio de modelo.
8. **Estados de pago**: toda venta se paga completa al confirmar (sin crédito ni abonos).
9. **Conservación de documentos**: comprobantes y órdenes se regeneran desde datos inmutables; si
   se exige conservar el binario original, añadir almacenamiento.
10. **Facturación electrónica**: modalidad, proveedor tecnológico y numeración autorizada.

## 14. Limitaciones conocidas

- Sin cajas, turnos ni arqueo; sin clientes como entidad (solo nombre y documento opcionales en la
  venta); cantidades enteras.
- La seed fecha todas sus ventas en el día en que se ejecuta (usa el reloj real, sin manipular
  fechas).
- El POS consulta la cotización al backend en cada cambio del carrito (300 ms de espera); sin
  conexión no puede vender.
- Una devolución no registra el medio del reembolso; las unidades que no reingresan no generan
  movimiento de merma automático.
- Los reportes cargan las ventas del período (máx. 366 días) para agruparlas: adecuado para el
  volumen académico; con mucho volumen convendría agregación en la BD.
