package com.genetics.action;

import com.hypixel.hytale.codec.Codec;
import com.hypixel.hytale.codec.KeyedCodec;
import com.hypixel.hytale.codec.builder.BuilderCodec;
import com.hypixel.hytale.server.core.asset.type.entityeffect.config.EntityEffect;
import com.hypixel.hytale.server.core.asset.type.entityeffect.config.OverlapBehavior;
import com.hypixel.hytale.server.core.entity.effect.EffectControllerComponent;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

/** {@code { "Type": "ApplyEffect", "Id": "Slow", "Duration": 6 }}: applies an effect. Duration 0 keeps the effect's own. */
public class ApplyEffectAction extends Action {
    public static final String TYPE = "ApplyEffect";

    public static final BuilderCodec<ApplyEffectAction> CODEC = BuilderCodec.builder(ApplyEffectAction.class, ApplyEffectAction::new, BASE_CODEC)
            .append(new KeyedCodec<>("Id", Codec.STRING), (t, v) -> t.id = v, t -> t.id)
            .documentation("Effect id.").add()
            .append(new KeyedCodec<>("Duration", Codec.DOUBLE), (t, v) -> t.duration = v, t -> t.duration)
            .documentation("Seconds. 0 keeps the effect's own duration.").add()
            .build();

    private String id;
    private double duration;

    @Nullable
    public String getId() {
        return id;
    }

    @Override
    public void run(@Nonnull ActionContext context) {
        apply(context, id, duration);
    }

    static void apply(@Nonnull ActionContext context, @Nullable String effectId, double duration) {
        if (effectId == null) return;
        EntityEffect effect = EntityEffect.getAssetMap().getAsset(effectId);
        EffectControllerComponent effects = context.buffer().getComponent(context.entity(), EffectControllerComponent.getComponentType());
        if (effect == null || effects == null) return;
        if (duration > 0) effects.addEffect(context.entity(), effect, (float) duration, OverlapBehavior.OVERWRITE, context.buffer());
        else effects.addEffect(context.entity(), effect, context.buffer());
    }
}
