package com.genetics;

import com.genetics.codex.EffectEntry;
import com.genetics.codex.MobGeneProfile;
import com.genetics.codex.MutationEntry;
import com.genetics.codex.StatEntry;
import com.genetics.research.Research;
import com.genetics.research.ResearchNode;
import com.genetics.tier.Tiers;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** The shipped data files decode, and reference each other consistently. */
class DataFilesTest {
    @BeforeAll
    static void load() {
        assertEquals(List.of(), TestAssets.load(), "data files that failed to decode");
    }

    @Test
    void researchNodesFormAValidTree() {
        Map<String, ResearchNode> nodes = ResearchNode.getAssetMap().getAssetMap();
        assertFalse(nodes.isEmpty());
        List<String> problems = new ArrayList<>();
        Set<String> modItems = modItemIds();
        for (ResearchNode node : nodes.values()) {
            if (!Research.BRANCHES.contains(node.getBranch())) problems.add(node.getId() + ": unknown branch " + node.getBranch());
            for (String req : node.getRequires()) if (!nodes.containsKey(req)) problems.add(node.getId() + ": requires unknown " + req);
            for (String feature : node.getFeatures()) if (!Research.FEATURES.contains(feature)) problems.add(node.getId() + ": unknown feature " + feature);
            for (String recipe : node.getRecipes()) if (!modItems.contains(recipe)) problems.add(node.getId() + ": recipe for unknown item " + recipe);
            if (node.isStarter() && node.getCost() != 0) problems.add(node.getId() + ": starter nodes are free");
            if (hasCycle(node.getId(), nodes, new HashSet<>())) problems.add(node.getId() + ": circular requirement");
        }
        for (String feature : Research.FEATURES) {
            if (nodes.values().stream().noneMatch(n -> List.of(n.getFeatures()).contains(feature))) problems.add("no node grants " + feature);
        }
        assertEquals(List.of(), problems);
    }

    @Test
    void mobProfilesReferToKnownData() {
        Map<String, String> roleOwner = new HashMap<>();
        List<String> problems = new ArrayList<>();
        for (MobGeneProfile profile : MobGeneProfile.getAssetMap().getAssetMap().values()) {
            String id = profile.getId();
            for (String role : profile.getRoles()) {
                String other = roleOwner.put(role, id);
                if (other != null) problems.add(role + " is in both " + other + " and " + id);
            }
            if (profile.getStatPool().length == 0) problems.add(id + ": empty stat pool");
            for (MobGeneProfile.StatRoll roll : profile.getStatPool()) {
                if (StatEntry.getAssetMap().getAsset(roll.getStat()) == null) problems.add(id + ": stat without a codex weight " + roll.getStat());
                if (roll.getMin() <= 0 || roll.getMax() < roll.getMin()) problems.add(id + ": bad range for " + roll.getStat());
            }
            for (MobGeneProfile.Drawback d : profile.getDrawbacks()) {
                if (StatEntry.getAssetMap().getAsset(d.getStat()) == null) problems.add(id + ": drawback stat without a weight " + d.getStat());
                if (d.getChance() <= 0 || d.getChance() > 1) problems.add(id + ": drawback chance out of (0, 1]");
            }
            if (profile.getTemporaryEffect() != null) checkEffect(id, profile.getTemporaryEffect().getId(), problems);
            for (MobGeneProfile.EffectRoll e : profile.getPermanentEffects()) checkEffect(id, e.getId(), problems);
            if (profile.getPermanentEffects().length > 0 && profile.getPermanentEffectChance() <= 0) {
                problems.add(id + ": permanent effects that can never roll");
            }
        }
        assertEquals(List.of(), problems);
    }

    @Test
    void mutationsHaveAValidMinimumTier() {
        for (MutationEntry entry : MutationEntry.getAssetMap().getAssetMap().values()) {
            assertTrue(Tiers.rank(entry.getMinTier()) >= 0, entry.getId() + " MinTier " + entry.getMinTier());
            assertTrue(entry.getComplexity() > 0, entry.getId() + " Complexity");
        }
    }

    @Test
    void everyBuffHasANameAndADescription() throws IOException {
        String lang = Files.readString(TestAssets.resources().resolve("Server/Languages/en-US/server.lang"));
        List<String> missing = new ArrayList<>();
        for (EffectEntry entry : EffectEntry.getAssetMap().getAssetMap().values()) {
            if (entry.getId().equals("Default")) continue;
            String key = entry.getName() != null ? entry.getName().replaceFirst("^server[.]", "") : null;
            if (key == null || !lang.contains(key + " =")) missing.add(entry.getId() + " name");
            else if (!lang.contains(key.replaceFirst("[.]name$", ".desc") + " =")) missing.add(entry.getId() + " description");
        }
        assertEquals(List.of(), missing);
    }

    @Test
    void permanentEffectsCostMoreThanTemporary() {
        for (EffectEntry entry : EffectEntry.getAssetMap().getAssetMap().values()) {
            assertTrue(entry.getComplexity(true) > entry.getComplexity(false), entry.getId());
        }
    }

    private static void checkEffect(String profile, String effect, List<String> problems) {
        if (effect == null) problems.add(profile + ": effect without an id");
        else if (EffectEntry.getAssetMap().getAsset(effect) == null) problems.add(profile + ": effect without a codex entry " + effect);
    }

    private static boolean hasCycle(String id, Map<String, ResearchNode> nodes, Set<String> path) {
        if (!path.add(id)) return true;
        ResearchNode node = nodes.get(id);
        if (node != null) for (String req : node.getRequires()) if (hasCycle(req, nodes, new HashSet<>(path))) return true;
        return false;
    }

    /** Ids of the mod's own items (file names under Server/Item/Items). */
    static Set<String> modItemIds() {
        Set<String> ids = new HashSet<>();
        try (var files = Files.walk(TestAssets.resources().resolve("Server/Item/Items"))) {
            files.filter(p -> p.toString().endsWith(".json"))
                    .forEach(p -> ids.add(p.getFileName().toString().replace(".json", "")));
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
        return ids;
    }
}
