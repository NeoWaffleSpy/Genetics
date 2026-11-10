package com.genetics.codex;

import com.hypixel.hytale.assetstore.map.DefaultAssetMap;
import com.hypixel.hytale.assetstore.map.JsonAssetWithMap;
import com.hypixel.hytale.logger.HytaleLogger;
import com.hypixel.hytale.server.core.asset.HytaleAssetStore;
import com.hypixel.hytale.server.core.asset.type.entityeffect.config.EntityEffect;
import com.hypixel.hytale.server.core.modules.entitystats.asset.EntityStatType;
import com.hypixel.hytale.server.core.plugin.registry.AssetRegistry;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;

/**
 * Entry point to the mod's data files. They are registered as Hytale asset types, so other asset packs and mods can
 * add or override entries, and edits are picked up on asset reload:
 * <pre>
 * Server/Genetics/EffectCodex/Stats/&lt;Stat&gt;.json      stat weights        ({@link StatEntry})
 * Server/Genetics/EffectCodex/Effects/&lt;Effect&gt;.json  effect Complexity   ({@link EffectEntry})
 * Server/Genetics/MutationCodex/&lt;Mutation&gt;.json      mutations           ({@link MutationEntry})
 * Server/Genetics/MobProfiles/&lt;Profile&gt;.json         mob gene profiles   ({@link MobGeneProfile})
 * </pre>
 */
public final class Codex {
    /** Entry id used as the fallback in the stat, effect and profile folders. */
    public static final String DEFAULT_ID = "Default";

    private static final HytaleLogger LOGGER = HytaleLogger.forEnclosingClass();
    private static final Set<String> WARNED = ConcurrentHashMap.newKeySet();

    private Codex() {
    }

