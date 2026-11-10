package com.genetics.codex;

import com.genetics.action.Action;
import com.hypixel.hytale.assetstore.AssetExtraInfo;
import com.hypixel.hytale.assetstore.AssetRegistry;
import com.hypixel.hytale.assetstore.AssetStore;
import com.hypixel.hytale.assetstore.codec.AssetBuilderCodec;
import com.hypixel.hytale.assetstore.map.DefaultAssetMap;
import com.hypixel.hytale.assetstore.map.JsonAssetWithMap;
import com.hypixel.hytale.codec.Codec;
import com.hypixel.hytale.codec.KeyedCodec;
import com.hypixel.hytale.codec.codecs.array.ArrayCodec;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

/**
 * Sickness Event Codex (design doc §9.2): something funny that happens to a sick entity. One file per event in
 * {@code Server/Genetics/SicknessEvents/}. Events are picked by weight among those whose minimum band the entity has
 * reached and that aren't on cooldown.
 */
public final class SicknessEvent implements JsonAssetWithMap<String, DefaultAssetMap<String, SicknessEvent>> {
    public static final String PATH = "Genetics/SicknessEvents";

    public static final AssetBuilderCodec<String, SicknessEvent> CODEC = AssetBuilderCodec.builder(
                    SicknessEvent.class, SicknessEvent::new, Codec.STRING, (t, k) -> t.id = k, t -> t.id, (t, d) -> t.data = d, t -> t.data)
            .append(new KeyedCodec<>("MinBand", Codec.STRING), (t, v) -> t.minBand = v, t -> t.minBand)
            .documentation("Lowest sickness band (from the Balance file) in which the event can happen.").add()
            .append(new KeyedCodec<>("MaxBand", Codec.STRING), (t, v) -> t.maxBand = v, t -> t.maxBand)
            .documentation("Optional highest band in which the event can happen, so worse bands keep their own character.").add()
            .append(new KeyedCodec<>("Weight", Codec.DOUBLE), (t, v) -> t.weight = v, t -> t.weight).add()
            .append(new KeyedCodec<>("Cooldown", Codec.DOUBLE), (t, v) -> t.cooldown = v, t -> t.cooldown)
            .documentation("Seconds before the same event can happen again to the same entity.").add()
            .append(new KeyedCodec<>("Message", Codec.STRING), (t, v) -> t.message = v, t -> t.message)
            .documentation("Optional translation key sent to the player when the event happens. {mob} is the source mob's name.").add()
            .append(new KeyedCodec<>("Actions", new ArrayCodec<>(Action.CODEC, Action[]::new)), (t, v) -> t.actions = v, t -> t.actions).add()
            .build();

    private static AssetStore<String, SicknessEvent, DefaultAssetMap<String, SicknessEvent>> store;

    private AssetExtraInfo.Data data;
    private String id;
    private String minBand;
    private String maxBand;
    private double weight = 1;
    private double cooldown;
    private String message;
    private Action[] actions = new Action[0];

    private SicknessEvent() {
    }

    @Nonnull
    public static AssetStore<String, SicknessEvent, DefaultAssetMap<String, SicknessEvent>> getAssetStore() {
        if (store == null) store = AssetRegistry.getAssetStore(SicknessEvent.class);
        return store;
    }

    @Nonnull
    public static DefaultAssetMap<String, SicknessEvent> getAssetMap() {
        return getAssetStore().getAssetMap();
    }

    @Override
    public String getId() {
        return id;
    }

    @Nullable
    public String getMinBand() {
        return minBand;
    }

    @Nullable
    public String getMaxBand() {
        return maxBand;
    }

    public double getWeight() {
        return weight;
    }

    public double getCooldown() {
        return cooldown;
    }

    @Nullable
    public String getMessage() {
        return message;
    }

    @Nonnull
    public Action[] getActions() {
        return actions != null ? actions : new Action[0];
    }
}
