package com.unisen.sgp.model.dto;

import com.unisen.sgp.model.entity.DetalleOrdenCompra;
import com.unisen.sgp.model.entity.EstadoOrdenCompra;
import com.unisen.sgp.model.entity.OrdenCompra;
import com.unisen.sgp.model.entity.ProductoReferencia;
import com.unisen.sgp.model.entity.ProveedorReferencia;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

/** Orden de compra con sus líneas. Los datos de emisión y cancelación se omiten mientras no existen. */
public record OrdenCompraResponseDTO(
        Long id,
        long numero,
        EstadoOrdenCompra estado,
        Proveedor proveedor,
        Long solicitudId,
        String observaciones,
        List<Linea> detalles,
        BigDecimal total,
        int unidadesPedidas,
        int unidadesRecibidas,
        UsuarioResumenDTO creadoPor,
        Instant creadoEn,
        Instant actualizadoEn,
        UsuarioResumenDTO emitidaPor,
        Instant emitidaEn,
        UsuarioResumenDTO canceladaPor,
        Instant canceladaEn,
        String motivoCancelacion) {

    /** {@code activo = false}: el proveedor se dio de baja después de crear la orden. */
    public record Proveedor(Long id, String nit, String razonSocial, boolean activo) {

        static Proveedor from(ProveedorReferencia proveedor) {
            return new Proveedor(proveedor.getId(), proveedor.getNit(), proveedor.getRazonSocial(), proveedor.isActivo());
        }
    }

    public record Producto(Long id, String sku, String nombre, String codigoBarras, boolean activo) {

        public static Producto from(ProductoReferencia producto) {
            return new Producto(producto.getId(), producto.getSku(), producto.getNombre(), producto.getCodigoBarras(),
                    producto.isActivo());
        }
    }

    /** Línea con el costo pactado, lo recibido y lo pendiente. */
    public record Linea(Long id, Producto producto, int cantidad, BigDecimal costoUnitario, BigDecimal subtotal,
                        int cantidadRecibida, int pendiente) {

        static Linea from(DetalleOrdenCompra detalle) {
            return new Linea(detalle.getId(), Producto.from(detalle.getProducto()), detalle.getCantidad(),
                    detalle.getCostoUnitario(), detalle.subtotal(), detalle.getCantidadRecibida(), detalle.pendiente());
        }
    }

    /** Debe invocarse dentro de la transacción: recorre asociaciones LAZY. */
    public static OrdenCompraResponseDTO from(OrdenCompra orden) {
        List<Linea> lineas = orden.getDetalles().stream().map(Linea::from).toList();
        return new OrdenCompraResponseDTO(
                orden.getId(),
                orden.getNumero(),
                orden.getEstado(),
                Proveedor.from(orden.getProveedor()),
                orden.getSolicitudId(),
                orden.getObservaciones(),
                lineas,
                orden.total(),
                lineas.stream().mapToInt(Linea::cantidad).sum(),
                lineas.stream().mapToInt(Linea::cantidadRecibida).sum(),
                UsuarioResumenDTO.from(orden.getCreadoPor()),
                orden.getCreadoEn(),
                orden.getActualizadoEn(),
                UsuarioResumenDTO.from(orden.getEmitidaPor()),
                orden.getEmitidaEn(),
                UsuarioResumenDTO.from(orden.getCanceladaPor()),
                orden.getCanceladaEn(),
                orden.getMotivoCancelacion());
    }
}
