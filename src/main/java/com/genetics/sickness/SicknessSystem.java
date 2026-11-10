package com.genetics.sickness;

import com.genetics.action.ActionContext;
import com.genetics.codex.Balance;
import com.genetics.codex.SicknessEvent;
import com.genetics.genome.ActiveGenome;
import com.genetics.genome.Genome;
import com.hypixel.hytale.component.ArchetypeChunk;
import com.hypixel.hytale.component.CommandBuffer;
import com.hypixel.hytale.component.ComponentAccessor;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.component.query.Query;
import com.hypixel.hytale.component.system.tick.EntityTickingSystem;
import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.asset.type.entityeffect.config.EntityEffect;
import com.hypixel.hytale.server.core.asset.type.entityeffect.config.OverlapBehavior;
import com.hypixel.hytale.server.core.asset.type.entityeffect.config.RemovalBehavior;
import com.hypixel.hytale.server.core.entity.effect.EffectControllerComponent;
import com.hypixel.hytale.server.core.modules.entity.damage.DeathComponent;
import com.hypixel.hytale.server.core.modules.entitystats.EntityStatMap;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Runs Genetic Sickness for every entity that has been injected (design doc §9.2):
 * <ul>
 *   <li><b>Decay</b>: sickness drops by {@code DecayPerSecond} while the entity is alive.</li>
 *   <li><b>Bands</b>: entering a band tells the player, and the band's effect shows an icon with the time left until
 *   fully healed.</li>
 *   <li><b>Events</b>: in a band with events, a random event from the Sickness Event Codex fires every
 *   {@code EventIntervalMin}–{@code EventIntervalMax} seconds.</li>
 *   <li><b>Death</b>: sickness is kept through death and restored after respawning.</li>
 * </ul>
 */
public class SicknessSystem extends EntityTickingSystem<EntityStore> {
    private static final double STEP = 1;
    /** The first event of a newly reached band comes sooner, so a fresh injection is felt quickly. */
    private static final double FIRST_EVENT_MIN = 8;

    @Nonnull
    @Override
    public Query<EntityStore> getQuery() {
        return Query.and(SicknessState.getComponentType(), EntityStatMap.getComponentType());
    }

    @Override
    public void tick(float dt, int index, @Nonnull ArchetypeChunk<EntityStore> chunk, @Nonnull Store<EntityStore> store,
                     @Nonnull CommandBuffer<EntityStore> buffer) {
        SicknessState state = chunk.getComponent(index, SicknessState.getComponentType());
        EntityStatMap stats = chunk.getComponent(index, EntityStatMap.getComponentType());
        int statIndex = Sickness.statIndex();
        if (state == null || stats == null || statIndex == Integer.MIN_VALUE || stats.get(statIndex) == null) return;
        Ref<EntityStore> ref = chunk.getReferenceTo(index);
        float value = stats.get(statIndex).get();

        // Death: remember the value, and put it back once respawned (respawning resets every stat).
        if (chunk.getComponent(index, DeathComponent.getComponentType()) != null) {
            if (value > 0) state.heldValue = value;
            return;
        }
        if (state.heldValue >= 0) {
            value = stats.setStatValue(statIndex, (float) state.heldValue);
            state.heldValue = -1;
        }

        Balance.Sickness balance = Balance.get().sickness();
        ActionContext context = new ActionContext(ref, buffer, source(chunk, index));
        state.actions.tick(dt, context);
        state.cooldowns.replaceAll((id, left) -> left - dt);

        state.nextStep -= dt;
        boolean step = state.nextStep <= 0;
        if (step) {
            state.nextStep = STEP;
            if (value > 0) value = stats.subtractStatValue(statIndex, (float) (balance.getDecayPerSecond() * STEP));
        }

        Balance.Band band = balance.bandFor(value);
        String bandId = band != null ? band.getId() : null;
        if (state.band == null || !state.band.equals(bandId)) {
            onBandChange(ref, buffer, state, balance, band);
            step = true;
        }
        if (step) showBand(ref, buffer, balance, band, value);

        if (band != null && band.getEventIntervalMin() > 0) {
            state.nextEvent -= dt;
            if (state.nextEvent <= 0) {
                fireEvent(ref, buffer, state, balance, band, context);
                state.nextEvent = between(band.getEventIntervalMin(), band.getEventIntervalMax());
            }
        }
    }

