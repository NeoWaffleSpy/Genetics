package com.genetics.mutation;

import com.genetics.action.ActionContext;
import com.genetics.action.RestoreStatAction;
import com.genetics.codex.MutationEntry;
import com.genetics.genome.ActiveGenome;
import com.genetics.genome.Genome;
import com.genetics.journal.Catalogue;
import com.hypixel.hytale.component.ArchetypeChunk;
import com.hypixel.hytale.component.CommandBuffer;
import com.hypixel.hytale.component.ComponentAccessor;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.component.SystemGroup;
import com.hypixel.hytale.component.dependency.Dependency;
import com.hypixel.hytale.component.dependency.Order;
import com.hypixel.hytale.component.dependency.SystemDependency;
import com.hypixel.hytale.component.query.Query;
import com.hypixel.hytale.server.core.asset.type.entityeffect.config.EntityEffect;
import com.hypixel.hytale.server.core.entity.effect.EffectControllerComponent;
import com.hypixel.hytale.server.core.entity.knockback.KnockbackComponent;
import com.hypixel.hytale.server.core.modules.entity.damage.Damage;
import com.hypixel.hytale.server.core.modules.entity.damage.DamageCause;
import com.hypixel.hytale.server.core.modules.entity.damage.DamageEventSystem;
import com.hypixel.hytale.server.core.modules.entity.damage.DamageModule;
import com.hypixel.hytale.server.core.modules.entity.damage.DamageSystems;
import com.hypixel.hytale.server.core.modules.entitystats.EntityStatMap;
import com.hypixel.hytale.server.core.modules.entitystats.EntityStatValue;
import com.hypixel.hytale.server.core.modules.entitystats.asset.DefaultEntityStatTypes;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import com.hypixel.hytale.server.npc.entities.NPCEntity;
import com.hypixel.hytale.server.npc.role.support.MarkedEntitySupport;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.WeakHashMap;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Runs the event triggers of mutations from damage events (design doc §3.3). Damage events are dispatched on the entity
 * that is hurt; the attacker comes from the event's source.
 * <ul>
 *   <li>{@link Filter} (before damage is applied, after armor): invulnerability windows, OnLethal (survive a killing
 *   blow), Bulwark's knockback immunity, and the attacker's OnHit decisions and damage bonuses.</li>
 *   <li>{@link Inspect} (after damage is applied): the OnHit effects (healing, actions), OnKill, and the victim's
 *   OnHurt reactions.</li>
 * </ul>
 * Damage from effects (burn, poison ticks) never counts as a hit.
 */
public final class MutationDamageSystems {
    /** Effect that grants knockback immunity while active (Bulwark). */
    public static final String KNOCKBACK_IMMUNE_EFFECT = "Genetics_Bulwark";

    /** OnHit behaviors that fired in Filter, run in Inspect once the damage went through. */
    private static final Map<Damage, List<HitFire>> FIRED = Collections.synchronizedMap(new WeakHashMap<>());

    private record HitFire(Ref<EntityStore> attacker, String key, Behavior.OnHit behavior, @Nullable Genome.Source source) {
    }

    private MutationDamageSystems() {
    }

    /** The entity that dealt a hit, or null (environment, commands, and effect damage such as burn or poison ticks). */
    @Nullable
    static Ref<EntityStore> attacker(@Nonnull Damage damage) {
        Damage.Source source = damage.getSource();
        if (source instanceof Damage.EntityEffectSource) return null;
        if (source instanceof Damage.EntitySource entity) {
            Ref<EntityStore> ref = entity.getRef();
            return ref != null && ref.isValid() ? ref : null;
        }
        return null;
    }

    static boolean melee(@Nonnull Damage damage) {
        Damage.Source source = damage.getSource();
        return source instanceof Damage.EntitySource && !(source instanceof Damage.ProjectileSource) && !(source instanceof Damage.EntityEffectSource);
    }

    @Nullable
    private static Genome genome(@Nonnull Ref<EntityStore> ref, @Nonnull ComponentAccessor<EntityStore> accessor) {
        ActiveGenome active = accessor.getComponent(ref, ActiveGenome.getComponentType());
        return active != null ? active.getGenome() : null;
    }

