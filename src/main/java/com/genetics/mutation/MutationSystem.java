package com.genetics.mutation;

import com.genetics.action.Action;
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
import com.hypixel.hytale.component.ComponentAccessor;
import com.hypixel.hytale.component.ComponentType;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.component.query.Query;
import com.hypixel.hytale.component.system.tick.EntityTickingSystem;
import com.hypixel.hytale.protocol.MovementStates;
import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.asset.type.entityeffect.config.EntityEffect;
import com.hypixel.hytale.server.core.entity.effect.EffectControllerComponent;
import com.hypixel.hytale.server.core.entity.movement.MovementStatesComponent;
import com.hypixel.hytale.server.core.modules.entity.component.TransformComponent;
import com.hypixel.hytale.server.core.modules.entity.damage.DeathComponent;
import com.hypixel.hytale.server.core.modules.entitystats.EntityStatMap;
import com.hypixel.hytale.server.core.modules.entitystats.EntityStatValue;
import com.hypixel.hytale.server.core.modules.entitystats.asset.DefaultEntityStatTypes;
import com.hypixel.hytale.server.core.modules.time.WorldTimeResource;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Runs the behaviors of the mutations in each entity's active genome (design doc §3.3). Every tick it reads the
 * entity's health, movement, effects and the time of day, checks each tick trigger, and runs the actions of those that
 * fire and aren't on cooldown. Event triggers (hits, kills, hurt, killing blows) are run by
 * {@link MutationDamageSystems}, sharing this system's per-entity {@link State}. Mutation stats and effects are applied
 * with the rest of the genome by GenomeApplier.
 */
public class MutationSystem extends EntityTickingSystem<EntityStore> {
    /** Below this sunlight factor it counts as night. */
    private static final double NIGHT_SUNLIGHT = 0.25;
    /** Above this sunlight factor it counts as day. */
    private static final double DAY_SUNLIGHT = 0.5;
    /** How long after sprinting a jump still counts as a sprint jump. */
    private static final double SPRINT_MEMORY = 1.5;

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
        List<MutationEntry> mutations = mutations(genome);
        if (mutations.isEmpty()) return;

        Ref<EntityStore> ref = chunk.getReferenceTo(index);
        State state = chunk.getComponent(index, State.getComponentType());
        if (state == null) {
            state = new State();
            buffer.putComponent(ref, State.getComponentType(), state);
        }
        state.clock += dt;

        EntityStatMap stats = chunk.getComponent(index, EntityStatMap.getComponentType());
        EntityStatValue health = stats != null ? stats.get(DefaultEntityStatTypes.getHealth()) : null;
        double current = health != null ? health.get() : 0;
        double max = health != null ? health.getMax() : 0;
        double damage = state.lastHealth >= 0 ? Math.max(0, state.lastHealth - current) : 0;
        state.lastHealth = current;

        MovementStatesComponent movement = chunk.getComponent(index, MovementStatesComponent.getComponentType());
        MovementStates states = movement != null ? movement.getMovementStates() : null;
        if (states != null && states.sprinting) state.lastSprint = state.clock;
        TransformComponent transform = chunk.getComponent(index, TransformComponent.getComponentType());
        EffectControllerComponent effects = chunk.getComponent(index, EffectControllerComponent.getComponentType());
        WorldTimeResource time = store.getResource(WorldTimeResource.getResourceType());
        double sunlight = time != null ? time.getSunlightFactor() : 1;
        Behavior.Input input = new Behavior.Input(max > 0 ? current / max : 1, damage, sunlight < NIGHT_SUNLIGHT, sunlight > DAY_SUNLIGHT,
                states, transform != null ? transform.getPosition().y : 0, dt, state.clock - state.lastSprint < SPRINT_MEMORY,
                id -> hasEffect(effects, id));
        state.lastInput = input;

        ActionContext context = new ActionContext(ref, buffer, genome.source());
        state.actions.tick(dt, context);
        state.cooldowns.replaceAll((key, left) -> left - dt);

