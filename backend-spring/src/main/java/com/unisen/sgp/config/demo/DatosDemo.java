package com.unisen.sgp.config.demo;

import java.util.List;

/**
 * Catálogo ficticio de las cinco empresas de demostración. Todo es inventado: nombres, NIT,
 * proveedores, códigos de barras (prefijo 200-299, reservado a uso interno en GS1, que nunca
 * coincide con un producto real) y la clasificación del IVA, que es solo de demostración y no una
 * clasificación tributaria validada.
 */
final class DatosDemo {

    private DatosDemo() {
    }

    record Persona(String usuario, String nombres, String apellidos) {
    }

    /**
     * @param costo        costo unitario del inventario inicial (null = sin costo conocido)
     * @param precioVenta  precio de venta sin IVA
     * @param tratamiento  GRAVADO, EXENTO o EXCLUIDO (ficticio)
     * @param stock        unidades iniciales (0 = agotado)
     */
    record Producto(String sku, String nombre, String precioCompra, String costo, int stock, int stockMinimo,
                    String precioVenta, String tratamiento, String tarifa) {
    }

    record Proveedor(String nit, String razonSocial, String email, String telefono, String direccion) {
    }

    /**
     * @param activa      false = empresa desactivada al crearla (sus usuarios no pueden entrar)
     * @param escenarios  casos de prueba adicionales (solicitudes, invitaciones, estados...); null si no tiene
     */
    record Empresa(String codigo, String nombre, String nit, Persona administrador, List<Persona> trabajadores,
                   List<Proveedor> proveedores, List<Producto> productos, boolean activa, Escenarios escenarios) {

        Empresa(String codigo, String nombre, String nit, Persona administrador, List<Persona> trabajadores,
                List<Proveedor> proveedores, List<Producto> productos) {
            this(codigo, nombre, nit, administrador, trabajadores, proveedores, productos, true, null);
        }
    }

    /**
     * Escenarios completos de prueba (ver {@link EscenariosDemo}). El proveedor y los productos son
     * los mismos (NIT, SKU y código de barras) en todas las empresas que los usan: sirven para
     * comprobar que esas claves son únicas por empresa y que cada una ve solo los suyos.
     *
     * @param pendiente  se registra con el código de la empresa y queda PENDIENTE
     * @param rechazado  se registra con el código de la empresa y el administrador lo rechaza
     * @param inactivo   cuenta activa que el administrador desactiva
     * @param invitado   se registra con una invitación (que queda USADA)
     */
    record Escenarios(Persona pendiente, Persona rechazado, Persona inactivo, Persona invitado, Proveedor proveedor,
                      Proveedor proveedorDeBaja, List<Producto> productos, Producto productoDeBaja) {
    }

    /** Proveedor y productos de los escenarios, idénticos en DEMO01 y DEMO02 (ver {@link Escenarios}). */
    static final Proveedor PROVEEDOR_ESCENARIOS = new Proveedor("800999001-1", "Suministros Compartidos S.A.S. (demo)",
            "pedidos@compartidos-demo.test", "6015559001", "Carrera 9 # 9-99, Bogotá");

    /**
     * Stock final esperado: ESC-COM-01 y ESC-COM-02 por encima del mínimo (reciben compras),
     * ESC-MIN-IG exactamente en el mínimo y ESC-MIN-BA por debajo (ambos CRITICO, sin agotarse).
     */
    static final List<Producto> PRODUCTOS_ESCENARIOS = List.of(
            new Producto("ESC-COM-01", "Producto compartido A (demo)", "5000", "5000", 40, 5, null, null, null),
            new Producto("ESC-COM-02", "Producto compartido B (demo)", "7000", "7000", 30, 5, null, null, null),
            new Producto("ESC-MIN-IG", "Producto en el stock mínimo (demo)", "4000", "4000", 6, 6, null, null, null),
            new Producto("ESC-MIN-BA", "Producto bajo el stock mínimo (demo)", "3000", "3000", 2, 10, null, null, null));

    static final Producto PRODUCTO_DE_BAJA = new Producto("ESC-BAJA-01", "Producto descontinuado (demo)", "2000", null, 0, 0,
            null, null, null);

