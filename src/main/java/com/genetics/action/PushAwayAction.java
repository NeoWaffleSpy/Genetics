package com.genetics.action;

import com.hypixel.hytale.codec.Codec;
import com.hypixel.hytale.codec.KeyedCodec;
import com.hypixel.hytale.codec.builder.BuilderCodec;
import org.joml.Vector3d;

import javax.annotation.Nonnull;

/**
 * {@code { "Type": "PushAway", "Force": 1.5, "Up": 0.3 }}: dashes the entity away from the other entity of the event
 * (the attacker when hurt). Does nothing without one.
 */
public class PushAwayAction extends Action {
    public static final String TYPE = "PushAway";

    public static final BuilderCodec<PushAwayAction> CODEC = BuilderCodec.builder(PushAwayAction.class, PushAwayAction::new, BASE_CODEC)
            .append(new KeyedCodec<>("Force", Codec.DOUBLE), (t, v) -> t.force = v, t -> t.force).add()
            .append(new KeyedCodec<>("Up", Codec.DOUBLE), (t, v) -> t.up = v, t -> t.up).add()
            .build();

    private double force = 1.5;
    private double up = 0.3;

    @Override
    public void run(@Nonnull ActionContext context) {
        if (context.other() == null || !context.other().isValid()) return;
        Vector3d from = Nearby.position(context, context.other());
        Vector3d at = Nearby.position(context, context.entity());
        if (from != null && at != null) ImpulseAction.push(context.buffer(), context.entity(), ImpulseAction.away(from, at, force, up), false);
    }
}
