package com.genetics.codex;

import com.hypixel.hytale.assetstore.AssetExtraInfo;
import com.hypixel.hytale.assetstore.AssetRegistry;
import com.hypixel.hytale.assetstore.AssetStore;
import com.hypixel.hytale.assetstore.codec.AssetBuilderCodec;
import com.hypixel.hytale.assetstore.map.DefaultAssetMap;
import com.hypixel.hytale.assetstore.map.JsonAssetWithMap;
import com.hypixel.hytale.codec.Codec;
import com.hypixel.hytale.codec.KeyedCodec;
import com.hypixel.hytale.codec.codecs.array.ArrayCodec;
import com.hypixel.hytale.codec.codecs.map.MapCodec;
import com.genetics.mutation.Behavior;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.HashMap;
import java.util.Map;

/**
 * Mutation Codex: a special gene that never appears on a source mob.
 * One file per mutation in {@code Server/Genetics/MutationCodex/}, named after the mutation id.
 * Mutations only come from splicing (design doc §7); their behaviors are run by MutationSystem.
 */
public final class MutationEntry implements JsonAssetWithMap<String, DefaultAssetMap<String, MutationEntry>> {
    public static final String PATH = "Genetics/MutationCodex";

    public static final AssetBuilderCodec<String, MutationEntry> CODEC = AssetBuilderCodec.builder(
                    MutationEntry.class, MutationEntry::new, Codec.STRING, (t, k) -> t.id = k, t -> t.id, (t, d) -> t.data = d, t -> t.data)
            .append(new KeyedCodec<>("Name", Codec.STRING), (t, v) -> t.name = v, t -> t.name)
            .documentation("Translation key of the mutation's name.").add()
            .append(new KeyedCodec<>("Complexity", Codec.DOUBLE), (t, v) -> t.complexity = v, t -> t.complexity).add()
            .append(new KeyedCodec<>("Stats", new MapCodec<>(Codec.DOUBLE, HashMap::new)), (t, v) -> t.stats = v, t -> t.stats)
            .documentation("Stat modifiers in percent, by vanilla stat id.").add()
            .append(new KeyedCodec<>("Effects", Codec.STRING_ARRAY), (t, v) -> t.effects = v, t -> t.effects)
            .documentation("Vanilla effect ids, applied permanently.").add()
            .append(new KeyedCodec<>("Behaviors", new ArrayCodec<>(Behavior.CODEC, Behavior[]::new)), (t, v) -> t.behaviors = v, t -> t.behaviors)
            .documentation("Triggers that run actions (see com.genetics.mutation.Behavior), e.g. OnLowHealth, OnDamaged, AtNight, Periodic.").add()
            .append(new KeyedCodec<>("Weight", Codec.DOUBLE), (t, v) -> t.weight = v, t -> t.weight)
            .documentation("How likely the mutation is to be picked when one is rolled.").add()
            .append(new KeyedCodec<>("MinTier", Codec.STRING), (t, v) -> t.minTier = v, t -> t.minTier)
            .documentation("Lowest Complexity tier (id from ComplexityTiers) a splice result must reach for this mutation to be rolled. Required.").add()
            .build();

    private static AssetStore<String, MutationEntry, DefaultAssetMap<String, MutationEntry>> store;

    private AssetExtraInfo.Data data;
    private String id;
    private String name;
    private double complexity;
    private Map<String, Double> stats = Map.of();
    private String[] effects = new String[0];
    private Behavior[] behaviors = new Behavior[0];
    private double weight = 1;
    private String minTier;

    private MutationEntry() {
    }

    @Nonnull
    public static AssetStore<String, MutationEntry, DefaultAssetMap<String, MutationEntry>> getAssetStore() {
        if (store == null) store = AssetRegistry.getAssetStore(MutationEntry.class);
        return store;
    }

    @Nonnull
    public static DefaultAssetMap<String, MutationEntry> getAssetMap() {
        return getAssetStore().getAssetMap();
    }

    @Override
    public String getId() {
        return id;
    }

    @Nullable
    public String getName() {
        return name;
    }

    public double getComplexity() {
        return complexity;
    }

    @Nonnull
    public Map<String, Double> getStats() {
        return stats != null ? stats : Map.of();
    }

    @Nonnull
    public String[] getEffects() {
        return effects != null ? effects : new String[0];
    }

    @Nonnull
    public Behavior[] getBehaviors() {
        return behaviors != null ? behaviors : new Behavior[0];
    }

    public double getWeight() {
        return weight;
    }

    @Nullable
    public String getMinTier() {
        return minTier;
    }
}
