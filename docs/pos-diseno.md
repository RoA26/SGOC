# Diseño del punto de venta (POS) — Entrega E

Estado: **propuesta para revisión. No hay código del POS en el repositorio.** Este documento
fija entidades, contratos, flujo, permisos, concurrencia y comprobantes para aprobarlos antes de
implementar. Se apoya en lo que ya existe y está probado: existencias por producto con bloqueo
de fila, kárdex inmutable (`movimientos_inventario`), claves de idempotencia, búsqueda por código
de barras o SKU limitada a la empresa del contexto y el aislamiento multiempresa con FK compuestas.

> **Facturación electrónica.** El SGP **no** emite facturas electrónicas ni documentos
> equivalentes válidos ante la DIAN, y nada de este diseño lo garantiza por sí solo. El primer
> alcance propuesto es un **comprobante interno sin valor fiscal**. La facturación electrónica
> exige requisitos e integraciones (numeración autorizada, formato y validación previa, firma,
> proveedor tecnológico u otra modalidad habilitada…) que deben verificarse con asesoría contable
> y tributaria y con la normativa vigente (p. ej. la Resolución DIAN 000165 de 2023 sobre
> factura electrónica y documento equivalente electrónico POS) antes de diseñar esa integración.

## 1. Qué se reutiliza

| Pieza existente | Uso en el POS |
| --- | --- |
| `existencias` (una fila por producto, `FOR UPDATE`, `ck_existencias_cantidad >= 0`) | Descontar unidades vendidas sin carreras ni saldo negativo |
| `movimientos_inventario` (kárdex inmutable, saldo resultante, usuario, fecha) | Nuevo tipo `VENTA` (salida) y `DEVOLUCION_VENTA` (entrada compensatoria) |
| `InventarioService.bloquear` (bloqueo en orden de producto) | Mismo orden de bloqueo: sin interbloqueos entre ventas, recepciones y ajustes |
| Claves de idempotencia (`Patrones.CLAVE_IDEMPOTENCIA`, UNIQUE por empresa) | Reintentar un cobro nunca duplica la venta |
| `GET /api/v1/productos/buscar?codigo=` | Escáner (lector HID) y búsqueda por SKU en la caja |
| Numeración correlativa con bloqueo de fila (órdenes de compra) | Numeración de ventas por caja |
| `@TenantId` + FK compuestas `(empresa_id, x_id)` | Toda tabla nueva pertenece a una empresa y la BD impide cruzarlas |

## 2. Entidades propuestas

Todas con `empresa_id` y FK compuestas, como V11.

| Tabla | Campos principales | Notas |
| --- | --- | --- |
| `cajas` | nombre, activa, prefijo, `ultimo_numero` | Punto de venta físico de una empresa |
| `turnos_caja` | caja, usuario, abierto_en, base_inicial, cerrado_en, efectivo_contado, diferencia, estado (`ABIERTO`/`CERRADO`) | Un turno abierto por caja (índice único parcial) |
| `ventas` | caja, turno, número, cliente (opcional), estado (`COMPLETADA`/`ANULADA`), subtotal, descuento, impuestos, total, clave_idempotencia, vendida_por, vendida_en, anulada_por/en, motivo_anulacion | Cabecera inmutable salvo la anulación |
| `detalles_venta` | venta, producto, cantidad, precio_unitario, descuento, tasa_impuesto, impuesto, subtotal | Precio e impuesto **congelados** al vender |
| `pagos_venta` | venta, medio (`EFECTIVO`, `TARJETA`, `TRANSFERENCIA`…), valor, referencia, cambio | Varios medios por venta; la suma cubre el total |
| `clientes` | tipo y número de documento, nombre, correo, teléfono | Opcional. Datos personales: tratamiento según la Ley 1581 de 2012 |
| `movimientos_inventario` | + `detalle_venta_id` y los tipos `VENTA` y `DEVOLUCION_VENTA` | Migración que amplía `ck_movimientos_inventario_tipo` y `_origen` |

