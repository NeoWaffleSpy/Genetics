package com.genetics.tree;

import com.genetics.TestAssets;
import com.genetics.codex.Balance;
import com.genetics.genome.Gene;
import com.genetics.genome.Genome;
import com.genetics.interaction.Injection;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Deque;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.ToIntFunction;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Design doc §8.3: the Genome Tree. */
class GenomeTreeTest {
    private static final ToIntFunction<TreeNode> ALL = node -> Integer.MAX_VALUE;

    @BeforeAll
    static void load() {
        assertEquals(List.of(), TestAssets.load());
    }

    private static TreeLayout tree() {
        TreeLayout layout = TreeLayout.get();
        assertTrue(layout.getNodes().size() > 20, "the shipped tree loaded");
        return layout;
    }

    private static Balance.GenomeTree balance() {
        return Balance.get().genomeTree();
    }

    /** The shortest path from the Origin to a node (the node included, the Origin not). */
    private static List<String> path(TreeLayout layout, String target) {
        String origin = layout.origin().getId();
        Map<String, String> parent = new HashMap<>();
        Deque<String> queue = new ArrayDeque<>(List.of(origin));
        parent.put(origin, origin);
        while (!queue.isEmpty()) {
            String at = queue.poll();
            for (String next : layout.neighbours(at)) {
                if (parent.putIfAbsent(next, at) == null) queue.add(next);
            }
        }
        List<String> out = new ArrayList<>();
        for (String at = target; !at.equals(origin); at = parent.get(at)) out.add(at);
        Collections.reverse(out);
        return out;
    }

    private static void own(TreeLayout layout, TreeAllocation allocation, String target) {
        for (String id : path(layout, target)) allocation.set(id, Math.max(1, allocation.rank(id)));
    }

    private static List<String> nodesOf(TreeLayout layout, TreeNode.Kind kind, String buffOrMutation) {
        return layout.getNodes().stream().filter(n -> n.kind() == kind
                && (buffOrMutation.equals(n.getBuff()) || buffOrMutation.equals(n.getMutation()))).map(TreeNode::getId).toList();
    }

    @Test
    void theShippedTreeFollowsTheLayoutRules() {
        assertEquals(List.of(), GenomeTree.check(tree()));
    }

    @Test
    void everyMutationAndEveryBuffTierIsOnTheTree() {
        TreeLayout layout = tree();
        for (String mutation : com.genetics.codex.MutationEntry.getAssetMap().getAssetMap().keySet()) {
            assertEquals(1, nodesOf(layout, TreeNode.Kind.NOTABLE, mutation).size(), mutation + " has one Notable");
        }
        Map<String, Integer> cores = new HashMap<>();
        for (TreeNode node : layout.getNodes()) {
            if (node.kind() == TreeNode.Kind.CORE) cores.merge(node.getBuff(), 1, Integer::sum);
        }
        for (TreeNode node : layout.getNodes()) {
            if (node.kind() == TreeNode.Kind.CORE) {
                assertEquals(node.getTiers().size(), cores.get(node.getBuff()), node.getBuff() + " has one Core per tier");
            }
        }
        for (com.genetics.codex.EffectEntry effect : com.genetics.codex.EffectEntry.getAssetMap().getAssetMap().values()) {
            if (effect.getId().equals("Default")) continue;
            assertTrue(layout.getNodes().stream().anyMatch(n -> n.getTiers().contains(effect.getId())), effect.getId() + " is a Core tier");
        }
    }

    @Test
    void notablesDoubleAndCoresTriple() {
        assertEquals(12 + 24 + 48, GenomeTree.escalated(List.of(12.0, 12.0, 12.0), 2));
        assertEquals(15 + 45 + 135, GenomeTree.escalated(List.of(15.0, 15.0, 15.0), 3));
        // The cheapest node pays the highest multiplier, whatever the order.
        assertEquals(20 + 10 * 2, GenomeTree.escalated(List.of(10.0, 20.0), 2));
        assertEquals(GenomeTree.escalated(List.of(10.0, 20.0), 2), GenomeTree.escalated(List.of(20.0, 10.0), 2));
    }

