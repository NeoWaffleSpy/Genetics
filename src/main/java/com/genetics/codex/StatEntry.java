package com.genetics.codex;

import com.hypixel.hytale.assetstore.AssetExtraInfo;
import com.hypixel.hytale.assetstore.AssetRegistry;
import com.hypixel.hytale.assetstore.AssetStore;
import com.hypixel.hytale.assetstore.codec.AssetBuilderCodec;
import com.hypixel.hytale.assetstore.map.DefaultAssetMap;
import com.hypixel.hytale.assetstore.map.JsonAssetWithMap;
import com.hypixel.hytale.codec.Codec;
import com.hypixel.hytale.codec.KeyedCodec;

import javax.annotation.Nonnull;

/**
 * Effect Codex, stat part: how much Complexity one percent of a vanilla stat is worth.
 * One file per stat in {@code Server/Genetics/EffectCodex/Stats/}, named after the stat ({@code Health.json}).
 * {@code Default.json} applies to stats that have no entry.
 */
public final class StatEntry implements JsonAssetWithMap<String, DefaultAssetMap<String, StatEntry>> {
    public static final String PATH = "Genetics/EffectCodex/Stats";

    public static final AssetBuilderCodec<String, StatEntry> CODEC = AssetBuilderCodec.builder(
                    StatEntry.class, StatEntry::new, Codec.STRING, (t, k) -> t.id = k, t -> t.id, (t, d) -> t.data = d, t -> t.data)
            .append(new KeyedCodec<>("WeightPerPercent", Codec.DOUBLE), (t, v) -> t.weightPerPercent = v, t -> t.weightPerPercent)
            .documentation("Complexity added by each percent of this stat.").add()
            .build();

    private static AssetStore<String, StatEntry, DefaultAssetMap<String, StatEntry>> store;

    private AssetExtraInfo.Data data;
    private String id;
    private double weightPerPercent;

    private StatEntry() {
    }

    @Nonnull
    public static AssetStore<String, StatEntry, DefaultAssetMap<String, StatEntry>> getAssetStore() {
        if (store == null) store = AssetRegistry.getAssetStore(StatEntry.class);
        return store;
    }

    @Nonnull
    public static DefaultAssetMap<String, StatEntry> getAssetMap() {
        return getAssetStore().getAssetMap();
    }

    @Override
    public String getId() {
        return id;
    }

    public double getWeightPerPercent() {
        return weightPerPercent;
    }
}
