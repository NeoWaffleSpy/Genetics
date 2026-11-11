package com.genetics.process;

import com.genetics.genome.ComplexityCalculator;
import com.genetics.genome.Gene;
import com.genetics.genome.Genome;
import com.genetics.tier.ComplexityTier;
import com.genetics.tier.Tiers;
import com.hypixel.hytale.server.core.inventory.MaterialQuantity;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.List;

/**
 * Processing a raw sample (design doc §7). It is also how a sample is identified: the processing screen reveals the
 * genes, the player picks which to keep, Complexity is recalculated over them, and the cost is one Identification
 * Reagent plus the materials of that Complexity's tier. The result is genetic material: injected exactly as it is,
 * with reduced sickness, and spliceable, but never processed again. Processing never creates a mutation; it can only
 * keep one the sample already has.
 */
public final class Processing {
    /** Item every processing consumes, on top of the tier materials. */
    public static final String REAGENT_ID = "Genetics_Identification_Reagent";

    private Processing() {
    }

    /**
     * What processing would give and cost.
     *
     * @param result  the genetic material, or null if nothing is kept
     * @param blocked why it can't be done at this station, or null if it can (the cost may still be unaffordable)
     */
    public record Plan(@Nullable Genome result, @Nullable ComplexityTier tier, @Nonnull List<MaterialQuantity> cost, @Nullable String blocked) {
    }

    /** Why this sample can't be processed at all, or null if it can. */
    @Nullable
    public static String refusal(@Nullable Genome sample) {
        if (sample == null) return "Put a filled syringe in the slot.";
        if (sample.processed()) return "Genetic material can't be processed again. Splice it instead.";
        if (sample.genes().isEmpty()) return "This sample has no genes to keep.";
        return null;
    }

    /** True if processing can make this gene permanent: a temporary effect lasting at least the Permanence MinDuration. */
    public static boolean canMakePermanent(@Nonnull Gene gene) {
        return gene instanceof Gene.Effect effect && !effect.permanent()
                && effect.duration() >= com.genetics.codex.Balance.get().permanence().getMinDuration();
    }

    @Nonnull
    public static Plan plan(@Nonnull Genome sample, @Nonnull boolean[] keep, int stationTier) {
        return plan(sample, keep, new boolean[0], stationTier);
    }

    /**
     * @param permanent which genes (by index) to make permanent; ignored for genes that aren't eligible
     *                  ({@link #canMakePermanent}). Each one costs its permanent Complexity plus catalysts.
     */
    @Nonnull
    public static Plan plan(@Nonnull Genome sample, @Nonnull boolean[] keep, @Nonnull boolean[] permanent, int stationTier) {
        List<Gene> kept = new ArrayList<>();
        int madePermanent = 0;
        for (int i = 0; i < sample.genes().size(); i++) {
            if (i >= keep.length || !keep[i]) continue;
            Gene gene = sample.genes().get(i);
            if (i < permanent.length && permanent[i] && canMakePermanent(gene)) {
                Gene.Effect effect = (Gene.Effect) gene;
                Gene.Effect stable = new Gene.Effect(effect.effect(), true, 0);
                madePermanent++;
                if (kept.contains(stable)) continue;
                gene = stable;
            }
            kept.add(gene);
        }
        if (kept.isEmpty()) return new Plan(null, null, List.of(), "Keep at least one gene.");

        double complexity = ComplexityCalculator.compute(kept);
        Genome result = new Genome(Genome.FORMAT_VERSION, true, true, complexity, sample.source(), kept);
        ComplexityTier tier = Tiers.forComplexity(complexity);
        if (tier == null) return new Plan(result, null, List.of(), null);
        String blocked = Tiers.allows(stationTier, tier) ? null
                : tier.getName() + " Complexity needs a Lab Station T" + tier.getStationTier() + ".";
        List<MaterialQuantity> cost = new ArrayList<>(Tiers.cost(tier, complexity));
        cost.addFirst(new MaterialQuantity(REAGENT_ID, null, null, 1, null));
        com.genetics.codex.Balance.Permanence permanence = com.genetics.codex.Balance.get().permanence();
        if (madePermanent > 0 && permanence.getCatalystPerEffect() > 0) {
            cost.add(1, new MaterialQuantity(permanence.getCatalyst(), null, null, madePermanent * permanence.getCatalystPerEffect(), null));
        }
        return new Plan(result, tier, cost, blocked);
    }
}
