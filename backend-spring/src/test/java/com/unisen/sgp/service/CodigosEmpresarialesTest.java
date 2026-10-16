package com.unisen.sgp.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import java.util.stream.IntStream;
import org.junit.jupiter.api.Test;

class CodigosEmpresarialesTest {

    @Test
    void elPrefijoSaleDelNombreSinTildesNiSignosYEmpiezaPorLetra() {
        assertThat(CodigosEmpresariales.prefijo("NASCAR")).isEqualTo("NASC");
        assertThat(CodigosEmpresariales.prefijo("nascar")).isEqualTo("NASC");
        assertThat(CodigosEmpresariales.prefijo("Ñandú S.A.S.")).isEqualTo("NAND");
        assertThat(CodigosEmpresariales.prefijo("Café Él")).isEqualTo("CAFE");
        assertThat(CodigosEmpresariales.prefijo("3M Colombia")).isEqualTo("MCOL");
        assertThat(CodigosEmpresariales.prefijo("HP")).isEqualTo("HP");
        // Sin dos letras utilizables, un prefijo genérico.
        assertThat(CodigosEmpresariales.prefijo("1234")).isEqualTo("EMP");
        assertThat(CodigosEmpresariales.prefijo("東京")).isEqualTo("EMP");
        assertThat(CodigosEmpresariales.prefijo("")).isEqualTo("EMP");
    }

    @Test
    void proponeElPrimerNumeroLibreDelPrefijo() {
        assertThat(CodigosEmpresariales.primeroLibre("NASC", List.of())).isEqualTo("NASC01");
        assertThat(CodigosEmpresariales.primeroLibre("NASC", List.of("NASC01", "NASC02", "NASCA1"))).isEqualTo("NASC03");
        // Los huecos se reutilizan: el resultado solo depende de lo que hay en la BD.
        assertThat(CodigosEmpresariales.primeroLibre("NASC", List.of("NASC01", "NASC03"))).isEqualTo("NASC02");
        List<String> noventaYNueve = IntStream.rangeClosed(1, 99).mapToObj(n -> "NASC%02d".formatted(n)).toList();
        assertThat(CodigosEmpresariales.primeroLibre("NASC", noventaYNueve)).isEqualTo("NASC100");
        assertThat(CodigosEmpresariales.sugerir("NASCAR", prefijo -> List.of("NASC01"))).isEqualTo("NASC02");
    }

    @Test
    void todoCodigoPropuestoCumpleElFormato() {
        for (String nombre : List.of("NASCAR", "Ñandú", "3M Colombia", "HP", "1234", "a")) {
            assertThat(CodigosEmpresariales.tieneFormatoValido(CodigosEmpresariales.sugerir(nombre, p -> List.of())))
                    .as(nombre).isTrue();
        }
        List<String> todos = IntStream.rangeClosed(1, 9999).mapToObj(n -> "NASC%02d".formatted(n)).toList();
        assertThat(CodigosEmpresariales.tieneFormatoValido("NASC9999")).isTrue();
        assertThatThrownBy(() -> CodigosEmpresariales.primeroLibre("NASC", todos))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void normalizaYValidaElFormato() {
        assertThat(CodigosEmpresariales.normalizar("  nasc01 ")).isEqualTo("NASC01");
        assertThat(CodigosEmpresariales.normalizar(null)).isNull();
        assertThat(CodigosEmpresariales.tieneFormatoValido("NASC01")).isTrue();
        assertThat(CodigosEmpresariales.tieneFormatoValido("ABC")).isTrue();
        for (String invalido : new String[] {null, "", "AB", "1ABC", "nasc01", "NAS-01", "ÑAND01", "ABCDEFGHIJKLM"}) {
            assertThat(CodigosEmpresariales.tieneFormatoValido(invalido)).as(String.valueOf(invalido)).isFalse();
        }
    }
}
