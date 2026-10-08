package com.unisen.sgp.security;

import java.security.SecureRandom;
import java.util.Locale;
import java.util.function.Predicate;

/**
 * Generación y normalización de códigos de acceso: los de invitación (un solo uso) y el
 * código permanente de cada empresa ({@code empresas.codigo_empresa}), con el mismo formato.
 *
 * <p>Formato {@code XXXX-XXXX-XXXX-XXXX} con el alfabeto Base32 de Crockford (sin I, L, O
 * ni U, que se confunden al copiarlos): 16 caracteres × 5 bits = 80 bits de entropía, que
 * hacen inviable adivinar un código por fuerza bruta.
 */
public final class CodigosInvitacion {

    private static final char[] ALFABETO = "0123456789ABCDEFGHJKMNPQRSTVWXYZ".toCharArray();
    private static final int LONGITUD = 16;
    private static final int GRUPO = 4;
    private static final SecureRandom RANDOM = new SecureRandom();
    /** Con 80 bits de entropía una colisión es prácticamente imposible; el límite evita un bucle infinito. */
    private static final int MAX_INTENTOS_GENERACION = 5;

    private CodigosInvitacion() {
    }

    public static String generar() {
        StringBuilder codigo = new StringBuilder(LONGITUD);
        for (int i = 0; i < LONGITUD; i++) {
            codigo.append(ALFABETO[RANDOM.nextInt(ALFABETO.length)]);
        }
        return agrupar(codigo.toString());
    }

    /**
     * Genera un código que no esté en uso. La restricción UNIQUE de la BD sigue siendo la
     * garantía final ante altas simultáneas.
     *
     * @param enUso comprueba si un código ya existe
     */
    public static String generarUnico(Predicate<String> enUso) {
        for (int intento = 0; intento < MAX_INTENTOS_GENERACION; intento++) {
            String codigo = generar();
            if (!enUso.test(codigo)) {
                return codigo;
            }
        }
        throw new IllegalStateException("No se pudo generar un código único.");
    }

    /**
     * Lleva lo que escribe el usuario al formato almacenado: ignora espacios, guiones y
     * minúsculas, y corrige confusiones habituales (O→0, I/L→1).
     * {@code " 7kq2 m9xa-4hpr t3vw "} → {@code "7KQ2-M9XA-4HPR-T3VW"}.
     */
    public static String normalizar(String entrada) {
        if (entrada == null) {
            return "";
        }
        String limpio = entrada.toUpperCase(Locale.ROOT)
                .replaceAll("[^A-Z0-9]", "")
                .replace('O', '0')
                .replace('I', '1')
                .replace('L', '1');
        return limpio.length() == LONGITUD ? agrupar(limpio) : limpio;
    }

    private static String agrupar(String codigo) {
        StringBuilder agrupado = new StringBuilder(LONGITUD + LONGITUD / GRUPO);
        for (int i = 0; i < codigo.length(); i += GRUPO) {
            if (i > 0) {
                agrupado.append('-');
            }
            agrupado.append(codigo, i, i + GRUPO);
        }
        return agrupado.toString();
    }
}
