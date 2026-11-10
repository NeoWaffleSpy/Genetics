package com.genetics.genome;

import com.genetics.codex.Codex;
import com.genetics.codex.MutationEntry;

import javax.annotation.Nonnull;
import java.util.List;

/**
 * Genome → Complexity, using the Effect and Mutation Codices (design doc §3.1). Knows nothing about tiers or costs.
 * <pre>
 * Complexity = stat part + effect part + mutation part
 * </pre>
 * Drawbacks subtract at {@link #DRAWBACK_WEIGHT} of a positive gene's weight, can remove at most
 * {@link #DRAWBACK_CAP} of the positive total, and Complexity never goes below {@link #MINIMUM}.
 */
public final class ComplexityCalculator {
    public static final double DRAWBACK_WEIGHT = 0.6;
    public static final double DRAWBACK_CAP = 0.3;
    public static final double MINIMUM = 1;

    private ComplexityCalculator() {
    }

    public static double compute(@Nonnull List<Gene> genes) {
        double positive = 0;
        double drawbacks = 0;
        for (Gene gene : genes) {
            switch (gene) {
                case Gene.Stat stat -> {
                    double value = Math.abs(stat.percent()) * Codex.statWeight(stat.stat());
                    if (stat.isDrawback()) drawbacks += value * DRAWBACK_WEIGHT;
                    else positive += value;
                }
                case Gene.Effect effect -> positive += Codex.effectComplexity(effect.effect(), effect.permanent());
                case Gene.Mutation mutation -> {
                    MutationEntry entry = Codex.mutation(mutation.mutation());
                    if (entry != null) positive += entry.getComplexity();
                }
            }
        }
        double complexity = positive - Math.min(drawbacks, positive * DRAWBACK_CAP);
        return round(Math.max(MINIMUM, complexity));
    }

    private static double round(double value) {
        return Math.round(value * 10) / 10.0;
    }
}
