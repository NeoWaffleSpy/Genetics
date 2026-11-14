package com.genetics.capacity;

import com.genetics.TestAssets;
import com.genetics.genome.ComplexityCalculator;
import com.genetics.genome.Gene;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Design doc §3.4: an over-capacity genome degrades until it fits. */
class DegradeStrategyTest {
    @BeforeAll
    static void load() {
        TestAssets.load();
    }

    @Test
    void mutationsAndPermanentEffectsGoFirst() {
        List<Gene> genes = new ArrayList<>(List.of(new Gene.Stat("Health", 10), new Gene.Mutation("VoidSiphon"),
                new Gene.Effect("Genetics_NightVision", true, 0)));
        DefaultDegradeStrategy.INSTANCE.degrade(genes, 20, new Random(1));
        assertEquals(List.of(new Gene.Stat("Health", 10)), genes);
    }

    @Test
    void statsShrinkRatherThanDisappear() {
        List<Gene> genes = new ArrayList<>(List.of(new Gene.Stat("Health", 50))); // Complexity 40
        DefaultDegradeStrategy.INSTANCE.degrade(genes, 20, new Random(1));
        assertEquals(1, genes.size());
        assertTrue(ComplexityCalculator.compute(genes) <= 20);
        assertTrue(((Gene.Stat) genes.getFirst()).percent() >= 24.9);
    }

    @Test
    void drawbacksAreNeverRemoved() {
        List<Gene> genes = new ArrayList<>(List.of(new Gene.Stat("Health", 60), new Gene.Stat("Mana", -10)));
        DefaultDegradeStrategy.INSTANCE.degrade(genes, 10, new Random(1));
        assertTrue(genes.contains(new Gene.Stat("Mana", -10)));
        assertTrue(ComplexityCalculator.compute(genes) <= 10);
    }

    @Test
    void aGenomeUnderCapacityIsUntouched() {
        List<Gene> genes = new ArrayList<>(List.of(new Gene.Stat("Health", 5)));
        assertTrue(DefaultDegradeStrategy.INSTANCE.degrade(genes, 20, new Random(1)).isEmpty());
        assertEquals(1, genes.size());
    }
}
