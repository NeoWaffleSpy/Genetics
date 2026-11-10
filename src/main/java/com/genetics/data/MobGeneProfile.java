package com.genetics.data;

import com.hypixel.hytale.assetstore.AssetExtraInfo;
import com.hypixel.hytale.assetstore.AssetRegistry;
import com.hypixel.hytale.assetstore.AssetStore;
import com.hypixel.hytale.assetstore.codec.AssetBuilderCodec;
import com.hypixel.hytale.assetstore.map.DefaultAssetMap;
import com.hypixel.hytale.assetstore.map.JsonAssetWithMap;
import com.hypixel.hytale.codec.Codec;
import com.hypixel.hytale.codec.KeyedCodec;
import com.hypixel.hytale.codec.builder.BuilderCodec;
import com.hypixel.hytale.codec.codecs.array.ArrayCodec;
import com.hypixel.hytale.codec.validation.Validators;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

/**
 * The genes a family of mobs can give (GDD §6, "how a mob's genes are defined").
 * One file per profile: {@code Server/Genetics/MobProfiles/Cattle.json}. The profile named {@value #DEFAULT_ID} is
 * used for mobs that no profile lists.
 *
 * <pre>
 * {
 *   "Roles": [ "Cow", "Cow_Calf" ],
 *   "StatRolls": 1,
 *   "Stats": [ { "Stat": "Health", "Min": 3, "Max": 8, "Weight": 1 } ],
 *   "TemporaryEffect": { "Effect": "HealthRegen_Buff_T1", "Duration": 180 },
 *   "PermanentEffects": [ { "Effect": "…", "Chance": 0.1 } ],
 *   "Drawbacks": [ { "Stat": "Stamina", "Min": -5, "Max": -5, "Chance": 0.3 } ]
 * }
 * </pre>
 */
public class MobGeneProfile implements JsonAssetWithMap<String, DefaultAssetMap<String, MobGeneProfile>> {
    public static final String PATH = "Genetics/MobProfiles";
    /** Id of the fallback profile, used for mobs that no profile lists. */
    public static final String DEFAULT_ID = "Default";

    public static final AssetBuilderCodec<String, MobGeneProfile> CODEC = AssetBuilderCodec.builder(
                    MobGeneProfile.class, MobGeneProfile::new, Codec.STRING, (t, k) -> t.id = k, t -> t.id, (t, data) -> t.data = data, t -> t.data
            )
            .documentation("The genes a family of mobs can give. '" + DEFAULT_ID + "' is used for mobs no profile lists.")
            .<String[]>appendInherited(new KeyedCodec<>("Roles", Codec.STRING_ARRAY), (t, v) -> t.roles = v, t -> t.roles, (t, parent) -> t.roles = parent.roles)
            .documentation("NPC role ids (e.g. 'Cow') that use this profile.")
            .add()
            .<Integer>appendInherited(new KeyedCodec<>("StatRolls", Codec.INTEGER), (t, v) -> t.statRolls = v, t -> t.statRolls, (t, parent) -> t.statRolls = parent.statRolls)
            .documentation("How many different stats are picked from 'Stats' for one sample.")
            .addValidator(Validators.min(0))
            .add()
            .<StatRoll[]>appendInherited(new KeyedCodec<>("Stats", StatRoll.ARRAY_CODEC), (t, v) -> t.stats = v, t -> t.stats, (t, parent) -> t.stats = parent.stats)
            .documentation("Stat pool: stats this mob can modify, each with a pick weight and a roll range in percent.")
            .add()
            .<EffectRoll>appendInherited(new KeyedCodec<>("TemporaryEffect", EffectRoll.CODEC),
                    (t, v) -> t.temporaryEffect = v, t -> t.temporaryEffect, (t, parent) -> t.temporaryEffect = parent.temporaryEffect)
            .documentation("The short 'taste' effect the injection triggers.")
            .add()
            .<EffectRoll[]>appendInherited(new KeyedCodec<>("PermanentEffects", EffectRoll.ARRAY_CODEC),
                    (t, v) -> t.permanentEffects = v, t -> t.permanentEffects, (t, parent) -> t.permanentEffects = parent.permanentEffects)
            .documentation("Permanent effects, each rolled on its own 'Chance'.")
            .add()
            .<StatRoll[]>appendInherited(new KeyedCodec<>("Drawbacks", StatRoll.ARRAY_CODEC),
                    (t, v) -> t.drawbacks = v, t -> t.drawbacks, (t, parent) -> t.drawbacks = parent.drawbacks)
            .documentation("Negative stat genes, each rolled on its own 'Chance'. Ranges should be negative.")
            .add()
            .build();

    private static AssetStore<String, MobGeneProfile, DefaultAssetMap<String, MobGeneProfile>> assetStore;

    protected AssetExtraInfo.Data data;
    protected String id;
    protected String[] roles = new String[0];
    protected int statRolls = 1;
    protected StatRoll[] stats = new StatRoll[0];
    protected EffectRoll temporaryEffect;
    protected EffectRoll[] permanentEffects = new EffectRoll[0];
    protected StatRoll[] drawbacks = new StatRoll[0];