    private static void onBandChange(@Nonnull Ref<EntityStore> ref, @Nonnull CommandBuffer<EntityStore> buffer, @Nonnull SicknessState state,
                                     @Nonnull Balance.Sickness balance, @Nullable Balance.Band band) {
        boolean first = state.band == null;
        boolean worse = band != null && balance.rank(band.getId()) > balance.rank(state.band);
        state.band = band != null ? band.getId() : null;
        if (band != null && band.getEventIntervalMin() > 0 && (first || worse)) {
            state.nextEvent = between(Math.min(FIRST_EVENT_MIN, band.getEventIntervalMin()), band.getEventIntervalMin());
        }
        // Tell the player when it gets worse, and when they are healthy again; stay quiet as it fades between bands.
        boolean healed = !first && band != null && band.getEventIntervalMin() <= 0;
        if (band != null && band.getMessage() != null && ((worse && !first) || healed || (first && band.getEventIntervalMin() > 0))) {
            PlayerRef player = buffer.getComponent(ref, PlayerRef.getComponentType());
            if (player != null) player.sendMessage(Message.translation(band.getMessage()));
        }
    }

    /** Keeps exactly the current band's effect on the entity, lasting until the sickness is gone. */
    private static void showBand(@Nonnull Ref<EntityStore> ref, @Nonnull CommandBuffer<EntityStore> buffer,
                                 @Nonnull Balance.Sickness balance, @Nullable Balance.Band current, float value) {
        EffectControllerComponent effects = buffer.getComponent(ref, EffectControllerComponent.getComponentType());
        if (effects == null) return;
        for (Balance.Band band : balance.getBands()) {
            String id = band.getEffect();
            int effectIndex = id != null ? EntityEffect.getAssetMap().getIndex(id) : Integer.MIN_VALUE;
            if (effectIndex == Integer.MIN_VALUE) continue;
            EntityEffect effect = EntityEffect.getAssetMap().getAsset(effectIndex);
            if (band == current && value > 0 && effect != null) {
                float left = (float) (value / Math.max(0.01, balance.getDecayPerSecond()));
                effects.addEffect(ref, effectIndex, effect, left, OverlapBehavior.OVERWRITE, buffer);
            } else if (effects.hasEffect(effectIndex)) {
                effects.removeEffect(ref, effectIndex, RemovalBehavior.COMPLETE, buffer);
            }
        }
    }

    private static void fireEvent(@Nonnull Ref<EntityStore> ref, @Nonnull CommandBuffer<EntityStore> buffer, @Nonnull SicknessState state,
                                  @Nonnull Balance.Sickness balance, @Nonnull Balance.Band band, @Nonnull ActionContext context) {
        int rank = balance.rank(band.getId());
        List<SicknessEvent> eligible = new ArrayList<>();
        double total = 0;
        for (SicknessEvent event : SicknessEvent.getAssetMap().getAssetMap().values()) {
            if (event.getWeight() <= 0 || balance.rank(event.getMinBand()) > rank) continue;
            if (event.getMaxBand() != null && balance.rank(event.getMaxBand()) < rank) continue;
            if (state.cooldowns.getOrDefault(event.getId(), 0.0) > 0) continue;
            eligible.add(event);
            total += event.getWeight();
        }
        if (eligible.isEmpty()) return;

        double pick = ThreadLocalRandom.current().nextDouble() * total;
        SicknessEvent chosen = eligible.getLast();
        for (SicknessEvent event : eligible) {
            pick -= event.getWeight();
            if (pick < 0) {
                chosen = event;
                break;
            }
        }
        run(state, chosen, context);
    }

    private static void run(@Nonnull SicknessState state, @Nonnull SicknessEvent event, @Nonnull ActionContext context) {
        state.cooldowns.put(event.getId(), event.getCooldown());
        state.actions.start(event.getActions(), context);
        PlayerRef player = context.buffer().getComponent(context.entity(), PlayerRef.getComponentType());
        if (player != null && event.getMessage() != null) {
            String mob = context.source() != null ? context.source().mobName() : "something";
            player.sendMessage(Message.translation(event.getMessage()).param("mob", mob));
        }
    }

    /** Fires an event right away, ignoring bands and cooldowns (used by the debug command). */
    public static void trigger(@Nonnull Ref<EntityStore> ref, @Nonnull ComponentAccessor<EntityStore> accessor, @Nonnull SicknessEvent event) {
        SicknessState state = accessor.getComponent(ref, SicknessState.getComponentType());
        if (state == null) {
            state = new SicknessState();
            accessor.putComponent(ref, SicknessState.getComponentType(), state);
        }
        ActiveGenome active = accessor.getComponent(ref, ActiveGenome.getComponentType());
        Genome genome = active != null ? active.getGenome() : null;
        run(state, event, new ActionContext(ref, accessor, genome != null ? genome.source() : null));
    }

    @Nullable
    private static Genome.Source source(@Nonnull ArchetypeChunk<EntityStore> chunk, int index) {
        ActiveGenome active = chunk.getComponent(index, ActiveGenome.getComponentType());
        Genome genome = active != null ? active.getGenome() : null;
        return genome != null ? genome.source() : null;
    }

    private static double between(double min, double max) {
        return max > min ? min + ThreadLocalRandom.current().nextDouble() * (max - min) : min;
    }
}