    @Nullable
    private static EntityStatValue health(@Nonnull Ref<EntityStore> ref, @Nonnull ComponentAccessor<EntityStore> accessor) {
        EntityStatMap stats = accessor.getComponent(ref, EntityStatMap.getComponentType());
        return stats != null ? stats.get(DefaultEntityStatTypes.getHealth()) : null;
    }

    private static boolean hasEffect(@Nonnull Ref<EntityStore> ref, @Nonnull ComponentAccessor<EntityStore> accessor, @Nonnull String id) {
        EffectControllerComponent effects = accessor.getComponent(ref, EffectControllerComponent.getComponentType());
        int index = EntityEffect.getAssetMap().getIndex(id);
        return effects != null && index != Integer.MIN_VALUE && effects.hasEffect(index);
    }

    /** True if an NPC has the entity among its targets (it has noticed it). */
    private static boolean isTargeting(@Nonnull Ref<EntityStore> npc, @Nonnull Ref<EntityStore> entity, @Nonnull ComponentAccessor<EntityStore> accessor) {
        MarkedEntitySupport marked = accessor.getComponent(npc, MarkedEntitySupport.getComponentType());
        if (marked == null || marked.getEntityTargets() == null) return false;
        for (Ref<EntityStore> target : marked.getEntityTargets()) if (entity.equals(target)) return true;
        return false;
    }

    // ---------------------------------------------------------------- Filter

    /** Before damage is applied (after armor): may cancel or change it. */
    public static class Filter extends DamageEventSystem {
        @Nullable
        @Override
        public SystemGroup<EntityStore> getGroup() {
            return DamageModule.get().getFilterDamageGroup();
        }

        @Nonnull
        @Override
        public Query<EntityStore> getQuery() {
            return EntityStatMap.getComponentType();
        }

        @Nonnull
        @Override
        public Set<Dependency<EntityStore>> getDependencies() {
            return Set.of(new SystemDependency<>(Order.AFTER, DamageSystems.ArmorDamageReduction.class));
        }

        @Override
        public void handle(int index, @Nonnull ArchetypeChunk<EntityStore> chunk, @Nonnull Store<EntityStore> store,
                           @Nonnull CommandBuffer<EntityStore> buffer, @Nonnull Damage damage) {
            if (damage.isCancelled()) return;
            Ref<EntityStore> target = chunk.getReferenceTo(index);
            Ref<EntityStore> attacker = attacker(damage);
            victim(target, attacker, damage, buffer);
            if (!damage.isCancelled() && attacker != null && !attacker.equals(target)) hitter(attacker, target, damage, buffer);
        }

        private static void victim(@Nonnull Ref<EntityStore> target, @Nullable Ref<EntityStore> attacker, @Nonnull Damage damage,
                                   @Nonnull CommandBuffer<EntityStore> buffer) {
            Genome genome = genome(target, buffer);
            List<MutationEntry> mutations = MutationSystem.mutations(genome);
            if (mutations.isEmpty()) return;
            MutationSystem.State state = MutationSystem.state(target, buffer);
            if (state.invulnerableUntil > state.clock) {
                damage.setCancelled(true);
                return;
            }
            if (hasEffect(target, buffer, KNOCKBACK_IMMUNE_EFFECT)) {
                damage.removeMetaObject(Damage.KNOCKBACK_COMPONENT);
                buffer.tryRemoveComponent(target, KnockbackComponent.getComponentType());
            }
            EntityStatValue health = health(target, buffer);
            if (health == null || damage.getAmount() < health.get() - health.getMin()) return;
            for (MutationEntry mutation : mutations) {
                Behavior[] behaviors = mutation.getBehaviors();
                for (int i = 0; i < behaviors.length; i++) {
                    if (!(behaviors[i] instanceof Behavior.OnLethal lethal)) continue;
                    String key = MutationSystem.key(mutation, i);
                    if (state.onCooldown(key) || !lethal.conditionsMet(state.lastInput)) continue;
                    damage.setAmount((float) Math.max(0, Math.floor(health.get() - health.getMin() - 1)));
                    state.invulnerableUntil = state.clock + lethal.invulnerable;
                    MutationSystem.fire(state, key, lethal, lethal.getActions(), new ActionContext(target, buffer, genome.source(), attacker), buffer);
                    return;
                }
            }
        }

