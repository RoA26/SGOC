package com.unisen.sgp.service.comercial;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.unisen.sgp.exception.CampoInvalidoException;
import com.unisen.sgp.model.entity.TipoDescuento;
import com.unisen.sgp.model.entity.TratamientoIva;
import com.unisen.sgp.service.comercial.CalculadoraVenta.Descuento;
import com.unisen.sgp.service.comercial.CalculadoraVenta.Entrada;
import com.unisen.sgp.service.comercial.CalculadoraVenta.Linea;
import com.unisen.sgp.service.comercial.CalculadoraVenta.Resultado;
import java.math.BigDecimal;
import java.util.List;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/** Fórmulas comerciales sin base de datos: descuentos, IVA, redondeo, reparto, rentabilidad y valoración. */
class CalculoComercialTest {

    private static final BigDecimal DIECINUEVE = new BigDecimal("19");

    private static BigDecimal d(String valor) {
        return new BigDecimal(valor);
    }

    private static Entrada gravada(String precio, int cantidad, Descuento descuento) {
        return new Entrada(d(precio), cantidad, TratamientoIva.GRAVADO, DIECINUEVE, descuento);
    }

    private static Entrada excluida(String precio, int cantidad) {
        return new Entrada(d(precio), cantidad, TratamientoIva.EXCLUIDO, BigDecimal.ZERO, null);
    }

    /** La suma de las líneas coincide siempre con los totales de la venta. */
    private static void coherente(Resultado r) {
        assertThat(r.lineas().stream().map(Linea::subtotal).reduce(BigDecimal.ZERO, BigDecimal::add)).isEqualByComparingTo(r.subtotal());
        assertThat(r.lineas().stream().map(Linea::descuentoGeneral).reduce(BigDecimal.ZERO, BigDecimal::add))
                .isEqualByComparingTo(r.descuentoGeneral());
        assertThat(r.lineas().stream().map(Linea::baseGravable).reduce(BigDecimal.ZERO, BigDecimal::add))
                .isEqualByComparingTo(r.baseGravable());
        assertThat(r.lineas().stream().map(Linea::impuesto).reduce(BigDecimal.ZERO, BigDecimal::add))
                .isEqualByComparingTo(r.impuestos());
        assertThat(r.lineas().stream().map(Linea::total).reduce(BigDecimal.ZERO, BigDecimal::add)).isEqualByComparingTo(r.total());
        assertThat(r.subtotal().subtract(r.descuentoLineas()).subtract(r.descuentoGeneral())).isEqualByComparingTo(r.baseGravable());
        assertThat(r.baseGravable().add(r.impuestos())).isEqualByComparingTo(r.total());
    }

    @Nested
    class Impuestos {

        @Test
        void productoGravadoAl19() {
            Resultado r = CalculadoraVenta.calcular(List.of(gravada("10000", 3, null)), null);
            assertThat(r.baseGravable()).isEqualByComparingTo("30000");
            assertThat(r.impuestos()).isEqualByComparingTo("5700");
            assertThat(r.total()).isEqualByComparingTo("35700");
            coherente(r);
        }

        @Test
        void exentosYExcluidosNoCobranIvaPeroConservanSuTratamiento() {
            Resultado r = CalculadoraVenta.calcular(List.of(
                    new Entrada(d("2500"), 2, TratamientoIva.EXENTO, BigDecimal.ZERO, null),
                    excluida("1800", 1)), null);
            assertThat(r.impuestos()).isEqualByComparingTo("0");
            assertThat(r.total()).isEqualByComparingTo("6800");
            assertThat(r.lineas()).extracting(Linea::tratamiento).containsExactly(TratamientoIva.EXENTO, TratamientoIva.EXCLUIDO);
            coherente(r);
        }

        @Test
        void elIvaSeRedondeaPorLineaAlCentavo() {
            // 333,33 × 19 % = 63,3327 → 63,33
            Resultado r = CalculadoraVenta.calcular(List.of(gravada("333.33", 1, null)), null);
            assertThat(r.impuestos()).isEqualByComparingTo("63.33");
            // 0,05 × 19 % = 0,0095 → 0,01 (medio hacia arriba)
            Resultado minimo = CalculadoraVenta.calcular(List.of(gravada("0.05", 1, null)), null);
            assertThat(minimo.impuestos()).isEqualByComparingTo("0.01");
        }
    }

    @Nested
    class Descuentos {

