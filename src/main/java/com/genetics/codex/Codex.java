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
 * Server/Genetics/SicknessEvents/&lt;Event&gt;.json       sickness events     ({@link SicknessEvent})
 * Server/Genetics/Balance/Default.json              balancing levers    ({@link Balance})
 * Server/Genetics/ComplexityTiers/&lt;Tier&gt;.json      Complexity tiers    ({@link com.genetics.tier.ComplexityTier})
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
        registry.register(store(SicknessEvent.class, SicknessEvent.PATH, SicknessEvent.CODEC, SicknessEvent::getId));
        registry.register(store(Balance.class, Balance.PATH, Balance.CODEC, Balance::getId));
        registry.register(store(com.genetics.tier.ComplexityTier.class, com.genetics.tier.ComplexityTier.PATH,
                com.genetics.tier.ComplexityTier.CODEC, com.genetics.tier.ComplexityTier::getId));
        registry.register(store(com.genetics.research.ResearchNode.class, com.genetics.research.ResearchNode.PATH,
                com.genetics.research.ResearchNode.CODEC, com.genetics.research.ResearchNode::getId));
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
            if (mutation.getMinTier() == null) problems += warn(where + ": has no MinTier, so splicing will never roll it.");
            else if (com.genetics.tier.Tiers.rank(mutation.getMinTier()) < 0) problems += warn(where + ": unknown MinTier '" + mutation.getMinTier() + "'.");
            for (com.genetics.mutation.Behavior behavior : mutation.getBehaviors()) problems += checkActions(behavior.getActions(), where);
        }
        for (com.genetics.tier.ComplexityTier tier : com.genetics.tier.Tiers.all()) {
            for (com.genetics.tier.ComplexityTier.Cost cost : tier.getCosts()) {
                String where = "Complexity tier " + tier.getId();
                if (cost.getScaling() == null) problems += warn(where + ": a cost has no Scaling.");
                if (cost.getItemId() != null && com.hypixel.hytale.server.core.asset.type.item.config.Item.getAssetMap().getAsset(cost.getItemId()) == null) {
                    problems += warn(where + ": unknown item '" + cost.getItemId() + "'.");
                } else if (cost.getItemId() == null && (cost.getResourceTypeId() == null
                        || com.hypixel.hytale.server.core.asset.type.item.config.ResourceType.getAssetMap().getAsset(cost.getResourceTypeId()) == null)) {
                    problems += warn(where + ": a cost has no valid ItemId or ResourceTypeId.");
                }
            }
        }
        if (com.genetics.tier.Tiers.all().isEmpty()) problems += warn("No Complexity tiers: processing and splicing are unavailable.");
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
            if (profile.getSound() != null) problems += checkSound(profile.getSound(), where);
        }
        Balance.Sickness sickness = Balance.get().sickness();
        for (Balance.Band band : sickness.getBands()) {
            if (band.getEffect() != null) problems += checkEffect(band.getEffect(), "sickness band " + band.getId());
        }
        if (EntityStatType.getAssetMap().getAsset(com.genetics.sickness.Sickness.STAT_ID) == null) {
            problems += warn("Missing the " + com.genetics.sickness.Sickness.STAT_ID + " entity stat.");
        }
        if (EntityStatType.getAssetMap().getAsset(com.genetics.capacity.GenomeCapacity.STAT_ID) == null) {
            problems += warn("Missing the " + com.genetics.capacity.GenomeCapacity.STAT_ID + " entity stat.");
        }
        if (com.hypixel.hytale.server.core.asset.type.item.config.Item.getAssetMap().getAsset(Balance.get().library().getExtractItem()) == null) {
            problems += warn("Library extract item '" + Balance.get().library().getExtractItem() + "' doesn't exist.");
        }
        for (com.genetics.research.ResearchNode node : com.genetics.research.ResearchNode.getAssetMap().getAssetMap().values()) {
            String where = "research node " + node.getId();
            for (String required : node.getRequires()) {
                if (com.genetics.research.ResearchNode.getAssetMap().getAsset(required) == null) problems += warn(where + ": unknown required node '" + required + "'.");
            }
            for (String recipe : node.getRecipes()) {
                if (com.hypixel.hytale.server.core.asset.type.item.config.Item.getAssetMap().getAsset(recipe) == null) problems += warn(where + ": unknown item '" + recipe + "'.");
            }
            for (String feature : node.getFeatures()) {
                if (!com.genetics.research.Research.FEATURES.contains(feature)) problems += warn(where + ": unknown feature '" + feature + "'.");
            }
        }
        for (SicknessEvent event : SicknessEvent.getAssetMap().getAssetMap().values()) {
            String where = "sickness event " + event.getId();
            if (sickness.rank(event.getMinBand()) < 0) problems += warn(where + ": unknown MinBand '" + event.getMinBand() + "'.");
            if (event.getMaxBand() != null && sickness.rank(event.getMaxBand()) < 0) problems += warn(where + ": unknown MaxBand '" + event.getMaxBand() + "'.");
            problems += checkActions(event.getActions(), where);
        }
        if (StatEntry.getAssetMap().getAsset(DEFAULT_ID) == null) problems += warn("Effect Codex has no " + DEFAULT_ID + " stat entry.");
        if (EffectEntry.getAssetMap().getAsset(DEFAULT_ID) == null) problems += warn("Effect Codex has no " + DEFAULT_ID + " effect entry.");

        LOGGER.atInfo().log("Genetics data loaded: %d stat weights, %d effects, %d mutations, %d mob profiles, %d sickness events, %d Complexity tiers, %d research nodes, %d problem(s).",
                StatEntry.getAssetMap().getAssetMap().size(),
                EffectEntry.getAssetMap().getAssetMap().size(),
                MutationEntry.getAssetMap().getAssetMap().size(),
                MobGeneProfile.getAssetMap().getAssetMap().size(),
                SicknessEvent.getAssetMap().getAssetMap().size(),
                com.genetics.tier.ComplexityTier.getAssetMap().getAssetMap().size(),
                com.genetics.research.ResearchNode.getAssetMap().getAssetMap().size(),
                problems);
    }

    private static int checkActions(@Nonnull com.genetics.action.Action[] actions, @Nonnull String where) {
        int problems = 0;
        for (com.genetics.action.Action action : actions) {
            if (action instanceof com.genetics.action.ApplyEffectAction apply) problems += checkEffect(apply.getId(), where);
            if (action instanceof com.genetics.action.PlaySoundAction sound) problems += checkSound(sound.getId(), where);
            if (action instanceof com.genetics.action.CameraShakeAction shake && com.hypixel.hytale.server.core.asset.type.camera.CameraEffect.getAssetMap().getAsset(String.valueOf(shake.getId())) == null) {
                problems += warn(where + ": unknown camera effect '" + shake.getId() + "'.");
            }
        }
        return problems;
    }

    private static int checkStat(@Nullable String stat, @Nonnull String where) {
        if (stat == null) return warn(where + ": missing stat id.");
        return EntityStatType.getAssetMap().getAsset(stat) == null ? warn(where + ": unknown stat '" + stat + "'.") : 0;
    }

    private static int checkEffect(@Nullable String effect, @Nonnull String where) {
        if (effect == null) return warn(where + ": missing effect id.");
        return EntityEffect.getAssetMap().getAsset(effect) == null ? warn(where + ": unknown effect '" + effect + "'.") : 0;
    }

    private static int checkSound(@Nullable String sound, @Nonnull String where) {
        if (sound == null) return warn(where + ": missing sound id.");
        return com.hypixel.hytale.server.core.asset.type.soundevent.config.SoundEvent.getAssetMap().getIndex(sound) == Integer.MIN_VALUE
                ? warn(where + ": unknown sound '" + sound + "'.") : 0;
    }

    private static int warn(@Nonnull String message) {
        LOGGER.atWarning().log(message);
        return 1;
    }

    private static void warnOnce(@Nonnull String key, @Nonnull String message) {
        if (WARNED.add(key)) LOGGER.atWarning().log(message);
    }
}
