package com.genetics.labstation;

import com.genetics.capacity.GenomeCapacity;
import com.genetics.codex.Balance;
import com.genetics.codex.MutationEntry;
import com.genetics.genome.Gene;
import com.genetics.genome.GeneText;
import com.genetics.genome.Genome;
import com.genetics.genome.Translations;
import com.genetics.interaction.Syringes;
import com.genetics.journal.Catalogue;
import com.genetics.journal.GeneJournal;
import com.genetics.journal.Journal;
import com.genetics.research.Research;
import com.genetics.research.ResearchNode;
import com.genetics.tier.ComplexityTier;
import com.genetics.tier.Materials;
import com.genetics.tier.Tiers;
import com.genetics.tree.GenomeTree;
import com.genetics.tree.TreeAllocation;
import com.genetics.tree.TreeLayout;
import com.genetics.tree.TreeNode;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.protocol.packets.interface_.CustomUIEventBindingType;
import com.hypixel.hytale.server.core.inventory.ItemStack;
import com.hypixel.hytale.server.core.inventory.MaterialQuantity;
import com.hypixel.hytale.server.core.ui.Anchor;
import com.hypixel.hytale.server.core.ui.Value;
import com.hypixel.hytale.server.core.ui.builder.EventData;
import com.hypixel.hytale.server.core.ui.builder.UICommandBuilder;
import com.hypixel.hytale.server.core.ui.builder.UIEventBuilder;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.List;
import java.util.function.ToIntFunction;

/**
 * The Genome Tree tab (design doc §8.3), which replaced the Library tab: the tree on the left, where a click takes a
 * node (or a Sink's next rank) and a right click gives it back; the sidebar on the right with the syringe slot, the
 * capacity the tree spends, the genes the owned nodes give and what crafting them costs. Notables and Cores open up
 * with Catalogue discoveries. Needs the Genome Tree research and a Lab Station T3.
 */
final class TreeTab {
    private static final String NODE = "Genetics/TreeNode.ui";
    private static final String LINK = "Genetics/TreeLink.ui";
    private static final String COST_LINE = "Genetics/TreeCostLine.ui";
    /** Inner width of the sidebar's capacity track: 262 sidebar - 2 x 10 padding - 64 slot - 10 gap - 2 outline. */
    private static final int TRACK = 166;
    /** Space between the canvas edge and the first grid cell. */
    private static final int MARGIN = 2;
    /** Room beside the canvas for the up-and-down scrollbar. */
    private static final int SCROLLBAR = 14;
    private static final int LINK_WIDTH = 2;
    /** Node states, matching the state groups of TreeNode.ui. */
    static final List<String> STATES = List.of("Off", "Avail", "Locked", "OnStat", "OnBig", "OnNotable", "OnCore", "OnSink", "OnOrigin",
            "DOff", "DAvail", "DLocked", "DOn");
    private static final String HELP = "Click a node linked to yours to take it, right click to give it back. Sinks take a rank per click."
            + " Notables (mutations) and Cores (permanent buffs) sit at the end of side branches and open up as you discover them in the Catalogue;"
            + " each one owned makes the next dearer.";

    private TreeTab() {
    }

    /** True once the tree can be used here: the Genome Tree research, and a T3 station. */
    static boolean open(@Nonnull LabStationPage page, @Nonnull GeneJournal journal) {
        return Research.hasFeature(journal, Research.GENOME_TREE) && page.station.tier() >= 3;
    }

