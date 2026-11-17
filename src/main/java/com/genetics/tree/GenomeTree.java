package com.genetics.tree;

import com.genetics.codex.Balance;
import com.genetics.genome.ComplexityCalculator;
import com.genetics.genome.Gene;
import com.genetics.genome.Genome;
import com.genetics.library.GeneLibrary;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Deque;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Predicate;

/**
 * The Genome Tree rules (design doc §8.3). Nodes are paid in Complexity, so a player's Genome Capacity is the tree's
 * budget. Owned nodes must stay connected to the Origin. The genetic material it makes holds:
 * <ul>
 *   <li>one stat gene per stat, the sum of the Small, Big and Sink nodes that raise it;</li>
 *   <li>one permanent effect per buff: owning n of its Cores gives its tier n;</li>
 *   <li>one mutation per Notable.</li>
 * </ul>
 * Its Complexity is the tree's cost, not the Codex sum: injecting it checks capacity against that cost.
 */
public final class GenomeTree {
    /** Source id of genetic material made on the tree. */
    public static final String SOURCE_ID = "Genetics_GenomeTree";

    private GenomeTree() {
    }

    /** What an allocation costs, by part. */
    public record Cost(double path, double sinks, double notables, double cores) {
        public double total() {
            return round(path + sinks + notables + cores);
        }
    }

    /** A node's base cost: its own Cost, or its type's default. For a Sink, the first rank. */
    public static double baseCost(@Nonnull TreeNode node, @Nonnull Balance.GenomeTree balance) {
        if (node.getCost() > 0) return node.getCost();
        TreeNode.Kind kind = node.kind();
        if (kind == null) return 0;
        return switch (kind) {
            case ORIGIN -> 0;
            case SMALL -> balance.getSmallCost();
            case BIG -> balance.getBigCost();
            case NOTABLE -> balance.getNotableCost();
            case CORE -> balance.getCoreCost();
            case SINK -> balance.getSinkCost();
        };
    }

    /** Cost of a Sink at this many ranks: rank r costs base + (r - 1) x growth. */
    public static double sinkCost(double base, double growth, int ranks) {
        return ranks <= 0 ? 0 : ranks * base + growth * ranks * (ranks - 1) / 2.0;
    }

    /**
     * Total cost of nodes whose price multiplies with each one owned. The cheapest pays the highest multiplier, so the
     * total never depends on the order nodes were taken in.
     */
    public static double escalated(@Nonnull List<Double> bases, double multiplier) {
        List<Double> sorted = new ArrayList<>(bases);
        sorted.sort(Comparator.reverseOrder());
        double total = 0;
        double factor = 1;
        for (double base : sorted) {
            total += base * factor;
            factor *= multiplier;
        }
        return total;
    }

    @Nonnull
    public static Cost cost(@Nonnull TreeLayout layout, @Nonnull TreeAllocation allocation, @Nonnull Balance.GenomeTree balance) {
        double path = 0;
        double sinks = 0;
        List<Double> notables = new ArrayList<>();
        List<Double> cores = new ArrayList<>();
        for (String id : allocation.ids()) {
            TreeNode node = layout.node(id);
            if (node == null || node.kind() == null) continue;
            double base = baseCost(node, balance);
            switch (node.kind()) {
                case SMALL, BIG -> path += base;
                case SINK -> sinks += sinkCost(base, balance.getSinkCostGrowth(), allocation.rank(id));
                case NOTABLE -> notables.add(base);
                case CORE -> cores.add(base);
                case ORIGIN -> {
                }
            }
        }
        return new Cost(path, sinks, escalated(notables, balance.getNotableMultiplier()), escalated(cores, balance.getCoreMultiplier()));
    }

    /** What taking this node (or a Sink's next rank) adds to the total. */
    public static double nextCost(@Nonnull TreeLayout layout, @Nonnull TreeAllocation allocation, @Nonnull TreeNode node,
                                  @Nonnull Balance.GenomeTree balance) {
        TreeAllocation next = new TreeAllocation(allocation);
        next.set(node.getId(), node.kind() == TreeNode.Kind.SINK ? allocation.rank(node.getId()) + 1 : 1);
        return round(cost(layout, next, balance).total() - cost(layout, allocation, balance).total());
    }

