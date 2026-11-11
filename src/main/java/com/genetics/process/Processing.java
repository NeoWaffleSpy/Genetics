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
 * Processing a raw sample (design doc §7): the player picks which genes to keep, Complexity is recalculated over
 * them, and the cost comes from that Complexity's tier. The result is genetic material: injected exactly as it is,
 * with reduced sickness, and spliceable, but never processed again. Processing never creates a mutation; it can only
 * keep one the sample already has.
 */
public final class Processing {
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
        if (!sample.identified()) return "Identify the sample first.";
        if (sample.genes().isEmpty()) return "This sample has no genes to keep.";
        return null;
    }

    @Nonnull
    public static Plan plan(@Nonnull Genome sample, @Nonnull boolean[] keep, int stationTier) {
        List<Gene> kept = new ArrayList<>();
        for (int i = 0; i < sample.genes().size(); i++) {
            if (i < keep.length && keep[i]) kept.add(sample.genes().get(i));
        }
        if (kept.isEmpty()) return new Plan(null, null, List.of(), "Keep at least one gene.");

        double complexity = ComplexityCalculator.compute(kept);
        Genome result = new Genome(Genome.FORMAT_VERSION, true, true, complexity, sample.source(), kept);
        ComplexityTier tier = Tiers.forComplexity(complexity);
        if (tier == null) return new Plan(result, null, List.of(), null);
        String blocked = Tiers.allows(stationTier, tier) ? null
                : tier.getName() + " Complexity needs a Lab Station T" + tier.getStationTier() + ".";
        return new Plan(result, tier, Tiers.cost(tier, complexity), blocked);
    }
}
