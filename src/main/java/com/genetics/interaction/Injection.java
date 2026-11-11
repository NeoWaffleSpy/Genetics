package com.genetics.interaction;

import com.genetics.capacity.DefaultDegradeStrategy;
import com.genetics.capacity.DegradeStrategy;
import com.genetics.capacity.GenomeCapacity;
import com.genetics.codex.Balance;
import com.genetics.genome.ActiveGenome;
import com.genetics.genome.ComplexityCalculator;
import com.genetics.genome.Gene;
import com.genetics.genome.GeneText;
import com.genetics.genome.Genome;
import com.genetics.genome.GenomeApplier;
import com.genetics.genome.InjectionRoller;
import com.genetics.sickness.Sickness;
import com.hypixel.hytale.component.ComponentAccessor;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.inventory.ItemStack;
import com.hypixel.hytale.server.core.modules.entitystats.EntityStatMap;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Injecting a syringe into its user, and copying the user's genome into an empty one (design doc §6, §3.4, §9.1).
 * <ol>
 *   <li>The sample is rolled ({@link InjectionRoller}); temporary effects start right away.</li>
 *   <li>The lasting genes are checked against the user's Genome Capacity. If they don't fit, they degrade until they
 *   do ({@link DefaultDegradeStrategy}) and the user gets massive sickness.</li>
 *   <li>The result replaces the active genome, and the injection's sickness is added.</li>
 * </ol>
 */
public final class Injection {
    /** Source id written into copies of a player's genome. */
    public static final String PLAYER_SOURCE_ID = "Player";

    private Injection() {
    }

    /** Complexity of the genes an injection of this sample would keep (everything but temporary effects). */
    public static double lastingComplexity(@Nonnull Genome sample) {
        return ComplexityCalculator.compute(lasting(sample.genes()));
    }

    /** True if injecting this sample, as it is, would go over the user's capacity. */
    public static boolean isOverCapacity(@Nonnull Genome sample, @Nonnull Ref<EntityStore> self, @Nonnull ComponentAccessor<EntityStore> accessor) {
        return lastingComplexity(sample) > GenomeCapacity.capacity(self, accessor);
    }

    /** Injects the syringe's sample. Returns the emptied syringe, or null if nothing happened. */
    @Nullable
    public static ItemStack inject(@Nonnull Ref<EntityStore> self, @Nonnull PlayerRef user, @Nonnull ItemStack syringe,
                                   @Nonnull ComponentAccessor<EntityStore> accessor) {
        Genome sample = Genome.read(syringe);
        if (sample == null) {
            user.sendMessage(Message.raw("This sample comes from a newer version of Genetics and can't be injected."));
            return null;
        }

        String language = user.getLanguage();
        List<Gene> lasting = new ArrayList<>();
        List<String> report = new ArrayList<>();
        List<String> lost = new ArrayList<>();
        for (InjectionRoller.Roll roll : InjectionRoller.roll(sample, ThreadLocalRandom.current())) {
            Gene gene = roll.result();
            if (gene == null) {
                lost.add(GeneText.describe(roll.original(), language));
                continue;
            }
            if (gene instanceof Gene.Effect effect && !effect.permanent()) {
                GenomeApplier.startTemporary(self, accessor, effect);
            } else {
                lasting.add(gene);
            }
            String text = GeneText.describe(gene, language);
            report.add(switch (roll.outcome()) {
                case WEAKENED -> text + " [weakened]";
                case INVERTED -> text + " [turned against you]";
                default -> text;
            });
        }

        // Capacity check, after the roll (design doc §3.4).
        float capacity = GenomeCapacity.capacity(self, accessor);
        double carried = ComplexityCalculator.compute(lasting);
        double overflow = Math.max(0, carried - capacity);
        List<DegradeStrategy.Change> degraded = overflow > 0
                ? DefaultDegradeStrategy.INSTANCE.degrade(lasting, capacity, ThreadLocalRandom.current())
                : List.of();

        ActiveGenome current = accessor.getComponent(self, ActiveGenome.getComponentType());
        Genome previous = current != null ? current.getGenome() : null;
        Genome next = new Genome(Genome.FORMAT_VERSION, sample.processed(), true, ComplexityCalculator.compute(lasting), sample.source(), lasting);
        // Update the existing component in place, so the maintenance system never re-applies the previous genome.
        if (current != null) current.setGenome(next);
        else accessor.putComponent(self, ActiveGenome.getComponentType(), new ActiveGenome(next));
        GenomeApplier.replace(self, accessor, previous, next);
        GenomeCapacity.setUsed(accessor.getComponent(self, EntityStatMap.getComponentType()), next.complexity());

        Sickness.add(self, accessor, Sickness.fromInjection(sample));
        if (overflow > 0) overflowSickness(self, accessor, overflow);

        StringBuilder message = new StringBuilder("You injected " + sample.title() + ".");
        message.append(report.isEmpty() ? " Nothing took hold." : " You gained: " + String.join(", ", report) + ".");
        if (!lost.isEmpty()) message.append(" Lost: ").append(String.join(", ", lost)).append(".");
        if (previous != null && !previous.genes().isEmpty()) message.append(" Your previous genome was replaced.");
        user.sendMessage(Message.raw(message.toString()));
        if (overflow > 0) user.sendMessage(Message.raw(overflowReport(carried, capacity, degraded, language)).color("#e06666"));

        return Syringes.empty(syringe);
    }

