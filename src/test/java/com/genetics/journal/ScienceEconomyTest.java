package com.genetics.journal;

import com.genetics.TestAssets;
import com.genetics.codex.Balance;
import com.genetics.codex.Codex;
import com.genetics.codex.MobGeneProfile;
import com.genetics.codex.MutationEntry;
import com.genetics.research.ResearchNode;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Design doc §9.4: every discovery in the game pays about twice what the whole Research tree costs, so players can
 * unlock everything without finding everything. Tune the band when playtests say so.
 */
class ScienceEconomyTest {
    static final double MIN_RATIO = 1.8;
    static final double MAX_RATIO = 2.4;

    @BeforeAll
    static void load() {
        TestAssets.load();
    }

    static int required() {
        return ResearchNode.getAssetMap().getAssetMap().values().stream().mapToInt(ResearchNode::getCost).sum();
    }

    static int achievable() {
        Balance.Science rewards = Balance.get().science();
        int total = 0;
        Set<String> buffs = new HashSet<>();
        for (String species : Catalogue.allSpecies()) {
            Set<String> genes = Catalogue.possibleGenes(species);
            total += rewards.getFirstSample() + genes.size() * rewards.getNewGene() + rewards.getSpeciesComplete();
            for (String key : genes) if (key.startsWith(Catalogue.EFFECT)) buffs.add(key);
        }
        total += buffs.size() * rewards.getNewBuff();
        total += MutationEntry.getAssetMap().getAssetMap().size() * rewards.getNewMutation();
        return total;
    }

    @Test
    void discoveriesPayAboutTwiceTheResearchTree() {
        double ratio = (double) achievable() / required();
        assertTrue(ratio >= MIN_RATIO && ratio <= MAX_RATIO,
                "achievable " + achievable() + " / required " + required() + " = " + ratio);
    }

    @Test
    void everyCreatureIsASpeciesAndItsVariantsFoldIntoIt() {
        assertEquals("Cow", Catalogue.speciesOf("Cow"));
        assertEquals("Cow", Catalogue.speciesOf("Tamed_Cow_Calf"));
        assertEquals("Bison", Catalogue.speciesOf("Bison_Calf"), "a creature of the same gene family is its own species");
        assertEquals("Goblin_Lobber", Catalogue.speciesOf("Dungeon_Goblin_Lobber_Patrol"));
        assertEquals("Goblin_Gobliterator", Catalogue.speciesOf("Goblin_Gobliterator_Phase2_HM"));
        assertEquals("Crawler_Void", Catalogue.speciesOf("Crawler_Void_Surge"));
        assertEquals("Livestock_Cattle", Catalogue.profileOf("Bison").getId());
        assertNull(Catalogue.speciesOf("Livestock_Cattle"), "a gene profile is not a species");
        assertNull(Catalogue.speciesOf("Dragon_Fire"), "unfinished mobs are not in the Catalogue");
        assertNull(Catalogue.speciesOf("Some_Unprofiled_Role"));
        assertTrue(Catalogue.allSpecies().size() > 200, "about one species per creature");
    }

    @Test
    void savedRoleKeysMigrateToTheirSpecies() {
        GeneJournal journal = new GeneJournal();
        var source = new com.genetics.genome.Genome.Source("Cow", "Cow", null, null, 1L);
        journal.recordSample("Tamed_Cow", "Cow", source);
        journal.recordSample("Cow_Calf", "Calf", source);
        journal.recordSample("Livestock_Cattle", "Cattle", source);
        journal.addSpeciesGene("Tamed_Cow", "Stat:Health");
        journal.addSpeciesGene("Cow_Calf", "Drawback:Stamina");
        journal.addSpeciesGene("Cow_Calf", "Mutation:Bloodfang");
        journal.markCompleted("Cow_Calf");
        assertTrue(journal.migrateSpecies(Catalogue::speciesOf, (species, fallback) -> species));
        assertEquals(Set.of("Cow"), journal.getSpecies().keySet(), "variants fold into the creature, gene profiles are dropped");
        assertEquals(2, journal.getSpecies().get("Cow").getSamples());
        assertEquals(Set.of("Stat:Health", "Drawback:Stamina"), journal.genesOf("Cow"), "mutations are not species genes");
        assertTrue(!journal.isCompleted("Cow"), "completion is re-earned");
    }
}
