package com.genetics.capacity;

import com.genetics.genome.Gene;

import javax.annotation.Nonnull;
import java.util.List;
import java.util.random.RandomGenerator;

/**
 * Shrinks a genome until its Complexity fits a capacity (design doc §3.4). Kept separate from the injection code so
 * the removal order can change without touching it.
 */
public interface DegradeStrategy {
    /** What degradation did to one gene: {@code result} is the shrunk gene, or null if it was removed. */
    record Change(@Nonnull Gene original, Gene result) {
    }

    /**
     * Degrades {@code genes} (which it may modify) until their Complexity is at most {@code capacity}, or nothing more
     * can be removed. Returns what was changed, in order.
     */
    @Nonnull
    List<Change> degrade(@Nonnull List<Gene> genes, double capacity, @Nonnull RandomGenerator random);
}
