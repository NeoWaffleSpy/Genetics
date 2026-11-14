package com.genetics.labstation;

import com.genetics.TestAssets;
import com.genetics.codex.EffectEntry;
import com.genetics.codex.MobGeneProfile;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

/** Every buff belongs to a family group, and every species to a biome or tribe group, in the Catalogue. */
class CatalogueGroupsTest {
    @BeforeAll
    static void load() {
        TestAssets.load();
    }

    @Test
    void everyBuffHasAFamily() {
        List<String> orphans = new ArrayList<>();
        for (EffectEntry entry : EffectEntry.getAssetMap().getAssetMap().values()) {
            if (!entry.getId().equals("Default") && CatalogueTab.buffFamily(entry.getId()).equals("Other")) orphans.add(entry.getId());
        }
        assertEquals(List.of(), orphans);
    }

    @Test
    void everySpeciesHasACategory() {
        List<String> missing = new ArrayList<>();
        for (MobGeneProfile profile : MobGeneProfile.getAssetMap().getAssetMap().values()) {
            if (!profile.getId().equals("Default") && profile.getCategory() == null) missing.add(profile.getId());
        }
        assertEquals(List.of(), missing);
    }
}
