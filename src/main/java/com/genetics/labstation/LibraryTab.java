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
import com.genetics.library.GeneLibrary;
import com.genetics.library.Library;
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
import java.util.ArrayList;
import java.util.List;

/**
 * The Library tab (design doc §8.1): the player's extracted genes, ticked into a combination previewed on the right,
 * combined into new genetic material in the empty syringe in the station's slot. Needs the Genetic Library research
 * and a Lab Station T3 (the upgrade is offered here once researched).
 */
final class LibraryTab {
    private static final String ROW = "Genetics/LibraryRow.ui";
    /** Inner width of the preview's capacity track: 300 panel - 2 x 12 padding - 2 outline. */
    private static final int TRACK = 274;

    private LibraryTab() {
    }

    /** True once the Library can be used here: researched, and a T3 station. */
    static boolean open(@Nonnull LabStationPage page, @Nonnull GeneJournal journal) {
        return Research.hasFeature(journal, Research.GENETIC_LIBRARY) && page.station.tier() >= 3;
    }

    static void build(@Nonnull LabStationPage page, @Nonnull Ref<EntityStore> ref, @Nonnull Store<EntityStore> store,
                      @Nonnull UICommandBuilder c, @Nonnull UIEventBuilder e) {
        GeneJournal journal = Journal.of(ref, store);
        boolean open = open(page, journal);
        c.set("#LibraryLocked.Visible", !open);
        c.set("#LibraryOpen.Visible", open);
        if (!open) {
            locked(page, ref, store, c, journal);
            return;
        }
        GeneLibrary library = Library.of(ref, store);
        List<GeneLibrary.Entry> entries = library.getEntries();
        page.libraryUse.removeIf(id -> library.get(id) == null);
        boolean refining = Research.hasFeature(journal, Research.REFINING);
        String language = page.player().getLanguage();
        c.clear("#LibraryList");
        for (int i = 0; i < entries.size(); i++) {
            GeneLibrary.Entry entry = entries.get(i);
            Gene gene = Library.effective(entry);
            String row = "#LibraryList[" + i + "]";
            c.append("#LibraryList", ROW);
            String key = Catalogue.key(gene);
            String type = Catalogue.type(key);
            Widgets.typeIcon(c, row, type);
            c.set(row + " #Name.Text", Catalogue.name(key, language));
            int cost = Library.refineCost(entry);
            String level = entry.getLevel() > 0 ? "  ·  refined +" + entry.getLevel() : "";
            String next = refining && Library.canRefine(entry) ? "  ·  next: " + cost + " " + refineItemName() : "";
            c.set(row + " #Meta.Text", "From " + entry.getSource() + level + next);
            c.set(row + " #Value.Text", ProcessTab.value(gene));
            c.set(row + " #Value.Style.TextColor", Widgets.valueColor(type));
            c.set(row + " #Use.Value", page.libraryUse.contains(entry.getId()));
            c.set(row + " #Refine.Visible", refining && Library.canRefine(entry));
            e.addEventBinding(CustomUIEventBindingType.ValueChanged, row + " #Use",
                    new EventData().append("Action", "LibraryUse").append("Node", entry.getId()).append("@Checked", row + " #Use.Value"), false);
            e.addEventBinding(CustomUIEventBindingType.Activating, row + " #Refine", new EventData().append("Action", "Refine").append("Node", entry.getId()), false);
            e.addEventBinding(CustomUIEventBindingType.Activating, row + " #Discard", new EventData().append("Action", "Discard").append("Node", entry.getId()), false);
        }
        c.set("#LibraryEmpty.Visible", entries.isEmpty());
        c.set("#LibraryCount.Text", entries.size() + (entries.size() == 1 ? " gene" : " genes"));
        update(page, ref, store, c);
    }

