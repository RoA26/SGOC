package com.unisen.sgp.model.dto;

import com.unisen.sgp.model.entity.DetalleDevolucionVenta;
import com.unisen.sgp.model.entity.DetalleVenta;
import com.unisen.sgp.model.entity.DevolucionVenta;
import com.unisen.sgp.model.entity.EstadoStock;
import com.unisen.sgp.model.entity.EstadoVenta;
import com.unisen.sgp.model.entity.MedioPago;
import com.unisen.sgp.model.entity.MetodoCosto;
import com.unisen.sgp.model.entity.PagoVenta;
import com.unisen.sgp.model.entity.TipoDescuento;
import com.unisen.sgp.model.entity.TipoDevolucion;
import com.unisen.sgp.model.entity.TratamientoIva;
import com.unisen.sgp.model.entity.Venta;
import com.unisen.sgp.service.comercial.Dinero;
import com.unisen.sgp.service.comercial.Rentabilidad;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/**
 * Contratos del punto de venta. El cliente envía productos, cantidades, descuentos pedidos y
 * pagos; nunca precios, impuestos, costos ni totales (si los envía, se ignoran: el servidor los
 * toma del catálogo y los recalcula). Los campos de costo y rentabilidad solo se rellenan para el
 * administrador comercial (y el SUPER_ADMIN en supervisión); para el trabajador son null y no
 * aparecen en el JSON.
 */
public final class VentaDTO {

    public static final int MAX_LINEAS = 100;

    private VentaDTO() {
    }

    // ------------------------------------------------------------------ peticiones

    public record DescuentoRequest(
            @NotNull(message = "Indica el tipo de descuento (PORCENTAJE o VALOR).") TipoDescuento tipo,
            @NotNull(message = "Indica el valor del descuento.")
            @Positive(message = "El descuento debe ser mayor que 0.")
            @Digits(integer = 12, fraction = 2, message = "El descuento admite 2 decimales.")
            BigDecimal valor) {
    }

    public record LineaRequest(
            @NotNull(message = "Indica el producto.") @Positive(message = "Producto no válido.") Long productoId,
            @NotNull(message = "Indica la cantidad.")
            @Min(value = 1, message = "La cantidad mínima es 1.")
            @Max(value = 9_999, message = "La cantidad máxima por línea es 9999.")
            Integer cantidad,
            @Valid DescuentoRequest descuento) {
    }

    public record PagoRequest(
            @NotNull(message = "Indica el medio de pago.") MedioPago medio,
            @NotNull(message = "Indica el valor del pago.")
            @Positive(message = "El valor del pago debe ser mayor que 0.")
            @Digits(integer = 12, fraction = 2, message = "El valor admite 2 decimales.")
            BigDecimal valor,
            @Schema(description = "Solo efectivo: lo que entrega el cliente (para el cambio)")
            @Positive(message = "Lo recibido debe ser mayor que 0.")
            @Digits(integer = 12, fraction = 2, message = "Lo recibido admite 2 decimales.")
            BigDecimal recibido,
            @Size(max = 60, message = "La referencia no puede superar 60 caracteres.") String referencia) {

        public PagoRequest {
            referencia = Textos.vacioANull(referencia);
        }
    }

    /** Cotización del carrito: el mismo cálculo que la venta, sin registrar nada. */
    public record CotizacionRequest(
            @NotEmpty(message = "Agrega al menos un producto.")
            @Size(max = MAX_LINEAS, message = "Una venta admite hasta 100 líneas.")
            List<@Valid @NotNull LineaRequest> lineas,
            @Valid DescuentoRequest descuentoGeneral,
            @Size(max = 32, message = "Código de autorización no válido.") String codigoAutorizacion) {

        public CotizacionRequest {
            codigoAutorizacion = Textos.vacioANull(codigoAutorizacion);
        }
    }

    public record VentaRequest(
            @Schema(example = "6f1c0c1e-4a5b-4f6e-9d1a-0b2c3d4e5f60",
                    description = "Generada por el cliente para cada intento de cobro: un reintento con la misma clave no duplica la venta")
            @NotBlank(message = "Falta la clave de idempotencia.")
            @Pattern(regexp = Patrones.CLAVE_IDEMPOTENCIA, message = "Clave de idempotencia no válida.")
            String claveIdempotencia,
            @NotEmpty(message = "Agrega al menos un producto.")
            @Size(max = MAX_LINEAS, message = "Una venta admite hasta 100 líneas.")
            List<@Valid @NotNull LineaRequest> lineas,
            @Valid DescuentoRequest descuentoGeneral,
            @NotEmpty(message = "Registra al menos un pago.")
            @Size(max = 5, message = "Una venta admite hasta 5 pagos.")
            List<@Valid @NotNull PagoRequest> pagos,
            @Size(max = 32, message = "Código de autorización no válido.") String codigoAutorizacion,
            @Size(max = 150, message = "El nombre del cliente no puede superar 150 caracteres.") String clienteNombre,
            @Pattern(regexp = "^[0-9A-Za-z-]{3,20}$", message = "El documento admite de 3 a 20 letras, números o guiones.")
            String clienteDocumento) {

        public VentaRequest {
            codigoAutorizacion = Textos.vacioANull(codigoAutorizacion);
            clienteNombre = Textos.vacioANull(clienteNombre);
            clienteDocumento = Textos.vacioANull(clienteDocumento);
        }
    }

