package com.genetics.interaction;

import com.hypixel.hytale.codec.builder.BuilderCodec;
import com.hypixel.hytale.component.Component;
import com.hypixel.hytale.component.ComponentType;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;

import javax.annotation.Nonnull;

/**
 * Marks a mob that was already sampled, for mobs that can only be sampled once each (those with a signature mutation,
 * such as bosses). Saved with the mob.
 */
public class SampledOnce implements Component<EntityStore> {
    public static final String ID = "Genetics_SampledOnce";
    public static final BuilderCodec<SampledOnce> CODEC = BuilderCodec.builder(SampledOnce.class, SampledOnce::new).build();

    private static ComponentType<EntityStore, SampledOnce> componentType;

    public static void setComponentType(@Nonnull ComponentType<EntityStore, SampledOnce> type) {
        componentType = type;
    }

    @Nonnull
    public static ComponentType<EntityStore, SampledOnce> getComponentType() {
        return componentType;
    }

    @Override
    public Component<EntityStore> clone() {
        return new SampledOnce();
    }
}
