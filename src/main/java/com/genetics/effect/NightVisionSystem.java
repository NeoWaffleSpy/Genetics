package com.genetics.effect;

import com.hypixel.hytale.component.ArchetypeChunk;
import com.hypixel.hytale.component.CommandBuffer;
import com.hypixel.hytale.component.Component;
import com.hypixel.hytale.component.ComponentType;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.component.query.Query;
import com.hypixel.hytale.component.system.tick.EntityTickingSystem;
import com.hypixel.hytale.protocol.ColorLight;
import com.hypixel.hytale.server.core.asset.type.entityeffect.config.EntityEffect;
import com.hypixel.hytale.server.core.entity.effect.EffectControllerComponent;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.modules.entity.component.DynamicLight;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;

import javax.annotation.Nonnull;

/**
 * Gives the {@value #EFFECT_ID} effect its behavior. Effects can't change how the world is lit, so while a player
 * has the effect they carry a soft light around them, like an invisible lantern. The light is removed when the
 * effect ends. A player who already has a light from something else keeps it.
 */
public class NightVisionSystem extends EntityTickingSystem<EntityStore> {
    public static final String EFFECT_ID = "Genetics_NightVision";
    private static final byte RADIUS = 14;
    private static final byte RED = 9;
    private static final byte GREEN = 9;
    private static final byte BLUE = 11;

    /** Marks a light that this system added, so it never removes someone else's. Not saved. */
    public static final class Light implements Component<EntityStore> {
        private static ComponentType<EntityStore, Light> componentType;

        public static void setComponentType(@Nonnull ComponentType<EntityStore, Light> type) {
            componentType = type;
        }

        public static ComponentType<EntityStore, Light> getComponentType() {
            return componentType;
        }

        @Nonnull
        @Override
        public Component<EntityStore> clone() {
            return new Light();
        }
    }

    @Nonnull
    @Override
    public Query<EntityStore> getQuery() {
        return Query.and(Player.getComponentType(), EffectControllerComponent.getComponentType());
    }

    @Override
    public void tick(float dt, int index, @Nonnull ArchetypeChunk<EntityStore> chunk, @Nonnull Store<EntityStore> store,
                     @Nonnull CommandBuffer<EntityStore> commandBuffer) {
        EffectControllerComponent effects = chunk.getComponent(index, EffectControllerComponent.getComponentType());
        if (effects == null) return;
        int effectIndex = EntityEffect.getAssetMap().getIndex(EFFECT_ID);
        boolean active = effectIndex != Integer.MIN_VALUE && effects.hasEffect(effectIndex);
        boolean ours = chunk.getComponent(index, Light.getComponentType()) != null;
        Ref<EntityStore> ref = chunk.getReferenceTo(index);

        if (active && !ours && chunk.getComponent(index, DynamicLight.getComponentType()) == null) {
            commandBuffer.addComponent(ref, DynamicLight.getComponentType(), new DynamicLight(new ColorLight(RADIUS, RED, GREEN, BLUE)));
            commandBuffer.addComponent(ref, Light.getComponentType(), new Light());
        } else if (!active && ours) {
            commandBuffer.tryRemoveComponent(ref, DynamicLight.getComponentType());
            commandBuffer.removeComponent(ref, Light.getComponentType());
        }
    }
}
