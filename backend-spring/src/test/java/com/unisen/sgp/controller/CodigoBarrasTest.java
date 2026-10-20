package com.unisen.sgp.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.unisen.sgp.model.entity.Empresa;
import com.unisen.sgp.model.entity.Rol;
import com.unisen.sgp.support.ComprasApiTest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * Código de barras y búsqueda por escáner (Entrega D): texto que conserva los ceros a la
 * izquierda, único por empresa, y búsqueda siempre dentro de la empresa del contexto (sin
 * aceptar un empresa_id del cliente).
 */
class CodigoBarrasTest extends ComprasApiTest {

    private static final String PRODUCTOS = "/api/v1/productos";
    private static final String BUSCAR = PRODUCTOS + "/buscar?codigo=";
    private static final String EAN = "0070330506220";

    private long proveedor;

    @BeforeEach
    void prepararProveedor() throws Exception {
        proveedor = crearProveedor("900111111-1", "Aceros Andinos");
    }

    private String producto(String sku, String codigoBarras) {
        String codigo = codigoBarras == null ? "null" : "\"" + codigoBarras + "\"";
        return """
                {"sku": "%s", "codigoBarras": %s, "nombre": "Producto %s", "precio": 1000, "proveedorId": %d}
                """.formatted(sku, codigo, sku, proveedor);
    }

    @Test
    void conservaLosCerosALaIzquierdaYEsOpcional() throws Exception {
        postJson(PRODUCTOS, adminToken, producto("EAN-01", "  " + EAN + "  "))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.codigoBarras").value(EAN))
                .andExpect(jsonPath("$.stockMinimo").value(0));
        postJson(PRODUCTOS, adminToken, producto("SIN-01", "   "))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.codigoBarras").doesNotExist());
        postJson(PRODUCTOS, adminToken, producto("SIN-02", null)).andExpect(status().isCreated());
        assertThat(jdbcTemplate.queryForObject("SELECT codigo_barras FROM productos WHERE sku = 'EAN-01'", String.class))
                .isEqualTo(EAN);
    }

