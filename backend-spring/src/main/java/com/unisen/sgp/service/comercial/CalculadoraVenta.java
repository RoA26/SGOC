package com.unisen.sgp.service.comercial;

import com.unisen.sgp.exception.CampoInvalidoException;
import com.unisen.sgp.model.entity.TipoDescuento;
import com.unisen.sgp.model.entity.TratamientoIva;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;

/**
 * Cálculo de una venta, siempre en el servidor (los importes del cliente se ignoran).
 *
 * <p>Orden de cálculo de cada línea (todos los importes sin IVA hasta el paso 5):
 * <ol>
 *   <li>Subtotal = precio unitario × cantidad.</li>
 *   <li>Descuento de línea: porcentaje del subtotal (redondeado a centavos) o valor fijo de la
 *       línea (no por unidad), nunca mayor que el subtotal.</li>
 *   <li>Neto de línea = subtotal − descuento de línea.</li>
 *   <li>Descuento general: porcentaje de la suma de netos o valor fijo, repartido entre las líneas
 *       en proporción a su neto. El reparto es determinista y exacto al centavo: cada línea recibe
 *       su parte truncada y los centavos sobrantes van, uno a uno, a las líneas con mayor resto
 *       (a igual resto, la de menor número de línea). La suma de las partes es siempre el
 *       descuento general.</li>
 *   <li>Base gravable = neto − parte del descuento general. Los descuentos incondicionales se
 *       aplican antes del impuesto: reducen la base.</li>
 *   <li>IVA = base × tarifa / 100, redondeado a centavos por línea (exentos y excluidos: 0).</li>
 *   <li>Total de la línea = base + IVA. Total de la venta = suma de los totales de línea.</li>
 * </ol>
 * Así la suma de las líneas coincide siempre con los totales de la venta.
 */
public final class CalculadoraVenta {

    private CalculadoraVenta() {
    }

    public record Descuento(TipoDescuento tipo, BigDecimal valor) {
        public Descuento {
            Objects.requireNonNull(tipo, "tipo");
            Objects.requireNonNull(valor, "valor");
        }
    }

    /** Datos de una línea; el precio, el tratamiento y la tarifa salen del catálogo, no del cliente. */
    public record Entrada(BigDecimal precioUnitario, int cantidad, TratamientoIva tratamiento, BigDecimal tarifa,
                          Descuento descuento) {
    }

    public record Linea(int numero, BigDecimal precioUnitario, int cantidad, BigDecimal subtotal,
                        BigDecimal descuentoLinea, BigDecimal descuentoGeneral, BigDecimal baseGravable,
                        TratamientoIva tratamiento, BigDecimal tarifa, BigDecimal impuesto, BigDecimal total) {

        public BigDecimal descuentoTotal() {
            return descuentoLinea.add(descuentoGeneral);
        }

        /** Descuento efectivo de la línea (de línea + su parte del general) sobre su subtotal, en %. */
        public BigDecimal porcentajeDescuento() {
            return Dinero.porcentaje(descuentoTotal(), subtotal);
        }

        /** true si el descuento efectivo supera {@code limite} (comparación exacta, sin redondeos). */
        public boolean superaDescuento(BigDecimal limite) {
            return descuentoTotal().multiply(Dinero.CIEN).compareTo(limite.multiply(subtotal)) > 0;
        }

        /** true si la base queda por debajo del costo de las unidades (costo desconocido: no se puede saber). */
        public boolean bajoCosto(BigDecimal costoUnitario) {
            return costoUnitario != null && baseGravable.compareTo(costoUnitario.multiply(BigDecimal.valueOf(cantidad))) < 0;
        }
    }

    public record Resultado(List<Linea> lineas, BigDecimal subtotal, BigDecimal descuentoLineas,
                            BigDecimal descuentoGeneral, BigDecimal baseGravable, BigDecimal impuestos,
                            BigDecimal total) {

        /** El mayor descuento efectivo entre las líneas, en % (0 sin descuentos). */
        public BigDecimal descuentoMaximo() {
            return lineas.stream().map(Linea::porcentajeDescuento).filter(Objects::nonNull)
                    .max(Comparator.naturalOrder()).orElse(Dinero.CERO);
        }
    }

