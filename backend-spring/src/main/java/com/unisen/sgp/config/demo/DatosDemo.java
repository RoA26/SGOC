package com.unisen.sgp.config.demo;

import com.unisen.sgp.model.entity.EstadoSolicitud;
import com.unisen.sgp.model.entity.EstadoUsuario;
import java.util.List;

/**
 * Catálogo ficticio de las cinco empresas de demostración: un administrador comercial y un
 * trabajador por empresa (diez cuentas). Todo es inventado: nombres de personas, NIT (12 dígitos
 * con prefijo 999, un formato distinto al de los NIT de 9 dígitos + DV; no se han contrastado con
 * ningún registro y no representan empresas existentes), proveedores, correos bajo el dominio
 * reservado {@code .test}, códigos de barras (prefijo 200-299, reservado a uso interno en GS1) y la
 * clasificación del IVA, que es solo de demostración y no una clasificación tributaria validada.
 */
final class DatosDemo {

    private DatosDemo() {
    }

    /** Dominio de los correos demo (RFC 2606: {@code .test} nunca se resuelve en internet). */
    static final String DOMINIO_CORREO = "sgp-demo.test";

    record Persona(String usuario, String nombres, String apellidos) {
    }

    record Proveedor(String nit, String razonSocial, String email, String telefono) {
    }

    /**
     * @param proveedor    índice en {@link Negocio#proveedores()}
     * @param codigoBarras EAN-13 ficticio
     * @param costo        costo unitario del inventario inicial
     * @param stock        unidades iniciales (0 = agotado)
     * @param precioVenta  precio de venta sin IVA
     * @param tratamiento  GRAVADO, EXENTO o EXCLUIDO (ficticio)
     */
    record Producto(String sku, String codigoBarras, String nombre, int proveedor, String costo, int stock, int stockMinimo,
                    String precioVenta, String tratamiento, String tarifa) {
    }

    /** Qué se hace con una solicitud tras revisarla. */
    enum Proceso {
        NINGUNO,
        /** Se genera su orden, se emite y el trabajador la recibe en parte, con una incidencia de faltante. */
        ORDEN_RECIBIDA_EN_PARTE
    }

    record Linea(String sku, int cantidad) {
    }

    /** La crea el trabajador; el administrador la deja en {@code estado}. Se identifica por justificación + solicitante. */
    record Solicitud(String justificacion, List<Linea> lineas, EstadoSolicitud estado, String comentario, Proceso proceso) {
    }

    /**
     * Datos de negocio de una empresa activa.
     *
     * @param completo con ventas variadas (descuentos, bajo el costo, pago mixto) y una devolución
     *                 parcial; si no, dos ventas sencillas
     */
    record Negocio(List<Proveedor> proveedores, List<Producto> productos, List<Solicitud> solicitudes, boolean completo) {
    }

    /**
     * @param estadoTrabajador ACTIVO (alta directa), PENDIENTE (se registra con el código de la
     *                         empresa) o RECHAZADO (se registra y el administrador lo rechaza)
     * @param activa           false = empresa desactivada al crearla (nadie de ella puede entrar)
     * @param negocio          null = solo las cuentas
     */
    record Empresa(String codigo, String nombre, String nit, Persona administrador, Persona trabajador,
                   EstadoUsuario estadoTrabajador, boolean activa, Negocio negocio) {
    }

    /** Mismo proveedor (NIT) y mismo producto (SKU y código de barras) en las dos primeras empresas. */
    static final String NIT_COMPARTIDO = "999200000099";
    static final String SKU_COMPARTIDO = "AGUA-600";
    static final String CODIGO_BARRAS_COMPARTIDO = ean13(9, 1);

