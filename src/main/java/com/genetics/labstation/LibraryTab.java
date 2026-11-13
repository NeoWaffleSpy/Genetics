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
 * The Library tab (design doc §8.1): the player's genes, one per kind at its best value, each ticked into the
 * combination at an amount from 0 to that value; the preview on the right; the result goes into the empty syringe in
 * the station's slot. Needs the Genetic Library research and a Lab Station T3 (the upgrade is offered here).
 */
final class LibraryTab {
    private static final String ROW = "Genetics/LibraryRow.ui";
    /** Inner width of the preview's capacity track: 260 panel - 2 x 12 padding - 2 outline. */
    private static final int TRACK = 234;

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
        String language = page.player().getLanguage();
        c.clear("#LibraryList");
        for (int i = 0; i < entries.size(); i++) {
            GeneLibrary.Entry entry = entries.get(i);
            Gene gene = entry.getGene();
            String row = "#LibraryList[" + i + "]";
            c.append("#LibraryList", ROW);
            String key = Catalogue.key(gene);
            String type = Catalogue.type(key);
            Widgets.typeIcon(c, row, type);
            c.set(row + " #Name.Text", Catalogue.name(key, language) + (gene instanceof Gene.Effect effect && effect.permanent() ? " (permanent)" : ""));
            c.set(row + " #Meta.Text", "From " + entry.getSource());
            c.set(row + " #Value.Style.TextColor", Widgets.valueColor(type));
            c.set(row + " #Use.Value", page.libraryUse.contains(entry.getId()));
            boolean adjustable = Library.adjustable(gene);
            c.set(row + " #AmountBox.Visible", adjustable);
            c.set(row + " #Value.Visible", !adjustable);
            if (adjustable) controls(page, c, row + " ", entry, true, true);
            e.addEventBinding(CustomUIEventBindingType.ValueChanged, row + " #Use",
                    new EventData().append("Action", "LibraryUse").append("Node", entry.getId()).append("@Checked", row + " #Use.Value"), false);
            if (adjustable) {
                e.addEventBinding(CustomUIEventBindingType.ValueChanged, row + " #Amount",
                        new EventData().append("Action", "LibraryAmount").append("Node", entry.getId()).append("@Amount", row + " #Amount.Value"), false);
                e.addEventBinding(CustomUIEventBindingType.ValueChanged, row + " #AmountField",
                        new EventData().append("Action", "LibraryField").append("Node", entry.getId()).append("@Field", row + " #AmountField.Value"), false);
            }
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
        String text = "The Genetic Library keeps the best version of every gene you extract from genetic material, and combines them at will: "
                + "pick how much of each gene to use, and mix several mutations. It needs a Lab Station T3.\n\n";
        if (!researched) text += "First research " + name + " in the Research tab (" + (node != null ? node.getCost() : 0)
                + " Science, you have " + journal.getScience() + ").";
        else if (needsT2) text += "Upgrade this station to T2 first (in the Splice tab).";
        else text += "Upgrade this station to T3 with these materials (free in Creative):";
        c.set("#LibraryLockedText.Text", text);
        Widgets.costs(c, "#LibUpgradeCost", cost, ref, store);
        c.set("#LibUpgradeButton.Visible", !needsT2);
        c.set("#LibUpgradeButton.Disabled", !researched || upgrade == null || !Materials.canPay(ref, store, cost));
    }