        for (MutationEntry mutation : mutations) {
            Behavior[] behaviors = mutation.getBehaviors();
            for (int i = 0; i < behaviors.length; i++) {
                Behavior behavior = behaviors[i];
                if (behavior instanceof Behavior.EventBehavior) continue;
                String key = key(mutation, i);
                if (!behavior.tick(input, state.memory(key)) || state.onCooldown(key)) continue;
                fire(state, key, behavior, behavior.getActions(), context, buffer);
            }
        }
    }

    /** Starts a behavior's cooldown, runs actions on the context and shows its message. */
    static void fire(@Nonnull State state, @Nonnull String key, @Nonnull Behavior behavior, @Nonnull Action[] actions,
                     @Nonnull ActionContext context, @Nonnull ComponentAccessor<EntityStore> accessor) {
        state.cooldowns.put(key, behavior.getCooldown());
        state.actions.start(actions, context);
        if (behavior.getMessage() != null) {
            PlayerRef player = accessor.getComponent(context.entity(), PlayerRef.getComponentType());
            if (player != null) player.sendMessage(Message.translation(behavior.getMessage()));
        }
    }

    /** The mutations of a genome that have behaviors. */
    @Nonnull
    static List<MutationEntry> mutations(@Nullable Genome genome) {
        List<MutationEntry> out = new ArrayList<>();
        if (genome == null) return out;
        for (Gene gene : genome.genes()) {
            if (gene instanceof Gene.Mutation m) {
                MutationEntry entry = Codex.mutation(m.mutation());
                if (entry != null && entry.getBehaviors().length > 0) out.add(entry);
            }
        }
        return out;
    }

    @Nonnull
    static String key(@Nonnull MutationEntry mutation, int behavior) {
        return mutation.getId() + "#" + behavior;
    }

    private static boolean hasEffect(@Nullable EffectControllerComponent effects, @Nonnull String id) {
        if (effects == null) return false;
        int index = EntityEffect.getAssetMap().getIndex(id);
        return index != Integer.MIN_VALUE && effects.hasEffect(index);
    }

    /** An entity's State, created if missing (for event triggers on an entity whose tick hasn't run yet). */
    @Nonnull
    static State state(@Nonnull Ref<EntityStore> ref, @Nonnull ComponentAccessor<EntityStore> accessor) {
        State state = accessor.getComponent(ref, State.getComponentType());
        if (state == null) {
            state = new State();
            accessor.putComponent(ref, State.getComponentType(), state);
        }
        return state;
    }

    /** Per-entity bookkeeping, rebuilt on load: cooldowns, trigger memories, stacks and what was seen last tick. */
    public static class State implements Component<EntityStore> {
        private static ComponentType<EntityStore, State> componentType;

        final Map<String, Double> cooldowns = new HashMap<>();
        final Map<String, Behavior.Memory> memories = new HashMap<>();
        /** Stack key → {count, clock time it expires}. */
        final Map<String, double[]> stacks = new HashMap<>();
        final ActionRunner actions = new ActionRunner();
        double lastHealth = -1;
        double clock;
        double lastSprint = -100;
        double invulnerableUntil = -1;
        @Nonnull
        Behavior.Input lastInput = Behavior.Input.NONE;

        public static void setComponentType(@Nonnull ComponentType<EntityStore, State> type) {
            componentType = type;
        }

        @Nonnull
        public static ComponentType<EntityStore, State> getComponentType() {
            return componentType;
        }

        @Nonnull
        Behavior.Memory memory(@Nonnull String key) {
            return memories.computeIfAbsent(key, k -> new Behavior.Memory());
        }

        boolean onCooldown(@Nonnull String key) {
            return cooldowns.getOrDefault(key, 0.0) > 0;
        }

        int stacks(@Nonnull String key) {
            double[] stack = stacks.get(key);
            if (stack == null) return 0;
            if (stack[1] < clock) {
                stacks.remove(key);
                return 0;
            }
            return (int) stack[0];
        }

        /** Adds to a stack (capped), refreshing its expiry. Returns the new count. */
        int addStacks(@Nonnull String key, int gain, int max, double window) {
            int count = Math.min(Math.max(1, max), stacks(key) + gain);
            stacks.put(key, new double[]{count, clock + window});
            return count;
        }

        void clearStacks(@Nonnull String key) {
            stacks.remove(key);
        }

        @Override
        public Component<EntityStore> clone() {
            return new State();
        }
    }
}
