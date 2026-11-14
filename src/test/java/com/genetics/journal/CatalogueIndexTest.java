package com.genetics.journal;

import com.genetics.TestAssets;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The species index is built once and reused: the Catalogue makes thousands of lookups per click, and rebuilding it
 * on each one made every interaction take over a second.
 */
class CatalogueIndexTest {
    @BeforeAll
    static void load() {
        TestAssets.load();
    }

    @Test
    void lookupsReuseTheIndex() {
        List<String> species = Catalogue.allSpecies();
        Catalogue.speciesOf("Cow");
        long start = System.nanoTime();
        for (int i = 0; i < 50; i++) {
            for (String id : species) {
                Catalogue.speciesOf(id);
                Catalogue.profileOf(id);
                Catalogue.rolesOf(id);
            }
        }
        long millis = (System.nanoTime() - start) / 1_000_000;
        int lookups = 50 * species.size() * 3;
        assertTrue(millis < 1000, lookups + " lookups took " + millis + " ms");
    }
}
