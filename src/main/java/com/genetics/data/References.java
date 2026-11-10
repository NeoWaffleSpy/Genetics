package com.genetics.data;

import com.hypixel.hytale.codec.validation.ValidationResults;
import com.hypixel.hytale.server.core.asset.type.entityeffect.config.EntityEffect;
import com.hypixel.hytale.server.core.modules.entitystats.asset.EntityStatType;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

/**
 * Checks that data files only reference vanilla stats and effects that exist. The asset stores load after the vanilla
 * stats and effects, so the checks run as each file is decoded, including on hot reload. (Hytale's late key
 * validators only run in a full validation pass, so they don't catch typos while editing.)
 */
final class References {
    private References() {
    }

    static void checkStat(@Nullable String statId, @Nonnull ValidationResults results) {
        if (statId != null && EntityStatType.getAssetMap().getAsset(statId) == null) {
            results.fail("Unknown stat '" + statId + "'.");
        }
    }

    static void checkEffect(@Nullable String effectId, @Nonnull ValidationResults results) {
        if (effectId != null && EntityEffect.getAssetMap().getAsset(effectId) == null) {
            results.fail("Unknown effect '" + effectId + "'.");
        }
    }
}
