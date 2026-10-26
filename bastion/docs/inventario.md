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

- **Concurrencia.** En una transacción: se asegura la fila de saldo (`INSERT … ON CONFLICT DO
  NOTHING`) y se bloquea con `SELECT … FOR UPDATE`. Las operaciones sobre el mismo producto y
  bodega se serializan. Probado con 12 salidas simultáneas sobre 5 unidades: exactamente 5 se
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

Por producto **y bodega** (la columna `stock.avg_cost`):

```
nuevo_costo = (Q · C + q · c) / (Q + q)
Q = saldo previo, C = costo promedio previo, q = cantidad que entra, c = costo unitario de la entrada
```

Redondeo a 4 decimales (`ROUND_HALF_UP`). Decisiones:

- **Entrada sin costo:** conserva el costo promedio actual (las unidades entran valoradas al
  promedio vigente).
- **Salidas y ajustes:** no cambian el costo promedio; las unidades salen al promedio vigente.
- **Saldo en cero:** se conserva el último costo promedio para la siguiente entrada sin costo.
- **Reversión de una entrada con costo:** `(Q · C − q · c) / (Q − q)` si el saldo resultante es
  positivo y el resultado no es negativo; si no, se conserva el promedio.

**Pendiente de contrastar con Poliway.** No se encontraron los scripts SQL originales
(incluido el script 07 de costos), así que no se pudo verificar que esta semántica coincida con
la de Poliway. Lo único observado en su código (`lib/tipos.ts`) es que la vista `v_inventario`
expone `valor_total` y `costo_unitario`, y que en ausencia de este último se calcula como
`valor_total / cantidad`, lo que es compatible con un promedio ponderado. La valoración debe
considerarse **provisional** hasta revisar ese script.