    static void build(@Nonnull LabStationPage page, @Nonnull Ref<EntityStore> ref, @Nonnull Store<EntityStore> store,
                      @Nonnull UICommandBuilder c, @Nonnull UIEventBuilder e) {
        GeneJournal journal = Journal.of(ref, store);
        boolean open = open(page, journal);
        c.set("#TreeLocked.Visible", !open);
        c.set("#TreeOpen.Visible", open);
        page.treeRows.clear();
        page.treeLinks.clear();
        page.treeStates.clear();
        page.treeLinkStates.clear();
        if (!open) {
            locked(page, ref, store, c, journal);
            return;
        }
        TreeLayout layout = TreeLayout.get();
        GenomeTree.prune(layout, journal.getTree());
        int cell = layout.getCell();
        // The canvas is as big as the tree; the view around it scrolls when it is bigger than the panel.
        int columns = 0;
        int rows = 0;
        for (TreeNode node : layout.getNodes()) {
            columns = Math.max(columns, node.getX() + 1);
            rows = Math.max(rows, node.getY() + 1);
        }
        int canvasWidth = 2 * MARGIN + columns * cell;
        int canvasHeight = 2 * MARGIN + rows * cell;
        Anchor canvas = new Anchor();
        canvas.setWidth(Value.of(canvasWidth));
        canvas.setHeight(Value.of(canvasHeight));
        c.setObject("#TreeCanvas.Anchor", canvas);
        Anchor treeView = new Anchor();
        treeView.setWidth(Value.of(canvasWidth + SCROLLBAR));
        c.setObject("#TreeView.Anchor", treeView);
        c.clear("#TreeCanvas");
        int index = 0;
        // Links first, so the nodes are drawn over them.
        for (String[] link : layout.links()) {
            TreeNode a = layout.node(link[0]);
            TreeNode b = layout.node(link[1]);
            if (a == null || b == null) continue;
            String row = "#TreeCanvas[" + index + "]";
            c.append("#TreeCanvas", LINK);
            int ax = center(a.getX(), cell);
            int ay = center(a.getY(), cell);
            int bx = center(b.getX(), cell);
            int by = center(b.getY(), cell);
            if (ay == by) c.setObject(row + ".Anchor", anchor(Math.min(ax, bx), ay - LINK_WIDTH / 2, Math.abs(bx - ax), LINK_WIDTH));
            else c.setObject(row + ".Anchor", anchor(ax - LINK_WIDTH / 2, Math.min(ay, by), LINK_WIDTH, Math.abs(by - ay)));
            page.treeLinks.add(link);
            index++;
        }
        for (TreeNode node : layout.getNodes()) {
            String row = "#TreeCanvas[" + index + "]";
            c.append("#TreeCanvas", NODE);
            int w = width(node, cell);
            int h = height(node, cell);
            c.setObject(row + ".Anchor", anchor(center(node.getX(), cell) - w / 2, center(node.getY(), cell) - h / 2, w, h));
            String glyph = glyph(node);
            for (String state : STATES) c.set(row + " #T" + state + ".Text", glyph);
            String id = node.getId();
            e.addEventBinding(CustomUIEventBindingType.Activating, row + " #Button", new EventData().append("Action", "TreeTake").append("Node", id), false);
            e.addEventBinding(CustomUIEventBindingType.RightClicking, row + " #Button", new EventData().append("Action", "TreeRefund").append("Node", id), false);
            e.addEventBinding(CustomUIEventBindingType.MouseEntered, row + " #Button", new EventData().append("Action", "TreeHover").append("Node", id), false);
            page.treeRows.put(id, index);
            index++;
        }
        update(page, ref, store, c);
    }

    private static void locked(@Nonnull LabStationPage page, @Nonnull Ref<EntityStore> ref, @Nonnull Store<EntityStore> store,
                               @Nonnull UICommandBuilder c, @Nonnull GeneJournal journal) {
        boolean researched = Research.hasFeature(journal, Research.GENOME_TREE);
        ResearchNode node = Research.grantingNode(Research.GENOME_TREE);
        String name = node != null ? node.getName() : "Genome Tree";
        boolean needsT2 = page.station.tier() < 2;
        Balance.StationUpgrade upgrade = needsT2 ? null : Balance.get().stationUpgrade(3);
        List<MaterialQuantity> cost = upgrade != null ? upgrade.getMaterials() : List.of();
        String text = "The Genome Tree turns your Genome Capacity into a genome of your own design: stats along the paths, mutations and "
                + "permanent buffs at the end of side branches. It needs a Lab Station T3.\n\n";
        if (!researched) text += "First research " + name + " in the Research tab (" + (node != null ? node.getCost() : 0)
                + " Science, you have " + journal.getScience() + ").";
        else if (needsT2) text += "Upgrade this station to T2 first (in the Splice tab).";
        else text += "Upgrade this station to T3 with these materials (free in Creative):";
        c.set("#TreeLockedText.Text", text);
        Widgets.costs(c, "#TreeUpgradeCost", cost, ref, store);
        c.set("#TreeUpgradeButton.Visible", !needsT2);
        c.set("#TreeUpgradeButton.Disabled", !researched || upgrade == null || !Materials.canPay(ref, store, cost));
    }

