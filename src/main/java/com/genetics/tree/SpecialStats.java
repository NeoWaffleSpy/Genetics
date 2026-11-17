package com.genetics.tree;

import com.genetics.codex.Balance;
import com.genetics.genome.Genome;
import com.genetics.genome.GenomeApplier;
import com.genetics.mutation.MutationDamageSystems;
import com.hypixel.hytale.component.ArchetypeChunk;
import com.hypixel.hytale.component.CommandBuffer;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.component.SystemGroup;
import com.hypixel.hytale.component.dependency.Dependency;
import com.hypixel.hytale.component.dependency.Order;
import com.hypixel.hytale.component.dependency.SystemDependency;
import com.hypixel.hytale.component.query.Query;
import com.hypixel.hytale.server.core.entity.knockback.KnockbackComponent;
import com.hypixel.hytale.server.core.modules.entity.damage.Damage;
import com.hypixel.hytale.server.core.modules.entity.damage.DamageEventSystem;
import com.hypixel.hytale.server.core.modules.entity.damage.DamageModule;
import com.hypixel.hytale.server.core.modules.entity.damage.DamageSystems;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.Map;
import java.util.Set;

/**
 * The mod's special stats (design doc §8.3): stat genes that aren't vanilla entity stats, so {@link GenomeApplier}
 * skips them and {@link Damage} applies them here, after armor.
 * <ul>
 *   <li>{@value #DAMAGE_RESISTANCE}: percent less damage taken, up to {@code DamageResistanceCap};</li>
 *   <li>{@value #KNOCKBACK_RESISTANCE}: percent less knockback, up to {@code KnockbackResistanceCap}.</li>
 * </ul>
 */
public final class SpecialStats {
    public static final String DAMAGE_RESISTANCE = "Genetics_DamageResistance";
    public static final String KNOCKBACK_RESISTANCE = "Genetics_KnockbackResistance";
    public static final Set<String> ALL = Set.of(DAMAGE_RESISTANCE, KNOCKBACK_RESISTANCE);

    private SpecialStats() {
    }

    /** Fraction of damage removed by this much damage resistance (percent), capped. */
    public static double damageFactor(double percent, @Nonnull Balance.GenomeTree balance) {
        return 1 - Math.clamp(percent, 0, balance.getDamageResistanceCap()) / 100.0;
    }

    /** Multiplier on knockback for this much knockback resistance (percent), capped. */
    public static double knockbackFactor(double percent, @Nonnull Balance.GenomeTree balance) {
        return 1 - Math.clamp(percent, 0, balance.getKnockbackResistanceCap()) / 100.0;
    }

    /** Before damage is applied, after armor: resistances from the victim's genome. */
    public static class Filter extends DamageEventSystem {
        @Nullable
        @Override
        public SystemGroup<EntityStore> getGroup() {
            return DamageModule.get().getFilterDamageGroup();
        }

        @Nonnull
        @Override
        public Query<EntityStore> getQuery() {
            return com.genetics.genome.ActiveGenome.getComponentType();
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
            Genome genome = MutationDamageSystems.genome(target, buffer);
            if (genome == null) return;
            Map<String, Double> totals = GenomeApplier.statTotals(genome);
            Balance.GenomeTree balance = Balance.get().genomeTree();
            Double resistance = totals.get(DAMAGE_RESISTANCE);
            if (resistance != null && resistance > 0 && damage.getAmount() > 0) {
                damage.setAmount((float) (damage.getAmount() * damageFactor(resistance, balance)));
            }
            Double knockback = totals.get(KNOCKBACK_RESISTANCE);
            if (knockback != null && knockback > 0) {
                KnockbackComponent component = damage.getIfPresentMetaObject(Damage.KNOCKBACK_COMPONENT);
                if (component != null) component.addModifier(knockbackFactor(knockback, balance));
            }
        }
    }
}