    /** Row values, the combination preview, cost and button. */
    static void update(@Nonnull LabStationPage page, @Nonnull Ref<EntityStore> ref, @Nonnull Store<EntityStore> store, @Nonnull UICommandBuilder c) {
        GeneJournal journal = Journal.of(ref, store);
        if (!open(page, journal)) return;
        List<GeneLibrary.Entry> entries = Library.of(ref, store).getEntries();
        for (int i = 0; i < entries.size(); i++) {
            GeneLibrary.Entry entry = entries.get(i);
            Gene gene = entry.getGene();
            String row = "#LibraryList[" + i + "]";
            boolean used = page.libraryUse.contains(entry.getId());
            if (!Library.adjustable(gene)) c.set(row + " #Value.Text", ProcessTab.value(gene));
            c.set(row + " #Name.Style.TextColor", used ? Widgets.STRONG : Widgets.TEXT);
        }

        Library.Plan plan = Library.plan(uses(page, entries), page.station.tier());
        String language = page.player().getLanguage();
        Genome result = plan.result();
        double capacity = GenomeCapacity.capacity(ref, store);
        c.set("#DesignName.Text", result != null ? result.title() : "New genome");
        capacity(c, result != null ? Injection.lastingComplexity(result) : 0, capacity);
        c.set("#DesignMutations.Text", plan.mutations() > 1
                ? plan.mutations() + " mutations  ·  cost x" + GeneText.number(plan.multiplier())
                : plan.mutations() == 1 ? "1 mutation" : "");
        c.set("#DesignHeader.Text", plan.genes().size() + (plan.genes().size() == 1 ? " gene" : " genes")
                + (result != null ? "  ·  Complexity " + GeneText.number(result.complexity()) : ""));
        c.clear("#DesignList");
        for (int i = 0; i < plan.genes().size(); i++) {
            Gene gene = plan.genes().get(i);
            String row = "#DesignList[" + i + "]";
            c.append("#DesignList", Widgets.DETAIL_ROW);
            String key = Catalogue.key(gene);
            String type = Catalogue.type(key);
            Widgets.typeIcon(c, row, type);
            c.set(row + " #Name.Text", Catalogue.name(key, language));
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
        c.set("#LCapLegend.Text", over ? "Over capacity by " + GeneText.number(Widgets.round(complexity - capacity))
                : "Lasting genes " + GeneText.number(Widgets.round(complexity)) + "  ·  free " + GeneText.number(Widgets.round(capacity - complexity)));
    }

    /** The amount chosen for an entry, in its unit; its maximum until changed. */
    static double amount(@Nonnull LabStationPage page, @Nonnull GeneLibrary.Entry entry) {
        Gene gene = entry.getGene();
        double max = gene != null ? Library.maxAmount(gene) : 0;
        return Math.clamp(page.libraryAmount.getOrDefault(entry.getId(), max), 0, max);
    }

    /**
     * Writes a row's amount controls: the max label, and the number field and/or the slider (0-100% of the max).
     * The control the player is using is left alone, so typing isn't interrupted.
     */
    static void controls(@Nonnull LabStationPage page, @Nonnull UICommandBuilder c, @Nonnull String row, @Nonnull GeneLibrary.Entry entry,
                         boolean field, boolean slider) {
        Gene gene = entry.getGene();
        if (gene == null) return;
        double max = Library.maxAmount(gene);
        double value = amount(page, entry);
        String unit = Library.unit(gene);
        c.set(row + " #Max.Text", "/ " + GeneText.number(max) + (unit.equals("%") ? "%" : " min"));
        c.set(row + " #Unit.Text", unit.equals("%") ? (gene instanceof Gene.Stat s && s.isDrawback() ? "-%" : "+%") : "min");
        if (field) c.set(row + " #AmountField.Value", value);
        if (slider) c.set(row + " #Amount.Value", max > 0 ? (int) Math.round(value / max * 100) : 0);
    }

    /** Sets an entry's amount from the slider (percent of max) or the field (a value), then syncs the other control. */
    static void setAmount(@Nonnull LabStationPage page, @Nonnull Ref<EntityStore> ref, @Nonnull Store<EntityStore> store,
                          @Nonnull UICommandBuilder c, @Nonnull String id, double input, boolean fromSlider) {
        List<GeneLibrary.Entry> entries = Library.of(ref, store).getEntries();
        for (int i = 0; i < entries.size(); i++) {
            GeneLibrary.Entry entry = entries.get(i);
            if (!entry.getId().equals(id) || entry.getGene() == null) continue;
            double max = Library.maxAmount(entry.getGene());
            double value = fromSlider ? Math.round(max * Math.clamp(input, 0, 100) / 100.0 * 10) / 10.0 : Math.round(input * 10) / 10.0;
            boolean clamped = value < 0 || value > max;
            page.libraryAmount.put(id, Math.clamp(value, 0, max));
            controls(page, c, "#LibraryList[" + i + "] ", entry, fromSlider || clamped, !fromSlider);
            return;
        }
    }

    @Nonnull
    private static List<Library.Use> uses(@Nonnull LabStationPage page, @Nonnull List<GeneLibrary.Entry> entries) {
        List<Library.Use> out = new ArrayList<>();
        for (GeneLibrary.Entry entry : entries) {
            if (page.libraryUse.contains(entry.getId())) out.add(new Library.Use(entry, amount(page, entry)));
        }
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
        Library.Plan plan = Library.plan(uses(page, Library.of(ref, store).getEntries()), page.station.tier());
        if (plan.blocked() != null) return plan.blocked();
        if (plan.result() == null) return null;
        if (!emptySyringeInSlot(page)) return "Put a single empty syringe in the slot to hold the result.";
        if (!Materials.pay(ref, store, plan.cost())) return "You don't carry enough materials to combine these genes.";
        ItemStack syringe = page.sampleStack();
        page.container.setItemStackForSlot(LabStationPage.SAMPLE_SLOT, Syringes.fill(syringe, plan.result()));
        page.libraryUse.clear();
        page.libraryAmount.clear();
        return "Combined into " + plan.result().title() + ": " + plan.genes().size() + " genes, Complexity "
                + GeneText.number(plan.result().complexity()) + ".";
    }

    @Nonnull
    static String discard(@Nonnull LabStationPage page, @Nonnull Ref<EntityStore> ref, @Nonnull Store<EntityStore> store, @Nonnull String id) {
        GeneLibrary library = Library.of(ref, store);
        GeneLibrary.Entry entry = library.get(id);
        if (entry == null) return "That gene is no longer in your library.";
        Gene gene = entry.getGene();
        library.remove(id);
        page.libraryUse.remove(id);
        page.libraryAmount.remove(id);
        return "Removed " + (gene != null ? GeneText.describe(gene, page.player().getLanguage()) : "a gene") + " from your library.";
    }
}