    /** Node and link states, the hovered node's details and the sidebar. Only changed states are sent. */
    static void update(@Nonnull LabStationPage page, @Nonnull Ref<EntityStore> ref, @Nonnull Store<EntityStore> store, @Nonnull UICommandBuilder c) {
        if (!open(page, Journal.of(ref, store)) || page.treeRows.isEmpty()) return;
        TreeLayout layout = TreeLayout.get();
        GeneJournal journal = Journal.of(ref, store);
        TreeAllocation allocation = journal.getTree();
        Balance.GenomeTree balance = Balance.get().genomeTree();
        double capacity = GenomeCapacity.capacity(ref, store);
        ToIntFunction<TreeNode> unlocks = GenomeTree.unlocksFrom(journal, balance.isRequireDiscovery());

        for (TreeNode node : layout.getNodes()) {
            Integer index = page.treeRows.get(node.getId());
            if (index == null) continue;
            String row = "#TreeCanvas[" + index + "]";
            String state = shownState(layout, allocation, node, capacity, balance, unlocks);
            String previous = page.treeStates.put(node.getId(), state);
            if (!state.equals(previous)) for (String s : STATES) c.set(row + " #" + s + ".Visible", s.equals(state));
            if (node.kind() == TreeNode.Kind.SINK) c.set(row + " #TOnSink.Text", String.valueOf(allocation.rank(node.getId())));
        }
        TreeNode origin = layout.origin();
        for (int i = 0; i < page.treeLinks.size(); i++) {
            String[] link = page.treeLinks.get(i);
            boolean lit = owned(allocation, origin, link[0]) && owned(allocation, origin, link[1]);
            Boolean previous = page.treeLinkStates.put(i, lit);
            if (previous == null || previous != lit) {
                c.set("#TreeCanvas[" + i + "] #On.Visible", lit);
                c.set("#TreeCanvas[" + i + "] #Off.Visible", !lit);
            }
        }

        TreeNode hovered = layout.node(page.treeHover);
        String info = hovered != null ? describe(layout, allocation, hovered, capacity, balance, unlocks, page.player().getLanguage()) : HELP;
        if (page.treeMessage != null) info = page.treeMessage + "\n" + info;
        c.set("#TreeInfo.Text", info);
        sidebar(page, ref, store, c, layout, allocation, balance, capacity);
    }

    private static void sidebar(@Nonnull LabStationPage page, @Nonnull Ref<EntityStore> ref, @Nonnull Store<EntityStore> store, @Nonnull UICommandBuilder c,
                                @Nonnull TreeLayout layout, @Nonnull TreeAllocation allocation, @Nonnull Balance.GenomeTree balance, double capacity) {
        double total = GenomeTree.cost(layout, allocation, balance).total();
        capacityGauge(c, total, capacity);
        long notables = allocation.ids().stream().map(layout::node).filter(n -> n != null && n.kind() == TreeNode.Kind.NOTABLE).count();
        long cores = allocation.ids().stream().map(layout::node).filter(n -> n != null && n.kind() == TreeNode.Kind.CORE).count();
        c.set("#TreeNext.Text", "Mutations " + notables + " / " + balance.getMaxMutations() + "  ·  next "
                + GeneText.number(GenomeTree.nextDefault(layout, allocation, TreeNode.Kind.NOTABLE, balance))
                + "\nCores " + cores + "  ·  next " + GeneText.number(GenomeTree.nextDefault(layout, allocation, TreeNode.Kind.CORE, balance)));

        String language = page.player().getLanguage();
        List<Gene> genes = GenomeTree.genes(layout, allocation);
        c.clear("#TreeBuffs");
        for (int i = 0; i < genes.size(); i++) {
            Gene gene = genes.get(i);
            String row = "#TreeBuffs[" + i + "]";
            c.append("#TreeBuffs", Widgets.DETAIL_ROW);
            String key = Catalogue.key(gene);
            String type = Catalogue.type(key);
            Widgets.typeIcon(c, row, type);
            c.set(row + " #Name.Text", Catalogue.name(key, language));
            c.set(row + " #Value.Text", ProcessTab.value(gene));
            c.set(row + " #Value.Style.TextColor", Widgets.valueColor(type));
        }
        c.set("#TreeHeader.Text", genes.isEmpty() ? "Active nodes  ·  none" : "Active nodes  ·  " + allocation.ids().size() + " owned");

        ComplexityTier tier = genes.isEmpty() ? null : Tiers.forComplexity(total);
        List<MaterialQuantity> cost = tier != null ? Tiers.cost(tier, total) : List.of();
        costLines(c, cost, ref, store);
        c.set("#TreeCostLabel.Text", tier != null ? "Cost  ·  " + tier.getName() : "Cost");
        String reason = craftBlocked(page, ref, store, genes, tier, cost, total, capacity);
        c.set("#TreeReason.Text", reason != null ? reason : "");
        c.set("#TreeCraftButton.Disabled", reason != null);
        c.set("#TreeResetButton.Disabled", allocation.isEmpty());
    }

