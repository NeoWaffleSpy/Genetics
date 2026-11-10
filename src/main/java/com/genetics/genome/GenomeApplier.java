package com.genetics.genome;

import com.genetics.codex.Codex;
import com.genetics.codex.MutationEntry;
import com.hypixel.hytale.component.ComponentAccessor;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.server.core.asset.type.entityeffect.config.EntityEffect;
import com.hypixel.hytale.server.core.asset.type.entityeffect.config.OverlapBehavior;
import com.hypixel.hytale.server.core.asset.type.entityeffect.config.RemovalBehavior;
import com.hypixel.hytale.server.core.entity.effect.ActiveEntityEffect;
import com.hypixel.hytale.server.core.entity.effect.EffectControllerComponent;
import com.hypixel.hytale.server.core.modules.entitystats.EntityStatMap;
import com.hypixel.hytale.server.core.modules.entitystats.EntityStatValue;
import com.hypixel.hytale.server.core.modules.entitystats.asset.EntityStatType;
import com.hypixel.hytale.server.core.modules.entitystats.modifier.Modifier;
import com.hypixel.hytale.server.core.modules.entitystats.modifier.StaticModifier;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

/**
 * Turns a genome into changes on an entity: one max-stat modifier per stat (all of the genome's genes for that stat
 * summed up) and one infinite effect per permanent effect gene. Mutations add their Codex stats and effects.
 * <p>
 * Stat genes are a percentage of the stat's <b>base</b> max, applied as an additive modifier so they stack
 * predictably with vanilla modifiers (armor, food buffs).
 */
public final class GenomeApplier {
    /** Key of the genome's modifier on each stat. */
    public static final String MODIFIER_KEY = "Genetics_Genome";

    private GenomeApplier() {
    }

    /**
     * Replaces the entity's previous genome with a new one (the override rule, design doc §6): the previous stat
     * modifiers and permanent effects are removed. Temporary effects that are already running are left alone.
     */
    public static void replace(@Nonnull Ref<EntityStore> ref, @Nonnull ComponentAccessor<EntityStore> accessor,
                               @Nullable Genome previous, @Nullable Genome next) {
        Set<String> keep = next != null ? permanentEffects(next) : Set.of();
        EffectControllerComponent effects = accessor.getComponent(ref, EffectControllerComponent.getComponentType());
        if (previous != null && effects != null) {
            for (String effect : permanentEffects(previous)) {
                if (keep.contains(effect)) continue;
                int index = EntityEffect.getAssetMap().getIndex(effect);
                ActiveEntityEffect active = index != Integer.MIN_VALUE ? effects.getActiveEffects().get(index) : null;
                if (active != null && active.isInfinite()) effects.removeEffect(ref, index, RemovalBehavior.COMPLETE, accessor);
            }
        }
        ensure(ref, accessor, next);
    }

    /**
     * Makes sure the entity carries exactly this genome's stat modifiers and permanent effects. Safe to call
     * repeatedly: it only changes what is missing or different (used after death, which clears effects, and on join).
     */
    public static void ensure(@Nonnull Ref<EntityStore> ref, @Nonnull ComponentAccessor<EntityStore> accessor, @Nullable Genome genome) {
        EntityStatMap stats = accessor.getComponent(ref, EntityStatMap.getComponentType());
        if (stats != null) {
            Map<String, Double> totals = genome != null ? statTotals(genome) : Map.of();
            for (int i = 0; i < stats.size(); i++) {
                EntityStatValue value = stats.get(i);
                EntityStatType type = EntityStatType.getAssetMap().getAsset(i);
                if (value == null || type == null) continue;
                Double percent = totals.get(value.getId());
                Modifier current = stats.getModifier(i, MODIFIER_KEY);
                if (percent == null || percent == 0) {
                    if (current != null) stats.removeModifier(i, MODIFIER_KEY);
                    continue;
                }
                float amount = (float) (type.getMax() * percent / 100);
                if (current instanceof StaticModifier modifier && modifier.getAmount() == amount
                        && modifier.getCalculationType() == StaticModifier.CalculationType.ADDITIVE) continue;
                stats.putModifier(i, MODIFIER_KEY, new StaticModifier(Modifier.ModifierTarget.MAX, StaticModifier.CalculationType.ADDITIVE, amount));
            }
        }

        EffectControllerComponent effects = accessor.getComponent(ref, EffectControllerComponent.getComponentType());
        if (effects != null && genome != null) {
            for (String id : permanentEffects(genome)) {
                int index = EntityEffect.getAssetMap().getIndex(id);
                EntityEffect effect = index != Integer.MIN_VALUE ? EntityEffect.getAssetMap().getAsset(index) : null;
                if (effect == null) continue;
                ActiveEntityEffect active = effects.getActiveEffects().get(index);
                if (active == null || !active.isInfinite()) effects.addInfiniteEffect(ref, index, effect, accessor);
            }
        }
    }

    /** Starts a temporary effect gene. Overwrites the remaining time of the same effect if it is already running. */
    public static boolean startTemporary(@Nonnull Ref<EntityStore> ref, @Nonnull ComponentAccessor<EntityStore> accessor, @Nonnull Gene.Effect gene) {
        EffectControllerComponent effects = accessor.getComponent(ref, EffectControllerComponent.getComponentType());
        EntityEffect effect = EntityEffect.getAssetMap().getAsset(gene.effect());
        if (effects == null || effect == null || gene.duration() <= 0) return false;
        return effects.addEffect(ref, effect, (float) gene.duration(), OverlapBehavior.OVERWRITE, accessor);
    }

    /** The genome's total percent per stat, mutations included. */
    @Nonnull
    public static Map<String, Double> statTotals(@Nonnull Genome genome) {
        Map<String, Double> totals = new LinkedHashMap<>();
        for (Gene gene : genome.genes()) {
            switch (gene) {
                case Gene.Stat stat -> totals.merge(stat.stat(), stat.percent(), Double::sum);
                case Gene.Mutation mutation -> {
                    MutationEntry entry = Codex.mutation(mutation.mutation());
                    if (entry != null) entry.getStats().forEach((stat, percent) -> totals.merge(stat, percent, Double::sum));
                }
                case Gene.Effect ignored -> {
                }
            }
        }
        return totals;
    }

    /** The genome's permanent effect ids, mutations included. */
    @Nonnull
    public static Set<String> permanentEffects(@Nonnull Genome genome) {
        Set<String> out = new LinkedHashSet<>();
        for (Gene gene : genome.genes()) {
            switch (gene) {
                case Gene.Effect effect -> {
                    if (effect.permanent()) out.add(effect.effect());
                }
                case Gene.Mutation mutation -> {
                    MutationEntry entry = Codex.mutation(mutation.mutation());
                    if (entry != null) out.addAll(java.util.List.of(entry.getEffects()));
                }
                case Gene.Stat ignored -> {
                }
            }
        }
        return out;
    }
}
