package com.DartsCounter.DartsCounter_backend;

import java.security.SecureRandom;
import java.util.Locale;

// Zugangscodes im Format XXXX-XXXX-XXXX
public final class AccessCodes {
    // Zeichen ohne Verwechslungsgefahr (kein 0/O, 1/I/L)
    static final String ALPHABET = "ABCDEFGHJKMNPQRSTUVWXYZ23456789";
    static final int LENGTH = 12;

    private static final SecureRandom RANDOM = new SecureRandom();

    private AccessCodes() {
    }

    public static String generate() {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < LENGTH; i++) {
            if (i > 0 && i % 4 == 0) sb.append('-');
            sb.append(ALPHABET.charAt(RANDOM.nextInt(ALPHABET.length()))); // nextInt ist gleichverteilt
        }
        return sb.toString();
    }

    // Eingabe tolerant machen: Großbuchstaben, ohne Leerzeichen und Bindestriche
    public static String normalize(String code) {
        if (code == null) return "";
        return code.toUpperCase(Locale.ROOT).replaceAll("[\\s-]", "");
    }
}