    /** What the next Notable or Core of a kind would cost at its type's default price. */
    public static double nextDefault(@Nonnull TreeLayout layout, @Nonnull TreeAllocation allocation, @Nonnull TreeNode.Kind kind,
                                     @Nonnull Balance.GenomeTree balance) {
        long owned = allocation.ids().stream().map(layout::node).filter(n -> n != null && n.kind() == kind).count();
        return kind == TreeNode.Kind.NOTABLE ? balance.getNotableCost() * Math.pow(balance.getNotableMultiplier(), owned)
                : balance.getCoreCost() * Math.pow(balance.getCoreMultiplier(), owned);
    }

    /** True if the node is linked to the Origin or to an owned node. */
    public static boolean reachable(@Nonnull TreeLayout layout, @Nonnull TreeAllocation allocation, @Nonnull String id) {
        TreeNode origin = layout.origin();
        for (String other : layout.neighbours(id)) {
            if (allocation.owns(other) || (origin != null && origin.getId().equals(other))) return true;
        }
        return false;
    }

    /**
     * Why the node (or a Sink's next rank) can't be taken, or null if it can.
     *
     * @param unlocked     whether the player may take a Notable or Core (see {@link #unlockedBy})
     * @param maxMutations most Notables at once
     */
    @Nullable
    public static String whyNot(@Nonnull TreeLayout layout, @Nonnull TreeAllocation allocation, @Nonnull TreeNode node, double capacity,
                                @Nonnull Balance.GenomeTree balance, @Nonnull Predicate<TreeNode> unlocked, int maxMutations) {
        TreeNode.Kind kind = node.kind();
        if (kind == null) return "This node is broken (unknown type).";
        if (kind == TreeNode.Kind.ORIGIN) return "The origin is always yours.";
        if (allocation.owns(node.getId()) && kind != TreeNode.Kind.SINK) return "Already taken.";
        if (!allocation.owns(node.getId()) && !reachable(layout, allocation, node.getId())) return "Not linked to your nodes yet.";
        if (!unlocked.test(node)) {
            return kind == TreeNode.Kind.NOTABLE
                    ? "Extract " + nodeName(node) + " into your Genetic Library first."
                    : "Extract any form of " + nodeName(node) + " into your Genetic Library first.";
        }
        if (kind == TreeNode.Kind.NOTABLE) {
            long owned = allocation.ids().stream().map(layout::node).filter(n -> n != null && n.kind() == TreeNode.Kind.NOTABLE).count();
            if (owned >= maxMutations) return "At most " + maxMutations + " mutations in one genome.";
        }
        if (kind == TreeNode.Kind.CORE) {
            int owned = buffCount(layout, allocation, node.getBuff());
            if (owned >= node.getTiers().size()) return nodeName(node) + " has no higher tier.";
        }
        double total = cost(layout, allocation, balance).total() + nextCost(layout, allocation, node, balance);
        if (total > capacity + 1e-9) return "Over your Genome Capacity (" + format(total) + " / " + format(capacity) + ").";
        return null;
    }

    /** Takes the node (or a Sink's next rank) if allowed. Returns why not, or null when done. */
    @Nullable
    public static String take(@Nonnull TreeLayout layout, @Nonnull TreeAllocation allocation, @Nonnull TreeNode node, double capacity,
                              @Nonnull Balance.GenomeTree balance, @Nonnull Predicate<TreeNode> unlocked, int maxMutations) {
        String why = whyNot(layout, allocation, node, capacity, balance, unlocked, maxMutations);
        if (why != null) return why;
        allocation.set(node.getId(), node.kind() == TreeNode.Kind.SINK ? allocation.rank(node.getId()) + 1 : 1);
        return null;
    }