    /** Copies the user's active genome into the empty syringe. Returns the filled syringe, or null if there is nothing to copy. */
    @Nullable
    public static ItemStack copy(@Nonnull Ref<EntityStore> self, @Nonnull PlayerRef user, @Nonnull ItemStack syringe,
                                 @Nonnull ComponentAccessor<EntityStore> accessor) {
        ActiveGenome active = accessor.getComponent(self, ActiveGenome.getComponentType());
        Genome genome = active != null ? active.getGenome() : null;
        if (genome == null || genome.genes().isEmpty()) {
            user.sendMessage(Message.raw("You have no active genome to copy."));
            return null;
        }

        String name = user.getUsername();
        Genome copy = Genome.raw(new Genome.Source(PLAYER_SOURCE_ID, name, null, null, System.currentTimeMillis()), genome.genes());
        user.sendMessage(Message.raw("Copied your genome (" + copy.genes().size() + " genes, Complexity " + GeneText.number(copy.complexity()) + ")."));
        return Syringes.fill(syringe, copy);
    }

    /** Overflow pushes sickness straight to the overflow band (Severe), plus extra per point over capacity. */
    private static void overflowSickness(@Nonnull Ref<EntityStore> self, @Nonnull ComponentAccessor<EntityStore> accessor, double overflow) {
        Balance balance = Balance.get();
        double floor = 0;
        for (Balance.Band band : balance.sickness().getBands()) {
            if (band.getId().equals(balance.capacity().getOverflowBand())) floor = band.getMinSickness();
        }
        float now = Sickness.get(accessor.getComponent(self, EntityStatMap.getComponentType()));
        Sickness.set(self, accessor, Math.max(now, floor) + overflow * balance.capacity().getOverflowSicknessPerPoint());
    }

    @Nonnull
    private static String overflowReport(double carried, float capacity, @Nonnull List<DegradeStrategy.Change> changes, String language) {
        List<String> removed = new ArrayList<>();
        List<String> shrunk = new ArrayList<>();
        for (DegradeStrategy.Change change : changes) {
            if (change.result() == null) removed.add(GeneText.describe(change.original(), language));
            else shrunk.add(GeneText.describe(change.result(), language));
        }
        StringBuilder text = new StringBuilder("Your body can't hold this genome (Complexity " + GeneText.number(carried)
                + ", capacity " + GeneText.number(capacity) + ")! It degrades to fit.");
        if (!removed.isEmpty()) text.append(" Destroyed: ").append(String.join(", ", removed)).append(".");
        if (!shrunk.isEmpty()) text.append(" Weakened to: ").append(String.join(", ", shrunk)).append(".");
        return text.toString();
    }

    @Nonnull
    private static List<Gene> lasting(@Nonnull List<Gene> genes) {
        return genes.stream().filter(gene -> !(gene instanceof Gene.Effect effect && !effect.permanent())).toList();
    }
}
