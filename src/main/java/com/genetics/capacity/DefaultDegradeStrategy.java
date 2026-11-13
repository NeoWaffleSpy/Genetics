package com.genetics.capacity;

import com.genetics.codex.Codex;
import com.genetics.codex.MutationEntry;
import com.genetics.genome.ComplexityCalculator;
import com.genetics.genome.Gene;

import javax.annotation.Nonnull;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Predicate;
import java.util.random.RandomGenerator;

/**
 * The removal order from design doc §3.4. Genes are removed one at a time, picking at random within the first group
 * that still has genes, weighted by how much Complexity each adds:
 * <ol>
 *   <li>mutations;</li>
 *   <li>positive permanent effects;</li>
 *   <li>positive temporary effects (an active genome doesn't keep them, but a sample can);</li>
 *   <li>positive stats, which are <b>shrunk</b> rather than removed when shrinking is enough to fit.</li>
 * </ol>
 * Drawbacks are never removed: removing one would raise Complexity.
 */
public final class DefaultDegradeStrategy implements DegradeStrategy {
    public static final DefaultDegradeStrategy INSTANCE = new DefaultDegradeStrategy();

    private static final List<Predicate<Gene>> ORDER = List.of(
            gene -> gene instanceof Gene.Mutation,
            gene -> gene instanceof Gene.Effect effect && effect.permanent(),
            gene -> gene instanceof Gene.Effect effect && !effect.permanent(),
            gene -> gene instanceof Gene.Stat stat && !stat.isDrawback()
    );

    private DefaultDegradeStrategy() {
    }

    @Nonnull
    @Override
    public List<Change> degrade(@Nonnull List<Gene> genes, double capacity, @Nonnull RandomGenerator random) {
        List<Change> changes = new ArrayList<>();
        while (ComplexityCalculator.compute(genes) > capacity) {
            List<Gene> group = firstGroup(genes);
            if (group.isEmpty()) break;
            Gene picked = pick(group, random);
            int index = genes.indexOf(picked);

            if (picked instanceof Gene.Stat stat) {
                double excess = ComplexityCalculator.compute(genes) - capacity;
                double weight = Math.max(0.0001, Codex.statWeight(stat.stat()));
                // A fused stat costs more than its value: shrink its cost, and its value in the same ratio.
                double ratio = stat.cost() / Math.max(0.0001, stat.percent());
                double cost = stat.cost() - excess / weight;
                double shrunk = Math.floor(cost / ratio * 10) / 10.0;
                if (shrunk >= 0.1) {
                    Gene.Stat result = new Gene.Stat(stat.stat(), shrunk, shrunk * ratio);
                    genes.set(index, result);
                    changes.add(new Change(stat, result));
                    continue;
                }
            }
            genes.remove(index);
            changes.add(new Change(picked, null));
        }
        return changes;
    }

    @Nonnull
    private static List<Gene> firstGroup(@Nonnull List<Gene> genes) {
        for (Predicate<Gene> rule : ORDER) {
            List<Gene> group = genes.stream().filter(rule).toList();
            if (!group.isEmpty()) return group;
        }
        return List.of();
    }

    @Nonnull
    private static Gene pick(@Nonnull List<Gene> group, @Nonnull RandomGenerator random) {
        double total = 0;
        for (Gene gene : group) total += weight(gene);
        double roll = random.nextDouble() * total;
        for (Gene gene : group) {
            roll -= weight(gene);
            if (roll < 0) return gene;
        }
        return group.getLast();
    }

    /** How much Complexity a gene adds on its own. */
    private static double weight(@Nonnull Gene gene) {
        double value = switch (gene) {
            case Gene.Stat stat -> stat.cost() * Codex.statWeight(stat.stat());
            case Gene.Effect effect -> Codex.effectComplexity(effect.effect(), effect.permanent());
            case Gene.Mutation mutation -> {
                MutationEntry entry = Codex.mutation(mutation.mutation());
                yield entry != null ? entry.getComplexity() : 1;
            }
        };
        return Math.max(0.01, value);
    }
}
