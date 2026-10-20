package com.unisen.sgp.model.dto;

import com.unisen.sgp.model.entity.AutorizacionComercial;
import com.unisen.sgp.model.entity.CambioPrecio;
import com.unisen.sgp.model.entity.EstadoStock;
import com.unisen.sgp.model.entity.TratamientoIva;
import com.unisen.sgp.model.entity.VentaBajoCosto;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.time.Instant;

/** Contratos de la gestión comercial (precios, IVA, rentabilidad, política y autorizaciones). */
public final class ComercialDTO {

    private ComercialDTO() {
    }

    /**
     * Producto con sus condiciones de venta y su rentabilidad unitaria estimada (precio sin IVA
     * frente al costo promedio vigente). Los indicadores son null cuando no están definidos
     * (sin precio, sin costo o con costo cero): desconocido no es cero.
     */
    public record ProductoComercial(
            Long id,
            String sku,
            String codigoBarras,
            String nombre,
            boolean activo,
            @Schema(description = "Precio de referencia de compra (solicitudes y órdenes)") BigDecimal precioCompraReferencia,
            @Schema(description = "Precio de venta unitario sin IVA; null = no está a la venta") BigDecimal precioVenta,
            TratamientoIva tratamientoIva,
            BigDecimal tarifaIva,
            BigDecimal precioConIva,
            @Schema(description = "Costo promedio ponderado (método provisional); null = desconocido") BigDecimal costoPromedio,
            BigDecimal utilidadUnitaria,
            BigDecimal margen,
            BigDecimal recargo,
            boolean bajoCosto,
            @Schema(description = "Precio que daría el margen objetivo de la política (solo sugerencia)") BigDecimal precioSugerido,
            int existencias,
            EstadoStock estadoStock) {
    }

    /** Nuevas condiciones de venta (precio sin IVA y tratamiento del IVA) con su motivo. */
    public record CondicionesVentaRequest(
            @Schema(example = "4500")
            @NotNull(message = "El precio de venta es obligatorio.")
            @Positive(message = "El precio de venta debe ser mayor que 0.")
            @Digits(integer = 12, fraction = 2, message = "El precio admite hasta 12 enteros y 2 decimales.")
            BigDecimal precioVenta,

            @NotNull(message = "Indica el tratamiento del IVA (GRAVADO, EXENTO o EXCLUIDO).")
            TratamientoIva tratamientoIva,

            @Schema(example = "19", description = "Tarifa en %: mayor que 0 si es GRAVADO; 0 si es EXENTO o EXCLUIDO")
            @NotNull(message = "La tarifa del IVA es obligatoria (0 para exentos y excluidos).")
            @DecimalMin(value = "0", message = "La tarifa no puede ser negativa.")
            @DecimalMax(value = "99.99", message = "La tarifa debe ser menor que 100 %.")
            @Digits(integer = 2, fraction = 2, message = "La tarifa admite 2 enteros y 2 decimales.")
            BigDecimal tarifaIva,

            @Size(max = CambioPrecio.MOTIVO_MAX_LENGTH, message = "El motivo no puede superar 300 caracteres.")
            String motivo) {

        public CondicionesVentaRequest {
            motivo = Textos.vacioANull(motivo);
        }
    }

    public record CambioPrecioDTO(Long id, Long productoId, String sku, String nombre, BigDecimal precioAnterior,
                                  BigDecimal precioNuevo, TratamientoIva tratamientoAnterior,
                                  TratamientoIva tratamientoNuevo, BigDecimal tarifaAnterior, BigDecimal tarifaNueva,
                                  BigDecimal costoReferencia, String motivo, UsuarioResumenDTO usuario, Instant fecha) {

        public static CambioPrecioDTO from(CambioPrecio cambio) {
            return new CambioPrecioDTO(cambio.getId(), cambio.getProducto().getId(), cambio.getProducto().getSku(),
                    cambio.getProducto().getNombre(), cambio.getPrecioAnterior(), cambio.getPrecioNuevo(),
                    cambio.getTratamientoAnterior(), cambio.getTratamientoNuevo(), cambio.getTarifaAnterior(),
                    cambio.getTarifaNueva(), cambio.getCostoReferencia(), cambio.getMotivo(),
                    UsuarioResumenDTO.from(cambio.getUsuario()), cambio.getFecha());
        }
    }