    /**
     * @throws CampoInvalidoException si un descuento es negativo, cero, supera el 100 % o el importe
     *                                al que se aplica
     */
    public static Resultado calcular(List<Entrada> entradas, Descuento general) {
        if (entradas.isEmpty()) {
            throw new CampoInvalidoException("lineas", "La venta debe tener al menos una línea.");
        }
        int n = entradas.size();
        BigDecimal[] subtotales = new BigDecimal[n];
        BigDecimal[] descuentosLinea = new BigDecimal[n];
        BigDecimal[] netos = new BigDecimal[n];
        BigDecimal sumaNetos = BigDecimal.ZERO;
        for (int i = 0; i < n; i++) {
            Entrada e = entradas.get(i);
            if (e.cantidad() <= 0) {
                throw new CampoInvalidoException("lineas[" + i + "].cantidad", "La cantidad debe ser mayor que 0.");
            }
            if (e.precioUnitario() == null || e.precioUnitario().signum() <= 0) {
                throw new IllegalArgumentException("Precio unitario no válido en la línea " + (i + 1));
            }
            subtotales[i] = Dinero.redondear(e.precioUnitario().multiply(BigDecimal.valueOf(e.cantidad())));
            descuentosLinea[i] = importeDescuento(e.descuento(), subtotales[i], "lineas[" + i + "].descuento");
            netos[i] = subtotales[i].subtract(descuentosLinea[i]);
            sumaNetos = sumaNetos.add(netos[i]);
        }

        BigDecimal descuentoGeneral = importeDescuento(general, sumaNetos, "descuentoGeneral");
        BigDecimal[] partes = repartir(descuentoGeneral, netos, sumaNetos);

        List<Linea> lineas = new ArrayList<>(n);
        BigDecimal subtotal = BigDecimal.ZERO;
        BigDecimal totalDescuentoLineas = BigDecimal.ZERO;
        BigDecimal base = BigDecimal.ZERO;
        BigDecimal impuestos = BigDecimal.ZERO;
        BigDecimal total = BigDecimal.ZERO;
        for (int i = 0; i < n; i++) {
            Entrada e = entradas.get(i);
            BigDecimal baseLinea = netos[i].subtract(partes[i]);
            BigDecimal tarifa = e.tratamiento().cobraImpuesto() ? e.tarifa() : BigDecimal.ZERO;
            BigDecimal impuesto = e.tratamiento().cobraImpuesto() ? Dinero.porcentajeDe(baseLinea, tarifa) : Dinero.CERO;
            BigDecimal totalLinea = baseLinea.add(impuesto);
            lineas.add(new Linea(i + 1, Dinero.redondear(e.precioUnitario()), e.cantidad(), subtotales[i],
                    descuentosLinea[i], partes[i], baseLinea, e.tratamiento(), tarifa.setScale(2, RoundingMode.HALF_UP),
                    impuesto, totalLinea));
            subtotal = subtotal.add(subtotales[i]);
            totalDescuentoLineas = totalDescuentoLineas.add(descuentosLinea[i]);
            base = base.add(baseLinea);
            impuestos = impuestos.add(impuesto);
            total = total.add(totalLinea);
        }
        return new Resultado(List.copyOf(lineas), subtotal, totalDescuentoLineas, Dinero.redondear(descuentoGeneral),
                base, impuestos, total);
    }

    /** Importe en pesos de un descuento sobre {@code importe}; 0 si no hay descuento. */
    static BigDecimal importeDescuento(Descuento descuento, BigDecimal importe, String campo) {
        if (descuento == null) {
            return Dinero.CERO;
        }
        BigDecimal valor = descuento.valor();
        if (valor.signum() <= 0) {
            throw new CampoInvalidoException(campo, "El descuento debe ser mayor que 0.");
        }
        if (valor.scale() > 2) {
            throw new CampoInvalidoException(campo, "El descuento admite como máximo 2 decimales.");
        }
        return switch (descuento.tipo()) {
            case PORCENTAJE -> {
                if (valor.compareTo(Dinero.CIEN) > 0) {
                    throw new CampoInvalidoException(campo, "El porcentaje de descuento no puede superar el 100 %.");
                }
                yield Dinero.porcentajeDe(importe, valor);
            }
            case VALOR -> {
                if (valor.compareTo(importe) > 0) {
                    throw new CampoInvalidoException(campo, "El descuento no puede superar el importe al que se aplica.");
                }
                yield Dinero.redondear(valor);
            }
        };
    }

    /**
     * Reparte {@code total} (en centavos) en proporción a {@code pesos} por el método del mayor
     * resto. Determinista: a igual resto gana el índice menor.
     */
    static BigDecimal[] repartir(BigDecimal total, BigDecimal[] pesos, BigDecimal sumaPesos) {
        int n = pesos.length;
        BigDecimal[] partes = new BigDecimal[n];
        if (total.signum() == 0 || sumaPesos.signum() == 0) {
            java.util.Arrays.fill(partes, Dinero.CERO);
            return partes;
        }
        BigDecimal[] restos = new BigDecimal[n];
        BigDecimal asignado = BigDecimal.ZERO;
        for (int i = 0; i < n; i++) {
            BigDecimal exacta = total.multiply(pesos[i]).divide(sumaPesos, 12, RoundingMode.DOWN);
            partes[i] = exacta.setScale(Dinero.ESCALA, RoundingMode.DOWN);
            restos[i] = exacta.subtract(partes[i]);
            asignado = asignado.add(partes[i]);
        }
        int centavos = total.subtract(asignado).movePointRight(Dinero.ESCALA).intValueExact();
        Integer[] orden = new Integer[n];
        for (int i = 0; i < n; i++) {
            orden[i] = i;
        }
        java.util.Arrays.sort(orden, Comparator.<Integer, BigDecimal>comparing(i -> restos[i]).reversed()
                .thenComparing(Comparator.naturalOrder()));
        for (int k = 0; k < centavos; k++) {
            int i = orden[k % n];
            partes[i] = partes[i].add(Dinero.CENTAVO);
        }
        return partes;
    }
}