    /**
     * Gives back the node (or a Sink's top rank). Returns why it can't be, or null when done: a node whose removal
     * would cut owned nodes off from the Origin stays.
     */
    @Nullable
    public static String refund(@Nonnull TreeLayout layout, @Nonnull TreeAllocation allocation, @Nonnull TreeNode node) {
        String id = node.getId();
        if (!allocation.owns(id)) return "You don't own this node.";
        if (node.kind() == TreeNode.Kind.SINK && allocation.rank(id) > 1) {
            allocation.set(id, allocation.rank(id) - 1);
            return null;
        }
        TreeAllocation without = new TreeAllocation(allocation);
        without.remove(id);
        if (!connected(layout, without).containsAll(without.ids())) return "Other nodes depend on this one: give those back first.";
        allocation.remove(id);
        return null;
    }

    /** Owned nodes still linked to the Origin through owned nodes. */
    @Nonnull
    public static Set<String> connected(@Nonnull TreeLayout layout, @Nonnull TreeAllocation allocation) {
        Set<String> seen = new HashSet<>();
        TreeNode origin = layout.origin();
        if (origin == null) return seen;
        Deque<String> queue = new ArrayDeque<>();
        queue.add(origin.getId());
        Set<String> visited = new HashSet<>(Set.of(origin.getId()));
        while (!queue.isEmpty()) {
            for (String next : layout.neighbours(queue.poll())) {
                if (allocation.owns(next) && visited.add(next)) {
                    seen.add(next);
                    queue.add(next);
                }
            }
        }
        return seen;
    }

    /** Drops owned nodes that no longer exist or are cut off (after the tree file changed). Returns how many. */
    public static int prune(@Nonnull TreeLayout layout, @Nonnull TreeAllocation allocation) {
        int before = allocation.ids().size();
        for (String id : List.copyOf(allocation.ids())) {
            TreeNode node = layout.node(id);
            if (node == null || node.kind() == null || node.kind() == TreeNode.Kind.ORIGIN) allocation.remove(id);
        }
        Set<String> keep = connected(layout, allocation);
        for (String id : List.copyOf(allocation.ids())) if (!keep.contains(id)) allocation.remove(id);
        return before - allocation.ids().size();
    }

    /** Owned Cores of a buff. */
    public static int buffCount(@Nonnull TreeLayout layout, @Nonnull TreeAllocation allocation, @Nullable String buff) {
        if (buff == null) return 0;
        int n = 0;
        for (String id : allocation.ids()) {
            TreeNode node = layout.node(id);
            if (node != null && node.kind() == TreeNode.Kind.CORE && buff.equals(node.getBuff())) n++;
        }
        return n;
    }

    /** The genes an allocation gives: stats summed per stat, one buff per Core group at its tier, one mutation per Notable. */
    @Nonnull
    public static List<Gene> genes(@Nonnull TreeLayout layout, @Nonnull TreeAllocation allocation) {
        Map<String, Double> stats = new LinkedHashMap<>();
        Map<String, List<String>> buffs = new LinkedHashMap<>();
        Map<String, Integer> buffCounts = new LinkedHashMap<>();
        List<String> mutations = new ArrayList<>();
        for (String id : allocation.ids()) {
            TreeNode node = layout.node(id);
            if (node == null || node.kind() == null) continue;
            switch (node.kind()) {
                case SMALL, BIG -> {
                    if (node.getStat() != null) stats.merge(node.getStat(), node.getValue(), Double::sum);
                }
                case SINK -> {
                    if (node.getStat() != null) stats.merge(node.getStat(), node.getValue() * allocation.rank(id), Double::sum);
                }
                case CORE -> {
                    if (node.getBuff() == null || node.getTiers().isEmpty()) continue;
                    buffs.putIfAbsent(node.getBuff(), node.getTiers());
                    buffCounts.merge(node.getBuff(), 1, Integer::sum);
                }
                case NOTABLE -> {
                    if (node.getMutation() != null && !mutations.contains(node.getMutation())) mutations.add(node.getMutation());
                }
                case ORIGIN -> {
                }
            }
        }
        List<Gene> genes = new ArrayList<>();
        stats.forEach((stat, percent) -> {
            double value = round(percent);
            if (value != 0) genes.add(new Gene.Stat(stat, value));
        });
        buffs.forEach((buff, tiers) -> {
            int tier = Math.min(buffCounts.getOrDefault(buff, 0), tiers.size());
            if (tier > 0) genes.add(new Gene.Effect(tiers.get(tier - 1), true, 0));
        });
        for (String mutation : mutations) genes.add(new Gene.Mutation(mutation));
        return genes;
    }

