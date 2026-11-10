package com.genetics.data;

import com.hypixel.hytale.assetstore.AssetExtraInfo;
import com.hypixel.hytale.assetstore.AssetRegistry;
import com.hypixel.hytale.assetstore.AssetStore;
import com.hypixel.hytale.assetstore.codec.AssetBuilderCodec;
import com.hypixel.hytale.assetstore.map.DefaultAssetMap;
import com.hypixel.hytale.assetstore.map.JsonAssetWithMap;
import com.hypixel.hytale.codec.Codec;
import com.hypixel.hytale.codec.KeyedCodec;
import com.hypixel.hytale.codec.validation.Validators;
import com.hypixel.hytale.server.core.modules.entitystats.asset.EntityStatType;

import javax.annotation.Nonnull;

/**
 * Effect Codex entry for a stat: how much Complexity one percent of that stat adds (GDD §3.1, §3.2).
 * One file per vanilla stat, named after it: {@code Server/Genetics/Stats/Health.json}.
 *
 * <pre>{ "WeightPerPercent": 0.8 }</pre>
 */
public class StatWeight implements JsonAssetWithMap<String, DefaultAssetMap<String, StatWeight>> {
    public static final String PATH = "Genetics/Stats";

    public static final AssetBuilderCodec<String, StatWeight> CODEC = AssetBuilderCodec.builder(
                    StatWeight.class, StatWeight::new, Codec.STRING, (t, k) -> t.id = k, t -> t.id, (t, data) -> t.data = data, t -> t.data
            )
            .documentation("Complexity weight of a vanilla stat. The file name is the stat id.")
            .<Double>appendInherited(new KeyedCodec<>("WeightPerPercent", Codec.DOUBLE),
                    (t, v) -> t.weightPerPercent = v, t -> t.weightPerPercent, (t, parent) -> t.weightPerPercent = parent.weightPerPercent)
            .documentation("Complexity added by each percent of modifier on this stat.")
            .addValidator(Validators.nonNull())
            .addValidator(Validators.min(0.0))
            .add()
            .validator((t, results) -> {
                if (EntityStatType.getAssetMap().getAsset(t.id) == null) {
                    results.warn("No vanilla stat is named '" + t.id + "', so this weight is never used.");
                }
            })
            .build();

    private static AssetStore<String, StatWeight, DefaultAssetMap<String, StatWeight>> assetStore;

    protected AssetExtraInfo.Data data;
    protected String id;
    protected double weightPerPercent;

    protected StatWeight() {
    }

    @Nonnull
    public static AssetStore<String, StatWeight, DefaultAssetMap<String, StatWeight>> getAssetStore() {
        if (assetStore == null) assetStore = AssetRegistry.getAssetStore(StatWeight.class);
        return assetStore;
    }

    @Nonnull
    public static DefaultAssetMap<String, StatWeight> getAssetMap() {
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
