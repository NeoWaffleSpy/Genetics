package com.genetics.action;

import com.hypixel.hytale.codec.Codec;
import com.hypixel.hytale.codec.KeyedCodec;
import com.hypixel.hytale.codec.builder.BuilderCodec;
import com.hypixel.hytale.protocol.ChangeVelocityType;
import com.hypixel.hytale.protocol.VelocityThresholdStyle;
import com.hypixel.hytale.server.core.entity.knockback.KnockbackComponent;
import com.hypixel.hytale.server.core.modules.entity.component.HeadRotation;
import com.hypixel.hytale.server.core.modules.splitvelocity.VelocityConfig;
import org.joml.Vector3d;

import javax.annotation.Nonnull;

/**
 * {@code { "Type": "Impulse", "Up": 0.4, "Back": 0.8, "Side": 0 }}: pushes the entity, relative to where it is
 * looking. Back is negative to push forward, Side is positive to push right, Up can be negative to push into the
 * ground. The push slows down in the air and on the ground like a weapon's knockback, so it never carries far.
 */
public class ImpulseAction extends Action {
    public static final String TYPE = "Impulse";

    public static final BuilderCodec<ImpulseAction> CODEC = BuilderCodec.builder(ImpulseAction.class, ImpulseAction::new, BASE_CODEC)
            .append(new KeyedCodec<>("Up", Codec.DOUBLE), (t, v) -> t.up = v, t -> t.up).add()
            .append(new KeyedCodec<>("Back", Codec.DOUBLE), (t, v) -> t.back = v, t -> t.back).add()
            .append(new KeyedCodec<>("Side", Codec.DOUBLE), (t, v) -> t.side = v, t -> t.side).add()
            .build();

    /** Data values are kept small and readable; this turns them into velocity. */
    private static final double SCALE = 10;

    private double up;
    private double back;
    private double side;

    @Override
    public void run(@Nonnull ActionContext context) {
        HeadRotation head = context.buffer().getComponent(context.entity(), HeadRotation.getComponentType());
        float yaw = head != null ? head.getRotation().yaw() : 0;
        Vector3d forward = new Vector3d(0, 0, -1).rotateY(yaw);
        Vector3d right = new Vector3d(-forward.z, 0, forward.x);
        Vector3d velocity = new Vector3d(forward).mul(-back).add(new Vector3d(right).mul(side)).mul(SCALE);
        velocity.y = up * SCALE;

        KnockbackComponent knockback = context.buffer().ensureAndGetComponent(context.entity(), KnockbackComponent.getComponentType());
        knockback.setVelocity(velocity);
        knockback.setVelocityType(ChangeVelocityType.Set);
        knockback.setVelocityConfig(damping());
        knockback.setDuration(0);
        knockback.setTimer(0);
    }

    /** Same air and ground resistance as the default knockback preset. */
    @Nonnull
    private static VelocityConfig damping() {
        VelocityConfig config = new VelocityConfig();
        config.setAirResistance(0.96F);
        config.setAirResistanceMax(0.0F);
        config.setGroundResistance(0.82F);
        config.setGroundResistanceMax(0.0F);
        config.setThreshold(1.0F);
        config.setStyle(VelocityThresholdStyle.Linear);
        return config;
    }
}
