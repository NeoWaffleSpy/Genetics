package com.genetics.action;

import com.hypixel.hytale.codec.Codec;
import com.hypixel.hytale.codec.KeyedCodec;
import com.hypixel.hytale.codec.builder.BuilderCodec;
import org.joml.Vector3d;

import javax.annotation.Nonnull;

/** {@code { "Type": "AreaImpulse", "Radius": 5, "Force": 1.2, "Up": 0.5 }}: pushes the NPCs around away from the entity. */
public class AreaImpulseAction extends Action {
    public static final String TYPE = "AreaImpulse";

    public static final BuilderCodec<AreaImpulseAction> CODEC = BuilderCodec.builder(AreaImpulseAction.class, AreaImpulseAction::new, BASE_CODEC)
            .append(new KeyedCodec<>("Radius", Codec.DOUBLE), (t, v) -> t.radius = v, t -> t.radius).add()
            .append(new KeyedCodec<>("Force", Codec.DOUBLE), (t, v) -> t.force = v, t -> t.force).add()
            .append(new KeyedCodec<>("Up", Codec.DOUBLE), (t, v) -> t.up = v, t -> t.up).add()
            .build();

    private double radius = 5;
    private double force = 1;
    private double up = 0.4;

    @Override
    public void run(@Nonnull ActionContext context) {
        Vector3d center = Nearby.position(context, context.entity());
        if (center == null) return;
        for (var ref : Nearby.npcs(context, radius)) {
            Vector3d at = Nearby.position(context, ref);
            if (at != null) ImpulseAction.push(context.buffer(), ref, ImpulseAction.away(center, at, force, up), false);
        }
    }
}
