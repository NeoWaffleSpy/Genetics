package com.genetics.capacity;

import com.hypixel.hytale.component.ComponentAccessor;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.server.core.modules.entitystats.EntityStatMap;
import com.hypixel.hytale.server.core.modules.entitystats.EntityStatValue;
import com.hypixel.hytale.server.core.modules.entitystats.asset.EntityStatType;
import com.hypixel.hytale.server.core.modules.entitystats.modifier.Modifier;
import com.hypixel.hytale.server.core.modules.entitystats.modifier.StaticModifier;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

/**
 * Genome Capacity (design doc §3.4): an entity stat ({@value #STAT_ID}) whose <b>max</b> is the most Complexity the
 * body can carry and whose <b>value</b> is the Complexity of the active genome, so it reads like a bar ("12 / 20").
 * <p>
 * The base capacity is the stat's own max (20). Genome Expansions raise it with a max modifier, which is saved with
 * the entity and survives death. Genes can't modify it.
 */
public final class GenomeCapacity {
    /** Entity stat id, defined in {@code Server/Entity/Stats/Genetics_GenomeCapacity.json}. */
    public static final String STAT_ID = "Genetics_GenomeCapacity";
    /** Key of the modifier Genome Expansions put on the stat's max. */
    public static final String EXPANSION_KEY = "Genetics_Expansion";

    private GenomeCapacity() {
    }

    public static int statIndex() {
        return EntityStatType.getAssetMap().getIndex(STAT_ID);
    }

    /** The entity's capacity, or the base capacity if it has no such stat. */
    public static float capacity(@Nullable EntityStatMap stats) {
        EntityStatValue value = value(stats);
        if (value != null) return value.getMax();
        EntityStatType type = EntityStatType.getAssetMap().getAsset(STAT_ID);
        return type != null ? type.getMax() : 20;
    }

    public static float capacity(@Nonnull Ref<EntityStore> ref, @Nonnull ComponentAccessor<EntityStore> accessor) {
        return capacity(accessor.getComponent(ref, EntityStatMap.getComponentType()));
    }

    /** Shows the active genome's Complexity as the stat's value. */
    public static void setUsed(@Nullable EntityStatMap stats, double complexity) {
        int index = statIndex();
        EntityStatValue value = value(stats);
        if (value == null || stats == null) return;
        float target = (float) Math.min(complexity, value.getMax());
        if (value.get() != target) stats.setStatValue(index, target);
    }

    /**
     * Raises the capacity to a new value (a Genome Expansion). Returns false, changing nothing, if the capacity is
     * already at or above it.
     */
    public static boolean expandTo(@Nonnull Ref<EntityStore> ref, @Nonnull ComponentAccessor<EntityStore> accessor, double newCapacity) {
        EntityStatMap stats = accessor.getComponent(ref, EntityStatMap.getComponentType());
        EntityStatType type = EntityStatType.getAssetMap().getAsset(STAT_ID);
        if (value(stats) == null || type == null || newCapacity <= capacity(stats)) return false;
        stats.putModifier(statIndex(), EXPANSION_KEY,
                new StaticModifier(Modifier.ModifierTarget.MAX, StaticModifier.CalculationType.ADDITIVE, (float) (newCapacity - type.getMax())));
        return true;
    }

    /** Sets the capacity to any value, up or down (test tool). Values at or below the base remove the expansion. */
    public static void setCapacity(@Nonnull Ref<EntityStore> ref, @Nonnull ComponentAccessor<EntityStore> accessor, double newCapacity) {
        EntityStatMap stats = accessor.getComponent(ref, EntityStatMap.getComponentType());
        EntityStatType type = EntityStatType.getAssetMap().getAsset(STAT_ID);
        if (value(stats) == null || type == null) return;
        if (newCapacity <= type.getMax()) stats.removeModifier(statIndex(), EXPANSION_KEY);
        else stats.putModifier(statIndex(), EXPANSION_KEY,
                new StaticModifier(Modifier.ModifierTarget.MAX, StaticModifier.CalculationType.ADDITIVE, (float) (newCapacity - type.getMax())));
    }

    @Nullable
    private static EntityStatValue value(@Nullable EntityStatMap stats) {
        int index = statIndex();
        return stats != null && index != Integer.MIN_VALUE ? stats.get(index) : null;
    }
}
