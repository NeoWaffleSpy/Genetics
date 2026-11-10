package com.genetics.action;

import com.genetics.codex.MobGeneProfile;
import com.hypixel.hytale.codec.Codec;
import com.hypixel.hytale.codec.KeyedCodec;
import com.hypixel.hytale.codec.builder.BuilderCodec;

import javax.annotation.Nonnull;

/**
 * {@code { "Type": "ApplySourceMobEffect", "Duration": 5 }}: a short flash of the source mob's temporary effect (the
 * one from its gene profile). Does nothing if the source mob has none.
 */
public class ApplySourceMobEffectAction extends Action {
    public static final String TYPE = "ApplySourceMobEffect";

    public static final BuilderCodec<ApplySourceMobEffectAction> CODEC = BuilderCodec.builder(
                    ApplySourceMobEffectAction.class, ApplySourceMobEffectAction::new, BASE_CODEC)
            .append(new KeyedCodec<>("Duration", Codec.DOUBLE), (t, v) -> t.duration = v, t -> t.duration)
            .documentation("Seconds.").add()
            .build();

    private double duration = 5;

    @Override
    public void run(@Nonnull ActionContext context) {
        MobGeneProfile profile = context.sourceProfile();
        MobGeneProfile.EffectRoll effect = profile != null ? profile.getTemporaryEffect() : null;
        if (effect != null) ApplyEffectAction.apply(context, effect.getId(), duration);
    }
}
