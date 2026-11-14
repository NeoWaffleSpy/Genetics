package com.genetics.action;

import com.hypixel.hytale.codec.Codec;
import com.hypixel.hytale.codec.KeyedCodec;
import com.hypixel.hytale.codec.builder.BuilderCodec;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

/** {@code { "Type": "AreaEffect", "Id": "Slow", "Duration": 5, "Radius": 8 }}: applies an effect to the NPCs around. */
public class AreaEffectAction extends Action {
    public static final String TYPE = "AreaEffect";

    public static final BuilderCodec<AreaEffectAction> CODEC = BuilderCodec.builder(AreaEffectAction.class, AreaEffectAction::new, BASE_CODEC)
            .append(new KeyedCodec<>("Id", Codec.STRING), (t, v) -> t.id = v, t -> t.id).add()
            .append(new KeyedCodec<>("Duration", Codec.DOUBLE), (t, v) -> t.duration = v, t -> t.duration).add()
            .append(new KeyedCodec<>("Radius", Codec.DOUBLE), (t, v) -> t.radius = v, t -> t.radius).add()
            .build();

    private String id;
    private double duration;
    private double radius = 6;

    @Nullable
    public String getId() {
        return id;
    }

    @Override
    public void run(@Nonnull ActionContext context) {
        for (var ref : Nearby.npcs(context, radius)) ApplyEffectAction.apply(context.on(ref), id, duration);
    }
}
