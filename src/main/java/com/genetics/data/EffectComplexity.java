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
import com.hypixel.hytale.server.core.asset.type.entityeffect.config.EntityEffect;

import javax.annotation.Nonnull;

/**
 * Effect Codex entry for a status effect: its Complexity in temporary and permanent form (GDD §3.2).
 * One file per vanilla effect, named after it: {@code Server/Genetics/Effects/HealthRegen_Buff_T1.json}.
 * The entry named {@value #DEFAULT_ID} is used for effects that have no entry of their own.
 *
 * <pre>{ "Temporary": 3, "Permanent": 25 }</pre>
 */
public class EffectComplexity implements JsonAssetWithMap<String, DefaultAssetMap<String, EffectComplexity>> {
    public static final String PATH = "Genetics/Effects";
    /** Id of the fallback entry, used for effects missing from the Codex. */
    public static final String DEFAULT_ID = "Default";

    public static final AssetBuilderCodec<String, EffectComplexity> CODEC = AssetBuilderCodec.builder(
                    EffectComplexity.class, EffectComplexity::new, Codec.STRING, (t, k) -> t.id = k, t -> t.id, (t, data) -> t.data = data, t -> t.data
            )
            .documentation("Complexity of a vanilla status effect. The file name is the effect id; '" + DEFAULT_ID + "' is the fallback.")
            .<Double>appendInherited(new KeyedCodec<>("Temporary", Codec.DOUBLE),
                    (t, v) -> t.temporary = v, t -> t.temporary, (t, parent) -> t.temporary = parent.temporary)
            .documentation("Complexity of the effect when a gene applies it for a limited time.")
            .addValidator(Validators.nonNull())
            .addValidator(Validators.min(0.0))
            .add()
            .<Double>appendInherited(new KeyedCodec<>("Permanent", Codec.DOUBLE),
                    (t, v) -> t.permanent = v, t -> t.permanent, (t, parent) -> t.permanent = parent.permanent)
            .documentation("Complexity of the effect when a gene keeps it on permanently.")
            .addValidator(Validators.nonNull())
            .addValidator(Validators.min(0.0))
            .add()
            .validator((t, results) -> {
                if (!DEFAULT_ID.equals(t.id) && EntityEffect.getAssetMap().getAsset(t.id) == null) {
                    results.warn("No vanilla effect is named '" + t.id + "', so this entry is never used.");
                }
            })
            .build();

    private static AssetStore<String, EffectComplexity, DefaultAssetMap<String, EffectComplexity>> assetStore;

    protected AssetExtraInfo.Data data;
    protected String id;
    protected double temporary;
    protected double permanent;

    protected EffectComplexity() {
    }

    @Nonnull
    public static AssetStore<String, EffectComplexity, DefaultAssetMap<String, EffectComplexity>> getAssetStore() {
        if (assetStore == null) assetStore = AssetRegistry.getAssetStore(EffectComplexity.class);
        return assetStore;
    }

    @Nonnull
    public static DefaultAssetMap<String, EffectComplexity> getAssetMap() {
        return getAssetStore().getAssetMap();
    }

    @Override
    public String getId() {
        return id;
    }

    public double getTemporary() {
        return temporary;
    }

    public double getPermanent() {
        return permanent;
    }

    public double get(boolean permanent) {
        return permanent ? this.permanent : this.temporary;
    }
}
