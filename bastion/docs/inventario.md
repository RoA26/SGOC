# Reglas del inventario

Toda modificación de la tabla `stock` pasa por `app/modules/movements/service.py`.

## Movimientos

| Tipo | Quién | Cantidad guardada | Efecto en costo |
|---|---|---|---|
| `ENTRADA` | administrador, trabajador | positiva | recalcula el promedio si trae costo |
| `SALIDA` | administrador, trabajador | negativa | ninguno |
| `AJUSTE` | solo administrador, con justificación obligatoria | con signo | ninguno |
| `REVERSION` | solo administrador | opuesta al original | deshace el aporte de una entrada con costo |

Cada movimiento guarda usuario, fecha, tipo, cantidad, costo unitario (entradas), referencia,
notas, **saldo resultante** y **costo promedio resultante**.

## Garantías

- **Concurrencia.** En una transacción: se bloquea la fila del producto (`SELECT … FOR UPDATE`),
  se asegura la fila de saldo (`INSERT … ON CONFLICT DO NOTHING`) y se bloquea también. Las
  operaciones sobre un mismo producto se serializan, en cualquier bodega. Probado con 12 salidas simultáneas sobre 5 unidades: exactamente 5 se
  confirman y 7 se rechazan con `409 stock_insuficiente`.
- **Sin saldos negativos.** Se valida en el servicio y, además, con `CHECK (quantity >= 0)` en BD.
  No existe hoy una política que permita saldo negativo.
- **Atomicidad.** Saldo y movimiento se confirman juntos o no se confirma ninguno (prueba de
  rollback incluida).
- **Reintentos.** Cabecera opcional `Idempotency-Key` (8–80 caracteres), única por organización.
  Un reintento con la misma clave y los mismos datos devuelve el movimiento original (`200`);
  con datos distintos, `409 clave_reutilizada`. El frontend genera una clave por envío.
- **Historial inmutable.** Un trigger de PostgreSQL rechaza `UPDATE` y `DELETE` sobre
  `movements`. Los errores se corrigen con una `REVERSION`, que solo puede hacerse una vez por
  movimiento (índice único parcial) y nunca sobre otra reversión.

## Costo promedio ponderado

Alineado con `registrar_movimiento()` de Poliway (script 06; ver
[`hallazgos-poliway.md`](hallazgos-poliway.md) §4). Un costo **por producto** en toda la
organización (`products.avg_cost`), no por bodega:

```
nuevo_costo = (Q · C + q · c) / (Q + q)
Q = existencia total del producto (todas las bodegas) antes de la entrada
C = costo promedio vigente, q = cantidad que entra, c = costo unitario de la entrada
si Q <= 0: nuevo_costo = c
```

- **Entrada sin costo:** conserva el costo promedio vigente (igual que Poliway).
- **Salidas y ajustes:** no cambian el costo promedio (igual que Poliway).
- **Saldo en cero:** se conserva el último costo; la siguiente entrada con costo lo fija.
- **Valor del inventario:** `existencia en la bodega × costo promedio del producto`.
- **Reversión de una entrada con costo** (Poliway no tiene reversiones):
  `(Q · C − q · c) / (Q − q)` si la existencia restante es positiva y el resultado no es negativo;
  si no, se conserva el promedio.
- Cada movimiento guarda el costo promedio resultante (`avg_cost_after`).

Concurrencia: se bloquea **primero la fila del producto y después la del saldo**, en ese orden
fijo, como Poliway. Sin el bloqueo del producto, dos entradas con costo en bodegas distintas
perderían aportes al promedio; la prueba `test_entradas_concurrentes_con_costo_en_varias_bodegas`
lo detecta (comprobado: falla si se quita el bloqueo).

### Desviación consciente: 4 decimales en lugar de 2

Poliway redondea el costo a 2 decimales en cada entrada. Bastión guarda 4 (`ROUND_HALF_UP`) y
redondea a 2 solo al mostrar el valor total. Motivo: con artículos de bajo costo unitario el
redondeo repetido a 2 decimales acumula desviaciones en el valor del inventario. Si se requiere
exactamente el comportamiento de Poliway, basta con cambiar `COST_Q` en
`app/modules/movements/service.py`.

### Historial del cambio

La primera versión de Bastión calculaba el costo por (producto, bodega). La migración `0002`
lo movió a `products.avg_cost`; para datos existentes toma el promedio de las bodegas ponderado
por existencia. La valoración sigue siendo **provisional** hasta resolver las preguntas
abiertas de `hallazgos-poliway.md` §9.