Cambios en `productos` (a decidir, ver §8): `precio_venta` separado del actual `precio` (que hoy
es la referencia de compra de las solicitudes) y la tasa de impuesto aplicable.

## 3. Contratos (borrador)

| Método | Ruta | Quién | Respuesta |
| --- | --- | --- | --- |
| POST | `/api/v1/pos/turnos` | Cajero | `201` turno abierto en su caja · `409` ya hay uno abierto |
| POST | `/api/v1/pos/turnos/{id}/cierre` | Cajero del turno / ADMIN_EMPRESA | `200` con arqueo (esperado, contado, diferencia) |
| GET | `/api/v1/productos/buscar?codigo=` | Cajero | Ya existe: producto, existencias y estado |
| POST | `/api/v1/pos/ventas` | Cajero con turno abierto | `201` venta · `200` misma clave y mismos datos · `409` existencias insuficientes o clave con otros datos · `400` líneas o pagos inválidos |
| POST | `/api/v1/pos/ventas/{id}/anulacion` | ADMIN_EMPRESA | `200` anulada con movimientos compensatorios · `409` ya anulada |
| GET | `/api/v1/pos/ventas` · `/{id}` | Cajero (las suyas) / ADMIN_EMPRESA (todas) | Historial paginado y detalle |
| GET | `/api/v1/pos/ventas/{id}/comprobante` | Igual que el detalle | PDF interno (sin valor fiscal) |

Cuerpo de `POST /ventas`: `claveIdempotencia`, `clienteId?`, `lineas[{productoId, cantidad,
descuento?}]`, `pagos[{medio, valor, referencia?}]`. **El cliente nunca envía precios ni
impuestos**: el backend los toma del catálogo en la transacción (un precio manipulado en el
navegador no tiene efecto).

## 4. Flujo de una venta

1. El cajero abre su turno (base inicial).
2. Escanea o teclea códigos: `GET /productos/buscar`. El carrito vive en el navegador; nada se
   reserva hasta cobrar (sin cantidades "reservadas" en esta versión).
3. Al cobrar, el frontend genera una clave de idempotencia por intento y envía la venta completa.
4. En **una transacción**, el backend: bloquea la fila de la caja (número correlativo) → comprueba
   la clave (si ya existe con los mismos datos, devuelve esa venta) → valida productos activos de
   la empresa, precios e impuestos del catálogo y que los pagos cubran el total → bloquea las
   existencias **en orden de producto** → si alguna no alcanza, 409 con el producto y lo disponible
   → descuenta, escribe un movimiento `VENTA` por línea con su saldo y costo, guarda cabecera,
   líneas y pagos.
5. Se muestra el comprobante interno.
6. Al cerrar el turno se registra el efectivo contado y la diferencia.

**Anulación:** no borra nada. Marca la venta `ANULADA` (quién, cuándo, motivo) y escribe
movimientos `DEVOLUCION_VENTA` que reponen las unidades, en la misma transacción y con el mismo
orden de bloqueo. Las devoluciones parciales quedan como evolución.

## 5. Permisos

| Acción | SUPER_ADMIN | ADMIN_EMPRESA | Cajero |
| --- | --- | --- | --- |
| Configurar cajas y precios de venta | Consulta | Sí | No |
| Abrir/cerrar su turno, vender | No | Sí | Sí (su caja) |
| Ver ventas | Consulta (supervisión) | Todas | Las suyas |
| Anular ventas | No | Sí | No (decisión pendiente: anulación con autorización) |
| Ver costos y kárdex | Consulta | Sí | No |

"Cajero" puede ser el rol TRABAJADOR con una asignación a caja o un rol nuevo (§8). Como en el
resto del sistema, el backend autoriza cada petición; ocultar botones no es seguridad. Las FK de
autoría `(empresa_id, usuario_id)` impiden, también en la BD, que alguien de otra empresa (o el
SUPER_ADMIN, sin empresa) registre ventas.

