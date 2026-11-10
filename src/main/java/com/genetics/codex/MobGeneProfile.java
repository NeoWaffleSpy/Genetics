package com.genetics.codex;

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

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

/**
 * The genes a family of mobs can give. One file per profile in {@code Server/Genetics/MobProfiles/}.
 * <p>
 * A profile applies to every NPC role listed in {@code Roles}, and to the role named like the file.
 * {@code Default.json} is the fallback for mobs without a profile: its stat pool is narrowed to the stats the mob
 * actually has.
 */
public final class MobGeneProfile implements JsonAssetWithMap<String, DefaultAssetMap<String, MobGeneProfile>> {
    public static final String PATH = "Genetics/MobProfiles";
    public static final String DEFAULT_ID = "Default";

    /** A stat the mob can modify: picked by weight, then rolled between Min and Max percent. */
    public static final class StatRoll {
        public static final BuilderCodec<StatRoll> CODEC = BuilderCodec.builder(StatRoll.class, StatRoll::new)
                .append(new KeyedCodec<>("Stat", Codec.STRING), (t, v) -> t.stat = v, t -> t.stat).add()
                .append(new KeyedCodec<>("Weight", Codec.DOUBLE), (t, v) -> t.weight = v, t -> t.weight).add()
                .append(new KeyedCodec<>("Min", Codec.DOUBLE), (t, v) -> t.min = v, t -> t.min).add()
                .append(new KeyedCodec<>("Max", Codec.DOUBLE), (t, v) -> t.max = v, t -> t.max).add()
                .build();

        private String stat;
        private double weight = 1;
        private double min;
        private double max;

        public String getStat() {
            return stat;
        }

        public double getWeight() {
            return weight;
        }

        public double getMin() {
            return min;
        }

        public double getMax() {
            return max;
        }
    }

    /** A drawback: rolls on its own with {@code Chance}, and gives minus Min to Max percent of the stat. */
    public static final class Drawback {
        public static final BuilderCodec<Drawback> CODEC = BuilderCodec.builder(Drawback.class, Drawback::new)
                .append(new KeyedCodec<>("Stat", Codec.STRING), (t, v) -> t.stat = v, t -> t.stat).add()
                .append(new KeyedCodec<>("Chance", Codec.DOUBLE), (t, v) -> t.chance = v, t -> t.chance).add()
                .append(new KeyedCodec<>("Min", Codec.DOUBLE), (t, v) -> t.min = v, t -> t.min).add()
                .append(new KeyedCodec<>("Max", Codec.DOUBLE), (t, v) -> t.max = v, t -> t.max).add()
                .build();

        private String stat;
        private double chance = 1;
        private double min;
        private double max;

        public String getStat() {
            return stat;
        }

        public double getChance() {
            return chance;
        }

        public double getMin() {
            return min;
        }

        public double getMax() {
            return max;
        }
    }

    /** A vanilla effect the genome can carry. Duration (seconds) only matters for temporary effects. */
    public static final class EffectRoll {
        public static final BuilderCodec<EffectRoll> CODEC = BuilderCodec.builder(EffectRoll.class, EffectRoll::new)
                .append(new KeyedCodec<>("Id", Codec.STRING), (t, v) -> t.id = v, t -> t.id).add()
                .append(new KeyedCodec<>("Duration", Codec.DOUBLE), (t, v) -> t.duration = v, t -> t.duration).add()
                .append(new KeyedCodec<>("Weight", Codec.DOUBLE), (t, v) -> t.weight = v, t -> t.weight).add()
                .build();

        private String id;
        private double duration;
        private double weight = 1;

        public String getId() {
            return id;
        }

        public double getDuration() {
            return duration;
        }

        public double getWeight() {
            return weight;
        }
    }

    public static final AssetBuilderCodec<String, MobGeneProfile> CODEC = AssetBuilderCodec.builder(
                    MobGeneProfile.class, MobGeneProfile::new, Codec.STRING, (t, k) -> t.id = k, t -> t.id, (t, d) -> t.data = d, t -> t.data)
            .append(new KeyedCodec<>("Roles", Codec.STRING_ARRAY), (t, v) -> t.roles = v, t -> t.roles)
            .documentation("NPC role ids this profile applies to.").add()
            .append(new KeyedCodec<>("StatRolls", Codec.INTEGER), (t, v) -> t.statRolls = v, t -> t.statRolls)
            .documentation("How many different stats are picked from the stat pool.").add()
            .append(new KeyedCodec<>("StatPool", ArrayCodec.ofBuilderCodec(StatRoll.CODEC, StatRoll[]::new)), (t, v) -> t.statPool = v, t -> t.statPool).add()
            .append(new KeyedCodec<>("TemporaryEffect", EffectRoll.CODEC), (t, v) -> t.temporaryEffect = v, t -> t.temporaryEffect)
            .documentation("The short \"taste\" of the creature the injection triggers.").add()
            .append(new KeyedCodec<>("PermanentEffectChance", Codec.DOUBLE), (t, v) -> t.permanentEffectChance = v, t -> t.permanentEffectChance).add()
            .append(new KeyedCodec<>("PermanentEffects", ArrayCodec.ofBuilderCodec(EffectRoll.CODEC, EffectRoll[]::new)), (t, v) -> t.permanentEffects = v, t -> t.permanentEffects)
            .documentation("Pool of permanent effects; at most one is picked, by weight.").add()
            .append(new KeyedCodec<>("Drawbacks", ArrayCodec.ofBuilderCodec(Drawback.CODEC, Drawback[]::new)), (t, v) -> t.drawbacks = v, t -> t.drawbacks).add()
            .build();

    private static AssetStore<String, MobGeneProfile, DefaultAssetMap<String, MobGeneProfile>> store;

    private AssetExtraInfo.Data data;
    private String id;
    private String[] roles = new String[0];
    private int statRolls = 1;
    private StatRoll[] statPool = new StatRoll[0];
    private EffectRoll temporaryEffect;
    private double permanentEffectChance;
    private EffectRoll[] permanentEffects = new EffectRoll[0];
    private Drawback[] drawbacks = new Drawback[0];

    private MobGeneProfile() {
    }

    @Nonnull
    public static AssetStore<String, MobGeneProfile, DefaultAssetMap<String, MobGeneProfile>> getAssetStore() {
        if (store == null) store = AssetRegistry.getAssetStore(MobGeneProfile.class);
        return store;
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
    public StatRoll[] getStatPool() {
        return statPool != null ? statPool : new StatRoll[0];
    }

    @Nullable
    public EffectRoll getTemporaryEffect() {
        return temporaryEffect;
    }

    public double getPermanentEffectChance() {
        return permanentEffectChance;
    }

    @Nonnull
    public EffectRoll[] getPermanentEffects() {
        return permanentEffects != null ? permanentEffects : new EffectRoll[0];
    }

    @Nonnull
    public Drawback[] getDrawbacks() {
        return drawbacks != null ? drawbacks : new Drawback[0];
    }
}
