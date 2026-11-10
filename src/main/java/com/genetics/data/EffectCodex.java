package com.genetics.data;

import com.hypixel.hytale.logger.HytaleLogger;

import javax.annotation.Nonnull;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Lookups into the Effect Codex (GDD §3.2): stat weights and effect Complexity values.
 * Missing entries fall back to a default and log a warning once, so new or modded stats and effects still work.
 */
public final class EffectCodex {
    private static final HytaleLogger LOGGER = HytaleLogger.forEnclosingClass();
    /** Used for stats missing from the Codex. */
    public static final double DEFAULT_STAT_WEIGHT = 1.0;

    private static final Set<String> warned = ConcurrentHashMap.newKeySet();

    private EffectCodex() {
    }

    /** Complexity added by each percent of modifier on {@code statId}. */
    public static double statWeight(@Nonnull String statId) {
        StatWeight entry = StatWeight.getAssetMap().getAsset(statId);
        if (entry != null) return entry.getWeightPerPercent();
        warnOnce("stat:" + statId, "Stat '%s' has no entry in Server/%s, using weight %s.", statId, StatWeight.PATH, DEFAULT_STAT_WEIGHT);
        return DEFAULT_STAT_WEIGHT;
    }

    /** Complexity of {@code effectId} in its temporary or permanent form. */
    public static double effectComplexity(@Nonnull String effectId, boolean permanent) {
        EffectComplexity entry = EffectComplexity.getAssetMap().getAsset(effectId);
        if (entry != null) return entry.get(permanent);
        warnOnce("effect:" + effectId, "Effect '%s' has no entry in Server/%s, using '%s'.", effectId, EffectComplexity.PATH, EffectComplexity.DEFAULT_ID);
        EffectComplexity fallback = EffectComplexity.getAssetMap().getAsset(EffectComplexity.DEFAULT_ID);
        return fallback != null ? fallback.get(permanent) : 0.0;
    }

    private static void warnOnce(@Nonnull String key, @Nonnull String format, Object... args) {
        if (warned.add(key)) LOGGER.atWarning().log(String.format(format, args));
    }
}