        private static void hitter(@Nonnull Ref<EntityStore> attacker, @Nonnull Ref<EntityStore> target, @Nonnull Damage damage,
                                   @Nonnull CommandBuffer<EntityStore> buffer) {
            Genome genome = genome(attacker, buffer);
            List<MutationEntry> mutations = MutationSystem.mutations(genome);
            if (mutations.isEmpty()) return;
            MutationSystem.State state = MutationSystem.state(attacker, buffer);
            Behavior.Input input = state.lastInput;
            EntityStatValue targetHealth = health(target, buffer);
            double targetRatio = targetHealth != null && targetHealth.getMax() > 0 ? targetHealth.get() / targetHealth.getMax() : 1;
            boolean melee = melee(damage);
            ThreadLocalRandom random = ThreadLocalRandom.current();

            double bonus = 0;
            List<HitFire> fired = new ArrayList<>();
            for (MutationEntry mutation : mutations) {
                Behavior[] behaviors = mutation.getBehaviors();
                for (int i = 0; i < behaviors.length; i++) {
                    if (!(behaviors[i] instanceof Behavior.OnHit hit)) continue;
                    String key = MutationSystem.key(mutation, i);
                    if (state.onCooldown(key) || !hit.conditionsMet(input)) continue;
                    if (hit.melee && !melee) continue;
                    if (hit.targetHealthBelow > 0 && targetRatio >= hit.targetHealthBelow) continue;
                    if (hit.unnoticed && isTargeting(target, attacker, buffer)) continue;
                    if (hit.everyNth > 1) {
                        Behavior.Memory memory = state.memory(key);
                        if (!target.equals(memory.lastTarget)) {
                            memory.lastTarget = target;
                            memory.count = 0;
                        }
                        if (++memory.count % hit.everyNth != 0) continue;
                    }
                    if (hit.chance < 1 && random.nextDouble() >= hit.chance) continue;
                    if (hit.requireStacks > 0) {
                        if (hit.stackKey == null || state.stacks(hit.stackKey) < hit.requireStacks) continue;
                        state.clearStacks(hit.stackKey);
                    }
                    if (hit.stackKey != null && hit.stackGain > 0) {
                        bonus += state.addStacks(hit.stackKey, hit.stackGain, hit.stackMax, hit.stackWindow) * hit.bonusPerStack;
                    }
                    bonus += hit.damageBonus;
                    if (hit.getCooldown() > 0) state.cooldowns.put(key, hit.getCooldown());
                    fired.add(new HitFire(attacker, key, hit, genome.source()));
                }
            }
            if (bonus != 0) damage.setAmount((float) Math.max(0, damage.getAmount() * (1 + bonus)));
            if (!fired.isEmpty()) FIRED.put(damage, fired);
        }
    }

    // ---------------------------------------------------------------- Inspect

    /** After damage is applied: the effects of hits, kills and reactions. */
    public static class Inspect extends DamageEventSystem {
        @Nullable
        @Override
        public SystemGroup<EntityStore> getGroup() {
            return DamageModule.get().getInspectDamageGroup();
        }

        @Nonnull
        @Override
        public Query<EntityStore> getQuery() {
            return EntityStatMap.getComponentType();
        }

        @Override
        public void handle(int index, @Nonnull ArchetypeChunk<EntityStore> chunk, @Nonnull Store<EntityStore> store,
                           @Nonnull CommandBuffer<EntityStore> buffer, @Nonnull Damage damage) {
            List<HitFire> hits = FIRED.remove(damage);
            if (damage.isCancelled() || damage.getAmount() <= 0) return;
            Ref<EntityStore> target = chunk.getReferenceTo(index);
            Ref<EntityStore> attacker = attacker(damage);

            if (hits != null) {
                for (HitFire hit : hits) {
                    MutationSystem.State state = MutationSystem.state(hit.attacker(), buffer);
                    ActionContext context = new ActionContext(hit.attacker(), buffer, hit.source(), target);
                    if (hit.behavior().healFraction > 0) RestoreStatAction.add(context, "Health", damage.getAmount() * hit.behavior().healFraction);
                    MutationSystem.fire(state, hit.key(), hit.behavior(), hit.behavior().getActions(), context, buffer);
                    state.actions.start(hit.behavior().getTargetActions(), context.on(target));
                }
            }
            EntityStatValue health = health(target, buffer);
            if (attacker != null && !attacker.equals(target) && health != null && health.get() <= health.getMin()) kill(attacker, target, buffer);
            hurt(target, attacker, damage, health, buffer);
        }