    /** Política vigente; {@code porDefecto} = la empresa aún no ha definido la suya. */
    public record PoliticaDTO(BigDecimal descuentoMaxTrabajador, BigDecimal descuentoMaxAdministrador,
                              VentaBajoCosto ventaBajoCosto, BigDecimal margenObjetivo, boolean porDefecto,
                              UsuarioResumenDTO actualizadaPor, Instant actualizadaEn) {
    }

    public record PoliticaRequest(
            @Schema(example = "10", description = "Descuento efectivo máximo por línea para un trabajador, en %")
            @NotNull(message = "Indica el descuento máximo del trabajador.")
            @DecimalMin(value = "0", message = "El descuento no puede ser negativo.")
            @DecimalMax(value = "100", message = "El descuento no puede superar el 100 %.")
            @Digits(integer = 3, fraction = 2, message = "Admite 2 decimales.")
            BigDecimal descuentoMaxTrabajador,

            @Schema(example = "30")
            @NotNull(message = "Indica el descuento máximo del administrador comercial.")
            @DecimalMin(value = "0", message = "El descuento no puede ser negativo.")
            @DecimalMax(value = "100", message = "El descuento no puede superar el 100 %.")
            @Digits(integer = 3, fraction = 2, message = "Admite 2 decimales.")
            BigDecimal descuentoMaxAdministrador,

            @NotNull(message = "Indica qué hacer con las ventas bajo el costo (PERMITIR o AUTORIZACION).")
            VentaBajoCosto ventaBajoCosto,

            @Schema(example = "30", description = "Opcional: margen bruto objetivo para sugerir precios")
            @DecimalMin(value = "0", message = "El margen objetivo no puede ser negativo.")
            @DecimalMax(value = "99.99", message = "El margen objetivo debe ser menor que 100 %.")
            @Digits(integer = 2, fraction = 2, message = "Admite 2 decimales.")
            BigDecimal margenObjetivo) {
    }

    public record AutorizacionRequest(
            @Schema(example = "25", description = "Descuento efectivo máximo por línea que cubre, en %")
            @NotNull(message = "Indica el descuento máximo autorizado.")
            @DecimalMin(value = "0", message = "El descuento no puede ser negativo.")
            @DecimalMax(value = "100", message = "El descuento no puede superar el 100 %.")
            @Digits(integer = 3, fraction = 2, message = "Admite 2 decimales.")
            BigDecimal descuentoMaximo,

            @Schema(description = "Si también autoriza vender por debajo del costo")
            boolean permiteBajoCosto,

            @NotBlank(message = "Indica el motivo de la autorización.")
            @Size(max = 300, message = "El motivo no puede superar 300 caracteres.")
            String motivo,

            @Schema(example = "30", description = "Minutos de vigencia (5 a 1440; 30 por defecto)")
            @Min(value = 5, message = "La vigencia mínima es de 5 minutos.")
            @Max(value = 1440, message = "La vigencia máxima es de 24 horas.")
            Integer minutosVigencia) {

        public AutorizacionRequest {
            motivo = Textos.limpiar(motivo);
            minutosVigencia = minutosVigencia == null ? 30 : minutosVigencia;
        }
    }

    public enum EstadoAutorizacion {
        VIGENTE, USADA, VENCIDA
    }

    /** {@code codigo} completo solo en la respuesta del alta; en el listado, solo sus 4 últimos caracteres. */
    public record AutorizacionDTO(Long id, String codigo, BigDecimal descuentoMaximo, boolean permiteBajoCosto,
                                  String motivo, EstadoAutorizacion estado, UsuarioResumenDTO creadaPor,
                                  Instant creadaEn, Instant expiraEn, Long ventaId, UsuarioResumenDTO usadaPor,
                                  Instant usadaEn) {

        public static AutorizacionDTO from(AutorizacionComercial a, boolean codigoCompleto, Instant ahora) {
            EstadoAutorizacion estado = a.usada() ? EstadoAutorizacion.USADA
                    : a.vencida(ahora) ? EstadoAutorizacion.VENCIDA : EstadoAutorizacion.VIGENTE;
            String codigo = codigoCompleto ? a.getCodigo() : "…" + a.getCodigo().substring(a.getCodigo().length() - 4);
            return new AutorizacionDTO(a.getId(), codigo, a.getDescuentoMaximo(), a.isPermiteBajoCosto(), a.getMotivo(),
                    estado, UsuarioResumenDTO.from(a.getCreadaPor()), a.getCreadaEn(), a.getExpiraEn(), a.getVentaId(),
                    UsuarioResumenDTO.from(a.getUsadaPor()), a.getUsadaEn());
        }
    }
}