    public record LineaDevolucionRequest(
            @NotNull(message = "Indica la línea de la venta.") @Positive(message = "Línea no válida.") Long detalleVentaId,
            @NotNull(message = "Indica la cantidad.")
            @Min(value = 1, message = "La cantidad mínima es 1.")
            @Max(value = 9_999, message = "Cantidad no válida.")
            Integer cantidad,
            @Schema(description = "true si la unidad vuelve al inventario vendible; false si no (p. ej. dañada)")
            @NotNull(message = "Indica si las unidades vuelven al inventario.")
            Boolean reingresaInventario) {
    }

    public record DevolucionRequest(
            @NotBlank(message = "Falta la clave de idempotencia.")
            @Pattern(regexp = Patrones.CLAVE_IDEMPOTENCIA, message = "Clave de idempotencia no válida.")
            String claveIdempotencia,
            @NotBlank(message = "Indica el motivo.")
            @Size(max = Venta.MOTIVO_MAX_LENGTH, message = "El motivo no puede superar 300 caracteres.")
            String motivo,
            @NotEmpty(message = "Indica al menos una línea a devolver.")
            @Size(max = MAX_LINEAS, message = "Demasiadas líneas.")
            List<@Valid @NotNull LineaDevolucionRequest> lineas) {

        public DevolucionRequest {
            motivo = Textos.limpiar(motivo);
        }
    }

    public record AnulacionRequest(
            @NotBlank(message = "Falta la clave de idempotencia.")
            @Pattern(regexp = Patrones.CLAVE_IDEMPOTENCIA, message = "Clave de idempotencia no válida.")
            String claveIdempotencia,
            @NotBlank(message = "Indica el motivo de la anulación.")
            @Size(max = Venta.MOTIVO_MAX_LENGTH, message = "El motivo no puede superar 300 caracteres.")
            String motivo) {

        public AnulacionRequest {
            motivo = Textos.limpiar(motivo);
        }
    }

    // ------------------------------------------------------------------ respuestas

    /** Producto para la caja: precio vigente, IVA y disponibilidad (sin costos). */
    public record ProductoVenta(Long id, String sku, String codigoBarras, String nombre, BigDecimal precioVenta,
                                TratamientoIva tratamientoIva, BigDecimal tarifaIva, BigDecimal precioConIva,
                                int existencias, EstadoStock estadoStock) {
    }

    /** IVA agrupado por tratamiento y tarifa (desglose del comprobante). */
    public record ImpuestoPorTarifa(TratamientoIva tratamiento, BigDecimal tarifa, BigDecimal base, BigDecimal impuesto) {
    }

    public record LineaCotizada(int linea, Long productoId, String sku, String nombre, int cantidad,
                                BigDecimal precioUnitario, BigDecimal subtotal, BigDecimal descuentoLinea,
                                BigDecimal descuentoGeneral, BigDecimal baseGravable, TratamientoIva tratamientoIva,
                                BigDecimal tarifaIva, BigDecimal impuesto, BigDecimal total,
                                BigDecimal porcentajeDescuento, int disponibles, boolean suficiente,
                                boolean bajoCosto) {
    }

    /**
     * @param limiteDescuento      límite de quien vende según la política (%)
     * @param requiereAutorizacion la venta necesita un código de autorización válido para confirmarse
     * @param motivosAutorizacion  por qué (descuento por encima del límite, venta bajo el costo)
     * @param autorizacionValida   el código enviado existe, está vigente y cubre la venta
     */
    public record Cotizacion(List<LineaCotizada> lineas, BigDecimal subtotal, BigDecimal descuentoLineas,
                             BigDecimal descuentoGeneral, BigDecimal baseGravable, BigDecimal impuestos,
                             BigDecimal total, List<ImpuestoPorTarifa> impuestosPorTarifa, BigDecimal descuentoMaximo,
                             BigDecimal limiteDescuento, boolean existenciasSuficientes, boolean requiereAutorizacion,
                             List<String> motivosAutorizacion, Boolean autorizacionValida, boolean hayBajoCosto) {
    }

