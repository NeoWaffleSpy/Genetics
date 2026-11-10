package com.genetics.action;

import com.hypixel.hytale.codec.Codec;
import com.hypixel.hytale.codec.KeyedCodec;
import com.hypixel.hytale.codec.builder.BuilderCodec;

import javax.annotation.Nonnull;

/**
 * {@code { "Type": "PlaySourceMobSound" }}: the entity makes the source mob's call ({@link ActionContext#sourceSound()}).
 * Does nothing if no call is found.
 */
public class PlaySourceMobSoundAction extends Action {
    public static final String TYPE = "PlaySourceMobSound";

    public static final BuilderCodec<PlaySourceMobSoundAction> CODEC = BuilderCodec.builder(
                    PlaySourceMobSoundAction.class, PlaySourceMobSoundAction::new, BASE_CODEC)
            .append(new KeyedCodec<>("Pitch", Codec.DOUBLE), (t, v) -> t.pitch = v, t -> t.pitch)
            .documentation("Pitch multiplier. Default 1.").add()
            .build();

    private double pitch = 1;

    @Override
    public void run(@Nonnull ActionContext context) {
        PlaySoundAction.play(context, context.sourceSound(), pitch);
    }
}