        @Test
        void porcentajeDeLineaReduceLaBaseAntesDelIva() {
            Resultado r = CalculadoraVenta.calcular(List.of(gravada("10000", 2, new Descuento(TipoDescuento.PORCENTAJE, d("10")))), null);
            assertThat(r.descuentoLineas()).isEqualByComparingTo("2000");
            assertThat(r.baseGravable()).isEqualByComparingTo("18000");
            assertThat(r.impuestos()).isEqualByComparingTo("3420");
            assertThat(r.total()).isEqualByComparingTo("21420");
            assertThat(r.descuentoMaximo()).isEqualByComparingTo("10");
            coherente(r);
        }

        @Test
        void valorFijoDeLineaEsPorLaLineaNoPorUnidad() {
            Resultado r = CalculadoraVenta.calcular(List.of(gravada("10000", 3, new Descuento(TipoDescuento.VALOR, d("1500")))), null);
            assertThat(r.descuentoLineas()).isEqualByComparingTo("1500");
            assertThat(r.baseGravable()).isEqualByComparingTo("28500");
            assertThat(r.lineas().get(0).porcentajeDescuento()).isEqualByComparingTo("5");
        }

        @Test
        void descuentoGeneralSeRepartePorElNetoYCuadraAlCentavo() {
            // Netos 100 / 100 / 100 y 10 pesos de descuento: 3,33 + 3,33 + 3,34 (el centavo sobrante,
            // a igual resto, va a la primera línea).
            Resultado r = CalculadoraVenta.calcular(List.of(excluida("100", 1), excluida("100", 1), excluida("100", 1)),
                    new Descuento(TipoDescuento.VALOR, d("10")));
            assertThat(r.lineas()).extracting(Linea::descuentoGeneral)
                    .containsExactly(d("3.34"), d("3.33"), d("3.33"));
            assertThat(r.descuentoGeneral()).isEqualByComparingTo("10");
            coherente(r);
        }

        @Test
        void repartoDeterministaConPesosDistintosYMezclaDeIva() {
            Resultado r = CalculadoraVenta.calcular(List.of(gravada("19990", 1, null), excluida("1234.56", 3),
                            gravada("7.77", 7, new Descuento(TipoDescuento.PORCENTAJE, d("12.5")))),
                    new Descuento(TipoDescuento.PORCENTAJE, d("7")));
            Resultado otraVez = CalculadoraVenta.calcular(List.of(gravada("19990", 1, null), excluida("1234.56", 3),
                            gravada("7.77", 7, new Descuento(TipoDescuento.PORCENTAJE, d("12.5")))),
                    new Descuento(TipoDescuento.PORCENTAJE, d("7")));
            assertThat(otraVez).isEqualTo(r);
            coherente(r);
            // El descuento general es el 7 % de la suma de netos.
            BigDecimal netos = r.subtotal().subtract(r.descuentoLineas());
            assertThat(r.descuentoGeneral()).isEqualByComparingTo(Dinero.porcentajeDe(netos, d("7")));
            // Los exentos/excluidos no pagan IVA aunque reciban parte del descuento.
            assertThat(r.lineas().get(1).impuesto()).isEqualByComparingTo("0");
        }

        @Test
        void descuentoEfectivoSumaLineaYParteDelGeneral() {
            Resultado r = CalculadoraVenta.calcular(List.of(excluida("1000", 1)), new Descuento(TipoDescuento.PORCENTAJE, d("5")));
            Linea linea = r.lineas().get(0);
            assertThat(linea.porcentajeDescuento()).isEqualByComparingTo("5");
            assertThat(linea.superaDescuento(d("5"))).isFalse();
            assertThat(linea.superaDescuento(d("4.99"))).isTrue();
        }

        @Test
        void rechazaDescuentosInvalidos() {
            assertThatThrownBy(() -> CalculadoraVenta.calcular(List.of(gravada("1000", 1,
                    new Descuento(TipoDescuento.PORCENTAJE, d("100.01")))), null))
                    .isInstanceOf(CampoInvalidoException.class).hasMessageContaining("100 %");
            assertThatThrownBy(() -> CalculadoraVenta.calcular(List.of(gravada("1000", 1,
                    new Descuento(TipoDescuento.VALOR, d("1000.01")))), null))
                    .isInstanceOf(CampoInvalidoException.class).hasMessageContaining("superar el importe");
            assertThatThrownBy(() -> CalculadoraVenta.calcular(List.of(gravada("1000", 1,
                    new Descuento(TipoDescuento.VALOR, d("-5")))), null))
                    .isInstanceOf(CampoInvalidoException.class).hasMessageContaining("mayor que 0");
            assertThatThrownBy(() -> CalculadoraVenta.calcular(List.of(gravada("1000", 1, null)),
                    new Descuento(TipoDescuento.VALOR, d("2000"))))
                    .isInstanceOf(CampoInvalidoException.class);
            assertThatThrownBy(() -> CalculadoraVenta.calcular(List.of(gravada("1000", 0, null)), null))
                    .isInstanceOf(CampoInvalidoException.class);
        }