    public record Linea(Long id, int linea, Long productoId, String sku, String nombre, int cantidad,
                        BigDecimal precioUnitario, TipoDescuento descuentoTipo, BigDecimal descuentoValor,
                        BigDecimal subtotal, BigDecimal descuentoLinea, BigDecimal descuentoGeneral,
                        BigDecimal baseGravable, TratamientoIva tratamientoIva, BigDecimal tarifaIva,
                        BigDecimal impuesto, BigDecimal total, int cantidadDevuelta, int pendienteDeDevolver,
                        // Solo gestión:
                        BigDecimal costoUnitario, MetodoCosto metodoCosto, BigDecimal utilidad, BigDecimal margen,
                        Boolean bajoCosto) {

        static Linea from(DetalleVenta d, boolean verCostos) {
            Rentabilidad r = verCostos ? Rentabilidad.de(d.getBaseGravable(), d.costoTotal()) : null;
            return new Linea(d.getId(), d.getLinea(), d.getProducto().getId(), d.getSku(), d.getNombre(), d.getCantidad(),
                    d.getPrecioUnitario(), d.getDescuentoTipo(), d.getDescuentoValor(), d.getSubtotal(),
                    d.getDescuentoLinea(), d.getDescuentoGeneral(), d.getBaseGravable(), d.getTratamientoIva(),
                    d.getTarifaIva(), d.getImpuesto(), d.getTotal(), d.getCantidadDevuelta(), d.pendienteDeDevolver(),
                    verCostos ? d.getCostoUnitario() : null, verCostos ? d.getMetodoCosto() : null,
                    r == null ? null : r.utilidad(), r == null ? null : r.margen(), verCostos ? d.isBajoCosto() : null);
        }
    }

    public record Pago(MedioPago medio, BigDecimal valor, BigDecimal recibido, BigDecimal cambio, String referencia) {

        static Pago from(PagoVenta p) {
            return new Pago(p.getMedio(), p.getValor(), p.getRecibido(), p.getCambio(), p.getReferencia());
        }
    }

    public record LineaDevuelta(Long detalleVentaId, int linea, String sku, String nombre, int cantidad,
                                boolean reingresaInventario, BigDecimal baseGravable, BigDecimal impuesto,
                                BigDecimal total) {

        static LineaDevuelta from(DetalleDevolucionVenta d) {
            return new LineaDevuelta(d.getDetalleVenta().getId(), d.getDetalleVenta().getLinea(),
                    d.getDetalleVenta().getSku(), d.getDetalleVenta().getNombre(), d.getCantidad(),
                    d.isReingresaInventario(), d.getBaseGravable(), d.getImpuesto(), d.getTotal());
        }
    }

    public record Devolucion(Long id, TipoDevolucion tipo, String motivo, BigDecimal baseGravable, BigDecimal impuestos,
                             BigDecimal total, BigDecimal costoTotal, UsuarioResumenDTO registradaPor,
                             Instant registradaEn, List<LineaDevuelta> lineas) {

        public static Devolucion from(DevolucionVenta d, boolean verCostos) {
            return new Devolucion(d.getId(), d.getTipo(), d.getMotivo(), d.getBaseGravable(), d.getImpuestos(),
                    d.getTotal(), verCostos ? d.getCostoTotal() : null, UsuarioResumenDTO.from(d.getRegistradaPor()),
                    d.getRegistradaEn(), d.getDetalles().stream().map(LineaDevuelta::from).toList());
        }
    }

    public record AutorizacionAplicada(Long id, BigDecimal descuentoMaximo, boolean permiteBajoCosto, String motivo,
                                       UsuarioResumenDTO autorizadaPor, Instant autorizadaEn) {
    }

    /** Rentabilidad bruta de la venta neta de devoluciones (solo gestión). */
    public record RentabilidadVenta(BigDecimal ingresoNeto, BigDecimal costo, BigDecimal utilidad, BigDecimal margen,
                                    BigDecimal recargo, boolean costoCompleto) {
    }

