package com.unisen.sgp.controller;

import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.unisen.sgp.support.ApiIntegrationTest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class ProductoControllerTest extends ApiIntegrationTest {

    private static final String URL = "/api/v1/productos";
    private static final String PROVEEDORES = "/api/v1/proveedores";

    private long aceros;
    private long bombas;

    @BeforeEach
    void crearProveedores() throws Exception {
        aceros = crearProveedor("800000001", "Aceros Andinos");
        bombas = crearProveedor("800000002", "Bombas y Válvulas");
    }

    @Test
    void creaProductoConSuProveedor() throws Exception {
        postJson(URL, adminToken, """
                {"sku": " tor-m8-100 ", "nombre": "Tornillo M8", "descripcion": "  ", "precio": 1250.5,
                 "proveedorId": %d}
                """.formatted(aceros))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.sku").value("TOR-M8-100"))
                .andExpect(jsonPath("$.precio").value(1250.50))
                .andExpect(jsonPath("$.descripcion").doesNotExist())
                .andExpect(jsonPath("$.proveedor.id").value(aceros))
                .andExpect(jsonPath("$.proveedor.razonSocial").value("Aceros Andinos"))
                .andExpect(jsonPath("$.proveedor.nit").value("800000001"));
    }

    @Test
    void validaLosCampos() throws Exception {
        postJson(URL, adminToken, """
                {"sku": "a", "nombre": "", "precio": 0}
                """)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.sku").exists())
                .andExpect(jsonPath("$.errors.nombre").value("El nombre es obligatorio."))
                .andExpect(jsonPath("$.errors.precio").value("El precio debe ser mayor que 0."))
                .andExpect(jsonPath("$.errors.proveedorId").value("El proveedor es obligatorio."));

        postJson(URL, adminToken, """
                {"sku": "SKU-1", "nombre": "X", "precio": 10.999, "proveedorId": %d}
                """.formatted(aceros))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.precio").value("El precio admite hasta 12 enteros y 2 decimales."));
    }

    @Test
    void elProveedorDebeExistirYEstarActivo() throws Exception {
        String body = """
                {"sku": "SKU-1", "nombre": "X", "precio": 10, "proveedorId": %d}
                """;
        postJson(URL, adminToken, body.formatted(999_999))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.proveedorId").value("El proveedor seleccionado no existe o fue dado de baja."));

        deleteJson(PROVEEDORES + "/" + bombas, adminToken).andExpect(status().isNoContent());
        postJson(URL, adminToken, body.formatted(bombas))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.proveedorId").exists());
    }

    @Test
    void skuDuplicadoDevuelve409() throws Exception {
        crearProducto("SKU-1", "Original", "10", aceros);

        postJson(URL, adminToken, """
                {"sku": "sku-1", "nombre": "Copia", "precio": 20, "proveedorId": %d}
                """.formatted(bombas))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.errors.sku").value(containsString("Ya existe un producto con este SKU")));
    }

    @Test
    void listadoPaginadoIncluyeProveedorYOrdenaPorCampoAnidado() throws Exception {
        crearProducto("SKU-A", "Válvula", "50", bombas);
        crearProducto("SKU-B", "Ángulo", "20", aceros);
        crearProducto("SKU-C", "Brida", "30", bombas);

        getJson(URL + "?sort=proveedor.razonSocial,asc&sort=nombre,asc", usuarioToken)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.page.totalElements").value(3))
                .andExpect(jsonPath("$.content[0].proveedor.razonSocial").value("Aceros Andinos"))
                .andExpect(jsonPath("$.content[1].nombre").value("Brida"))
                .andExpect(jsonPath("$.content[2].nombre").value("Válvula"));
    }

    @Test
    void actualizaProductoYCambiaDeProveedor() throws Exception {
        long id = crearProducto("SKU-1", "Tornillo", "10", aceros);

        putJson(URL + "/" + id, adminToken, """
                {"sku": "SKU-1", "nombre": "Tornillo galvanizado", "descripcion": "Caja x100",
                 "precio": 12.75, "proveedorId": %d}
                """.formatted(bombas))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.nombre").value("Tornillo galvanizado"))
                .andExpect(jsonPath("$.descripcion").value("Caja x100"))
                .andExpect(jsonPath("$.precio").value(12.75))
                .andExpect(jsonPath("$.proveedor.id").value(bombas));

        getJson(URL + "/" + id, usuarioToken).andExpect(jsonPath("$.proveedor.razonSocial").value("Bombas y Válvulas"));
    }

    @Test
    void unProveedorConProductosActivosNoSePuedeEliminar() throws Exception {
        long producto = crearProducto("SKU-1", "Tornillo", "10", aceros);

        deleteJson(PROVEEDORES + "/" + aceros, adminToken)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.detail").value(containsString("tiene 1 producto activo")));

        // Tras dar de baja el producto, el proveedor ya se puede eliminar.
        deleteJson(URL + "/" + producto, adminToken).andExpect(status().isNoContent());
        getJson(URL + "/" + producto, adminToken).andExpect(status().isNotFound());
        deleteJson(PROVEEDORES + "/" + aceros, adminToken).andExpect(status().isNoContent());
    }

    @Test
    void unUsuarioSinRolAdminSoloPuedeLeer() throws Exception {
        long id = crearProducto("SKU-1", "Tornillo", "10", aceros);

        getJson(URL + "/" + id, usuarioToken).andExpect(status().isOk());
        postJson(URL, usuarioToken, """
                {"sku": "SKU-2", "nombre": "X", "precio": 10, "proveedorId": %d}
                """.formatted(aceros)).andExpect(status().isForbidden());
        deleteJson(URL + "/" + id, usuarioToken).andExpect(status().isForbidden());
    }
}
