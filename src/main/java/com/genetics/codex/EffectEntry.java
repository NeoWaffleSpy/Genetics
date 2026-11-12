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
 * Effect Codex, effect part: the Complexity of a vanilla status effect as a gene, in its temporary and permanent forms.
 * One file per effect in {@code Server/Genetics/EffectCodex/Effects/}, named after the effect id.
 * {@code Default.json} applies to effects that have no entry.
 */
public final class EffectEntry implements JsonAssetWithMap<String, DefaultAssetMap<String, EffectEntry>> {
    public static final String PATH = "Genetics/EffectCodex/Effects";

    public static final AssetBuilderCodec<String, EffectEntry> CODEC = AssetBuilderCodec.builder(
                    EffectEntry.class, EffectEntry::new, Codec.STRING, (t, k) -> t.id = k, t -> t.id, (t, d) -> t.data = d, t -> t.data)
            .append(new KeyedCodec<>("Temporary", Codec.DOUBLE), (t, v) -> t.temporary = v, t -> t.temporary)
            .documentation("Complexity of the effect as a temporary gene.").add()
            .append(new KeyedCodec<>("Permanent", Codec.DOUBLE), (t, v) -> t.permanent = v, t -> t.permanent)
            .documentation("Complexity of the effect as a permanent gene.").add()
            .append(new KeyedCodec<>("Name", Codec.STRING), (t, v) -> t.name = v, t -> t.name)
            .documentation("Short display name as a gene (translation key), e.g. \"Regen T1\". Overrides the effect's own name.").add()
            .build();

    private static AssetStore<String, EffectEntry, DefaultAssetMap<String, EffectEntry>> store;

    private AssetExtraInfo.Data data;
    private String id;
    private double temporary;
    private double permanent;
    @javax.annotation.Nullable
    private String name;

    private EffectEntry() {
    }

    @Nonnull
    public static AssetStore<String, EffectEntry, DefaultAssetMap<String, EffectEntry>> getAssetStore() {
        if (store == null) store = AssetRegistry.getAssetStore(EffectEntry.class);
        return store;
    }

    @Nonnull
    public static DefaultAssetMap<String, EffectEntry> getAssetMap() {
        return getAssetStore().getAssetMap();
    }

    @Override
    public String getId() {
        return id;
    }

    /** Translation key of the gene's display name, or null to use the effect's own name. */
    @javax.annotation.Nullable
    public String getName() {
        return name;
    }

    public double getComplexity(boolean permanent) {
        return permanent ? this.permanent : temporary;
    }
}
