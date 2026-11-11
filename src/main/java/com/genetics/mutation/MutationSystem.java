package com.genetics.mutation;

import com.genetics.action.ActionContext;
import com.genetics.action.ActionRunner;
import com.genetics.codex.Codex;
import com.genetics.codex.MutationEntry;
import com.genetics.genome.ActiveGenome;
import com.genetics.genome.Gene;
import com.genetics.genome.Genome;
import com.hypixel.hytale.component.ArchetypeChunk;
import com.hypixel.hytale.component.CommandBuffer;
import com.hypixel.hytale.component.Component;
import com.hypixel.hytale.component.ComponentType;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.component.query.Query;
import com.hypixel.hytale.component.system.tick.EntityTickingSystem;
import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.modules.entity.damage.DeathComponent;
import com.hypixel.hytale.server.core.modules.entitystats.EntityStatMap;
import com.hypixel.hytale.server.core.modules.entitystats.EntityStatValue;
import com.hypixel.hytale.server.core.modules.entitystats.asset.DefaultEntityStatTypes;
import com.hypixel.hytale.server.core.modules.time.WorldTimeResource;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;

import javax.annotation.Nonnull;
import java.util.HashMap;
import java.util.Map;

/**
 * Runs the behaviors of the mutation in each entity's active genome (design doc §3.3). Every tick it reads the
 * entity's health and the time of day, checks each behavior's trigger, and runs the actions of those that fire and
 * aren't on cooldown. Mutation stats and effects are applied with the rest of the genome by GenomeApplier.
 */
public class MutationSystem extends EntityTickingSystem<EntityStore> {
    /** Below this sunlight factor it counts as night. */
    private static final double NIGHT_SUNLIGHT = 0.25;

    @Nonnull
    @Override
    public Query<EntityStore> getQuery() {
        return Query.and(ActiveGenome.getComponentType(), EntityStatMap.getComponentType());
    }

    @Override
    public void tick(float dt, int index, @Nonnull ArchetypeChunk<EntityStore> chunk, @Nonnull Store<EntityStore> store,
                     @Nonnull CommandBuffer<EntityStore> buffer) {
        ActiveGenome active = chunk.getComponent(index, ActiveGenome.getComponentType());
        Genome genome = active != null ? active.getGenome() : null;
        if (genome == null || chunk.getComponent(index, DeathComponent.getComponentType()) != null) return;
        MutationEntry mutation = null;
        for (Gene gene : genome.genes()) {
            if (gene instanceof Gene.Mutation m) mutation = Codex.mutation(m.mutation());
        }
        if (mutation == null || mutation.getBehaviors().length == 0) return;

        Ref<EntityStore> ref = chunk.getReferenceTo(index);
        State state = chunk.getComponent(index, State.getComponentType());
        if (state == null) {
            state = new State();
            buffer.putComponent(ref, State.getComponentType(), state);
        }

        EntityStatMap stats = chunk.getComponent(index, EntityStatMap.getComponentType());
        EntityStatValue health = stats != null ? stats.get(DefaultEntityStatTypes.getHealth()) : null;
        double current = health != null ? health.get() : 0;
        double max = health != null ? health.getMax() : 0;
        double damage = state.lastHealth >= 0 ? Math.max(0, state.lastHealth - current) : 0;
        state.lastHealth = current;
        WorldTimeResource time = store.getResource(WorldTimeResource.getResourceType());
        Behavior.Input input = new Behavior.Input(max > 0 ? current / max : 1, damage, time != null && time.getSunlightFactor() < NIGHT_SUNLIGHT);

        ActionContext context = new ActionContext(ref, buffer, genome.source());
        state.actions.tick(dt, context);
        state.cooldowns.replaceAll((key, left) -> left - dt);

        Behavior[] behaviors = mutation.getBehaviors();
        for (int i = 0; i < behaviors.length; i++) {
            Behavior behavior = behaviors[i];
            String key = mutation.getId() + "#" + i;
            boolean[] armed = state.armed.computeIfAbsent(key, k -> new boolean[]{true});
            if (!behavior.check(input, armed) || state.cooldowns.getOrDefault(key, 0.0) > 0) continue;
            state.cooldowns.put(key, behavior.getCooldown());
            state.actions.start(behavior.getActions(), context);
            if (behavior.getMessage() != null) {
                PlayerRef player = buffer.getComponent(ref, PlayerRef.getComponentType());
                if (player != null) player.sendMessage(Message.translation(behavior.getMessage()));
            }
        }
    }

    /** Per-entity bookkeeping, rebuilt on load: cooldowns, trigger flags and the health seen last tick. */
    public static class State implements Component<EntityStore> {
        private static ComponentType<EntityStore, State> componentType;

        final Map<String, Double> cooldowns = new HashMap<>();
        final Map<String, boolean[]> armed = new HashMap<>();
        final ActionRunner actions = new ActionRunner();
        double lastHealth = -1;

        public static void setComponentType(@Nonnull ComponentType<EntityStore, State> type) {
            componentType = type;
        }

        @Nonnull
        public static ComponentType<EntityStore, State> getComponentType() {
            return componentType;
        }

        @Override
        public Component<EntityStore> clone() {
            return new State();
        }
    }
}