    /** The genetic material an allocation makes, or null if it gives nothing. */
    @Nullable
    public static Genome genome(@Nonnull TreeLayout layout, @Nonnull TreeAllocation allocation, @Nonnull Balance.GenomeTree balance) {
        List<Gene> genes = genes(layout, allocation);
        if (genes.isEmpty()) return null;
        double complexity = Math.max(ComplexityCalculator.MINIMUM, cost(layout, allocation, balance).total());
        return new Genome(Genome.FORMAT_VERSION, true, true, complexity,
                new Genome.Source(SOURCE_ID, "Designed", null, null, System.currentTimeMillis(), true), genes);
    }

    /** True for genetic material made on the tree: its stored Complexity is the tree's cost. */
    public static boolean isTreeGenome(@Nullable Genome genome) {
        return genome != null && genome.source() != null && SOURCE_ID.equals(genome.source().mobId());
    }

    /**
     * How much the genome's stored Complexity differs from the Codex sum of its genes: 1 for ordinary genomes, the
     * tree's markup for tree genomes. Capacity checks and degradation scale by it.
     */
    public static double complexityScale(@Nonnull Genome genome) {
        if (!isTreeGenome(genome)) return 1;
        double codex = ComplexityCalculator.compute(genome.genes());
        return codex > 0 ? Math.max(0.01, genome.complexity() / codex) : 1;
    }

    /**
     * Whether the player may take a Notable or Core: with {@code RequireLibrary}, a Notable needs its mutation in the
     * library and a Core any form (temporary or permanent, any tier) of its buff.
     */
    @Nonnull
    public static Predicate<TreeNode> unlockedBy(@Nonnull GeneLibrary library, boolean requireLibrary) {
        if (!requireLibrary) return node -> true;
        Set<String> mutations = new HashSet<>();
        Set<String> effects = new HashSet<>();
        for (GeneLibrary.Entry entry : library.getEntries()) {
            switch (entry.getGene()) {
                case Gene.Mutation mutation -> mutations.add(mutation.mutation());
                case Gene.Effect effect -> effects.add(effect.effect());
                case null, default -> {
                }
            }
        }
        return node -> switch (node.kind()) {
            case NOTABLE -> node.getMutation() != null && mutations.contains(node.getMutation());
            case CORE -> node.getTiers().stream().anyMatch(effects::contains);
            case null -> false;
            default -> true;
        };
    }