    @Nullable
    private static String craftBlocked(@Nonnull LabStationPage page, @Nonnull Ref<EntityStore> ref, @Nonnull Store<EntityStore> store,
                                       @Nonnull List<Gene> genes, @Nullable ComplexityTier tier, @Nonnull List<MaterialQuantity> cost,
                                       double total, double capacity) {
        if (genes.isEmpty()) return "Take nodes to design a genome.";
        if (total > capacity + 1e-9) return "Over your Genome Capacity.";
        if (tier != null && !Tiers.allows(page.station.tier(), tier)) return tier.getName() + " Complexity needs a Lab Station T" + tier.getStationTier() + ".";
        if (!emptySyringeInSlot(page)) return "Put one empty syringe in the slot.";
        if (!Materials.canPay(ref, store, cost)) return "Missing materials.";
        return null;
    }

    private static void costLines(@Nonnull UICommandBuilder c, @Nonnull List<MaterialQuantity> cost, @Nonnull Ref<EntityStore> ref, @Nonnull Store<EntityStore> store) {
        c.clear("#TreeCost");
        boolean free = Materials.isFree(ref, store);
        for (int i = 0; i < cost.size(); i++) {
            MaterialQuantity material = cost.get(i);
            String row = "#TreeCost[" + i + "] ";
            c.append("#TreeCost", COST_LINE);
            c.set(row + "#Icon.Visible", material.getItemId() != null);
            if (material.getItemId() != null) c.set(row + "#Icon.ItemId", material.getItemId());
            c.set(row + "#Item.TextSpans", Materials.name(material));
            int have = Materials.carried(ref, store, material);
            c.set(row + "#Amount.Text", have + " / " + material.getQuantity());
            c.set(row + "#Amount.Style.TextColor", free || have >= material.getQuantity() ? Widgets.GOOD : Widgets.BAD);
        }
    }

    private static void capacityGauge(@Nonnull UICommandBuilder c, double complexity, double capacity) {
        boolean over = complexity > capacity;
        c.set("#TCapNumbers.Text", GeneText.number(Widgets.round(complexity)) + " / " + GeneText.number(Widgets.round(capacity)));
        c.set("#TCapNumbers.Style.TextColor", over ? Widgets.BAD : Widgets.STRONG);
        int fill;
        int extra = 0;
        if (capacity <= 0) {
            fill = 0;
        } else if (!over) {
            fill = (int) Math.round(TRACK * Math.max(0, complexity) / capacity);
        } else {
            fill = (int) Math.round(TRACK * capacity / complexity);
            extra = TRACK - fill;
        }
        c.setObject("#TCapFill.Anchor", Widgets.width(fill));
        c.setObject("#TCapOver.Anchor", Widgets.width(extra));
        c.set("#TCapLegend.Text", over ? "Over by " + GeneText.number(Widgets.round(complexity - capacity))
                : "Free " + GeneText.number(Widgets.round(capacity - complexity)));
    }

    // ---- Nodes

    /** The state group to show; Notables use the diamond versions. */
    @Nonnull
    private static String shownState(@Nonnull TreeLayout layout, @Nonnull TreeAllocation allocation, @Nonnull TreeNode node, double capacity,
                                     @Nonnull Balance.GenomeTree balance, @Nonnull ToIntFunction<TreeNode> unlocks) {
        String state = state(layout, allocation, node, capacity, balance, unlocks);
        if (node.kind() != TreeNode.Kind.NOTABLE) return state;
        return switch (state) {
            case "OnNotable" -> "DOn";
            case "Avail" -> "DAvail";
            case "Locked" -> "DLocked";
            default -> "DOff";
        };
    }

