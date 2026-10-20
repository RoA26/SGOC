package db.migration;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Statement;
import org.flywaydb.core.api.migration.BaseJavaMigration;
import org.flywaydb.core.api.migration.Context;

/**
 * Calcula el costo promedio ponderado inicial de cada producto recorriendo su kárdex en orden
 * (fecha, id). Solo escribe {@code existencias.costo_promedio}: ningún movimiento ni saldo cambia.
 *
 * <p>Reglas (las mismas que aplica {@code ValoracionInventario} desde ahora en cada entrada):
 * <ul>
 *   <li>Entrada con costo: si no había unidades, el promedio es ese costo; si las había y su
 *       promedio se conoce, se pondera; si las había con costo desconocido, sigue desconocido.</li>
 *   <li>Entrada sin costo (ajuste): se valora al promedio vigente, que no cambia; sin promedio,
 *       sigue desconocido.</li>
 *   <li>Salidas: no cambian el promedio.</li>
 * </ul>
 * Autocontenida a propósito: una migración no debe depender de código que pueda cambiar después.
 */
public class V16__costo_promedio_inicial extends BaseJavaMigration {

    private static final int ESCALA = 4;

    @Override
    public void migrate(Context context) throws Exception {
        Connection conexion = context.getConnection();
        try (Statement consulta = conexion.createStatement();
             ResultSet filas = consulta.executeQuery(
                     "SELECT producto_id, cantidad, saldo_resultante, costo_unitario FROM movimientos_inventario "
                             + "ORDER BY producto_id, fecha, id");
             PreparedStatement actualizar = conexion.prepareStatement(
                     "UPDATE existencias SET costo_promedio = ? WHERE producto_id = ?")) {
            Long producto = null;
            BigDecimal promedio = null;
            while (filas.next()) {
                long actual = filas.getLong("producto_id");
                if (producto == null || producto != actual) {
                    guardar(actualizar, producto, promedio);
                    producto = actual;
                    promedio = null;
                }
                int cantidad = filas.getInt("cantidad");
                int saldo = filas.getInt("saldo_resultante");
                BigDecimal costo = filas.getBigDecimal("costo_unitario");
                if (cantidad > 0) {
                    promedio = trasEntrada(saldo - cantidad, promedio, cantidad, costo);
                }
            }
            guardar(actualizar, producto, promedio);
            actualizar.executeBatch();
        }
    }

    static BigDecimal trasEntrada(int saldoPrevio, BigDecimal promedio, int cantidad, BigDecimal costo) {
        if (costo == null) {
            return promedio;
        }
        if (saldoPrevio <= 0) {
            return costo.setScale(ESCALA, RoundingMode.HALF_UP);
        }
        if (promedio == null) {
            return null;
        }
        BigDecimal valor = promedio.multiply(BigDecimal.valueOf(saldoPrevio)).add(costo.multiply(BigDecimal.valueOf(cantidad)));
        return valor.divide(BigDecimal.valueOf((long) saldoPrevio + cantidad), ESCALA, RoundingMode.HALF_UP);
    }

    private static void guardar(PreparedStatement actualizar, Long producto, BigDecimal promedio) throws Exception {
        if (producto != null && promedio != null) {
            actualizar.setBigDecimal(1, promedio);
            actualizar.setLong(2, producto);
            actualizar.addBatch();
        }
    }
}