    static final List<Empresa> EMPRESAS = List.of(
            new Empresa("MVSA01", "Mercado Verde S.A.S.", "999100000001",
                    new Persona("carlos.mendoza", "Carlos", "Mendoza"), new Persona("laura.torres", "Laura", "Torres"),
                    EstadoUsuario.ACTIVO, true,
                    new Negocio(
                            List.of(new Proveedor("999200000011", "Granos del Valle S.A.S. (demo)",
                                            "ventas@granos-del-valle." + DOMINIO_CORREO, "6015550111"),
                                    new Proveedor("999200000012", "Lácteos La Pradera S.A.S. (demo)",
                                            "pedidos@lacteos-pradera." + DOMINIO_CORREO, null),
                                    new Proveedor(NIT_COMPARTIDO, "Bebidas del Río S.A.S. (demo)",
                                            "ventas@bebidas-del-rio." + DOMINIO_CORREO, "6015550199")),
                            List.of(new Producto("MV-ARR-500", ean13(1, 1), "Arroz blanco 500 g", 0, "2100", 80, 20, "2700",
                                            "EXCLUIDO", "0"),
                                    new Producto("MV-ACE-1L", ean13(1, 2), "Aceite vegetal 1 L", 0, "8200", 30, 8, "9900",
                                            "GRAVADO", "5"),
                                    new Producto("MV-GAL-PQ", ean13(1, 3), "Galletas surtidas paquete", 0, "2600", 40, 10,
                                            "2500", "GRAVADO", "19"),
                                    new Producto("MV-LEC-1L", ean13(1, 4), "Leche entera 1 L", 1, "3400", 60, 15, "4200",
                                            "EXENTO", "0"),
                                    new Producto("MV-HUE-30", ean13(1, 5), "Huevos (cubeta x30)", 1, "14000", 6, 8, "17500",
                                            "EXENTO", "0"),
                                    new Producto("MV-CAF-250", ean13(1, 6), "Café molido 250 g", 0, "9800", 0, 8, "13500",
                                            "GRAVADO", "5"),
                                    new Producto(SKU_COMPARTIDO, CODIGO_BARRAS_COMPARTIDO, "Agua en botella 600 ml", 2, "900",
                                            50, 12, "1500", "GRAVADO", "19")),
                            List.of(new Solicitud("Reposición de leche y huevos para el fin de semana (demo).",
                                            List.of(new Linea("MV-LEC-1L", 12), new Linea("MV-HUE-30", 4)),
                                            EstadoSolicitud.APROBADA, "Aprobada: generar la orden hoy (demo).",
                                            Proceso.ORDEN_RECIBIDA_EN_PARTE),
                                    new Solicitud("El café molido está agotado: hay que reponerlo (demo).",
                                            List.of(new Linea("MV-CAF-250", 16)), EstadoSolicitud.PENDIENTE, null,
                                            Proceso.NINGUNO),
                                    new Solicitud("Galletas para una promoción de temporada (demo).",
                                            List.of(new Linea("MV-GAL-PQ", 100)), EstadoSolicitud.RECHAZADA,
                                            "Cantidad excesiva para la rotación actual (demo).", Proceso.NINGUNO)),
                            true)),
            new Empresa("SCA002", "Suministros del Caribe S.A.S.", "999100000002",
                    new Persona("andres.rojas", "Andrés", "Rojas"), new Persona("maria.castro", "María", "Castro"),
                    EstadoUsuario.ACTIVO, true,
                    new Negocio(
                            List.of(new Proveedor("999200000021", "Papelería Mayorista del Caribe (demo)",
                                            "ventas@papeleria-caribe." + DOMINIO_CORREO, "6055550221"),
                                    new Proveedor(NIT_COMPARTIDO, "Bebidas del Río S.A.S. (demo)",
                                            "ventas@bebidas-del-rio." + DOMINIO_CORREO, "6055550299")),
                            List.of(new Producto("SC-RES-CAR", ean13(2, 1), "Resma papel carta 500 hojas", 0, "16000", 25, 5,
                                            "21900", "GRAVADO", "19"),
                                    new Producto("SC-BOL-NEG", ean13(2, 2), "Bolígrafo negro (caja x12)", 0, "9000", 30, 6,
                                            "13900", "GRAVADO", "19"),
                                    new Producto("SC-CUA-100", ean13(2, 3), "Cuaderno cuadriculado 100 hojas", 0, "3800", 0, 15,
                                            "5500", "EXENTO", "0"),
                                    new Producto(SKU_COMPARTIDO, CODIGO_BARRAS_COMPARTIDO, "Agua en botella 600 ml", 1, "950",
                                            40, 10, "1600", "GRAVADO", "19")),
                            List.of(new Solicitud("Reposición de cuadernos para el inicio de clases (demo).",
                                    List.of(new Linea("SC-CUA-100", 30)), EstadoSolicitud.PENDIENTE, null, Proceso.NINGUNO)),
                            false)),
            new Empresa("DNS003", "Distribuciones Nova S.A.S.", "999100000003",
                    new Persona("diego.martinez", "Diego", "Martínez"), new Persona("sofia.ramirez", "Sofía", "Ramírez"),
                    EstadoUsuario.PENDIENTE, true, null),
            new Empresa("CHS004", "Comercializadora Horizonte S.A.S.", "999100000004",
                    new Persona("miguel.ortiz", "Miguel", "Ortiz"), new Persona("valentina.gomez", "Valentina", "Gómez"),
                    EstadoUsuario.RECHAZADO, true, null),
            // Empresa desactivada: ni su administrador ni su trabajador pueden iniciar sesión.
            new Empresa("LAS005", "Logística Andina S.A.S.", "999100000005",
                    new Persona("sergio.vega", "Sergio", "Vega"), new Persona("paula.navarro", "Paula", "Navarro"),
                    EstadoUsuario.ACTIVO, false, null));

    /** Correo ficticio de una persona demo: {@code usuario@codigo.sgp-demo.test}. */
    static String correo(Persona p, String codigo) {
        return p.usuario() + "@" + codigo.toLowerCase() + "." + DOMINIO_CORREO;
    }

    /**
     * EAN-13 ficticio con su dígito de control: prefijo 20 + número de empresa + consecutivo. El
     * rango 200-299 está reservado por GS1 para circulación interna y no identifica productos reales.
     */
    static String ean13(int empresa, int producto) {
        String doce = "20%d%09d".formatted(empresa, producto);
        int suma = 0;
        for (int i = 0; i < 12; i++) {
            int digito = doce.charAt(i) - '0';
            suma += i % 2 == 0 ? digito : digito * 3;
        }
        return doce + ((10 - suma % 10) % 10);
    }
}