    @Nonnull
    private static String state(@Nonnull TreeLayout layout, @Nonnull TreeAllocation allocation, @Nonnull TreeNode node, double capacity,
                                @Nonnull Balance.GenomeTree balance, @Nonnull ToIntFunction<TreeNode> unlocks) {
        TreeNode.Kind kind = node.kind();
        if (kind == null) return "Off";
        if (kind == TreeNode.Kind.ORIGIN) return "OnOrigin";
        if (allocation.owns(node.getId())) {
            return switch (kind) {
                case BIG -> "OnBig";
                case NOTABLE -> "OnNotable";
                case CORE -> "OnCore";
                case SINK -> "OnSink";
                default -> "OnStat";
            };
        }
        if (GenomeTree.lockReason(layout, allocation, node, unlocks) != null) return "Locked";
        return GenomeTree.whyNot(layout, allocation, node, capacity, balance, unlocks) == null ? "Avail" : "Off";
    }

    private static boolean owned(@Nonnull TreeAllocation allocation, @Nullable TreeNode origin, @Nonnull String id) {
        return allocation.owns(id) || (origin != null && origin.getId().equals(id));
    }

    /** The hover line: what the node gives, its cost, and why it can't be taken. */
    @Nonnull
    private static String describe(@Nonnull TreeLayout layout, @Nonnull TreeAllocation allocation, @Nonnull TreeNode node, double capacity,
                                   @Nonnull Balance.GenomeTree balance, @Nonnull ToIntFunction<TreeNode> unlocks, @Nullable String language) {
        TreeNode.Kind kind = node.kind();
        if (kind == null) return node.getId();
        String what = switch (kind) {
            case ORIGIN -> "Origin: every path starts here.";
            case SMALL -> GeneText.statName(stat(node)) + " +" + GeneText.number(node.getValue()) + "%  ·  Small node";
            case BIG -> GeneText.statName(stat(node)) + " +" + GeneText.number(node.getValue()) + "%  ·  Big node";
            case SINK -> GeneText.statName(stat(node)) + " +" + GeneText.number(node.getValue()) + "% per rank  ·  Sink, rank "
                    + allocation.rank(node.getId());
            case NOTABLE -> {
                MutationEntry entry = node.getMutation() != null ? com.genetics.codex.Codex.mutation(node.getMutation()) : null;
                String description = entry != null ? Translations.resolve(language, entry.getDescription(), "") : "";
                yield GenomeTree.nodeName(node) + " (mutation)" + (description.isEmpty() ? "" : ": " + description);
            }
            case CORE -> {
                int owned = GenomeTree.buffCount(layout, allocation, node.getBuff());
                int tiers = node.getTiers().size();
                String now = owned > 0 ? GeneText.effectName(node.getTiers().get(Math.min(owned, tiers) - 1), language) : "none";
                yield GenomeTree.nodeName(node) + " Core  ·  " + owned + " / " + tiers + " owned (now " + now + ")"
                        + (allocation.owns(node.getId()) || owned >= tiers ? "" : ", this one gives "
                        + GeneText.effectName(node.getTiers().get(owned), language) + " permanently");
            }
        };
        if (kind == TreeNode.Kind.ORIGIN) return what;
        if (allocation.owns(node.getId()) && kind != TreeNode.Kind.SINK) return what + "  ·  owned (right click to give back)";
        String why = GenomeTree.whyNot(layout, allocation, node, capacity, balance, unlocks);
        String cost = "  ·  costs " + GeneText.number(GenomeTree.nextCost(layout, allocation, node, balance)) + " Complexity";
        return what + cost + (why != null ? "  ·  " + why : "");
    }

    @Nonnull
    private static String stat(@Nonnull TreeNode node) {
        return node.getStat() != null ? node.getStat() : "?";
    }

    /** The short text on a node: the buff's or mutation's initials, the Sink's rank. */
    @Nonnull
    private static String glyph(@Nonnull TreeNode node) {
        TreeNode.Kind kind = node.kind();
        if (kind == null) return "";
        return switch (kind) {
            case NOTABLE, CORE -> initials(GenomeTree.nodeName(node));
            case SINK -> "+";
            default -> "";
        };
    }

    @Nonnull
    static String initials(@Nonnull String name) {
        String[] words = name.trim().split("\\s+");
        if (words.length >= 2) return ("" + words[0].charAt(0) + words[1].charAt(0)).toUpperCase(java.util.Locale.ROOT);
        String word = words[0];
        return word.length() >= 2 ? word.substring(0, 1).toUpperCase(java.util.Locale.ROOT) + word.charAt(1) : word.toUpperCase(java.util.Locale.ROOT);
    }

