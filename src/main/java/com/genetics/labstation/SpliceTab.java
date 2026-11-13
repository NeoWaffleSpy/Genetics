package com.genetics.labstation;

import com.genetics.capacity.GenomeCapacity;
import com.genetics.codex.Balance;
import com.genetics.genome.Gene;
import com.genetics.genome.GeneText;
import com.genetics.genome.Genome;
import com.genetics.interaction.Injection;
import com.genetics.interaction.Syringes;
import com.genetics.journal.Catalogue;
import com.genetics.journal.GeneJournal;
import com.genetics.journal.Journal;
import com.genetics.process.Splicer;
import com.genetics.research.Research;
import com.genetics.research.ResearchNode;
import com.genetics.tier.Materials;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.protocol.packets.interface_.CustomUIEventBindingType;
import com.hypixel.hytale.server.core.inventory.ItemStack;
import com.hypixel.hytale.server.core.inventory.MaterialQuantity;
import com.hypixel.hytale.server.core.ui.builder.EventData;
import com.hypixel.hytale.server.core.ui.builder.UICommandBuilder;
import com.hypixel.hytale.server.core.ui.builder.UIEventBuilder;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

/**
 * The Splice tab (design doc §7): genetic material A (the station's slot) and B (dragged from the inventory) added
 * together. Needs the Splicing research and a Lab Station T2 (the upgrade is offered here once researched).
 */
final class SpliceTab {
    private static final String ROW = "Genetics/SpliceRow.ui";

    private SpliceTab() {
    }

    private enum Mode { LOCKED, UPGRADE, NO_A, NO_B, READY }

    @Nonnull
    private static Mode mode(@Nonnull LabStationPage page, @Nonnull GeneJournal journal) {
        if (page.station.tier() < 2) return Mode.UPGRADE;
        if (!Research.hasFeature(journal, Research.SPLICING)) return Mode.LOCKED;
        Genome a = page.sample();
        if (a == null || !a.processed()) return Mode.NO_A;
        if (page.spliceB() == null) return Mode.NO_B;
        return Mode.READY;
    }

    static void build(@Nonnull LabStationPage page, @Nonnull Ref<EntityStore> ref, @Nonnull Store<EntityStore> store,
                      @Nonnull UICommandBuilder c, @Nonnull UIEventBuilder e) {
        c.clear("#SpliceList");
        GeneJournal journal = Journal.of(ref, store);
        if (mode(page, journal) == Mode.READY) {
            Genome a = page.sample();
            Genome b = page.spliceB();
            Splicer.Plan plan = Splicer.plan(a, b, page.station.tier());
            if (page.spliceKeep.length != plan.lines().size()) {
                page.spliceKeep = new boolean[plan.lines().size()];
                Arrays.fill(page.spliceKeep, true);
            }
            for (int i = 0; i < plan.lines().size(); i++) {
                String row = "#SpliceList[" + i + "]";
                c.append("#SpliceList", ROW);
                c.set(row + " #Toggle.Value", page.spliceKeep[i]);
                e.addEventBinding(CustomUIEventBindingType.ValueChanged, row + " #Toggle",
                        new EventData().append("Action", "SpliceKeep").append("Index", String.valueOf(i)).append("@Checked", row + " #Toggle.Value"), false);
            }
        }
        update(page, ref, store, c);
    }

    static void update(@Nonnull LabStationPage page, @Nonnull Ref<EntityStore> ref, @Nonnull Store<EntityStore> store, @Nonnull UICommandBuilder c) {
        GeneJournal journal = Journal.of(ref, store);
        Mode mode = mode(page, journal);
        c.set("#SpliceEmpty.Visible", mode != Mode.READY);
        c.set("#SpliceUpgrade.Visible", mode == Mode.UPGRADE);
        c.set("#SpliceDetails.Visible", mode == Mode.READY);
        c.set("#SpliceBar.Visible", mode == Mode.READY);
        c.set("#SpliceReasonRow.Visible", mode == Mode.READY);
        switch (mode) {
            case LOCKED -> {
                ResearchNode node = Research.grantingNode(Research.SPLICING);
                c.set("#SpliceEmpty.Text", "Splicing isn't researched yet. Research " + (node != null ? node.getName() : "Splicing")
                        + " in the Research tab to add two genetic materials together.");
            }
            case UPGRADE -> {
                boolean researched = Research.hasFeature(journal, Research.SPLICING);
                ResearchNode node = Research.grantingNode(Research.SPLICING);
                String name = node != null ? node.getName() : "Splicing";
                c.set("#SpliceEmpty.Text", "Lab Station T2: splicing, and processing Moderate samples.");
                Balance.StationUpgrade upgrade = Balance.get().stationUpgrade(2);
                List<MaterialQuantity> cost = upgrade != null ? upgrade.getMaterials() : List.of();
                c.set("#SpliceUpgradeText.Text", researched
                        ? "Upgrade this station with these materials (free in Creative):"
                        : "First research " + name + " in the Research tab (" + (node != null ? node.getCost() : 0)
                        + " Science, you have " + journal.getScience() + "). Then upgrade the station with these materials:");
                Widgets.costs(c, "#UpgradeCost", cost, ref, store);
                c.set("#UpgradeButton.Disabled", !researched || upgrade == null || !Materials.canPay(ref, store, cost));
            }
            case NO_A -> c.set("#SpliceEmpty.Text", "Put genetic material (a processed sample) in slot A, then drag another one from your inventory into slot B.");
            case NO_B -> c.set("#SpliceEmpty.Text", "Drag another genetic material from your inventory into slot B. The two are added together: shared stats and timers add up, and genes only one side has carry over.");
            case READY -> ready(page, ref, store, c);
        }
    }