    @Test
    void sinkRanksGetDearer() {
        assertEquals(3 + 4 + 5, GenomeTree.sinkCost(3, 1, 3));
        assertEquals(0, GenomeTree.sinkCost(3, 1, 0));
    }

    @Test
    void nodesMustBeLinkedToTheAllocation() {
        TreeLayout layout = tree();
        TreeAllocation allocation = new TreeAllocation();
        String near = layout.neighbours(layout.origin().getId()).iterator().next();
        String far = nodesOf(layout, TreeNode.Kind.CORE, "Might").getFirst();
        assertNull(GenomeTree.whyNot(layout, allocation, layout.node(near), 1000, balance(), ALL));
        assertNotNull(GenomeTree.whyNot(layout, allocation, layout.node(far), 1000, balance(), ALL));
    }

    @Test
    void ownedNodesStayConnectedToTheOrigin() {
        TreeLayout layout = tree();
        TreeAllocation allocation = new TreeAllocation();
        List<String> route = path(layout, nodesOf(layout, TreeNode.Kind.CORE, "Might").getFirst());
        own(layout, allocation, route.getLast());
        assertNotNull(GenomeTree.refund(layout, allocation, layout.node(route.getFirst())), "the first step holds the rest");
        assertNull(GenomeTree.refund(layout, allocation, layout.node(route.getLast())), "the end of the branch can go");
        assertEquals(route.size() - 1, allocation.ids().size());
    }

    @Test
    void threeMightCoresGiveTierThree() {
        TreeLayout layout = tree();
        TreeAllocation allocation = new TreeAllocation();
        List<String> cores = nodesOf(layout, TreeNode.Kind.CORE, "Might");
        assertEquals(3, cores.size());
        own(layout, allocation, cores.get(0));
        assertTrue(GenomeTree.genes(layout, allocation).contains(new Gene.Effect("Genetics_Might_T1", true, 0)));
        own(layout, allocation, cores.get(1));
        own(layout, allocation, cores.get(2));
        List<Gene> genes = GenomeTree.genes(layout, allocation);
        assertTrue(genes.contains(new Gene.Effect("Genetics_Might_T3", true, 0)), genes.toString());
        assertEquals(1, genes.stream().filter(g -> g instanceof Gene.Effect).count(), "one effect per buff, at its tier");
        assertEquals(15 + 45 + 135, GenomeTree.cost(layout, allocation, balance()).cores());
    }

    @Test
    void buffsWithFewerTiersHaveFewerCores() {
        TreeLayout layout = tree();
        assertEquals(1, nodesOf(layout, TreeNode.Kind.CORE, "NightVision").size());
        assertEquals(2, nodesOf(layout, TreeNode.Kind.CORE, "Hide").size());
    }

    @Test
    void statsAddUpAndSinksScaleWithRank() {
        TreeLayout layout = tree();
        TreeAllocation allocation = new TreeAllocation();
        TreeNode sink = layout.getNodes().stream().filter(n -> n.kind() == TreeNode.Kind.SINK && "Health".equals(n.getStat())).findFirst().orElseThrow();
        own(layout, allocation, sink.getId());
        allocation.set(sink.getId(), 3);
        double expected = 0;
        for (String id : allocation.ids()) {
            TreeNode node = layout.node(id);
            if ("Health".equals(node.getStat())) expected += node.getValue() * allocation.rank(id);
        }
        Gene.Stat health = (Gene.Stat) GenomeTree.genes(layout, allocation).stream()
                .filter(g -> g instanceof Gene.Stat s && s.stat().equals("Health")).findFirst().orElseThrow();
        assertEquals(expected, health.percent(), 1e-9);
    }

    @Test
    void capacityIsTheBudget() {
        TreeLayout layout = tree();
        TreeAllocation allocation = new TreeAllocation();
        String near = layout.neighbours(layout.origin().getId()).iterator().next();
        assertNotNull(GenomeTree.whyNot(layout, allocation, layout.node(near), 1, balance(), ALL));
        assertNull(GenomeTree.take(layout, allocation, layout.node(near), 20, balance(), ALL));
        assertTrue(allocation.owns(near));
    }