        private static void kill(@Nonnull Ref<EntityStore> attacker, @Nonnull Ref<EntityStore> victim, @Nonnull CommandBuffer<EntityStore> buffer) {
            Genome genome = genome(attacker, buffer);
            List<MutationEntry> mutations = MutationSystem.mutations(genome);
            if (mutations.isEmpty()) return;
            MutationSystem.State state = MutationSystem.state(attacker, buffer);
            NPCEntity npc = buffer.getComponent(victim, NPCEntity.getComponentType());
            com.genetics.codex.MobGeneProfile family = npc != null ? Catalogue.profileOf(npc.getRoleName()) : null;
            String species = family != null ? family.getId() : null;
            ThreadLocalRandom random = ThreadLocalRandom.current();
            for (MutationEntry mutation : mutations) {
                Behavior[] behaviors = mutation.getBehaviors();
                for (int i = 0; i < behaviors.length; i++) {
                    if (!(behaviors[i] instanceof Behavior.OnKill onKill)) continue;
                    String key = MutationSystem.key(mutation, i);
                    if (state.onCooldown(key) || !onKill.conditionsMet(state.lastInput)) continue;
                    if (onKill.targetSpecies != null && onKill.targetSpecies.length > 0 && (species == null || !List.of(onKill.targetSpecies).contains(species))) continue;
                    if (onKill.chance < 1 && random.nextDouble() >= onKill.chance) continue;
                    ActionContext context = new ActionContext(attacker, buffer, genome.source(), victim);
                    MutationSystem.fire(state, key, onKill, onKill.getActions(), context, buffer);
                    state.actions.start(onKill.getTargetActions(), context.on(victim));
                }
            }
        }

        private static void hurt(@Nonnull Ref<EntityStore> target, @Nullable Ref<EntityStore> attacker, @Nonnull Damage damage,
                                 @Nullable EntityStatValue health, @Nonnull CommandBuffer<EntityStore> buffer) {
            Genome genome = genome(target, buffer);
            List<MutationEntry> mutations = MutationSystem.mutations(genome);
            if (mutations.isEmpty()) return;
            MutationSystem.State state = MutationSystem.state(target, buffer);
            DamageCause cause = damage.getCause();
            String causeId = cause != null ? cause.getId() : null;
            double fraction = health != null && health.getMax() > 0 ? damage.getAmount() / health.getMax() : 0;
            boolean melee = melee(damage);
            for (MutationEntry mutation : mutations) {
                Behavior[] behaviors = mutation.getBehaviors();
                for (int i = 0; i < behaviors.length; i++) {
                    if (!(behaviors[i] instanceof Behavior.OnHurt onHurt)) continue;
                    String key = MutationSystem.key(mutation, i);
                    if (!onHurt.conditionsMet(state.lastInput)) continue;
                    if (onHurt.causes != null && onHurt.causes.length > 0 && (causeId == null || List.of(onHurt.causes).stream().noneMatch(causeId::equalsIgnoreCase))) continue;
                    if (onHurt.fromEntity && attacker == null) continue;
                    if (onHurt.melee && !melee) continue;
                    if (fraction < onHurt.minFraction || damage.getAmount() < onHurt.minDamage) continue;
                    if (onHurt.clearStacks != null) state.clearStacks(onHurt.clearStacks);
                    if (onHurt.stackKey != null && onHurt.stackGain > 0) state.addStacks(onHurt.stackKey, onHurt.stackGain, onHurt.stackMax, onHurt.stackWindow);
                    if (state.onCooldown(key)) continue;
                    if (onHurt.everyNth > 1) {
                        Behavior.Memory memory = state.memory(key);
                        if (memory.count == 0 || state.clock - memory.timer > onHurt.window) {
                            memory.count = 0;
                            memory.timer = state.clock;
                        }
                        if (++memory.count < onHurt.everyNth) continue;
                        memory.count = 0;
                    }
                    ActionContext context = new ActionContext(target, buffer, genome.source(), attacker);
                    MutationSystem.fire(state, key, onHurt, onHurt.getActions(), context, buffer);
                    if (attacker != null) state.actions.start(onHurt.getAttackerActions(), context.on(attacker));
                }
            }
        }
    }
}
