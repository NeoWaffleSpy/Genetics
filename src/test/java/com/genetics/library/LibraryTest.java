package com.genetics.library;

import com.genetics.TestAssets;
import com.genetics.genome.Gene;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Design doc §8.1: the Genetic Library. */
class LibraryTest {
    @BeforeAll
    static void load() {
        TestAssets.load();
    }

    private static Library.Use use(GeneLibrary library, Gene gene, double amount) {
        library.add(gene, "test");
        GeneLibrary.Entry entry = library.find(gene);
        assertNotNull(entry);
        return new Library.Use(entry, amount);
    }

    @Test
    void keepsOneEntryPerGeneAtItsBestValue() {
        GeneLibrary library = new GeneLibrary();
        assertTrue(library.add(new Gene.Stat("Health", 5), "a"));
        assertTrue(library.add(new Gene.Stat("Health", 8), "b"));
        assertFalse(library.add(new Gene.Stat("Health", 6), "c"), "a weaker copy is refused");
        assertFalse(library.wouldImprove(new Gene.Stat("Health", 8)));
        assertEquals(8, ((Gene.Stat) library.find(new Gene.Stat("Health", 1)).getGene()).percent());
        assertTrue(library.add(new Gene.Stat("Health", -5), "d"), "a drawback is its own entry");
        assertTrue(library.add(new Gene.Effect("Meat_Buff_T1", true, 0), "e"));
        assertTrue(library.add(new Gene.Effect("Meat_Buff_T1", false, 180), "f"), "the temporary form is its own entry");
    }

    @Test
    void amountsAreClampedToTheBestValueWithOneDecimal() {
        Gene.Stat stat = new Gene.Stat("Health", 8);
        assertEquals(8, Library.maxAmount(stat));
        assertEquals(new Gene.Stat("Health", 4.5, 4.5), Library.scaled(stat, 4.54));
        assertEquals(new Gene.Stat("Health", 8, 8), Library.scaled(stat, 99));
        assertNull(Library.scaled(stat, 0));
        assertEquals(new Gene.Stat("Health", -3, 3), Library.scaled(new Gene.Stat("Health", -5), 3));
    }

    @Test
    void temporaryEffectsAreChosenInMinutesUpTo30() {
        Gene.Effect effect = new Gene.Effect("Meat_Buff_T1", false, 2400);
        assertEquals(30, Library.maxAmount(effect));
        assertEquals(new Gene.Effect("Meat_Buff_T1", false, 90), Library.scaled(effect, 1.5));
        assertFalse(Library.adjustable(new Gene.Effect("Meat_Buff_T1", true, 0)));
        assertFalse(Library.adjustable(new Gene.Mutation("VoidSiphon")));
    }

    @Test
    void enhancersScaleWithComplexity() {
        assertEquals(1, Library.enhancersFor(new Gene.Stat("Health", 1)), "at least one");
        assertEquals(2, Library.enhancersFor(new Gene.Stat("Health", 20)), "16 Complexity, one per 10");
        assertEquals(0, Library.enhancersFor(new Gene.Mutation("VoidSiphon")), "mutations need none");
    }

    @Test
    void aBonusAndADrawbackOfOneStatCantBeCombined() {
        GeneLibrary library = new GeneLibrary();
        Library.Plan plan = Library.plan(List.of(use(library, new Gene.Stat("Health", 8), 8),
                use(library, new Gene.Stat("Health", -5), 5)), 3);
        assertNotNull(plan.blocked());
    }

    @Test
    void drawbacksReduceComplexity() {
        GeneLibrary library = new GeneLibrary();
        double alone = Library.plan(List.of(use(library, new Gene.Stat("Health", 10), 10)), 3).result().complexity();
        double withDrawback = Library.plan(List.of(use(library, new Gene.Stat("Health", 10), 10),
                use(library, new Gene.Stat("Mana", -5), 5)), 3).result().complexity();
        assertTrue(withDrawback < alone);
    }

    @Test
    void aPermanentEffectReplacesItsTemporaryForm() {
        GeneLibrary library = new GeneLibrary();
        Library.Plan plan = Library.plan(List.of(
                use(library, new Gene.Effect("Meat_Buff_T1", false, 180), 3),
                use(library, new Gene.Effect("Meat_Buff_T1", true, 0), 0)), 3);
        assertNull(plan.blocked());
        assertEquals(List.of(new Gene.Effect("Meat_Buff_T1", true, 0)), plan.genes());
    }

    @Test
    void extraMutationsMultiplyTheCost() {
        GeneLibrary library = new GeneLibrary();
        Library.Plan one = Library.plan(List.of(use(library, new Gene.Mutation("Webspinner"), 0)), 3);
        Library.Plan three = Library.plan(List.of(use(library, new Gene.Mutation("Webspinner"), 0),
                use(library, new Gene.Mutation("VenomGlands"), 0), use(library, new Gene.Mutation("VoidSiphon"), 0)), 3);
        assertEquals(1, one.multiplier());
        assertEquals(9, three.multiplier());
        assertEquals(3, three.mutations());
    }

    @Test
    void atMostFourMutations() {
        GeneLibrary library = new GeneLibrary();
        Library.Plan plan = Library.plan(List.of(use(library, new Gene.Mutation("Webspinner"), 0),
                use(library, new Gene.Mutation("VenomGlands"), 0), use(library, new Gene.Mutation("VoidSiphon"), 0),
                use(library, new Gene.Mutation("AdrenalSurge"), 0), use(library, new Gene.Mutation("Extra"), 0)), 3);
        assertNotNull(plan.blocked());
    }

    @Test
    void nothingTickedIsBlocked() {
        assertNotNull(Library.plan(List.of(), 3).blocked());
    }
}
