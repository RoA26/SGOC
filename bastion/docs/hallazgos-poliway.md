# Auditoría del borrador de Poliway

Fecha: 2026-10-26. Alcance: **solo lectura**. El ZIP original no se modificó; se extrajo en un
directorio temporal aparte. No se ejecutó ningún script SQL ni instalador.

## 1. Paquete revisado

- Archivo: `Proyecto_Poliway_exportado.zip`, 2 570 535 bytes, 82 entradas (58 archivos),
  sin rutas absolutas ni `..`.
- SHA-256 del ZIP: `79dce1be27b2215a48e10adefe80f485340fe8169d348cbae273d2395a8f2499`.
- Hash de cada archivo: [`poliway-sha256.txt`](poliway-sha256.txt).
- Corte declarado en el LEEME: 25 de octubre de 2026. Código: ~6 300 líneas TS/TSX; SQL: 1 536
  líneas (7 scripts más 1 prototipo).

| Carpeta | Clasificación | Uso en Bastión |
|---|---|---|
| `LEEME - Estado del proyecto.{md,html}` | Documentación original | Fuente del estado y de los pendientes |
| `Conversacion_completa_Poliway.html` | Registro histórico (895 KB) | Solo consulta; contiene datos sensibles (§6) |
| `1_Base_de_datos/01…07` | Scripts originales, en orden de ejecución en Supabase | Referencia funcional; **no** ejecutables en Bastión |
| `2_Codigo_app/` | Código vigente (Next.js 15, React 19, Supabase) | Referencia; componentes a portar (§7) |
| `3_Prototipos/` | Prototipos (despiece HTML, módulo de repuestos del 20 oct.) | Antecedente; el módulo de repuestos usa otro esquema |
| `4_Instaladores_anteriores/` | 6 ZIP históricos | No se ejecutaron. Se revisaron sin extraer: sin `.env` ni llaves secretas |
| `Abrir inventario Poliway.bat` | Acceso directo local | No aplica |

## 2. Diferencias con el inventario descrito en el encargo

- **Roles reales de Poliway:** `admin`, `bodeguero`, `consulta` (script 01). El LEEME los llama
  Administrador, Almacén y Consulta.
- Unidades: `UND, M, M2, KG, ROLLO`.
- No hay módulo de proveedores con interfaz propia: la tabla existe y las entradas pueden citarla.
- El despiece (`lib/canones.ts`, `app/canones`) y el catálogo de 50 repuestos (script 03) son
  **específicos de los cañones Blaster**. En Bastión, que es genérico, deben ser datos de una
  organización o un módulo opcional, no parte del núcleo.

## 3. Modelo de datos (scripts 01, 05 y 06)

`perfiles` (sobre `auth.users`), `categorias`, `ubicaciones` (bodegas), `proveedores`,
`productos` (SKU y código de barras **únicos globales**, `costo_unitario`), `stock` (PK
producto+ubicación, `CHECK cantidad >= 0`), `movimientos` (kárdex con `saldo_resultante`),
`areas`, `funcionarios`, `clientes`, `equipos` (cañones instalados), `conteos`, `conteo_items`.
Vistas: `v_inventario` (incluye productos sin existencias) y `v_movimientos`.

**No es multiempresa:** no existe el concepto de organización.

## 4. `registrar_movimiento()` (versión 3, script 06)

- `SECURITY DEFINER`. Exige sesión y rol `admin` o `bodeguero` activo. AJUSTE solo `admin`.
- ENTRADA y SALIDA, cantidad positiva; AJUSTE con signo y distinto de cero.
- **Toda SALIDA exige destino:** `FUNCIONARIO` (con funcionario y área activos) o `CLIENTE`
  (con equipo opcional, que debe pertenecer a ese cliente).
- **Concurrencia:** bloquea `FOR UPDATE` primero la fila del **producto** (por el costo) y después
  la de `stock` (creada con `ON CONFLICT DO NOTHING`). Rechaza saldo negativo (`PW001`).
- Firma: texto data URL de hasta 300 000 caracteres **guardado en `movimientos.firma`**.
- No hay idempotencia: un reintento duplica el movimiento.
- El kárdex se protege con RLS y permisos (sin `INSERT/UPDATE/DELETE` para `authenticated`),
  pero el rol `service_role` tiene `ALL` sobre todas las tablas; no hay trigger de inmutabilidad.

### Costo promedio ponderado (fórmula original)

```
Q = suma de stock.cantidad del producto en TODAS las ubicaciones, antes de la entrada
si ENTRADA con costo c:
    Q <= 0  →  costo = c
    si no   →  costo = round((Q · C + q · c) / (Q + q), 2)        -- en productos.costo_unitario
```

- **Entrada sin costo:** no cambia el costo; el movimiento registra el costo vigente.
- **Salidas y ajustes:** no cambian el costo; el movimiento registra el costo vigente.
- **Valor del inventario:** `cantidad × productos.costo_unitario`.
- `v_movimientos` valora cada movimiento con `coalesce(m.costo_unitario, p.costo_unitario)`.

## 5. `aprobar_conteo()` (script 06)

- Solo `admin`. Bloquea el conteo `FOR UPDATE` y exige `estado = 'ABIERTO'`; por eso **una
  segunda aprobación falla** y no repite ajustes. Todo ocurre en una transacción.