    private static void locked(@Nonnull LabStationPage page, @Nonnull Ref<EntityStore> ref, @Nonnull Store<EntityStore> store,
                               @Nonnull UICommandBuilder c, @Nonnull GeneJournal journal) {
        boolean researched = Research.hasFeature(journal, Research.GENETIC_LIBRARY);
        ResearchNode node = Research.grantingNode(Research.GENETIC_LIBRARY);
        String name = node != null ? node.getName() : "Genetic Library";
        boolean needsT2 = page.station.tier() < 2;
        Balance.StationUpgrade upgrade = needsT2 ? null : Balance.get().stationUpgrade(3);
        List<MaterialQuantity> cost = upgrade != null ? upgrade.getMaterials() : List.of();
        String text = "The Genetic Library extracts single genes from genetic material and combines them at will: stack the same stat "
                + "(each extra copy adds less), mix several mutations, and refine genes past their creature's range. It needs a Lab Station T3.\n\n";
        if (!researched) text += "First research " + name + " in the Research tab (" + (node != null ? node.getCost() : 0)
                + " Science, you have " + journal.getScience() + ").";
        else if (needsT2) text += "Upgrade this station to T2 first (in the Splice tab).";
        else text += "Upgrade this station to T3 with these materials (free in Creative):";
        c.set("#LibraryLockedText.Text", text);
        Widgets.costs(c, "#LibUpgradeCost", cost, ref, store);
        c.set("#LibUpgradeButton.Visible", !needsT2);
        c.set("#LibUpgradeButton.Disabled", !researched || upgrade == null || !Materials.canPay(ref, store, cost));
    }

    /** The combination preview, cost and button for the ticked entries. */
    static void update(@Nonnull LabStationPage page, @Nonnull Ref<EntityStore> ref, @Nonnull Store<EntityStore> store, @Nonnull UICommandBuilder c) {
        GeneJournal journal = Journal.of(ref, store);
        if (!open(page, journal)) return;
        Library.Plan plan = Library.plan(chosen(page, ref, store), page.station.tier());
        String language = page.player().getLanguage();
        Genome result = plan.result();
        double capacity = GenomeCapacity.capacity(ref, store);
        c.set("#DesignName.Text", result != null ? result.title() : "New genome");
        double lasting = result != null ? Injection.lastingComplexity(result) : 0;
        capacity(c, lasting, capacity);
        c.set("#DesignMutations.Text", plan.mutations() > 1
                ? plan.mutations() + " mutations  ·  cost x" + GeneText.number(plan.multiplier())
                : plan.mutations() == 1 ? "1 mutation" : "");
        c.set("#DesignHeader.Text", "Result  ·  " + plan.genes().size() + (plan.genes().size() == 1 ? " gene" : " genes")
                + (result != null ? "  ·  Complexity " + GeneText.number(result.complexity()) : ""));
        c.clear("#DesignList");
        for (int i = 0; i < plan.genes().size(); i++) {
            Gene gene = plan.genes().get(i);
            String row = "#DesignList[" + i + "]";
            c.append("#DesignList", Widgets.DETAIL_ROW);
            String key = Catalogue.key(gene);
            String type = Catalogue.type(key);
            Widgets.typeIcon(c, row, type);
            String fused = gene instanceof Gene.Stat stat && stat.isFused() ? "  (fused)" : "";
            c.set(row + " #Name.Text", Catalogue.name(key, language) + fused);
            c.set(row + " #Value.Text", ProcessTab.value(gene));
            c.set(row + " #Value.Style.TextColor", Widgets.valueColor(type));
        }
        Widgets.costs(c, "#LibraryCost", plan.cost(), ref, store);
        c.set("#LibraryCostLabel.Text", plan.tier() != null ? "Cost  ·  " + plan.tier().getName() : "Cost");
        String reason = plan.blocked();
        if (reason == null && !emptySyringeInSlot(page)) reason = "Put an empty syringe in the slot to hold the result";
        if (reason == null && !Materials.canPay(ref, store, plan.cost())) reason = "Missing materials";
        c.set("#LibraryReason.Text", reason != null ? reason : "");
        c.set("#CombineButton.Disabled", reason != null);
    }

    /** A capacity gauge for the narrow preview panel. */
    private static void capacity(@Nonnull UICommandBuilder c, double complexity, double capacity) {
        boolean over = complexity > capacity;
        c.set("#LCapNumbers.Text", GeneText.number(Widgets.round(complexity)) + " / " + GeneText.number(Widgets.round(capacity)));
        c.set("#LCapNumbers.Style.TextColor", over ? Widgets.BAD : Widgets.STRONG);
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
        c.setObject("#LCapFill.Anchor", Widgets.width(fill));
        c.setObject("#LCapOver.Anchor", Widgets.width(extra));
        c.set("#LCapLegend.Text", over ? "Over capacity by " + GeneText.number(Widgets.round(complexity - capacity)) + ": it would degrade when injected"
                : "Lasting genes " + GeneText.number(Widgets.round(complexity)) + "  ·  free " + GeneText.number(Widgets.round(capacity - complexity)));
    }

