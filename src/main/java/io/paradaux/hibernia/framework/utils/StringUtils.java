package io.paradaux.hibernia.framework.utils;

import java.security.SecureRandom;

public class StringUtils {

    private static final String ALPHA_NUMERIC = "0123456789ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz";
    private static final String MM_TAG_REGEX = "<[^>]*>"; // simple, non-greedy MiniMessage tag strip
    private static final SecureRandom RANDOM = new SecureRandom();

    public static String random32() {
        StringBuilder sb = new StringBuilder(32);
        for (int i = 0; i < 32; i++) {
            int idx = RANDOM.nextInt(ALPHA_NUMERIC.length());
            sb.append(ALPHA_NUMERIC.charAt(idx));
        }
        return sb.toString();
    }

    public static String sanitize(String input) {
        if (input == null || input.isBlank()) {
            return "";
        }

        // 1) Remove MiniMessage components <...>
        String noMini = input.replaceAll(MM_TAG_REGEX, "");

        // 2) Keep letters, digits, whitespace, and underscore
        String clean = noMini.replaceAll("[^\\p{IsAlphabetic}\\p{IsDigit}\\s_]", "");

        // 3) Collapse multiple spaces and trim
        return clean.trim().replaceAll("\\s{2,}", " ");
    }

    public static boolean startsWithNumber(String s) {
        return s != null && s.matches("^[0-9].*");
    }

}

