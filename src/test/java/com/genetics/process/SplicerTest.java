package com.genetics.process;

import com.genetics.TestAssets;
import com.genetics.genome.Gene;
import com.genetics.genome.Genome;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Random;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Design doc §7: splicing adds two genetic materials together. */
class SplicerTest {
    @BeforeAll
    static void load() {
        TestAssets.load();
    }

    private static Genome material(Gene... genes) {
        Genome raw = Genome.raw(null, List.of(genes));
        return new Genome(raw.version(), true, true, raw.complexity(), null, raw.genes());
    }

    @Test
    void sameStatSumsAndDrawbacksSubtract() {
        Genome result = Splicer.plan(material(new Gene.Stat("Health", 6)), material(new Gene.Stat("Health", 4)), 3).result();
        assertEquals(List.of(new Gene.Stat("Health", 10, 10)), result.genes());

        Genome cancelled = Splicer.plan(material(new Gene.Stat("Health", 5)), material(new Gene.Stat("Health", -5)), 3).result();
        assertTrue(cancelled.genes().isEmpty(), "a bonus and an equal drawback cancel out");
    }

    @Test
    void genesOnlyOneInputHasCarryOver() {
        Genome result = Splicer.plan(material(new Gene.Stat("Health", 5)), material(new Gene.Stat("Stamina", 5)), 3).result();
        assertEquals(2, result.genes().size());
        assertFalse(result.processed(), "a splice result is raw");
    }

    @Test
    void timersAddUpToTheCap() {
        Genome a = material(new Gene.Effect("Meat_Buff_T1", false, 1500));
        Genome b = material(new Gene.Effect("Meat_Buff_T1", false, 1500));
        Gene.Effect merged = (Gene.Effect) Splicer.plan(a, b, 3).result().genes().getFirst();
        assertEquals(1800, merged.duration(), "capped at Splicing.MaxEffectDuration");
    }

    @Test
    void mutationsComeFromOneInputAndBlockNewOnes() {
        Genome a = material(new Gene.Stat("Health", 20), new Gene.Mutation("VoidSiphon"));
        Genome b = material(new Gene.Mutation("Webspinner"));
        Splicer.Plan plan = Splicer.plan(a, b, 3);
        List<Gene> mutations = plan.result().genes().stream().filter(g -> g instanceof Gene.Mutation).toList();
        assertEquals(List.of(new Gene.Mutation("VoidSiphon")), mutations, "the higher-Complexity input's mutations");
        assertEquals(0, plan.mutationChance(), "no new mutation when one is inherited");
    }

    @Test
    void mutationChanceRisesWithComplexity() {
        double low = Splicer.plan(material(new Gene.Stat("Health", 5)), material(new Gene.Stat("Health", 5)), 3).mutationChance();
        double high = Splicer.plan(material(new Gene.Stat("Health", 50)), material(new Gene.Stat("Health", 50)), 3).mutationChance();
        assertTrue(high > low && low > 0);
    }

    @Test
    void untickedLinesAreLeftOut() {
        Genome a = material(new Gene.Stat("Health", 5), new Gene.Stat("Stamina", 5));
        Genome result = Splicer.plan(a, material(new Gene.Stat("Mana", 5)), 3, new boolean[]{true, false, true}).result();
        assertEquals(2, result.genes().size());
    }

    @Test
    void buffTiersShareAFamily() {
        assertEquals("Meat_Buff", Splicer.family("Meat_Buff_T2"));
        assertEquals("Genetics_Speed", Splicer.family("Genetics_Speed"));
    }

    @Test
    void costUsesTheAverageComplexityTierAndTheStationGate() {
        Genome a = material(new Gene.Stat("Health", 50)); // Complexity 40, Moderate
        Genome b = material(new Gene.Stat("Health", 50));
        assertNotNull(Splicer.plan(a, b, 1).blocked(), "Moderate Complexity needs a T2 station");
        assertNull(Splicer.plan(a, b, 2).blocked());
    }

    @Test
    void aSpliceWithoutTheMutationRollIsThePlan() {
        Genome a = material(new Gene.Stat("Health", 5));
        Random never = new Random() {
            @Override
            public double nextDouble() {
                return 0.999;
            }
        };
        Splicer.Result result = Splicer.splice(a, a, null, never);
        assertNull(result.newMutation());
        assertEquals(List.of(new Gene.Stat("Health", 10, 10)), result.genome().genes());
    }
}
