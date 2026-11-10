package com.genetics.effect;

import com.hypixel.hytale.component.ArchetypeChunk;
import com.hypixel.hytale.component.CommandBuffer;
import com.hypixel.hytale.component.Component;
import com.hypixel.hytale.component.ComponentAccessor;
import com.hypixel.hytale.component.ComponentType;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.component.query.Query;
import com.hypixel.hytale.component.system.tick.EntityTickingSystem;
import com.hypixel.hytale.server.core.asset.type.entityeffect.config.EntityEffect;
import com.hypixel.hytale.server.core.asset.type.entityeffect.config.OverlapBehavior;
import com.hypixel.hytale.server.core.asset.type.model.config.Model;
import com.hypixel.hytale.server.core.asset.type.model.config.ModelAsset;
import com.hypixel.hytale.server.core.entity.effect.EffectControllerComponent;
import com.hypixel.hytale.server.core.modules.entity.component.ModelComponent;
import com.hypixel.hytale.server.core.modules.entity.damage.DeathComponent;
import com.hypixel.hytale.server.core.modules.entity.player.PlayerSkinComponent;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;

import javax.annotation.Nonnull;

/**
 * Turns an entity into any mob for a while. Vanilla morph potions need one effect per model; here the model is
 * swapped in code, and the {@value #EFFECT_ID} effect only shows the icon and timer. When that effect ends (or is
 * cleared, e.g. on respawn), the original model comes back. Models aren't saved, so a morph never survives a logout.
 */
public class MorphSystem extends EntityTickingSystem<EntityStore> {
    public static final String EFFECT_ID = "Genetics_Morph";

    /** The entity's model before the morph. Not saved. */
    public static final class State implements Component<EntityStore> {
        private static ComponentType<EntityStore, State> componentType;
        final Model original;

        public State() {
            this(null);
        }

        State(Model original) {
            this.original = original;
        }

        public static void setComponentType(@Nonnull ComponentType<EntityStore, State> type) {
            componentType = type;
        }

        public static ComponentType<EntityStore, State> getComponentType() {
            return componentType;
        }

        @Nonnull
        @Override
        public Component<EntityStore> clone() {
            return new State(original);
        }
    }

    /** Morphs the entity into a model for some seconds. Returns false if the model or the entity's model is missing. */
    public static boolean morph(@Nonnull Ref<EntityStore> ref, @Nonnull ComponentAccessor<EntityStore> accessor, @Nonnull String modelId, double seconds) {
        ModelAsset asset = ModelAsset.getAssetMap().getAsset(modelId);
        ModelComponent current = accessor.getComponent(ref, ModelComponent.getComponentType());
        EntityEffect effect = EntityEffect.getAssetMap().getAsset(EFFECT_ID);
        EffectControllerComponent effects = accessor.getComponent(ref, EffectControllerComponent.getComponentType());
        if (asset == null || current == null || effect == null || effects == null) return false;
        if (accessor.getComponent(ref, State.getComponentType()) == null) {
            accessor.putComponent(ref, State.getComponentType(), new State(current.getModel()));
        }
        accessor.putComponent(ref, ModelComponent.getComponentType(), new ModelComponent(Model.createRandomScaleModel(asset)));
        effects.addEffect(ref, effect, (float) seconds, OverlapBehavior.OVERWRITE, accessor);
        return true;
    }

    @Nonnull
    @Override
    public Query<EntityStore> getQuery() {
        return Query.and(State.getComponentType(), EffectControllerComponent.getComponentType(), Query.not(DeathComponent.getComponentType()));
    }

    @Override
    public void tick(float dt, int index, @Nonnull ArchetypeChunk<EntityStore> chunk, @Nonnull Store<EntityStore> store,
                     @Nonnull CommandBuffer<EntityStore> buffer) {
        EffectControllerComponent effects = chunk.getComponent(index, EffectControllerComponent.getComponentType());
        State state = chunk.getComponent(index, State.getComponentType());
        int effectIndex = EntityEffect.getAssetMap().getIndex(EFFECT_ID);
        if (effects == null || state == null || (effectIndex != Integer.MIN_VALUE && effects.hasEffect(effectIndex))) return;

        Ref<EntityStore> ref = chunk.getReferenceTo(index);
        if (state.original != null) buffer.putComponent(ref, ModelComponent.getComponentType(), new ModelComponent(state.original));
        PlayerSkinComponent skin = chunk.getComponent(index, PlayerSkinComponent.getComponentType());
        if (skin != null) skin.setNetworkOutdated();
        buffer.removeComponent(ref, State.getComponentType());
    }
}
