package com.unisen.sgp.service;

import com.unisen.sgp.model.dto.Patrones;
import java.text.Normalizer;
import java.util.Collection;
import java.util.HashSet;
import java.util.Locale;
import java.util.Set;
import java.util.function.Function;
import java.util.regex.Pattern;

/**
 * Código empresarial ({@code empresas.codigo}): identificador legible y estable de cada
 * empresa, como {@code NASC01}. No es aleatorio ni secreto, a diferencia del código de registro
 * de trabajadores ({@code CodigosInvitacion}).
 *
 * <p>Propuesta determinista: un prefijo de hasta 4 letras tomado del nombre (sin tildes ni
 * signos) y el primer número libre para ese prefijo según la BD: NASCAR → {@code NASC01}; si ya
 * existe, {@code NASC02}... Un simple recorte del nombre colisionaría (NASCAR y NASCIMENTO); el
 * número lo evita. La restricción UNIQUE de la BD es la garantía final ante altas simultáneas.
 */
public final class CodigosEmpresariales {

    private static final Pattern FORMATO = Pattern.compile(Patrones.CODIGO_EMPRESARIAL);
    private static final Pattern MARCAS_DIACRITICAS = Pattern.compile("\\p{M}+");
    private static final int LONGITUD_PREFIJO = 4;
    /** Prefijo para nombres sin al menos dos letras utilizables (p. ej. solo cifras o símbolos). */
    private static final String PREFIJO_POR_DEFECTO = "EMP";
    /** 01..99 con dos cifras; después, 100..9999. Con un prefijo de 4 letras caben en 12 caracteres. */
    private static final int MAX_SECUENCIA = 9999;

    private CodigosEmpresariales() {
    }

    /** Lleva lo que se escribe al formato almacenado: sin espacios a los lados y en mayúsculas. */
    public static String normalizar(String entrada) {
        return entrada == null ? null : entrada.strip().toUpperCase(Locale.ROOT);
    }

    /** @param codigo ya normalizado */
    public static boolean tieneFormatoValido(String codigo) {
        return codigo != null && FORMATO.matcher(codigo).matches();
    }

    /**
     * Prefijo del código a partir del nombre comercial: hasta 4 letras o cifras, sin tildes,
     * empezando siempre por letra. "Ñandú S.A.S." → {@code NAND}; "3M Colombia" → {@code MCOL}.
     */
    public static String prefijo(String nombre) {
        String sinTildes = MARCAS_DIACRITICAS.matcher(Normalizer.normalize(nombre == null ? "" : nombre,
                Normalizer.Form.NFD)).replaceAll("");
        String limpio = sinTildes.toUpperCase(Locale.ROOT)
                .replaceAll("[^A-Z0-9]", "")
                .replaceFirst("^[0-9]+", "");
        if (limpio.length() < 2) {
            return PREFIJO_POR_DEFECTO;
        }
        return limpio.substring(0, Math.min(LONGITUD_PREFIJO, limpio.length()));
    }

    /**
     * Primer código {@code prefijo + número} que no está en {@code enUso}.
     *
     * @throws IllegalStateException si el prefijo ya agotó todos sus números
     */
    public static String primeroLibre(String prefijo, Collection<String> enUso) {
        Set<String> usados = new HashSet<>(enUso);
        for (int numero = 1; numero <= MAX_SECUENCIA; numero++) {
            String candidato = prefijo + String.format(Locale.ROOT, "%02d", numero);
            if (!usados.contains(candidato)) {
                return candidato;
            }
        }
        throw new IllegalStateException("No quedan códigos libres con el prefijo " + prefijo + ".");
    }

    /**
     * Propone el código de una empresa nueva.
     *
     * @param codigosConPrefijo códigos existentes que empiezan por el prefijo indicado (consulta a la BD)
     */
    public static String sugerir(String nombre, Function<String, Collection<String>> codigosConPrefijo) {
        String prefijo = prefijo(nombre);
        return primeroLibre(prefijo, codigosConPrefijo.apply(prefijo));
    }
}
