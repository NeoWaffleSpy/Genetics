package com.genetics.genome;

import com.genetics.codex.Codex;
import com.genetics.codex.MutationEntry;
import com.hypixel.hytale.server.core.asset.type.entityeffect.config.EntityEffect;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.Locale;

/** Short, player-facing descriptions of genes, e.g. "Max Health +6.5%" or "Speed (2:00)". */
public final class GeneText {
    private GeneText() {
    }

    @Nonnull
    public static String describe(@Nonnull Gene gene, @Nullable String language) {
        return switch (gene) {
            case Gene.Stat stat -> "Max " + prettify(stat.stat()) + " " + (stat.percent() > 0 ? "+" : "") + number(stat.percent()) + "%";
            case Gene.Effect effect -> effectName(effect.effect(), language) + (effect.permanent() ? " (permanent)" : " (" + duration(effect.duration()) + ")");
            case Gene.Mutation mutation -> mutationName(mutation.mutation(), language) + " (mutation)";
        };
    }

    @Nonnull
    public static String effectName(@Nonnull String effectId, @Nullable String language) {
        EntityEffect asset = EntityEffect.getAssetMap().getAsset(effectId);
        return Translations.resolve(language, asset != null ? asset.getName() : null, prettify(effectId));
    }

    @Nonnull
    public static String mutationName(@Nonnull String mutationId, @Nullable String language) {
        MutationEntry entry = Codex.mutation(mutationId);
        return Translations.resolve(language, entry != null ? entry.getName() : null, prettify(mutationId));
    }

    @Nonnull
    public static String duration(double seconds) {
        long total = Math.round(seconds);
        return String.format(Locale.ROOT, "%d:%02d", total / 60, total % 60);
    }

    @Nonnull
    public static String prettify(@Nonnull String id) {
        return id.replace('_', ' ');
    }

    @Nonnull
    public static String number(double value) {
        return value == Math.rint(value) ? String.valueOf((long) value) : String.format(Locale.ROOT, "%.1f", value);
    }
}