    public record Detalle(Long id, long numero, String numeroVisible, EstadoVenta estado, Instant vendidaEn,
                          UsuarioResumenDTO vendidaPor, String clienteNombre, String clienteDocumento,
                          TipoDescuento descuentoGeneralTipo, BigDecimal descuentoGeneralValor, BigDecimal subtotal,
                          BigDecimal descuentoLineas, BigDecimal descuentoGeneral, BigDecimal baseGravable,
                          BigDecimal impuestos, BigDecimal total, List<ImpuestoPorTarifa> impuestosPorTarifa,
                          List<Linea> lineas, List<Pago> pagos, AutorizacionAplicada autorizacion,
                          List<Devolucion> devoluciones, BigDecimal totalDevuelto, UsuarioResumenDTO anuladaPor,
                          Instant anuladaEn, String motivoAnulacion, RentabilidadVenta rentabilidad) {

        public static Detalle from(Venta v, List<DevolucionVenta> devoluciones, boolean verCostos) {
            List<Devolucion> devs = devoluciones.stream().map(d -> Devolucion.from(d, verCostos)).toList();
            BigDecimal devuelto = devoluciones.stream().map(DevolucionVenta::getTotal).reduce(Dinero.CERO, BigDecimal::add);
            var a = v.getAutorizacion();
            AutorizacionAplicada autorizacion = a == null ? null : new AutorizacionAplicada(a.getId(),
                    a.getDescuentoMaximo(), a.isPermiteBajoCosto(), a.getMotivo(), UsuarioResumenDTO.from(a.getCreadaPor()),
                    a.getCreadaEn());
            return new Detalle(v.getId(), v.getNumero(), VentaDTO.numeroVisible(v.getNumero()), v.getEstado(), v.getVendidaEn(),
                    UsuarioResumenDTO.from(v.getVendidaPor()), v.getClienteNombre(), v.getClienteDocumento(),
                    v.getDescuentoGeneralTipo(), v.getDescuentoGeneralValor(), v.getSubtotal(), v.getDescuentoLineas(),
                    v.getDescuentoGeneral(), v.getBaseGravable(), v.getImpuestos(), v.getTotal(),
                    VentaDTO.impuestosPorTarifa(v.getDetalles()),
                    v.getDetalles().stream().map(d -> Linea.from(d, verCostos)).toList(),
                    v.getPagos().stream().map(Pago::from).toList(), autorizacion, devs, devuelto,
                    UsuarioResumenDTO.from(v.getAnuladaPor()), v.getAnuladaEn(), v.getMotivoAnulacion(),
                    verCostos ? rentabilidad(v, devoluciones) : null);
        }

        /**
         * Ingreso y costo netos de devoluciones (una venta anulada queda en cero). Con alguna línea
         * de costo desconocido, el costo y la utilidad son desconocidos (null), no cero.
         */
        static RentabilidadVenta rentabilidad(Venta v, List<DevolucionVenta> devoluciones) {
            BigDecimal ingreso = v.getBaseGravable();
            BigDecimal costo = v.getCostoTotal();
            for (DevolucionVenta d : devoluciones) {
                ingreso = ingreso.subtract(d.getBaseGravable());
                costo = costo == null || d.getCostoTotal() == null ? null : costo.subtract(d.getCostoTotal());
            }
            Rentabilidad r = Rentabilidad.de(ingreso, costo);
            return new RentabilidadVenta(r.ingreso(), r.costo(), r.utilidad(), r.margen(), r.recargo(), costo != null);
        }
    }

    /** Fila del historial. */
    public record Resumen(Long id, long numero, String numeroVisible, EstadoVenta estado, Instant vendidaEn,
                          UsuarioResumenDTO vendidaPor, int unidades, BigDecimal descuentos, BigDecimal baseGravable,
                          BigDecimal impuestos, BigDecimal total, String clienteNombre) {

        public static Resumen from(Venta v) {
            return new Resumen(v.getId(), v.getNumero(), VentaDTO.numeroVisible(v.getNumero()), v.getEstado(), v.getVendidaEn(),
                    UsuarioResumenDTO.from(v.getVendidaPor()),
                    v.getDetalles().stream().mapToInt(DetalleVenta::getCantidad).sum(),
                    v.getDescuentoLineas().add(v.getDescuentoGeneral()), v.getBaseGravable(), v.getImpuestos(), v.getTotal(),
                    v.getClienteNombre());
        }
    }

    public static String numeroVisible(long numero) {
        return "V-%05d".formatted(numero);
    }

    /** Agrupa base e IVA por (tratamiento, tarifa), en un orden estable. */
    public static List<ImpuestoPorTarifa> impuestosPorTarifa(List<DetalleVenta> detalles) {
        Map<String, BigDecimal[]> grupos = new TreeMap<>();
        Map<String, Object[]> claves = new TreeMap<>();
        for (DetalleVenta d : detalles) {
            String clave = d.getTratamientoIva().name() + "|" + d.getTarifaIva().toPlainString();
            claves.putIfAbsent(clave, new Object[] {d.getTratamientoIva(), d.getTarifaIva()});
            BigDecimal[] acumulado = grupos.computeIfAbsent(clave, k -> new BigDecimal[] {Dinero.CERO, Dinero.CERO});
            acumulado[0] = acumulado[0].add(d.getBaseGravable());
            acumulado[1] = acumulado[1].add(d.getImpuesto());
        }
        List<ImpuestoPorTarifa> resultado = new ArrayList<>();
        grupos.forEach((clave, valores) -> resultado.add(new ImpuestoPorTarifa((TratamientoIva) claves.get(clave)[0],
                (BigDecimal) claves.get(clave)[1], valores[0], valores[1])));
        return resultado;
    }
}