    /**
     * What is wrong with a tree's layout (empty if nothing): one Origin; known types; links to existing nodes in the
     * same row or column; one node per cell; Notables, Cores and Sinks at the end of a branch (one link), with
     * Notables and Cores hanging off a Small or Big node; each node's data present; no more Cores of a buff than it
     * has tiers; every node reachable from the Origin.
     */
    @Nonnull
    public static List<String> check(@Nonnull TreeLayout layout) {
        List<String> problems = new ArrayList<>();
        List<TreeNode> nodes = layout.getNodes();
        if (nodes.isEmpty()) return List.of("the tree has no nodes.");
        long origins = nodes.stream().filter(n -> n.kind() == TreeNode.Kind.ORIGIN).count();
        if (origins != 1) problems.add("needs exactly one Origin, found " + origins + ".");
        Map<String, String> cells = new LinkedHashMap<>();
        Map<String, List<String>> buffTiers = new LinkedHashMap<>();
        Map<String, Integer> buffCores = new LinkedHashMap<>();
        for (TreeNode node : nodes) {
            String id = node.getId();
            TreeNode.Kind kind = node.kind();
            if (kind == null) {
                problems.add(id + ": unknown Type.");
                continue;
            }
            String other = cells.put(node.getX() + "," + node.getY(), id);
            if (other != null) problems.add(id + " and " + other + " share a cell.");
            for (String link : layout.neighbours(id)) {
                TreeNode target = layout.node(link);
                if (target == null) problems.add(id + ": links to unknown node " + link + ".");
                else if (target.getX() != node.getX() && target.getY() != node.getY()) problems.add(id + ": link to " + link + " is not straight.");
            }
            int degree = layout.neighbours(id).size();
            switch (kind) {
                case SMALL, BIG -> {
                    if (node.getStat() == null || node.getValue() <= 0) problems.add(id + ": needs a Stat and a positive Value.");
                }
                case SINK -> {
                    if (node.getStat() == null || node.getValue() <= 0) problems.add(id + ": needs a Stat and a positive Value.");
                    if (degree != 1) problems.add(id + ": a Sink must end a branch (one link), has " + degree + ".");
                }
                case NOTABLE, CORE -> {
                    if (kind == TreeNode.Kind.NOTABLE && node.getMutation() == null) problems.add(id + ": needs a Mutation.");
                    if (kind == TreeNode.Kind.CORE) {
                        if (node.getBuff() == null || node.getTiers().isEmpty()) problems.add(id + ": needs a Buff and its Tiers.");
                        else {
                            List<String> known = buffTiers.putIfAbsent(node.getBuff(), node.getTiers());
                            if (known != null && !known.equals(node.getTiers())) problems.add(id + ": Tiers differ from other " + node.getBuff() + " Cores.");
                            buffCores.merge(node.getBuff(), 1, Integer::sum);
                        }
                    }
                    if (degree != 1) {
                        problems.add(id + ": must end a side branch (one link), has " + degree + ".");
                    } else {
                        TreeNode parent = layout.node(layout.neighbours(id).iterator().next());
                        if (parent != null && parent.kind() != TreeNode.Kind.SMALL && parent.kind() != TreeNode.Kind.BIG) {
                            problems.add(id + ": must hang off a Small or Big node, not " + parent.getId() + ".");
                        }
                    }
                }
                case ORIGIN -> {
                }
            }
        }
        buffCores.forEach((buff, count) -> {
            int tiers = buffTiers.getOrDefault(buff, List.of()).size();
            if (count > tiers) problems.add(buff + ": " + count + " Cores but only " + tiers + " tiers.");
        });
        TreeAllocation all = new TreeAllocation();
        for (TreeNode node : nodes) if (node.kind() != TreeNode.Kind.ORIGIN) all.set(node.getId(), 1);
        Set<String> reached = connected(layout, all);
        for (String id : all.ids()) if (!reached.contains(id)) problems.add(id + ": can't be reached from the Origin.");
        return problems;
    }

    /** A node's display name: its mutation, buff or stat. */
    @Nonnull
    public static String nodeName(@Nonnull TreeNode node) {
        TreeNode.Kind kind = node.kind();
        if (kind == null) return node.getId();
        return switch (kind) {
            case ORIGIN -> "Origin";
            case NOTABLE -> node.getMutation() != null ? com.genetics.genome.GeneText.mutationName(node.getMutation(), null) : node.getId();
            case CORE -> node.getBuff() != null ? com.genetics.genome.GeneText.cleanEffectId(node.getBuff()) : node.getId();
            default -> node.getStat() != null ? com.genetics.genome.GeneText.statName(node.getStat()) : node.getId();
        };
    }

    static double round(double value) {
        return Math.round(value * 10) / 10.0;
    }

    @Nonnull
    static String format(double value) {
        return com.genetics.genome.GeneText.number(round(value));
    }
}