    private static void ready(@Nonnull LabStationPage page, @Nonnull Ref<EntityStore> ref, @Nonnull Store<EntityStore> store, @Nonnull UICommandBuilder c) {
        Genome a = page.sample();
        Genome b = page.spliceB();
        String language = page.player().getLanguage();
        Splicer.Plan plan = Splicer.plan(a, b, page.station.tier(), page.spliceKeep);
        for (int i = 0; i < plan.lines().size(); i++) {
            Splicer.Line line = plan.lines().get(i);
            String row = "#SpliceList[" + i + "]";
            Gene any = line.fromA() != null ? line.fromA() : line.fromB();
            String key = Catalogue.key(any);
            boolean conflict = line.kept() && plan.conflict() != null && line.result() instanceof Gene.Effect effect
                    && conflictsWith(plan, effect);
            Widgets.typeIcon(c, row, Catalogue.type(key));
            c.set(row + " #Name.Text", Catalogue.name(key, language));
            c.set(row + " #Name.Style.TextColor", !line.kept() ? Widgets.FAINT : conflict ? Widgets.BAD : Widgets.STRONG);
            c.set(row + " #A.Text", line.fromA() != null ? ProcessTab.value(line.fromA()) : "-");
            c.set(row + " #B.Text", line.fromB() != null ? ProcessTab.value(line.fromB()) : "-");
            String result = !line.kept() ? "dropped" : conflict ? "conflict" : line.result() != null ? ProcessTab.value(line.result()) : "-";
            c.set(row + " #Result.Text", result);
            String color = !line.kept() ? Widgets.FAINT : conflict ? Widgets.BAD
                    : line.result() instanceof Gene.Stat stat && stat.isDrawback() ? Widgets.BAD
                    : line.result() instanceof Gene.Mutation ? Widgets.VIOLET
                    : line.result() instanceof Gene.Effect ? Widgets.GLOW : Widgets.GOOD;
            c.set(row + " #Result.Style.TextColor", color);
        }
        Genome result = plan.result();
        Widgets.capacity(c, "S", Injection.lastingComplexity(result), GenomeCapacity.capacity(ref, store), "Result");
        c.set("#Mutation.Text", plan.mutationChance() > 0 ? GeneText.number(Math.round(plan.mutationChance() * 1000) / 10.0) + "% chance of a new mutation"
                : "Mutation inherited");
        Widgets.costs(c, "#SpliceCost", plan.cost(), ref, store);
        c.set("#SpliceCostLabel.Text", plan.tier() != null ? "Cost  ·  " + plan.tier().getName() : "Cost");
        String reason = plan.blocked() != null ? plan.blocked()
                : plan.conflict() != null ? "Untick one of the conflicting buffs"
                : result.genes().isEmpty() ? "Keep at least one gene"
                : !Materials.canPay(ref, store, plan.cost()) ? "Missing materials" : null;
        c.set("#SpliceReason.Text", reason != null ? reason : "");
        c.set("#SpliceButton.Disabled", reason != null);
    }

    /** True if this kept effect is one of the same-buff-at-two-tiers pair. */
    private static boolean conflictsWith(@Nonnull Splicer.Plan plan, @Nonnull Gene.Effect effect) {
        String family = Splicer.family(effect.effect());
        int count = 0;
        for (Gene gene : plan.result().genes()) {
            if (gene instanceof Gene.Effect other && Splicer.family(other.effect()).equals(family)) count++;
        }
        return count > 1;
    }

    /** Splices A and B with the current choices. Returns a message for the player, or null. */
    @Nullable
    static String splice(@Nonnull LabStationPage page, @Nonnull Ref<EntityStore> ref, @Nonnull Store<EntityStore> store) {
        GeneJournal journal = Journal.of(ref, store);
        if (mode(page, journal) != Mode.READY) return null;
        ItemStack stackA = page.sampleStack();
        ItemStack stackB = page.bStack();
        Genome a = page.sample();
        Genome b = page.spliceB();
        if (stackA == null || stackB == null || a == null || b == null) return null;
        Splicer.Plan plan = Splicer.plan(a, b, page.station.tier(), page.spliceKeep);
        if (plan.blocked() != null) return plan.blocked();
        if (plan.conflict() != null) return plan.conflict();
        if (plan.result().genes().isEmpty()) return "Keep at least one gene.";
        if (!Materials.pay(ref, store, plan.cost())) return "You don't carry enough materials to splice these samples.";
        Splicer.Result result = Splicer.splice(a, b, page.spliceKeep, ThreadLocalRandom.current());
        page.bContainer.setItemStackForSlot(page.bSlot, Syringes.empty(stackB));
        page.container.setItemStackForSlot(LabStationPage.SAMPLE_SLOT, Syringes.fill(stackA, result.genome()));
        page.bContainer = null;
        page.bSlot = -1;
        page.tab = "Process";
        return result.newMutation() != null
                ? "Spliced into an unidentified sample. The splice reacted strangely... process it to find out."
                : "Spliced into an unidentified sample. Process it to reveal the result.";
    }
}
