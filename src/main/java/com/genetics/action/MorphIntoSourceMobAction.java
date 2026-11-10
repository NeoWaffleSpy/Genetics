package com.genetics.action;

import com.genetics.effect.MorphSystem;
import com.hypixel.hytale.codec.Codec;
import com.hypixel.hytale.codec.KeyedCodec;
import com.hypixel.hytale.codec.builder.BuilderCodec;

import javax.annotation.Nonnull;

/**
 * {@code { "Type": "MorphIntoSourceMob", "Duration": 8 }}: briefly turns the entity into its source mob (any mob, using
 * the model it had when sampled), and plays the mob's call. Does nothing for copies of a player.
 */
public class MorphIntoSourceMobAction extends Action {
    public static final String TYPE = "MorphIntoSourceMob";

    public static final BuilderCodec<MorphIntoSourceMobAction> CODEC = BuilderCodec.builder(
                    MorphIntoSourceMobAction.class, MorphIntoSourceMobAction::new, BASE_CODEC)
            .append(new KeyedCodec<>("Duration", Codec.DOUBLE), (t, v) -> t.duration = v, t -> t.duration)
            .documentation("Seconds.").add()
            .build();

    private double duration = 8;

    @Override
    public void run(@Nonnull ActionContext context) {
        String model = context.sourceModel();
        if (model != null && MorphSystem.morph(context.entity(), context.buffer(), model, duration)) {
            PlaySoundAction.play(context, context.sourceSound(), 1);
        }
    }
}
