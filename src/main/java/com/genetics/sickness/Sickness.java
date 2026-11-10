package com.genetics.sickness;

import com.genetics.codex.Balance;
import com.genetics.genome.Genome;
import com.hypixel.hytale.component.ComponentAccessor;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.server.core.modules.entitystats.EntityStatMap;
import com.hypixel.hytale.server.core.modules.entitystats.EntityStatValue;
import com.hypixel.hytale.server.core.modules.entitystats.asset.EntityStatType;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

/**
 * Genetic Sickness (design doc §9.2): an entity stat ({@value #STAT_ID}, 0 to 100) that builds up with each injection
 * and decays over time. Its bands, decay and events are driven by {@link SicknessSystem}.
 */
public final class Sickness {
    /** Entity stat id, defined in {@code Server/Entity/Stats/Genetics_Sickness.json}. */
    public static final String STAT_ID = "Genetics_Sickness";

    private Sickness() {
    }

    public static int statIndex() {
        return EntityStatType.getAssetMap().getIndex(STAT_ID);
    }

    /** Current sickness, or 0 if the entity has no such stat. */
    public static float get(@Nullable EntityStatMap stats) {
        int index = statIndex();
        EntityStatValue value = stats != null && index != Integer.MIN_VALUE ? stats.get(index) : null;
        return value != null ? value.get() : 0;
    }

    /** Sickness an injection of this sample causes: Complexity × PerComplexity, reduced for genetic material. */
    public static double fromInjection(@Nonnull Genome sample) {
        Balance.Sickness balance = Balance.get().sickness();
        double gain = sample.complexity() * balance.getPerComplexity();
        return sample.processed() ? gain * balance.getProcessedMultiplier() : gain;
    }

    /** Adds sickness to an entity and makes sure the sickness system tracks it. Returns the new value. */
    public static float add(@Nonnull Ref<EntityStore> ref, @Nonnull ComponentAccessor<EntityStore> accessor, double amount) {
        return change(ref, accessor, amount, false);
    }

    /** Sets an entity's sickness and makes sure the sickness system tracks it. Returns the new value. */
    public static float set(@Nonnull Ref<EntityStore> ref, @Nonnull ComponentAccessor<EntityStore> accessor, double value) {
        return change(ref, accessor, value, true);
    }

    private static float change(@Nonnull Ref<EntityStore> ref, @Nonnull ComponentAccessor<EntityStore> accessor, double amount, boolean absolute) {
        EntityStatMap stats = accessor.getComponent(ref, EntityStatMap.getComponentType());
        int index = statIndex();
        if (stats == null || index == Integer.MIN_VALUE || stats.get(index) == null) return 0;
        float value = absolute ? stats.setStatValue(index, (float) amount) : stats.addStatValue(index, (float) amount);
        if (accessor.getComponent(ref, SicknessState.getComponentType()) == null) {
            accessor.putComponent(ref, SicknessState.getComponentType(), new SicknessState());
        }
        return value;
    }
}
