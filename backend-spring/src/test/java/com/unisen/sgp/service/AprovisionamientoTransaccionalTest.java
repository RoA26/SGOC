package com.unisen.sgp.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doReturn;

import com.unisen.sgp.exception.DatoDuplicadoException;
import com.unisen.sgp.model.dto.EmpresaRequestDTO;
import com.unisen.sgp.model.entity.Empresa;
import com.unisen.sgp.model.entity.Rol;
import com.unisen.sgp.model.entity.Usuario;
import com.unisen.sgp.repository.UsuarioRepository;
import com.unisen.sgp.support.BaseDeDatosDePrueba;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;

/**
 * Atomicidad del aprovisionamiento: el fallo llega <em>después</em> de insertar la empresa y
 * el gerente (ambos ya enviados a la BD con flush). Nada debe quedar: ni empresa sin gerente
 * ni gerente huérfano.
 */
@SpringBootTest
@ActiveProfiles("test")
class AprovisionamientoTransaccionalTest {

    @Autowired
    private EmpresaService empresaService;
    @Autowired
    private JdbcTemplate jdbcTemplate;
    @MockitoSpyBean
    private UsuarioService usuarioService;
    @MockitoSpyBean
    private UsuarioRepository usuarioRepository;

    @BeforeEach
    void limpiar() {
        BaseDeDatosDePrueba.vaciar(jdbcTemplate);
    }

    private int contar(String sql) {
        return jdbcTemplate.queryForObject(sql, Integer.class);
    }

    @Test
    @WithMockUser(roles = "SUPER_ADMIN")
    void unFalloTrasCrearAlGerenteDeshaceTambienLaEmpresa() {
        AtomicReference<Usuario> gerenteInsertado = new AtomicReference<>();
        doAnswer(invocacion -> {
            Usuario gerente = (Usuario) invocacion.callRealMethod();
            gerenteInsertado.set(gerente);
            throw new IllegalStateException("Fallo simulado después de insertar al gerente");
        }).when(usuarioService).crearUsuario(anyString(), anyString(), anyString(), anyString(), eq(Rol.GERENTE),
                any(Empresa.class));

        EmpresaRequestDTO request = new EmpresaRequestDTO("Ferretería Andina", "901234567-8",
                new EmpresaRequestDTO.GerenteFundador("gerente.andina", "g@andina.com", "ClaveGerente2026", null));

        assertThatThrownBy(() -> empresaService.crearConGerente(request))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Fallo simulado");

        // El gerente llegó a insertarse (tiene id) dentro de la transacción...
        assertThat(gerenteInsertado.get()).isNotNull();
        assertThat(gerenteInsertado.get().getId()).isNotNull();
        assertThat(gerenteInsertado.get().getEmpresa().getId()).isNotNull();
        // ...pero el rollback se llevó la empresa y el gerente.
        assertThat(contar("SELECT COUNT(*) FROM empresas WHERE nit = '901234567-8'")).isZero();
        assertThat(contar("SELECT COUNT(*) FROM usuarios WHERE username = 'gerente.andina'")).isZero();
        assertThat(contar("SELECT COUNT(*) FROM empresas")).isZero();
        assertThat(contar("SELECT COUNT(*) FROM usuarios")).isZero();
    }

    @Test
    @WithMockUser(roles = "SUPER_ADMIN")
    void unUsernameQueOtraAltaOcupaALaVezSeAsignaAlGerenteYNoQuedaEmpresa() {
        usuarioService.crearUsuario("ocupado", "ocupado@x.com", "Ocupado", "ClaveGerente2026", Rol.SUPER_ADMIN, null);
        // Ventana de la carrera: la comprobación previa no lo ve y la UNIQUE de la BD salta al insertar.
        doReturn(false).when(usuarioRepository).existsByUsername("ocupado");

        EmpresaRequestDTO request = new EmpresaRequestDTO("Ferretería Andina", "901234567-8",
                new EmpresaRequestDTO.GerenteFundador("ocupado", "g@andina.com", "ClaveGerente2026", null));

        assertThatThrownBy(() -> empresaService.crearConGerente(request))
                .isInstanceOfSatisfying(DatoDuplicadoException.class,
                        ex -> assertThat(ex.getCampo()).isEqualTo("gerente.username"));
        assertThat(contar("SELECT COUNT(*) FROM empresas WHERE nit = '901234567-8'")).isZero();
        assertThat(contar("SELECT COUNT(*) FROM usuarios")).isEqualTo(1);
    }

    @Test
    @WithMockUser(roles = "SUPER_ADMIN")
    void sinFallosQuedanAmbos() {
        EmpresaRequestDTO request = new EmpresaRequestDTO("Ferretería Andina", "901234567-8",
                new EmpresaRequestDTO.GerenteFundador("gerente.andina", "g@andina.com", "ClaveGerente2026", null));

        empresaService.crearConGerente(request);

        assertThat(contar("SELECT COUNT(*) FROM empresas WHERE nit = '901234567-8'")).isEqualTo(1);
        assertThat(contar("SELECT COUNT(*) FROM usuarios u JOIN empresas e ON e.id = u.empresa_id "
                + "WHERE u.username = 'gerente.andina' AND u.rol = 'GERENTE' AND u.estado = 'ACTIVO' "
                + "AND e.nit = '901234567-8'")).isEqualTo(1);
    }
}