## 6. Concurrencia e integridad

- **Mismas unidades, dos cajas:** bloqueo pesimista de `existencias` (`FOR NO KEY UPDATE` en
  PostgreSQL) y comprobación del saldo con la fila bloqueada; `ck_existencias_cantidad` es la red
  de seguridad. Nunca "leer stock y actualizar después" sin bloqueo.
- **Interbloqueos:** todas las operaciones (ventas, recepciones, ajustes, anulaciones) bloquean
  existencias en orden ascendente de producto.
- **Reintentos:** clave de idempotencia UNIQUE por empresa; misma clave y mismos datos devuelve la
  venta registrada, otros datos responde 409.
- **Numeración:** bloqueo de la fila de la caja + UNIQUE `(empresa_id, caja_id, numero)`; sin
  huecos dentro de una caja salvo transacciones abortadas antes de confirmar (se documentará si
  la numeración fiscal exige otra cosa).
- **Atomicidad:** venta, líneas, pagos, movimientos y existencias en una sola transacción; si algo
  falla no queda ninguna parte.

## 7. Comprobantes

| Documento | Alcance | Estado |
| --- | --- | --- |
| Comprobante interno (PDF o tirilla) | Número de caja y venta, fecha, productos, impuestos desglosados, total, medios de pago, leyenda "Documento interno sin validez fiscal" | Propuesto para la primera versión |
| Factura electrónica / documento equivalente electrónico POS | Requiere habilitación ante la DIAN y una integración (propia o con un proveedor tecnológico), numeración autorizada, validación y representación gráfica | **No incluido.** Verificar requisitos antes de diseñarlo |

El modelo deja preparado el enlace (`documentos_venta`: venta, tipo, prefijo, número, estado de
validación, identificador externo, archivos) para no tener que rehacer las ventas cuando se
integre.

## 8. Decisiones pendientes (requieren aprobación)

1. **Precio de venta:** ¿columna `precio_venta` en productos, listas de precios o margen sobre
   costo? Hoy `precio` es la referencia de compra.
2. **Impuestos:** tasas de IVA por producto (0 %, 5 %, 19 %, exento/excluido), INC si aplica,
   redondeo y si los precios se capturan con o sin impuesto.
3. **Valoración del inventario:** el kárdex ya guarda el costo factual de cada entrada. Propuesta:
   **costo promedio ponderado** (`costo_promedio` en existencias, recalculado en cada entrada y
   aplicado a cada salida), validada con el área contable antes de calcular márgenes.
4. **Unidades fraccionarias** (metros de cable, kilos): hoy las cantidades son enteras.
5. **Venta sin existencias:** propuesta: prohibida (409). ¿Algún producto se vende sin control de
   stock (servicios)?
6. **Rol del cajero:** TRABAJADOR asignado a caja o rol nuevo `CAJERO`.
7. **Anulaciones:** ¿solo el administrador o el cajero con autorización? ¿Límite de tiempo
   (mismo turno)? ¿Devoluciones parciales?
8. **Clientes:** obligatorios u opcionales por tipo de venta; política de datos personales.
9. **Facturación electrónica:** modalidad y proveedor, cuando se decida abordarla.
10. **Descuentos:** quién los autoriza y con qué tope.

## 9. Plan propuesto (cuando se apruebe)

1. Migración: cajas, turnos, ventas, líneas, pagos y nuevos tipos de movimiento (no destructiva).
2. Servicio de venta transaccional con pruebas de concurrencia (dos cajas, mismas unidades),
   idempotencia y aislamiento (ids manipulados de otra empresa).
3. Anulación con movimientos compensatorios.
4. Interfaz de caja: escáner, carrito, cobro, comprobante interno.
5. Dashboard del trabajador: "ventas del día" solo cuando existan ventas persistidas.