    protected MobGeneProfile() {
    }

    @Nonnull
    public static AssetStore<String, MobGeneProfile, DefaultAssetMap<String, MobGeneProfile>> getAssetStore() {
        if (assetStore == null) assetStore = AssetRegistry.getAssetStore(MobGeneProfile.class);
        return assetStore;
    }

    @Nonnull
    public static DefaultAssetMap<String, MobGeneProfile> getAssetMap() {
        return getAssetStore().getAssetMap();
    }

    @Override
    public String getId() {
        return id;
    }

    @Nonnull
    public String[] getRoles() {
        return roles != null ? roles : new String[0];
    }

    public int getStatRolls() {
        return statRolls;
    }

    @Nonnull
    public StatRoll[] getStats() {
        return stats != null ? stats : new StatRoll[0];
    }

    @Nullable
    public EffectRoll getTemporaryEffect() {
        return temporaryEffect;
    }

    @Nonnull
    public EffectRoll[] getPermanentEffects() {
        return permanentEffects != null ? permanentEffects : new EffectRoll[0];
    }

    @Nonnull
    public StatRoll[] getDrawbacks() {
        return drawbacks != null ? drawbacks : new StatRoll[0];
    }

    /** A stat a profile can roll: a range in percent, a pick weight (stat pool) or a chance (drawbacks). */
    public static class StatRoll {
        public static final BuilderCodec<StatRoll> CODEC = BuilderCodec.builder(StatRoll.class, StatRoll::new)
                .append(new KeyedCodec<>("Stat", Codec.STRING), (t, v) -> t.stat = v, t -> t.stat)
                .documentation("Vanilla stat id, e.g. 'Health'.")
                .addValidator(Validators.nonNull())
                .add()
                .append(new KeyedCodec<>("Min", Codec.DOUBLE), (t, v) -> t.min = v, t -> t.min)
                .documentation("Lowest roll, in percent.")
                .addValidator(Validators.nonNull())
                .add()
                .append(new KeyedCodec<>("Max", Codec.DOUBLE), (t, v) -> t.max = v, t -> t.max)
                .documentation("Highest roll, in percent.")
                .addValidator(Validators.nonNull())
                .add()
                .append(new KeyedCodec<>("Weight", Codec.DOUBLE), (t, v) -> t.weight = v, t -> t.weight)
                .documentation("Pick weight within the stat pool.")
                .addValidator(Validators.min(0.0))
                .add()
                .append(new KeyedCodec<>("Chance", Codec.DOUBLE), (t, v) -> t.chance = v, t -> t.chance)
                .documentation("Chance (0 to 1) that a drawback rolls.")
                .addValidator(Validators.range(0.0, 1.0))
                .add()
                .validator((t, results) -> {
                    References.checkStat(t.stat, results);
                    if (t.min > t.max) results.fail("'Min' (" + t.min + ") is above 'Max' (" + t.max + ").");
                })
                .build();
        public static final ArrayCodec<StatRoll> ARRAY_CODEC = new ArrayCodec<>(CODEC, StatRoll[]::new);

        private String stat;
        private double min;
        private double max;
        private double weight = 1.0;
        private double chance = 1.0;

        public String getStat() {
            return stat;
        }

        public double getMin() {
            return min;
        }

        public double getMax() {
            return max;
        }

        public double getWeight() {
            return weight;
        }

        public double getChance() {
            return chance;
        }
    }

    /** An effect a profile can give: a duration in seconds (temporary) or a chance (permanent pool). */
    public static class EffectRoll {
        public static final BuilderCodec<EffectRoll> CODEC = BuilderCodec.builder(EffectRoll.class, EffectRoll::new)
                .append(new KeyedCodec<>("Effect", Codec.STRING), (t, v) -> t.effect = v, t -> t.effect)
                .documentation("Vanilla effect id.")
                .addValidator(Validators.nonNull())
                .add()
                .append(new KeyedCodec<>("Duration", Codec.DOUBLE), (t, v) -> t.duration = v, t -> t.duration)
                .documentation("Duration in seconds of a temporary effect.")
                .addValidator(Validators.min(0.0))
                .add()
                .append(new KeyedCodec<>("Chance", Codec.DOUBLE), (t, v) -> t.chance = v, t -> t.chance)
                .documentation("Chance (0 to 1) that a permanent effect rolls.")
                .addValidator(Validators.range(0.0, 1.0))
                .add()
                .validator((t, results) -> References.checkEffect(t.effect, results))
                .build();
        public static final ArrayCodec<EffectRoll> ARRAY_CODEC = new ArrayCodec<>(CODEC, EffectRoll[]::new);

        private String effect;
        private double duration;
        private double chance = 1.0;

        public String getEffect() {
            return effect;
        }

        public double getDuration() {
            return duration;
        }

        public double getChance() {
            return chance;
        }
    }
}