    @Test
    void notablesNeedTheirMutationDiscovered() {
        TreeLayout layout = tree();
        TreeNode bloodfang = layout.node(nodesOf(layout, TreeNode.Kind.NOTABLE, "Bloodfang").getFirst());
        TreeAllocation allocation = new TreeAllocation();
        own(layout, allocation, bloodfang.getId());
        allocation.remove(bloodfang.getId());
        assertNotNull(GenomeTree.lockReason(layout, allocation, bloodfang, GenomeTree.unlocksFrom(Set.of(), Set.of(), true)));
        assertNull(GenomeTree.take(layout, allocation, bloodfang, 1000, balance(), GenomeTree.unlocksFrom(Set.of(), Set.of("Bloodfang"), true)));
        assertNull(GenomeTree.lockReason(layout, new TreeAllocation(), bloodfang, GenomeTree.unlocksFrom(Set.of(), Set.of(), false)));
    }

    @Test
    void coresFollowTheHighestDiscoveredTier() {
        TreeLayout layout = tree();
        List<String> cores = nodesOf(layout, TreeNode.Kind.CORE, "Might");
        TreeAllocation allocation = new TreeAllocation();
        for (String core : cores) {
            own(layout, allocation, core);
            allocation.remove(core);
        }
        // Tiers 1 and 2 discovered: two Might Cores, not three.
        ToIntFunction<TreeNode> unlocks = GenomeTree.unlocksFrom(Set.of("Genetics_Might_T1", "Genetics_Might_T2"), Set.of(), true);
        assertNull(GenomeTree.take(layout, allocation, layout.node(cores.get(0)), 1000, balance(), unlocks));
        assertNull(GenomeTree.take(layout, allocation, layout.node(cores.get(1)), 1000, balance(), unlocks));
        String third = GenomeTree.take(layout, allocation, layout.node(cores.get(2)), 1000, balance(), unlocks);
        assertNotNull(third);
        assertTrue(third.contains("tier 2"), third);
        // Only tier 2 discovered still counts as two: the highest tier is what matters.
        assertEquals(2, GenomeTree.unlocksFrom(Set.of("Genetics_Might_T2"), Set.of(), true).applyAsInt(layout.node(cores.get(0))));
        assertEquals(0, GenomeTree.unlocksFrom(Set.of(), Set.of(), true).applyAsInt(layout.node(cores.get(0))));
    }

    @Test
    void treeMaterialCountsAtTheTreeCost() {
        TreeLayout layout = tree();
        TreeAllocation allocation = new TreeAllocation();
        own(layout, allocation, nodesOf(layout, TreeNode.Kind.CORE, "Might").getFirst());
        own(layout, allocation, nodesOf(layout, TreeNode.Kind.NOTABLE, "Bloodfang").getFirst());
        Genome genome = GenomeTree.genome(layout, allocation, balance());
        assertNotNull(genome);
        double cost = GenomeTree.cost(layout, allocation, balance()).total();
        assertTrue(GenomeTree.isTreeGenome(genome));
        assertEquals(cost, genome.complexity(), 1e-9);
        assertEquals(cost, Injection.lastingComplexity(genome), 0.11);
        Genome copy = Genome.fromBson(genome.toBson());
        assertNotNull(copy);
        assertEquals(cost, copy.complexity(), 1e-9, "the stored cost survives saving");
    }

    @Test
    void allocationsSaveAndLoad() {
        TreeAllocation allocation = new TreeAllocation();
        allocation.set("A", 1);
        allocation.set("Sink", 4);
        TreeAllocation loaded = TreeAllocation.load(allocation.save());
        assertEquals(1, loaded.rank("A"));
        assertEquals(4, loaded.rank("Sink"));
        assertEquals(0, loaded.rank("B"));
    }

    @Test
    void resistancesAreCapped() {
        Balance.GenomeTree balance = balance();
        assertEquals(0.9, SpecialStats.damageFactor(10, balance), 1e-9);
        assertEquals(1 - balance.getDamageResistanceCap() / 100, SpecialStats.damageFactor(500, balance), 1e-9);
        assertEquals(1 - balance.getKnockbackResistanceCap() / 100, SpecialStats.knockbackFactor(500, balance), 1e-9);
    }
}