- Por cada ítem: bloquea el producto y el saldo, calcula `diferencia = contado − sistema` y, si no
  es cero, fija el saldo al valor contado e inserta un AJUSTE `"Conteo físico #id"` con el
  costo vigente. Pasa a `APROBADO` y guarda quién cerró, cuándo y cuántos ajustes hubo.
- Anular: política RLS que permite pasar de `ABIERTO` a `ANULADO`.
- **Riesgos para el diseño de Bastión:**
  1. La diferencia se calcula **al aprobar**, no al contar: las entradas y salidas ocurridas entre
     el conteo y la aprobación quedan absorbidas por el ajuste.
  2. Un `bodeguero` puede abrir, llenar y anular conteos; quien aprueba (admin) podría ser la
     misma persona que contó (no hay separación de funciones).
  3. El ajuste no pasa por `registrar_movimiento()`: duplica su lógica.

## 6. Seguridad y datos sensibles

- **La llave secreta de Supabase (`sb_secret_…`) no está** en el paquete, ni en los 6
  instaladores (comprobado). El código la lee de variables de entorno.
- `Conversacion_completa_Poliway.html` contiene la **URL del proyecto Supabase y la llave
  pública** (`sb_publishable_…`). Esa llave está pensada para ir en el navegador, pero sumada a
  lo siguiente forma un riesgo:
  - El trigger `crear_perfil_nuevo_usuario` crea un perfil `consulta` para **cualquier** usuario
    nuevo de Supabase Auth, y la RLS permite a todo autenticado **leer todo** (productos, stock,
    movimientos, clientes, funcionarios, perfiles).
  - En la conversación la cuenta se creó con el botón *Sign up* y **no consta que el registro
    abierto se haya desactivado**. Si sigue activo, cualquiera con la URL y la llave pública
    podría registrarse y leer el inventario.
  - **Recomendación (para el proyecto Poliway, no para Bastión):** en Supabase › Authentication,
    desactivar *Allow new users to sign up* y revisar la lista de usuarios.
- El LEEME advierte que **la contraseña de la app quedó visible en una captura** de la
  conversación: cambiarla. El LEEME también incluye el correo del administrador (dato personal).
- `GET /api/alertas` exige `CRON_SECRET`; `/api/usuarios` exige administrador (`verificarAdmin`).
- La carpeta de Google Drive `poliway-inventario`, compartida desde otra cuenta, contiene
  el proyecto descomprimido **con `.env.local`** (no se abrió) y `.git`. Ahí sí puede estar la
  llave secreta: conviene restringir el acceso a esa carpeta, quitar `.env.local` y rotar la
  llave si alguien más tuvo acceso.
- Ninguno de estos datos se ha copiado a Bastión. Las pruebas de Bastión usan solo datos
  sintéticos.

## 7. Componentes reutilizables

| Componente | Dependencias | Valoración |
|---|---|---|
| `FirmaPad.tsx` | ninguna (canvas) | Portable. En Bastión la firma irá al almacenamiento privado de archivos, no al movimiento |
| `LectorCamara.tsx` | `html5-qrcode` (import dinámico) | Portable para la fase de escáner |
| `BuscadorProducto.tsx`, `TablaInventario.tsx`, `AvisoStock.tsx` | Supabase | Útiles como referencia de UX; hay que reescribirlos sobre la API |
| `lib/exportar.ts` | `exceljs`, `jspdf`, `jspdf-autotable` | Base para informes (fase de informes) |
| `lib/leerTablas.ts` | `exceljs`, `pdfjs-dist` | Base para importación; en Bastión conviene procesar en el servidor por lotes |
| `lib/canones.ts`, `app/canones` | — | Específico de Blaster (§2) |

## 8. Decisiones aplicadas en Bastión a partir de esta auditoría

1. **Costo promedio por producto en toda la organización** (antes era por producto y bodega).
   Migración `0002`. Es la semántica de Poliway y la más habitual en un kárdex valorizado.
2. **Bloqueo en el mismo orden que Poliway:** producto y luego saldo. Hay una prueba que falla si
   se quita el bloqueo del producto (entradas simultáneas con costo en dos bodegas).
3. **Bajo mínimo solo si `stock_minimo > 0`**, como en el script 07.

Desviaciones conscientes respecto de Poliway (documentadas en [`inventario.md`](inventario.md)):
costo con 4 decimales en lugar de 2, reversiones auditables, idempotencia, trigger de
inmutabilidad, y SKU y código de barras únicos **por organización**.

## 9. Pendiente de decisión

- **Rol `consulta`:** Bastión solo tiene tres roles y TRABAJADOR registra entradas y salidas. Si
  se necesita alguien que solo consulte, ¿un permiso operativo "solo lectura" sobre TRABAJADOR?
- **Destino obligatorio en salidas** (funcionario y área, o cliente y equipo): ¿se adopta como
  regla general de Bastión o como opción configurable por organización?
- **Conteos:** ¿calcular la diferencia contra el saldo en el momento del conteo (más preciso) en
  vez de al aprobar? ¿Impedir que apruebe quien contó?
- **Valoración de entradas sin costo:** Poliway conserva el promedio. ¿Es correcto para Poliway o
  debería exigirse el costo en las entradas de compra?
