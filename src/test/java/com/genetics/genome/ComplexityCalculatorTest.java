package com.genetics.genome;

import com.genetics.TestAssets;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

/** Design doc §3.1: stat part + effect part + mutation part, with drawback limits. */
class ComplexityCalculatorTest {
    @BeforeAll
    static void load() {
        TestAssets.load();
    }

    @Test
    void statsCostTheirWeightPerPercent() {
        // Health weighs 0.8 per percent.
        assertEquals(8.0, ComplexityCalculator.compute(List.of(new Gene.Stat("Health", 10))));
    }

    @Test
    void drawbacksCountAt60PercentAndAreCappedAt30PercentOfThePositives() {
        // -5 Stamina: 5 * 0.6 weight * 0.6 drawback discount = 1.8 off.
        assertEquals(6.2, ComplexityCalculator.compute(List.of(new Gene.Stat("Health", 10), new Gene.Stat("Stamina", -5))));
        // A huge drawback only takes 30% off.
        assertEquals(5.6, ComplexityCalculator.compute(List.of(new Gene.Stat("Health", 10), new Gene.Stat("Stamina", -100))));
    }

    @Test
    void neverBelowOne() {
        assertEquals(ComplexityCalculator.MINIMUM, ComplexityCalculator.compute(List.of()));
        assertEquals(ComplexityCalculator.MINIMUM, ComplexityCalculator.compute(List.of(new Gene.Stat("Oxygen", 1))));
    }

    @Test
    void temporaryEffectsScalePastTheReferenceDuration() {
        assertEquals(3.0, ComplexityCalculator.effect(new Gene.Effect("HealthRegen_Buff_T1", false, 180)));
        assertEquals(6.0, ComplexityCalculator.effect(new Gene.Effect("HealthRegen_Buff_T1", false, 360)));
        assertEquals(3.0, ComplexityCalculator.effect(new Gene.Effect("HealthRegen_Buff_T1", false, 60)),
                "shorter than the reference costs the base");
    }

    @Test
    void permanentEffectsAndMutationsUseTheirCodexValue() {
        assertEquals(100.0, ComplexityCalculator.compute(List.of(new Gene.Effect("HealthRegen_Buff_T1", true, 0))));
        assertEquals(35.0, ComplexityCalculator.compute(List.of(new Gene.Mutation("VoidSiphon"))));
    }

    @Test
    void unknownEffectsFallBackToTheDefaultEntry() {
        assertEquals(40.0, ComplexityCalculator.compute(List.of(new Gene.Effect("Not_A_Real_Effect", true, 0))));
    }
}
