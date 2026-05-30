package com.garden_bot.util;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Clase utilitaria encargada de detectar y extraer URLs soportadas de un mensaje de texto.
 * Utiliza un patrón regex para encontrar URLs y las filtra según una lista de dominios
 * soportados como Twitter, Instagram, Pinterest, YouTube y TikTok.
 */
public class URLDetector {

    // Dominios soportados
    private static final List<String> SUPPORTED_DOMAINS = List.of(
            "twitter.com",
            "x.com",
            "pinterest.com",
            "instagram.com",
            "tiktok.com",
            "youtube.com",
            "youtu.be"
    );

    // Regex para detectar URLs en un texto
    private static final Pattern URL_PATTERN = Pattern.compile(
            "https?://[\\w\\-._~:/?#\\[\\]@!$&'()*+,;=%]+"
    );

    public List<String> extract(String message) {
        List<String> result = new ArrayList<>();
        Matcher matcher = URL_PATTERN.matcher(message);

        while (matcher.find()) {
            String url = matcher.group();
            if (isSupportedUrl(url)) {
                result.add(url);
            }
        }
        return result;
    }

    // Evaluador de dominios permitidos
    private boolean isSupportedUrl(String url) {
        return SUPPORTED_DOMAINS.stream().anyMatch(url::contains);
    }
}