        @Test
        void descuentoTotalDejaIngresoCero() {
            Resultado r = CalculadoraVenta.calcular(List.of(gravada("1000", 1, null)),
                    new Descuento(TipoDescuento.PORCENTAJE, d("100")));
            assertThat(r.baseGravable()).isEqualByComparingTo("0");
            assertThat(r.impuestos()).isEqualByComparingTo("0");
            assertThat(r.total()).isEqualByComparingTo("0");
            coherente(r);
        }
    }

    @Nested
    class Indicadores {

        @Test
        void utilidadMargenYRecargoPositivos() {
            Rentabilidad r = Rentabilidad.de(d("10000"), d("7500"));
            assertThat(r.utilidad()).isEqualByComparingTo("2500");
            assertThat(r.margen()).isEqualByComparingTo("25");
            assertThat(r.recargo()).isEqualByComparingTo("33.33");
            assertThat(r.perdida()).isFalse();
        }

        @Test
        void perdidaBruta() {
            Rentabilidad r = Rentabilidad.de(d("9000"), d("10000"));
            assertThat(r.utilidad()).isEqualByComparingTo("-1000");
            assertThat(r.margen()).isEqualByComparingTo("-11.11");
            assertThat(r.recargo()).isEqualByComparingTo("-10");
            assertThat(r.perdida()).isTrue();
        }

        @Test
        void ingresoCeroDejaElMargenSinDefinir() {
            Rentabilidad r = Rentabilidad.de(BigDecimal.ZERO, d("500"));
            assertThat(r.utilidad()).isEqualByComparingTo("-500");
            assertThat(r.margen()).isNull();
            assertThat(r.recargo()).isEqualByComparingTo("-100");
        }

        @Test
        void costoCeroDejaElRecargoSinDefinirYCostoDesconocidoNoEsCero() {
            Rentabilidad cero = Rentabilidad.de(d("1000"), BigDecimal.ZERO);
            assertThat(cero.utilidad()).isEqualByComparingTo("1000");
            assertThat(cero.margen()).isEqualByComparingTo("100");
            assertThat(cero.recargo()).isNull();
            Rentabilidad desconocido = Rentabilidad.de(d("1000"), null);
            assertThat(desconocido.utilidad()).isNull();
            assertThat(desconocido.margen()).isNull();
            assertThat(desconocido.recargo()).isNull();
        }

        @Test
        void precioSugeridoParaUnMargenObjetivo() {
            // Costo 7.000 con 30 % de margen bruto: 7.000 / 0,70 = 10.000.
            assertThat(Rentabilidad.precioSugerido(d("7000"), d("30"))).isEqualByComparingTo("10000");
            assertThat(Rentabilidad.precioSugerido(null, d("30"))).isNull();
            assertThat(Rentabilidad.precioSugerido(d("7000"), null)).isNull();
        }

        @Test
        void bajoCostoComparaLaBaseConElCostoDeLasUnidades() {
            Linea linea = CalculadoraVenta.calcular(List.of(gravada("1000", 2, null)), null).lineas().get(0);
            assertThat(linea.bajoCosto(d("1000.01"))).isTrue();
            assertThat(linea.bajoCosto(d("1000"))).isFalse();
            assertThat(linea.bajoCosto(null)).isFalse();
        }
    }

    @Nested
    class Valoracion {

        @Test
        void promedioPonderadoDeEntradas() {
            BigDecimal promedio = ValoracionInventario.promedioTrasEntrada(0, null, 10, d("1000"));
            assertThat(promedio).isEqualByComparingTo("1000");
            // 10 a 1.000 + 5 a 1.300 = 16.500 / 15 = 1.100
            promedio = ValoracionInventario.promedioTrasEntrada(10, promedio, 5, d("1300"));
            assertThat(promedio).isEqualByComparingTo("1100");
            // Entrada sin costo: se valora al promedio vigente (no cambia).
            assertThat(ValoracionInventario.promedioTrasEntrada(15, promedio, 3, null)).isEqualByComparingTo("1100");
        }

        @Test
        void sinExistenciasElPromedioEsElCostoDeLaEntradaYLoDesconocidoNoSeMezcla() {
            assertThat(ValoracionInventario.promedioTrasEntrada(0, d("999"), 4, d("1200"))).isEqualByComparingTo("1200");
            assertThat(ValoracionInventario.promedioTrasEntrada(5, null, 4, d("1200"))).isNull();
            assertThat(ValoracionInventario.promedioTrasEntrada(0, null, 4, null)).isNull();
        }
    }
}
