package com.genetics.action;

import com.hypixel.hytale.codec.Codec;
import com.hypixel.hytale.codec.KeyedCodec;
import com.hypixel.hytale.codec.builder.BuilderCodec;
import com.hypixel.hytale.server.core.asset.type.entityeffect.config.EntityEffect;
import com.hypixel.hytale.server.core.entity.effect.EffectControllerComponent;

import javax.annotation.Nonnull;

/** {@code { "Type": "RemoveEffect", "Ids": ["Slow", "Freeze"] }}: removes effects from the entity. */
public class RemoveEffectAction extends Action {
    public static final String TYPE = "RemoveEffect";

    public static final BuilderCodec<RemoveEffectAction> CODEC = BuilderCodec.builder(RemoveEffectAction.class, RemoveEffectAction::new, BASE_CODEC)
            .append(new KeyedCodec<>("Ids", Codec.STRING_ARRAY), (t, v) -> t.ids = v, t -> t.ids).add()
            .build();

    private String[] ids = new String[0];

    @Nonnull
    public String[] getIds() {
        return ids != null ? ids : new String[0];
    }

    @Override
    public void run(@Nonnull ActionContext context) {
        EffectControllerComponent effects = context.buffer().getComponent(context.entity(), EffectControllerComponent.getComponentType());
        if (effects == null) return;
        for (String id : getIds()) {
            int index = EntityEffect.getAssetMap().getIndex(id);
            if (index != Integer.MIN_VALUE && effects.hasEffect(index)) effects.removeEffect(context.entity(), index, context.buffer());
        }
    }
}
