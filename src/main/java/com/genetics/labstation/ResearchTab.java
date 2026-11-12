package com.genetics.labstation;

import com.genetics.journal.GeneJournal;
import com.genetics.journal.Journal;
import com.genetics.research.Research;
import com.genetics.research.ResearchNode;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.protocol.packets.interface_.CustomUIEventBindingType;
import com.hypixel.hytale.server.core.ui.builder.EventData;
import com.hypixel.hytale.server.core.ui.builder.UICommandBuilder;
import com.hypixel.hytale.server.core.ui.builder.UIEventBuilder;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.List;

/** The Research tab (design doc §9.5): the tech tree by lane, and the selected node with its Research button. */
final class ResearchTab {
    private static final String LANE = "Genetics/ResearchLane.ui";
    private static final String NODE = "Genetics/ResearchNodeEntry.ui";
    private static final List<String> ICONS = List.of("Helix", "Stat", "Effect", "Mutation", "Syringe");

    private ResearchTab() {
    }

    static void build(@Nonnull LabStationPage page, @Nonnull Ref<EntityStore> ref, @Nonnull Store<EntityStore> store,
                      @Nonnull UICommandBuilder c, @Nonnull UIEventBuilder e) {
        GeneJournal journal = Journal.of(ref, store);
        c.clear("#ResearchTree");
        List<String> branches = Research.branches();
        for (int l = 0; l < branches.size(); l++) {
            String lane = "#ResearchTree[" + l + "]";
            c.append("#ResearchTree", LANE);
            c.set(lane + " #Lane.Text", branches.get(l));
            List<ResearchNode> nodes = Research.branch(branches.get(l));
            for (int j = 0; j < nodes.size(); j++) {
                ResearchNode node = nodes.get(j);
                String entry = lane + " #Nodes[" + j + "]";
                c.append(lane + " #Nodes", NODE);
                writeNode(c, entry, journal, node, j > 0 ? nodes.get(j - 1) : null);
                e.addEventBinding(CustomUIEventBindingType.Activating, entry + " #Button", new EventData().append("Action", "Node").append("Node", node.getId()), false);
            }
        }
        if (page.researchSelected == null || ResearchNode.getAssetMap().getAsset(page.researchSelected) == null) {
            page.researchSelected = Research.all().stream().filter(n -> Research.state(journal, n) == Research.State.AVAILABLE)
                    .map(ResearchNode::getId).findFirst().orElse(Research.all().isEmpty() ? null : Research.all().getFirst().getId());
        }
        detail(page, ref, store, c);
    }

    private static void writeNode(@Nonnull UICommandBuilder c, @Nonnull String entry, @Nonnull GeneJournal journal, @Nonnull ResearchNode node,
                                  @Nullable ResearchNode previous) {
        Research.State state = Research.state(journal, node);
        boolean linked = previous != null;
        boolean open = linked && Research.isResearched(journal, previous) && state != Research.State.LOCKED;
        c.set(entry + " #LinkOn.Visible", linked && open);
        c.set(entry + " #LinkOff.Visible", linked && !open);
        c.set(entry + " #AccentResearched.Visible", state == Research.State.RESEARCHED);
        c.set(entry + " #AccentAvailable.Visible", state == Research.State.AVAILABLE);
        c.set(entry + " #AccentLocked.Visible", state == Research.State.LOCKED);
        for (String icon : ICONS) c.set(entry + " #Icon" + icon + ".Visible", state != Research.State.LOCKED && icon.equals(node.getIcon()));
        c.set(entry + " #IconLock.Visible", state == Research.State.LOCKED);
        c.set(entry + " #Name.Text", node.getName());
        c.set(entry + " #Name.Style.TextColor", state == Research.State.LOCKED ? Widgets.TEXT : Widgets.STRONG);
        c.set(entry + " #Status.Text", status(journal, node, state));
        c.set(entry + " #Status.Style.TextColor", state == Research.State.RESEARCHED ? Widgets.GLOW
                : state == Research.State.AVAILABLE ? Widgets.AMBER : Widgets.FAINT);
    }

    @Nonnull
    private static String status(@Nonnull GeneJournal journal, @Nonnull ResearchNode node, @Nonnull Research.State state) {
        return switch (state) {
            case RESEARCHED -> node.isStarter() ? "Starter" : "Researched";
            case AVAILABLE -> node.getCost() + " Science";
            case LOCKED -> {
                ResearchNode missing = Research.missingRequirement(journal, node);
                yield "Needs " + (missing != null ? missing.getName() : "?");
            }
        };
    }

    /** The bottom bar for the selected node. */
    static void detail(@Nonnull LabStationPage page, @Nonnull Ref<EntityStore> ref, @Nonnull Store<EntityStore> store, @Nonnull UICommandBuilder c) {
        GeneJournal journal = Journal.of(ref, store);
        ResearchNode node = page.researchSelected != null ? ResearchNode.getAssetMap().getAsset(page.researchSelected) : null;
        c.set("#ResearchDetail.Visible", node != null);
        if (node == null) return;
        Research.State state = Research.state(journal, node);
        for (String icon : ICONS) c.set("#RDIcon" + icon + ".Visible", icon.equals(node.getIcon()));
        c.set("#RDName.Text", node.getName());
        c.set("#RDDescription.Text", node.getDescription());
        c.set("#RDStatus.Text", switch (state) {
            case RESEARCHED -> "Researched";
            case AVAILABLE -> "Available";
            case LOCKED -> status(journal, node, state);
        });
        c.set("#RDStatus.Style.TextColor", state == Research.State.RESEARCHED ? Widgets.GLOW : state == Research.State.AVAILABLE ? Widgets.AMBER : Widgets.FAINT);
        c.set("#RDCost.Text", state == Research.State.RESEARCHED ? "Done" : node.getCost() + " Science  ·  you have " + journal.getScience());
        c.set("#ResearchButton.Text", state == Research.State.RESEARCHED ? "Researched" : "Research");
        c.set("#ResearchButton.Disabled", state != Research.State.AVAILABLE || journal.getScience() < node.getCost());
    }

    /** Researches the selected node. Returns a message for the player, or null. */
    @Nullable
    static String research(@Nonnull LabStationPage page, @Nonnull Ref<EntityStore> ref, @Nonnull Store<EntityStore> store) {
        ResearchNode node = page.researchSelected != null ? ResearchNode.getAssetMap().getAsset(page.researchSelected) : null;
        if (node == null) return null;
        String failure = Research.research(ref, store, page.player(), node);
        return failure != null ? failure : "Researched " + node.getName() + ".";
    }
}