    private static int center(int gridPosition, int cell) {
        return MARGIN + gridPosition * cell + cell / 2;
    }

    /** A node's size on screen, for a 28 px cell, scaled to the tree's cell size. */
    private static int width(@Nonnull TreeNode node, int cell) {
        TreeNode.Kind kind = node.kind();
        int size = kind == null ? 12 : switch (kind) {
            case ORIGIN -> 22;
            case NOTABLE -> 27;
            case CORE -> 24;
            case SINK -> 26;
            case BIG -> 18;
            case SMALL -> 12;
        };
        return Math.round(size * cell / 28f);
    }

    private static int height(@Nonnull TreeNode node, int cell) {
        return node.kind() == TreeNode.Kind.SINK ? Math.round(18 * cell / 28f) : width(node, cell);
    }

    @Nonnull
    private static Anchor anchor(int left, int top, int width, int height) {
        Anchor anchor = new Anchor();
        anchor.setLeft(Value.of(left));
        anchor.setTop(Value.of(top));
        anchor.setWidth(Value.of(Math.max(1, width)));
        anchor.setHeight(Value.of(Math.max(1, height)));
        return anchor;
    }

    private static boolean emptySyringeInSlot(@Nonnull LabStationPage page) {
        ItemStack stack = page.sampleStack();
        return stack != null && Syringes.isSyringe(stack) && !Genome.isPresentOn(stack) && stack.getQuantity() == 1;
    }

    // ---- Actions

    /** Takes a node (or a Sink's next rank). Returns why it couldn't, or null. */
    @Nullable
    static String take(@Nonnull LabStationPage page, @Nonnull Ref<EntityStore> ref, @Nonnull Store<EntityStore> store, @Nonnull String id) {
        if (!open(page, Journal.of(ref, store))) return null;
        TreeLayout layout = TreeLayout.get();
        TreeNode node = layout.node(id);
        if (node == null) return null;
        GeneJournal journal = Journal.of(ref, store);
        Balance.GenomeTree balance = Balance.get().genomeTree();
        return GenomeTree.take(layout, journal.getTree(), node, GenomeCapacity.capacity(ref, store), balance,
                GenomeTree.unlocksFrom(journal, balance.isRequireDiscovery()));
    }

    /** Gives back a node (or a Sink's top rank). Returns why it couldn't, or null. */
    @Nullable
    static String refund(@Nonnull LabStationPage page, @Nonnull Ref<EntityStore> ref, @Nonnull Store<EntityStore> store, @Nonnull String id) {
        if (!open(page, Journal.of(ref, store))) return null;
        TreeLayout layout = TreeLayout.get();
        TreeNode node = layout.node(id);
        return node != null ? GenomeTree.refund(layout, Journal.of(ref, store).getTree(), node) : null;
    }

    static void reset(@Nonnull Ref<EntityStore> ref, @Nonnull Store<EntityStore> store) {
        Journal.of(ref, store).getTree().clear();
    }

    /** Crafts the owned nodes into the empty syringe in the slot. Returns a message for the player, or null. */
    @Nullable
    static String craft(@Nonnull LabStationPage page, @Nonnull Ref<EntityStore> ref, @Nonnull Store<EntityStore> store) {
        if (!open(page, Journal.of(ref, store))) return null;
        TreeLayout layout = TreeLayout.get();
        TreeAllocation allocation = Journal.of(ref, store).getTree();
        Balance.GenomeTree balance = Balance.get().genomeTree();
        Genome genome = GenomeTree.genome(layout, allocation, balance);
        if (genome == null) return "Take nodes on the tree first.";
        double total = genome.complexity();
        ComplexityTier tier = Tiers.forComplexity(total);
        List<MaterialQuantity> cost = tier != null ? Tiers.cost(tier, total) : List.of();
        String blocked = craftBlocked(page, ref, store, genome.genes(), tier, cost, total, GenomeCapacity.capacity(ref, store));
        if (blocked != null) return blocked;
        if (!Materials.pay(ref, store, cost)) return "You don't carry enough materials to craft this genome.";
        page.container.setItemStackForSlot(LabStationPage.SAMPLE_SLOT, Syringes.fill(page.sampleStack(), genome));
        return "Crafted " + genome.title() + ": " + genome.genes().size() + " genes, Complexity " + GeneText.number(total) + ".";
    }
}
