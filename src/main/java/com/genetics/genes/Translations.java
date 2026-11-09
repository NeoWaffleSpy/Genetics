package com.genetics.genes;

import com.hypixel.hytale.server.core.modules.i18n.I18nModule;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

/** Resolves translation keys to text on the server, so samples store readable names by value. */
final class Translations {
    private static final String SERVER_PREFIX = "server.";

    private Translations() {
    }

    @Nonnull
    static String resolve(@Nullable String language, @Nullable String key, @Nonnull String fallback) {
        if (key == null || key.isEmpty()) return fallback;

        I18nModule i18n = I18nModule.get();
        String lang = language != null ? language : I18nModule.DEFAULT_LANGUAGE;
        String text = i18n.getMessage(lang, key);
        if (text == null && key.startsWith(SERVER_PREFIX)) text = i18n.getMessage(lang, key.substring(SERVER_PREFIX.length()));
        return text != null && !text.isBlank() ? text : fallback;
    }
}