    @Test
    void esUnicoPorEmpresaYValidaElFormato() throws Exception {
        postJson(PRODUCTOS, adminToken, producto("EAN-01", EAN)).andExpect(status().isCreated());
        postJson(PRODUCTOS, adminToken, producto("EAN-02", EAN))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.errors.codigoBarras").exists());
        postJson(PRODUCTOS, adminToken, producto("EAN-03", "con espacio"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.codigoBarras").exists());
        postJson(PRODUCTOS, adminToken, producto("EAN-04", "1".repeat(65))).andExpect(status().isBadRequest());

        // Otra empresa puede usar el mismo código (otro catálogo).
        Empresa norte = crearEmpresa("Ferretería Norte", "800000002-2");
        String adminNorte = crearUsuarioYEntrar("admin.norte", Rol.ADMINISTRADOR_COMERCIAL, norte);
        long proveedorNorte = leer(postJson("/api/v1/proveedores", adminNorte,
                "{\"nit\": \"900333333-3\", \"razonSocial\": \"Norteños\", \"email\": \"n@n.com\"}")).get("id").asLong();
        postJson(PRODUCTOS, adminNorte, """
                {"sku": "NOR-01", "codigoBarras": "%s", "nombre": "Cable", "precio": 10, "proveedorId": %d}
                """.formatted(EAN, proveedorNorte)).andExpect(status().isCreated());
    }

    @Test
    void buscaPorCodigoDeBarrasYDespuesPorSkuConSusExistencias() throws Exception {
        long id = leer(postJson(PRODUCTOS, adminToken, producto("TOR-01", EAN))).get("id").asLong();
        ajustar(adminToken, id, 12, "Conteo").andExpect(status().isCreated());

        getJson(BUSCAR + EAN, usuarioToken)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.producto.id").value(id))
                .andExpect(jsonPath("$.producto.codigoBarras").value(EAN))
                .andExpect(jsonPath("$.existencias").value(12))
                .andExpect(jsonPath("$.estadoStock").value("NORMAL"))
                .andExpect(jsonPath("$.coincidePor").value("CODIGO_BARRAS"));
        // Tecleado: el SKU se normaliza a mayúsculas.
        getJson(BUSCAR + "tor-01", usuarioToken)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.coincidePor").value("SKU"));
        // Sin los ceros a la izquierda ya no es el mismo código.
        getJson(BUSCAR + EAN.substring(2), usuarioToken)
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.detail").value("Ningún producto tiene el código «" + EAN.substring(2) + "»."));
        getJson(BUSCAR, usuarioToken).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.codigo").exists());
        getJson(BUSCAR + "1".repeat(65), usuarioToken).andExpect(status().isBadRequest());
    }

    @Test
    void elCodigoDeBarrasTienePrioridadYUnProductoDadoDeBajaSeIndica() throws Exception {
        // El código de barras de uno coincide con el SKU de otro: gana el código de barras.
        long porCodigo = leer(postJson(PRODUCTOS, adminToken, producto("AAA-01", "BBB-02"))).get("id").asLong();
        leer(postJson(PRODUCTOS, adminToken, producto("BBB-02", null)));
        getJson(BUSCAR + "BBB-02", adminToken)
                .andExpect(jsonPath("$.producto.id").value(porCodigo))
                .andExpect(jsonPath("$.coincidePor").value("CODIGO_BARRAS"));
        // Si el de código de barras se da de baja, se encuentra el activo por SKU.
        deleteJson(PRODUCTOS + "/" + porCodigo, adminToken).andExpect(status().isNoContent());
        getJson(BUSCAR + "BBB-02", adminToken)
                .andExpect(jsonPath("$.producto.sku").value("BBB-02"))
                .andExpect(jsonPath("$.coincidePor").value("SKU"));
        // Solo coincide uno dado de baja: 409 con su nombre, no "no existe".
        long baja = leer(postJson(PRODUCTOS, adminToken, producto("BAJ-01", "7701234567890"))).get("id").asLong();
        deleteJson(PRODUCTOS + "/" + baja, adminToken).andExpect(status().isNoContent());
        getJson(BUSCAR + "7701234567890", adminToken)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.detail").value("«Producto BAJ-01» (SKU BAJ-01) tiene ese código, pero fue dado de baja."));
        // El código del producto dado de baja sigue reservado (como el SKU).
        postJson(PRODUCTOS, adminToken, producto("NUE-01", "7701234567890")).andExpect(status().isConflict());
    }

    @Test
    void laBusquedaNuncaSaleDeLaEmpresaDelContexto() throws Exception {
        postJson(PRODUCTOS, adminToken, producto("TOR-01", EAN)).andExpect(status().isCreated());
        Empresa norte = crearEmpresa("Ferretería Norte", "800000002-2");
        String trabajadorNorte = crearUsuarioYEntrar("trabajador.norte", Rol.TRABAJADOR, norte);
        // Otra empresa: no existe para ella, ni por código de barras ni por SKU.
        getJson(BUSCAR + EAN, trabajadorNorte).andExpect(status().isNotFound());
        getJson(BUSCAR + "TOR-01", trabajadorNorte).andExpect(status().isNotFound());
        // Un parámetro empresaId inventado se ignora: sigue siendo la empresa del token.
        getJson(BUSCAR + EAN + "&empresaId=" + empresa.getId(), trabajadorNorte).andExpect(status().isNotFound());

        // SUPER_ADMIN: solo supervisando una empresa; en modo global no busca en todas.
        String superAdmin = crearUsuarioYEntrar("plataforma", Rol.SUPER_ADMIN, null);
        getGlobal(BUSCAR + EAN, superAdmin)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.title").value("Empresa no seleccionada"));
        getEnEmpresa(BUSCAR + EAN, superAdmin, empresa.getId()).andExpect(status().isOk());
        getEnEmpresa(BUSCAR + EAN, superAdmin, norte.getId()).andExpect(status().isNotFound());
    }
}
