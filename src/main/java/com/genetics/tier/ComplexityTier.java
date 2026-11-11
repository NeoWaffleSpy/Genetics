package com.genetics.tier;

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
 * A Complexity tier (design doc §4): a Complexity range, the Lab Station tier needed to work in it, and what
 * processing or splicing in it costs. One file per tier in {@code Server/Genetics/ComplexityTiers/}:
 * <pre>
 * {
 *   "MinComplexity": 21,
 *   "StationTier": 2,
 *   "Costs": [
 *     { "ItemId": "Ingredient_Bar_Copper", "Scaling": { "Type": "Linear", "Base": 2, "PerPoint": 0.2 } }
 *   ]
 * }
 * </pre>
 * A tier runs from its MinComplexity up to the next tier's, so tiers can't overlap or leave gaps.
 */
public final class ComplexityTier implements JsonAssetWithMap<String, DefaultAssetMap<String, ComplexityTier>> {
    public static final String PATH = "Genetics/ComplexityTiers";

    /** One material of a tier's cost: an item, or any item of a resource type, in an amount given by a Scaling rule. */
    public static final class Cost {
        public static final BuilderCodec<Cost> CODEC = BuilderCodec.builder(Cost.class, Cost::new)
                .append(new KeyedCodec<>("ItemId", Codec.STRING), (t, v) -> t.itemId = v, t -> t.itemId).add()
                .append(new KeyedCodec<>("ResourceTypeId", Codec.STRING), (t, v) -> t.resourceTypeId = v, t -> t.resourceTypeId).add()
                .append(new KeyedCodec<>("Scaling", Scaling.CODEC), (t, v) -> t.scaling = v, t -> t.scaling).add()
                .build();

        @Nullable
        private String itemId;
        @Nullable
        private String resourceTypeId;
        @Nullable
        private Scaling scaling;

        @Nullable
        public String getItemId() {
            return itemId;
        }

        @Nullable
        public String getResourceTypeId() {
            return resourceTypeId;
        }

        @Nullable
        public Scaling getScaling() {
            return scaling;
        }
    }

    public static final AssetBuilderCodec<String, ComplexityTier> CODEC = AssetBuilderCodec.builder(
                    ComplexityTier.class, ComplexityTier::new, Codec.STRING, (t, k) -> t.id = k, t -> t.id, (t, d) -> t.data = d, t -> t.data)
            .append(new KeyedCodec<>("MinComplexity", Codec.DOUBLE), (t, v) -> t.minComplexity = v, t -> t.minComplexity)
            .documentation("The tier starts at this Complexity and runs up to the next tier's.").add()
            .append(new KeyedCodec<>("StationTier", Codec.INTEGER), (t, v) -> t.stationTier = v, t -> t.stationTier)
            .documentation("Lab Station tier needed to process or splice in this tier.").add()
            .append(new KeyedCodec<>("Name", Codec.STRING), (t, v) -> t.name = v, t -> t.name)
            .documentation("Display name. Defaults to the id.").add()
            .append(new KeyedCodec<>("Costs", new ArrayCodec<>(Cost.CODEC, Cost[]::new)), (t, v) -> t.costs = v, t -> t.costs).add()
            .build();

    private static AssetStore<String, ComplexityTier, DefaultAssetMap<String, ComplexityTier>> store;

    private AssetExtraInfo.Data data;
    private String id;
    private double minComplexity;
    private int stationTier = 2;
    @Nullable
    private String name;
    private Cost[] costs = new Cost[0];

    private ComplexityTier() {
    }

    @Nonnull
    public static AssetStore<String, ComplexityTier, DefaultAssetMap<String, ComplexityTier>> getAssetStore() {
        if (store == null) store = AssetRegistry.getAssetStore(ComplexityTier.class);
        return store;
    }

    @Nonnull
    public static DefaultAssetMap<String, ComplexityTier> getAssetMap() {
        return getAssetStore().getAssetMap();
    }

    @Override
    public String getId() {
        return id;
    }

    public double getMinComplexity() {
        return minComplexity;
    }

    public int getStationTier() {
        return stationTier;
    }

    @Nonnull
    public String getName() {
        return name != null ? name : id;
    }

    @Nonnull
    public Cost[] getCosts() {
        return costs != null ? costs : new Cost[0];
    }
}
