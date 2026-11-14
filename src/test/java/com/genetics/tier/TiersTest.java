package com.genetics.tier;

import com.genetics.TestAssets;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Design doc §4: Complexity tiers. */
class TiersTest {
    @BeforeAll
    static void load() {
        TestAssets.load();
    }

    @Test
    void tiersAreOrderedAndBoundariesFallInTheHigherTier() {
        List<String> ids = Tiers.all().stream().map(ComplexityTier::getId).toList();
        assertEquals(List.of("Simple", "Moderate", "Complex", "Advanced", "Exotic"), ids);
        assertEquals("Simple", Tiers.forComplexity(20.9).getId());
        assertEquals("Moderate", Tiers.forComplexity(21).getId());
        assertEquals("Exotic", Tiers.forComplexity(5000).getId());
        assertEquals("Simple", Tiers.forComplexity(-5).getId());
    }

    @Test
    void stationTierGatesProcessing() {
        assertTrue(Tiers.allows(1, Tiers.forComplexity(10)));
        assertFalse(Tiers.allows(1, Tiers.forComplexity(30)));
        assertFalse(Tiers.allows(2, Tiers.forComplexity(60)));
        assertTrue(Tiers.allows(3, Tiers.forComplexity(500)));
    }

    @Test
    void costGrowsInsideATier() {
        ComplexityTier moderate = Tiers.forComplexity(21);
        int low = Tiers.cost(moderate, 21).stream().mapToInt(m -> m.getQuantity()).sum();
        int high = Tiers.cost(moderate, 45).stream().mapToInt(m -> m.getQuantity()).sum();
        assertTrue(high > low);
    }
}