    @Nonnull
    private static List<GeneLibrary.Entry> chosen(@Nonnull LabStationPage page, @Nonnull Ref<EntityStore> ref, @Nonnull Store<EntityStore> store) {
        GeneLibrary library = Library.of(ref, store);
        List<GeneLibrary.Entry> out = new ArrayList<>();
        for (GeneLibrary.Entry entry : library.getEntries()) if (page.libraryUse.contains(entry.getId())) out.add(entry);
        return out;
    }

    private static boolean emptySyringeInSlot(@Nonnull LabStationPage page) {
        ItemStack stack = page.sampleStack();
        return stack != null && Syringes.isSyringe(stack) && !Genome.isPresentOn(stack) && stack.getQuantity() == 1;
    }

    // ---- Actions

    /** Combines the ticked entries into the empty syringe in the slot. Returns a message, or null. */
    @Nullable
    static String combine(@Nonnull LabStationPage page, @Nonnull Ref<EntityStore> ref, @Nonnull Store<EntityStore> store) {
        if (!open(page, Journal.of(ref, store))) return null;
        Library.Plan plan = Library.plan(chosen(page, ref, store), page.station.tier());
        if (plan.blocked() != null) return plan.blocked();
        if (plan.result() == null) return null;
        if (!emptySyringeInSlot(page)) return "Put a single empty syringe in the slot to hold the result.";
        if (!Materials.pay(ref, store, plan.cost())) return "You don't carry enough materials to combine these genes.";
        ItemStack syringe = page.sampleStack();
        page.container.setItemStackForSlot(LabStationPage.SAMPLE_SLOT, Syringes.fill(syringe, plan.result()));
        page.libraryUse.clear();
        return "Combined into " + plan.result().title() + ": " + plan.genes().size() + " genes, Complexity "
                + GeneText.number(plan.result().complexity()) + ".";
    }

    /** Refines one entry by a level, paying the refine items. Returns a message. */
    @Nonnull
    static String refine(@Nonnull LabStationPage page, @Nonnull Ref<EntityStore> ref, @Nonnull Store<EntityStore> store, @Nonnull String id) {
        GeneJournal journal = Journal.of(ref, store);
        if (!open(page, journal) || !Research.hasFeature(journal, Research.REFINING)) return "Research the Gene Enhancer first.";
        GeneLibrary library = Library.of(ref, store);
        GeneLibrary.Entry entry = library.get(id);
        if (entry == null) return "That gene is no longer in your library.";
        if (!Library.canRefine(entry)) return "This gene can't be refined further.";
        int cost = Library.refineCost(entry);
        List<MaterialQuantity> items = List.of(new MaterialQuantity(Balance.get().library().getRefineItem(), null, null, cost, null));
        if (!Materials.pay(ref, store, items)) return "Refining needs " + cost + " " + refineItemName() + ".";
        library.refine(id);
        Gene gene = Library.effective(entry);
        return "Refined to " + (gene != null ? GeneText.describe(gene, page.player().getLanguage()) : "?") + ".";
    }

    @Nonnull
    static String discard(@Nonnull LabStationPage page, @Nonnull Ref<EntityStore> ref, @Nonnull Store<EntityStore> store, @Nonnull String id) {
        GeneLibrary library = Library.of(ref, store);
        GeneLibrary.Entry entry = library.get(id);
        if (entry == null) return "That gene is no longer in your library.";
        Gene gene = entry.getBase();
        library.remove(id);
        page.libraryUse.remove(id);
        return "Removed " + (gene != null ? GeneText.describe(gene, page.player().getLanguage()) : "a gene") + " from your library.";
    }

    @Nonnull
    private static String refineItemName() {
        com.hypixel.hytale.server.core.asset.type.item.config.Item item =
                com.hypixel.hytale.server.core.asset.type.item.config.Item.getAssetMap().getAsset(Balance.get().library().getRefineItem());
        return item != null ? "Gene Enhancer" : Balance.get().library().getRefineItem();
    }
}