    public static void register(@Nonnull AssetRegistry registry) {
        registry.register(store(StatEntry.class, StatEntry.PATH, StatEntry.CODEC, StatEntry::getId));
        registry.register(store(EffectEntry.class, EffectEntry.PATH, EffectEntry.CODEC, EffectEntry::getId));
        registry.register(store(MutationEntry.class, MutationEntry.PATH, MutationEntry.CODEC, MutationEntry::getId));
        registry.register(store(MobGeneProfile.class, MobGeneProfile.PATH, MobGeneProfile.CODEC, MobGeneProfile::getId));
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    @Nonnull
    private static <T extends JsonAssetWithMap<String, DefaultAssetMap<String, T>>> HytaleAssetStore<String, T, DefaultAssetMap<String, T>> store(
            @Nonnull Class<T> type, @Nonnull String path,
            @Nonnull com.hypixel.hytale.assetstore.codec.AssetCodec<String, T> codec, @Nonnull Function<T, String> key
    ) {
        HytaleAssetStore.Builder builder = HytaleAssetStore.builder(type, new DefaultAssetMap<String, T>());
        builder.setPath(path);
        builder.setCodec(codec);
        builder.setKeyFunction(key);
        return (HytaleAssetStore<String, T, DefaultAssetMap<String, T>>) builder.build();
    }

    // ---- Lookups ----

    /** Complexity per percent of a stat, falling back to the Default entry, then to 0. */
    public static double statWeight(@Nonnull String stat) {
        StatEntry entry = StatEntry.getAssetMap().getAsset(stat);
        if (entry == null) {
            warnOnce("stat:" + stat, "No Effect Codex entry for stat '" + stat + "', using " + DEFAULT_ID + ".");
            entry = StatEntry.getAssetMap().getAsset(DEFAULT_ID);
        }
        return entry != null ? entry.getWeightPerPercent() : 0;
    }

    /** Complexity of an effect gene, falling back to the Default entry, then to 0. */
    public static double effectComplexity(@Nonnull String effect, boolean permanent) {
        EffectEntry entry = EffectEntry.getAssetMap().getAsset(effect);
        if (entry == null) {
            warnOnce("effect:" + effect, "No Effect Codex entry for effect '" + effect + "', using " + DEFAULT_ID + ".");
            entry = EffectEntry.getAssetMap().getAsset(DEFAULT_ID);
        }
        return entry != null ? entry.getComplexity(permanent) : 0;
    }

    @Nullable
    public static MutationEntry mutation(@Nonnull String id) {
        return MutationEntry.getAssetMap().getAsset(id);
    }

    /** The profile that lists this NPC role (or is named after it), else the Default profile, else null. */
    @Nullable
    public static MobGeneProfile profileFor(@Nonnull String role) {
        DefaultAssetMap<String, MobGeneProfile> profiles = MobGeneProfile.getAssetMap();
        MobGeneProfile named = profiles.getAsset(role);
        if (named != null) return named;
        for (MobGeneProfile profile : profiles.getAssetMap().values()) {
            for (String listed : profile.getRoles()) {
                if (listed.equals(role)) return profile;
            }
        }
        return profiles.getAsset(DEFAULT_ID);
    }

    // ---- Validation ----

    /** Logs what was loaded and warns about entries that reference unknown stats or effects. Call after assets load. */
    public static void validate() {
        int problems = 0;
        for (StatEntry stat : StatEntry.getAssetMap().getAssetMap().values()) {
            if (!stat.getId().equals(DEFAULT_ID)) problems += checkStat(stat.getId(), "Effect Codex stat entry");
        }
        for (EffectEntry effect : EffectEntry.getAssetMap().getAssetMap().values()) {
            if (!effect.getId().equals(DEFAULT_ID)) problems += checkEffect(effect.getId(), "Effect Codex effect entry");
        }
        for (MutationEntry mutation : MutationEntry.getAssetMap().getAssetMap().values()) {
            String where = "mutation " + mutation.getId();
            for (String stat : mutation.getStats().keySet()) problems += checkStat(stat, where);
            for (String effect : mutation.getEffects()) problems += checkEffect(effect, where);
        }
        for (MobGeneProfile profile : MobGeneProfile.getAssetMap().getAssetMap().values()) {
            String where = "mob profile " + profile.getId();
            for (MobGeneProfile.StatRoll roll : profile.getStatPool()) {
                problems += checkStat(roll.getStat(), where);
                if (roll.getMin() > roll.getMax()) problems += warn(where + ": stat " + roll.getStat() + " has Min > Max.");
            }
            for (MobGeneProfile.Drawback drawback : profile.getDrawbacks()) {
                problems += checkStat(drawback.getStat(), where);
                if (drawback.getMin() > drawback.getMax()) problems += warn(where + ": drawback " + drawback.getStat() + " has Min > Max.");
            }
            if (profile.getTemporaryEffect() != null) problems += checkEffect(profile.getTemporaryEffect().getId(), where);
            for (MobGeneProfile.EffectRoll effect : profile.getPermanentEffects()) problems += checkEffect(effect.getId(), where);
        }
        if (StatEntry.getAssetMap().getAsset(DEFAULT_ID) == null) problems += warn("Effect Codex has no " + DEFAULT_ID + " stat entry.");
        if (EffectEntry.getAssetMap().getAsset(DEFAULT_ID) == null) problems += warn("Effect Codex has no " + DEFAULT_ID + " effect entry.");

        LOGGER.atInfo().log("Genetics data loaded: %d stat weights, %d effects, %d mutations, %d mob profiles, %d problem(s).",
                StatEntry.getAssetMap().getAssetMap().size(),
                EffectEntry.getAssetMap().getAssetMap().size(),
                MutationEntry.getAssetMap().getAssetMap().size(),
                MobGeneProfile.getAssetMap().getAssetMap().size(),
                problems);
    }

    private static int checkStat(@Nullable String stat, @Nonnull String where) {
        if (stat == null) return warn(where + ": missing stat id.");
        return EntityStatType.getAssetMap().getAsset(stat) == null ? warn(where + ": unknown stat '" + stat + "'.") : 0;
    }

    private static int checkEffect(@Nullable String effect, @Nonnull String where) {
        if (effect == null) return warn(where + ": missing effect id.");
        return EntityEffect.getAssetMap().getAsset(effect) == null ? warn(where + ": unknown effect '" + effect + "'.") : 0;
    }

    private static int warn(@Nonnull String message) {
        LOGGER.atWarning().log(message);
        return 1;
    }

    private static void warnOnce(@Nonnull String key, @Nonnull String message) {
        if (WARNED.add(key)) LOGGER.atWarning().log(message);
    }
}