    static final List<Empresa> EMPRESAS = List.of(
            new Empresa("DEMO01", "Ferretería El Tornillo Feliz (demo)", "900990001-1",
                    new Persona("demo1.admin", "Camila", "Rojas Demo"),
                    List.of(new Persona("demo1.ana", "Ana", "Pérez Demo"), new Persona("demo1.luis", "Luis", "Gómez Demo"),
                            new Persona("demo1.marta", "Marta", "Díaz Demo")),
                    List.of(new Proveedor("800990101-1", "Herrajes Ficticios S.A.S. (demo)", "ventas@herrajes-demo.test",
                                    "6015550101", "Calle Falsa 123, Bogotá"),
                            new Proveedor("800990102-2", "Pinturas Imaginarias Ltda. (demo)", "pedidos@pinturas-demo.test",
                                    "6015550102", null)),
                    List.of(new Producto("FER-TOR-08", "Tornillo hexagonal 8 mm (caja x50)", "9000", "9000", 40, 5,
                                    "12500", "GRAVADO", "19"),
                            new Producto("FER-MAR-16", "Martillo de uña 16 oz", "21000", "21000", 15, 3, "32000", "GRAVADO", "19"),
                            new Producto("FER-CIN-05", "Cinta métrica 5 m", "8000", "8000", 25, 5, "11900", "GRAVADO", "19"),
                            new Producto("FER-PIN-GL", "Pintura blanca galón", "38000", "38000", 12, 4, "52000", "GRAVADO", "19"),
                            new Producto("FER-BRO-02", "Brocha 2 pulgadas", "3500", "3500", 30, 6, "3200", "GRAVADO", "19"),
                            new Producto("FER-GUA-NI", "Guantes de nitrilo (par)", "2500", "2500", 50, 10, "4200", "GRAVADO", "5"),
                            new Producto("FER-LIJ-80", "Lija grano 80", "900", "900", 0, 10, "1500", "GRAVADO", "19"),
                            new Producto("FER-CAN-01", "Candado de seguridad 40 mm", "15000", "15000", 8, 2, "23000", "GRAVADO", "19")),
                    true, escenarios(1)),
            new Empresa("DEMO02", "Minimercado La Canasta (demo)", "900990002-2",
                    new Persona("demo2.admin", "Jorge", "Herrera Demo"),
                    List.of(new Persona("demo2.sara", "Sara", "López Demo"), new Persona("demo2.pablo", "Pablo", "Ruiz Demo"),
                            new Persona("demo2.nora", "Nora", "Castro Demo")),
                    List.of(new Proveedor("800990201-1", "Distribuidora Granos del Sur (demo)", "granos@sur-demo.test",
                                    "6025550201", "Carrera 1 # 2-3, Cali"),
                            new Proveedor("800990202-2", "Lácteos La Vaquita (demo)", "pedidos@vaquita-demo.test", null, null)),
                    List.of(new Producto("MER-ARR-500", "Arroz blanco 500 g", "2100", "2100", 80, 20, "2700", "EXCLUIDO", "0"),
                            new Producto("MER-LEC-1L", "Leche entera 1 L", "3400", "3400", 60, 15, "4200", "EXENTO", "0"),
                            new Producto("MER-HUE-30", "Huevos (cubeta x30)", "14000", "14000", 20, 5, "17500", "EXENTO", "0"),
                            new Producto("MER-ACE-1L", "Aceite vegetal 1 L", "8200", "8200", 30, 8, "9900", "GRAVADO", "5"),
                            new Producto("MER-GAS-15", "Gaseosa 1,5 L", "3300", "3300", 45, 12, "4300", "GRAVADO", "19"),
                            new Producto("MER-GAL-PQ", "Galletas surtidas paquete", "2600", "2600", 40, 10, "2500", "GRAVADO", "19"),
                            new Producto("MER-JAB-3U", "Jabón de baño x3", "6500", null, 25, 6, "8900", "GRAVADO", "19"),
                            new Producto("MER-CAF-250", "Café molido 250 g", "9800", "9800", 0, 8, "13500", "GRAVADO", "5")),
                    true, escenarios(2)),
            new Empresa("DEMO03", "Papelería Punto y Coma (demo)", "900990003-3",
                    new Persona("demo3.admin", "Valentina", "Mora Demo"),
                    List.of(new Persona("demo3.diego", "Diego", "Suárez Demo"), new Persona("demo3.laura", "Laura", "Vega Demo")),
                    List.of(new Proveedor("800990301-1", "Papeles y Cartones Inventados (demo)", "ventas@papeles-demo.test",
                                    "6045550301", "Avenida Siempre Viva 742, Medellín")),
                    List.of(new Producto("PAP-CUA-100", "Cuaderno cuadriculado 100 hojas", "3800", "3800", 60, 15, "5500", "EXENTO", "0"),
                            new Producto("PAP-LAP-HB", "Lápiz HB", "500", "500", 200, 40, "900", "GRAVADO", "19"),
                            new Producto("PAP-BOL-NE", "Bolígrafo negro", "900", "900", 150, 30, "1500", "GRAVADO", "19"),
                            new Producto("PAP-RES-500", "Resma papel carta 500 hojas", "16000", "16000", 25, 5, "21900", "GRAVADO", "19"),
                            new Producto("PAP-CAL-CI", "Calculadora científica", "38000", "38000", 6, 2, "35000", "GRAVADO", "19"),
                            new Producto("PAP-PEG-ST", "Pegante en barra", "1800", "1800", 70, 15, "2900", "GRAVADO", "19"),
                            new Producto("PAP-MAR-PE", "Marcador permanente", "1600", "1600", 0, 20, "2600", "GRAVADO", "19"))),
            new Empresa("DEMO04", "Droguería Bienestar Total (demo)", "900990004-4",
                    new Persona("demo4.admin", "Andrés", "Salazar Demo"),
                    List.of(new Persona("demo4.julia", "Julia", "Ortiz Demo"), new Persona("demo4.mateo", "Mateo", "Ríos Demo"),
                            new Persona("demo4.elena", "Elena", "Cano Demo")),
                    List.of(new Proveedor("800990401-1", "Laboratorios Ficción (demo)", "pedidos@ficcion-demo.test",
                                    "6015550401", "Zona Industrial 45, Bogotá")),
                    List.of(new Producto("DRO-ACE-500", "Acetaminofén 500 mg x10 (demo)", "2200", "2200", 100, 20, "3500", "EXCLUIDO", "0"),
                            new Producto("DRO-ALC-250", "Alcohol antiséptico 250 ml", "3100", "3100", 40, 10, "4500", "GRAVADO", "19"),
                            new Producto("DRO-TAP-50", "Tapabocas x50", "9000", "9000", 20, 5, "13900", "GRAVADO", "19"),
                            new Producto("DRO-CRE-DE", "Crema dental 90 g", "4200", "4200", 35, 8, "5900", "GRAVADO", "19"),
                            new Producto("DRO-VIT-C", "Vitamina C x30 (demo)", "12000", "12000", 15, 4, "11000", "GRAVADO", "19"),
                            new Producto("DRO-PAN-AD", "Pañales etapa 3 x30", "28000", "28000", 0, 5, "36900", "GRAVADO", "5"))),
            new Empresa("DEMO05", "Deportes Gol de Oro (demo)", "900990005-5",
                    new Persona("demo5.admin", "Lucía", "Navarro Demo"),
                    List.of(new Persona("demo5.tomas", "Tomás", "Peña Demo"), new Persona("demo5.isabel", "Isabel", "Mejía Demo")),
                    List.of(new Proveedor("800990501-1", "Textiles Deportivos Irreales (demo)", "ventas@irreales-demo.test",
                                    "6055550501", "Calle 50 # 50-50, Barranquilla"),
                            new Proveedor("800990502-2", "Balones Imaginarios S.A.S. (demo)", "info@balones-demo.test", null, null)),
                    List.of(new Producto("DEP-BAL-F5", "Balón de fútbol número 5", "45000", "45000", 18, 4, "69900", "GRAVADO", "19"),
                            new Producto("DEP-CAM-M", "Camiseta deportiva talla M", "22000", "22000", 30, 6, "39900", "GRAVADO", "19"),
                            new Producto("DEP-MED-AL", "Medias deportivas (par)", "6000", "6000", 50, 10, "9900", "EXCLUIDO", "0"),
                            new Producto("DEP-TER-1L", "Termo 1 L", "18000", "18000", 12, 3, "17000", "GRAVADO", "19"),
                            new Producto("DEP-GUA-AR", "Guantes de arquero", "55000", "55000", 0, 2, "84900", "GRAVADO", "19"),
                            new Producto("DEP-CON-6", "Conos de entrenamiento x6", "12000", "12000", 20, 4, "19900", "GRAVADO", "5"))),
            // Empresa desactivada: ni su administrador ni sus trabajadores pueden iniciar sesión.
            new Empresa("DEMO06", "Distribuidora Cerrada (demo)", "900990006-6",
                    new Persona("demo6.admin", "Hernán", "Vargas Demo"),
                    List.of(new Persona("demo6.carlos", "Carlos", "Muñoz Demo")),
                    List.of(), List.of(), false, null));

    private static Escenarios escenarios(int empresa) {
        String u = "demo" + empresa + ".";
        return new Escenarios(new Persona(u + "pendiente", "Paula", "Prieto Demo"),
                new Persona(u + "rechazado", "Raúl", "Reyes Demo"),
                new Persona(u + "inactivo", "Inés", "Ibarra Demo"),
                new Persona(u + "invitado", "Iván", "Torres Demo"),
                PROVEEDOR_ESCENARIOS,
                new Proveedor("800999" + empresa + "02-2", "Proveedor Retirado " + empresa + " (demo)",
                        "retirado" + empresa + "@retirado-demo.test", null, null),
                PRODUCTOS_ESCENARIOS, PRODUCTO_DE_BAJA);
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
