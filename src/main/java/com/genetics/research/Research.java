package com.genetics.research;

import com.genetics.journal.GeneJournal;
import com.genetics.journal.Journal;
import com.hypixel.hytale.builtin.crafting.CraftingPlugin;
import com.hypixel.hytale.component.ComponentAccessor;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import com.hypixel.hytale.server.core.util.NotificationUtil;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * The Research tree (design doc §9.5): which nodes a player has, buying one with Science, and the Lab features they
 * turn on. A feature that no node grants is always on, so removing a node from the data never locks a feature.
 */
public final class Research {
    public static final String SPLICING = "Splicing";
    public static final String MAKE_PERMANENT = "MakePermanent";
    public static final String GENETIC_LIBRARY = "GeneticLibrary";
    public static final String REFINING = "Refining";
    public static final Set<String> FEATURES = Set.of(SPLICING, MAKE_PERMANENT, GENETIC_LIBRARY, REFINING);

    /** The feature that must be researched before upgrading a Lab Station to this tier, or null. */
    @Nullable
    public static String featureForStationTier(int tier) {
        return switch (tier) {
            case 2 -> SPLICING;
            case 3 -> GENETIC_LIBRARY;
            default -> null;
        };
    }
    /** Known lanes, in display order; other lanes follow alphabetically. */
    public static final List<String> BRANCHES = List.of("Lab", "Reagents", "Syringes", "Genome");

    public enum State { RESEARCHED, AVAILABLE, LOCKED }

    private Research() {
    }

    /** Every node, by lane then order. */
    @Nonnull
    public static List<ResearchNode> all() {
        List<ResearchNode> nodes = new ArrayList<>(ResearchNode.getAssetMap().getAssetMap().values());
        nodes.sort(Comparator.comparingInt((ResearchNode n) -> branchRank(n.getBranch())).thenComparing(ResearchNode::getBranch)
                .thenComparingInt(ResearchNode::getOrder).thenComparing(ResearchNode::getId));
        return nodes;
    }

    /** The lanes that have nodes, in display order. */
    @Nonnull
    public static List<String> branches() {
        Set<String> out = new LinkedHashSet<>();
        for (ResearchNode node : all()) out.add(node.getBranch());
        return new ArrayList<>(out);
    }

    @Nonnull
    public static List<ResearchNode> branch(@Nonnull String branch) {
        return all().stream().filter(n -> n.getBranch().equals(branch)).toList();
    }

    private static int branchRank(@Nonnull String branch) {
        int i = BRANCHES.indexOf(branch);
        return i >= 0 ? i : BRANCHES.size();
    }

    public static boolean isResearched(@Nonnull GeneJournal journal, @Nonnull ResearchNode node) {
        return node.isStarter() || journal.isResearched(node.getId());
    }

    /** The first prerequisite not researched yet, or null if all are. */
    @Nullable
    public static ResearchNode missingRequirement(@Nonnull GeneJournal journal, @Nonnull ResearchNode node) {
        for (String id : node.getRequires()) {
            ResearchNode required = ResearchNode.getAssetMap().getAsset(id);
            if (required != null && !isResearched(journal, required)) return required;
        }
        return null;
    }

    @Nonnull
    public static State state(@Nonnull GeneJournal journal, @Nonnull ResearchNode node) {
        if (isResearched(journal, node)) return State.RESEARCHED;
        return missingRequirement(journal, node) == null ? State.AVAILABLE : State.LOCKED;
    }

    /** True if a researched node turns this feature on, or if no node grants it at all. */
    public static boolean hasFeature(@Nonnull GeneJournal journal, @Nonnull String feature) {
        boolean granted = false;
        for (ResearchNode node : ResearchNode.getAssetMap().getAssetMap().values()) {
            for (String f : node.getFeatures()) {
                if (!f.equals(feature)) continue;
                granted = true;
                if (isResearched(journal, node)) return true;
            }
        }
        return !granted;
    }

    /** The node that grants a feature (for "Research X first" messages), or null. */
    @Nullable
    public static ResearchNode grantingNode(@Nonnull String feature) {
        for (ResearchNode node : all()) for (String f : node.getFeatures()) if (f.equals(feature)) return node;
        return null;
    }

    /** Buys a node: pays its Science, teaches its recipes. Returns why it failed, or null on success. */
    @Nullable
    public static String research(@Nonnull Ref<EntityStore> ref, @Nonnull ComponentAccessor<EntityStore> accessor, @Nonnull PlayerRef player,
                                  @Nonnull ResearchNode node) {
        GeneJournal journal = Journal.of(ref, accessor);
        if (isResearched(journal, node)) return node.getName() + " is already researched.";
        ResearchNode missing = missingRequirement(journal, node);
        if (missing != null) return "Research " + missing.getName() + " first.";
        if (!journal.spendScience(node.getCost())) return "Not enough Science (" + journal.getScience() + " / " + node.getCost() + ").";
        journal.markResearched(node.getId());
        teach(ref, accessor, node);
        NotificationUtil.sendNotification(player.getPacketHandler(), Message.raw("Researched: " + node.getName()),
                Message.raw(node.getDescription()), (String) null);
        return null;
    }

    /** Researches a node for free (debug command, migration). */
    public static void grant(@Nonnull Ref<EntityStore> ref, @Nonnull ComponentAccessor<EntityStore> accessor, @Nonnull ResearchNode node) {
        Journal.of(ref, accessor).markResearched(node.getId());
        teach(ref, accessor, node);
    }

    /**
     * Brings a player up to date: teaches the recipes of every researched node, and marks as researched any node
     * whose recipes the player already knows (players who unlocked them with the old Journal milestones).
     */
    public static void sync(@Nonnull Ref<EntityStore> ref, @Nonnull ComponentAccessor<EntityStore> accessor) {
        Player player = accessor.getComponent(ref, Player.getComponentType());
        if (player == null) return;
        GeneJournal journal = Journal.of(ref, accessor);
        Set<String> known = player.getPlayerConfigData().getKnownRecipes();
        for (ResearchNode node : all()) {
            if (isResearched(journal, node)) {
                teach(ref, accessor, node);
            } else if (node.getRecipes().length > 0 && known.containsAll(List.of(node.getRecipes()))) {
                journal.markResearched(node.getId());
            }
        }
    }

    private static void teach(@Nonnull Ref<EntityStore> ref, @Nonnull ComponentAccessor<EntityStore> accessor, @Nonnull ResearchNode node) {
        Player player = accessor.getComponent(ref, Player.getComponentType());
        if (player == null) return;
        Set<String> known = player.getPlayerConfigData().getKnownRecipes();
        for (String recipe : node.getRecipes()) if (!known.contains(recipe)) CraftingPlugin.learnRecipe(ref, recipe, accessor);
    }
}
