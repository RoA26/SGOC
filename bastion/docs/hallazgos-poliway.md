# Hallazgos sobre el borrador de Poliway (inspección inicial)

Fecha: 2026-10-26. Alcance: solo lectura. No se modificó, movió ni borró ningún archivo original.

## Qué se encontró y qué no

- **El ZIP `Proyecto_Poliway` no está disponible** en el contenedor de trabajo ni apareció en
  las búsquedas de Google Drive realizadas. Por tanto **no hay auditoría completa** ni inventario
  de los ~58 archivos descritos, y no se calcularon hashes SHA-256.
- En Google Drive existe una carpeta `poliway-inventario`, **compartida desde otra cuenta**, con
  un proyecto Next.js ya descomprimido (incluye `.git`, `.next` y `node_modules`). Se listó su
  estructura y se leyeron solo `README.md` (plantilla genérica de Next.js) y `lib/tipos.ts`.
- **No se encontraron en esa carpeta** los siete scripts SQL, los documentos LEEME ni el estado
  del proyecto. Las funciones `registrar_movimiento()` y `aprobar_conteo()` y el script 07 de
  costos no se han podido revisar.

## Estructura observada en `poliway-inventario`

- `app/`: `api`, `canones`, `conteo`, `equipos`, `escanear`, `etiquetas`, `importar`,
  `informes`, `listas`, `login`, `usuarios`.
- `components/`: `AvisoStock`, `BuscadorProducto`, `Encabezado`, `FirmaPad`, `LectorCamara`,
  `Menu`, `SelectorUbicacion`, `TablaInventario`.
- `lib/`: `canones`, `catalogo`, `exportar`, `formato`, `leerTablas`, `sesion`, `supabase`,
  `supabaseAdmin`, `tipos`.
- Raíz: `package.json`, `vercel.json`, `next.config.ts`, `tsconfig.json`, `.env.local`.

`lib/tipos.ts` confirma: unidades `UND | M | M2 | KG | ROLLO`, una vista `v_inventario` con
`sku`, `codigo_barras`, `categoria`, `ubicacion`, `cantidad`, `stock_minimo`, `bajo_minimo`,
`valor_total` y `costo_unitario` (este último añadido por el "script 07"). Bastión adoptó esas
unidades (más `L`) y los campos equivalentes.

## Riesgos

- **Posible exposición de credenciales:** la carpeta compartida contiene `.env.local` y un
  `lib/supabaseAdmin.ts`, lo que sugiere una clave de servicio de Supabase. **No se abrió el
  archivo.** Recomendación: revisar quién tiene acceso a la carpeta, quitar `.env.local` del
  recurso compartido y rotar las claves de Supabase si eran reales.
- La carpeta incluye `.git`: el historial podría contener secretos aunque se borren del árbol.

## Próximo paso propuesto

Obtener el ZIP original (o confirmar que la carpeta de Drive es la fuente vigente y dónde están
los SQL) para: inventariar con hashes, revisar `registrar_movimiento()`, `aprobar_conteo()`, el
costo promedio y las vistas, y decidir qué componentes (`LectorCamara`, `FirmaPad`,
`TablaInventario`, `BuscadorProducto`) merece la pena portar a Bastión desacoplados de Supabase.
