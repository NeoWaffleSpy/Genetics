package com.genetics.labstation;

import com.genetics.capacity.GenomeCapacity;
import com.genetics.genome.Gene;
import com.genetics.genome.GeneText;
import com.genetics.genome.Genome;
import com.genetics.interaction.Injection;
import com.genetics.interaction.Syringes;
import com.genetics.journal.Catalogue;
import com.genetics.journal.GeneJournal;
import com.genetics.journal.Journal;
import com.genetics.process.Processing;
import com.genetics.research.Research;
import com.genetics.research.ResearchNode;
import com.genetics.tier.Materials;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.protocol.packets.interface_.CustomUIEventBindingType;
import com.hypixel.hytale.server.core.inventory.ItemStack;
import com.hypixel.hytale.server.core.ui.builder.EventData;
import com.hypixel.hytale.server.core.ui.builder.UICommandBuilder;
import com.hypixel.hytale.server.core.ui.builder.UIEventBuilder;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

/**
 * The Process tab (design doc §6, §7): processing is also identification, so a raw sample's genes are shown right
 * away, ticked; the player unticks the ones they don't want, can make long temporary effects permanent (once
 * researched), and turns the sample into genetic material. Genetic material is shown read-only.
 */
final class ProcessTab {
    private ProcessTab() {
    }

    static void build(@Nonnull LabStationPage page, @Nonnull Ref<EntityStore> ref, @Nonnull Store<EntityStore> store,
                      @Nonnull UICommandBuilder c, @Nonnull UIEventBuilder e) {
        c.clear("#GeneList");
        Genome sample = page.sample();
        if (sample != null) {
            boolean editable = !sample.processed();
            boolean permanentAllowed = Research.hasFeature(Journal.of(ref, store), Research.MAKE_PERMANENT);
            boolean extract = !editable && LibraryTab.open(page, Journal.of(ref, store));
            for (int i = 0; i < sample.genes().size(); i++) {
                String row = "#GeneList[" + i + "]";
                c.append("#GeneList", Widgets.GENE_ROW);
                if (extract) {
                    c.set(row + " #ExtractBox.Visible", true);
                    e.addEventBinding(CustomUIEventBindingType.Activating, row + " #Extract",
                            new EventData().append("Action", "PickExtract").append("Index", String.valueOf(i)), false);
                }
                if (!editable) continue;
                c.set(row + " #Toggle.Value", page.keep[i]);
                e.addEventBinding(CustomUIEventBindingType.ValueChanged, row + " #Toggle",
                        new EventData().append("Action", "Keep").append("Index", String.valueOf(i)).append("@Checked", row + " #Toggle.Value"), false);
                if (!Processing.canMakePermanent(sample.genes().get(i))) continue;
                c.set(row + " #PermanentBox.Visible", true);
                c.set(row + " #PermanentCheck.Visible", permanentAllowed);
                if (permanentAllowed) {
                    c.set(row + " #Permanent.Value", page.permanent[i]);
                    e.addEventBinding(CustomUIEventBindingType.ValueChanged, row + " #Permanent",
                            new EventData().append("Action", "Permanent").append("Index", String.valueOf(i)).append("@Checked", row + " #Permanent.Value"), false);
                } else {
                    ResearchNode node = Research.grantingNode(Research.MAKE_PERMANENT);
                    c.set(row + " #PermanentLabel.Text", "Research " + (node != null ? node.getName() : "Make Permanent") + " to keep it");
                    c.set(row + " #PermanentLabel.Style.TextColor", Widgets.FAINT);
                }
            }
        }
        update(page, ref, store, c);
    }

    /** Texts, gauge, costs and button for the current choices. */
    static void update(@Nonnull LabStationPage page, @Nonnull Ref<EntityStore> ref, @Nonnull Store<EntityStore> store, @Nonnull UICommandBuilder c) {
        Genome sample = page.sample();
        c.set("#ProcessEmpty.Visible", sample == null);
        c.set("#ProcessDetails.Visible", sample != null);
        c.set("#ProcessBar.Visible", sample != null);
        c.set("#ProcessReason.Visible", sample != null);
        c.set("#UpgradeStationButton.Visible", false);
        c.set("#ExtractButton.Visible", false);
        if (sample == null) return;
        GeneJournal journal = Journal.of(ref, store);
        String language = page.player().getLanguage();
        double capacity = GenomeCapacity.capacity(ref, store);
        boolean raw = !sample.processed();
        boolean permanentAllowed = Research.hasFeature(journal, Research.MAKE_PERMANENT);
        boolean[] permanent = permanentAllowed ? page.permanent : new boolean[0];

        c.set("#GenomeName.Text", raw ? rawName(sample) : sample.title());
        c.set("#Kind.Text", raw ? "Raw sample" : "Genetic material");

        int kept = 0;
        for (int i = 0; i < sample.genes().size(); i++) {
            Gene gene = sample.genes().get(i);
            boolean keep = !raw || page.keep[i];
            if (keep) kept++;
            boolean stable = raw && i < permanent.length && permanent[i] && Processing.canMakePermanent(gene);
            Gene shown = stable ? new Gene.Effect(((Gene.Effect) gene).effect(), true, 0) : gene;
            String name = Catalogue.name(Catalogue.key(gene), language) + (raw && Journal.isNew(journal, sample, gene) ? "  ·  NEW" : "");
            double complexity = GeneText.complexity(shown);
            String row = "#GeneList[" + i + "]";
            Widgets.geneRow(c, row, Catalogue.type(Catalogue.key(gene)), name, value(shown),
                    (complexity >= 0 ? "+" : "") + GeneText.number(Widgets.round(complexity)), keep, raw);
            // Temporary effects wear off: they cost Complexity to process and inject, but don't use capacity.
            if (keep && shown instanceof Gene.Effect effect && !effect.permanent()) c.set(row + " #Complexity.Style.TextColor", Widgets.TEXT);
        }

        if (!raw) {
            Widgets.capacity(c, "P", Injection.lastingComplexity(sample), capacity, "Lasting genes");
            c.set("#PCapLegend.Text", legend(sample.genes(), Injection.lastingComplexity(sample), capacity));
            c.set("#NewGenes.Text", "");
            c.set("#GeneHeader.Text", "Genes  ·  " + sample.genes().size());
            c.set("#ProcessCostLabel.Visible", false);
            c.clear("#ProcessCost");
            c.set("#ProcessReason.Text", "");
            boolean fits = Injection.lastingComplexity(sample) <= capacity;
            c.set("#ProcessStatus.Visible", true);
            c.set("#ProcessStatus.Text", fits ? "Fits your capacity  ·  injected exactly as it is" : "Over your capacity: it would degrade when injected");
            c.set("#ProcessStatus.Style.TextColor", fits ? Widgets.GOOD : Widgets.BAD);
            c.set("#ProcessButton.Visible", false);
            c.set("#SpliceWithButton.Visible", true);
            c.set("#SpliceWithButton.Disabled", !Research.hasFeature(journal, Research.SPLICING));
            extractBar(page, ref, store, c, sample);
            return;
        }

        Processing.Plan plan = Processing.plan(sample, page.keep, permanent, page.station.tier());
        Genome result = plan.result();
        double lasting = result != null ? Injection.lastingComplexity(result) : 0;
        Widgets.capacity(c, "P", lasting, capacity, "Lasting genes");
        if (result != null) c.set("#PCapLegend.Text", legend(result.genes(), lasting, capacity));
        int science = Journal.scienceFor(journal, sample);
        c.set("#NewGenes.Text", science > 0 ? "+" + science + " Science when processed" : "");
        c.set("#GeneHeader.Text", "Tick the genes to keep  ·  " + kept + " / " + sample.genes().size());
        c.set("#ProcessCostLabel.Visible", true);
        c.set("#ProcessCostLabel.Text", plan.tier() != null ? "Cost  ·  " + plan.tier().getName() : "Cost");
        Widgets.costs(c, "#ProcessCost", plan.cost(), ref, store);
        String reason = plan.blocked();
        if (reason == null && !Materials.canPay(ref, store, plan.cost())) reason = "Missing materials";
        c.set("#ProcessReason.Text", reason != null ? reason : "");
        c.set("#ProcessReason.Style.TextColor", Widgets.BAD);
        c.set("#ProcessStatus.Visible", false);
        // A tier the station can't handle: offer the upgrade instead of a dead Process button.
        boolean needsStation = plan.tier() != null && plan.blocked() != null && page.station.tier() < plan.tier().getStationTier();
        c.set("#ProcessButton.Visible", !needsStation);
        c.set("#ProcessButton.Disabled", reason != null || result == null);
        c.set("#UpgradeStationButton.Visible", needsStation);
        c.set("#SpliceWithButton.Visible", false);
    }

    /** With a gene picked for extraction: its row highlighted, the extraction cost and the Extract button. */
    private static void extractBar(@Nonnull LabStationPage page, @Nonnull Ref<EntityStore> ref, @Nonnull Store<EntityStore> store,
                                   @Nonnull UICommandBuilder c, @Nonnull Genome sample) {
        int picked = page.extractIndex;
        for (int i = 0; i < sample.genes().size(); i++) {
            c.set("#GeneList[" + i + "] #Extract.Text", i == picked ? "Picked" : "Extract");
        }
        if (picked < 0 || picked >= sample.genes().size() || !LibraryTab.open(page, Journal.of(ref, store))) return;
        com.genetics.library.Library.ExtractPlan plan = com.genetics.library.Library.extractPlan(sample.genes().get(picked), page.station.tier());
        c.set("#ProcessStatus.Visible", false);
        c.set("#SpliceWithButton.Visible", false);
        c.set("#ProcessCostLabel.Visible", true);
        c.set("#ProcessCostLabel.Text", plan.tier() != null ? "Extract  ·  " + plan.tier().getName() : "Extract");
        Widgets.costs(c, "#ProcessCost", plan.cost(), ref, store);
        String reason = plan.blocked() != null ? plan.blocked()
                : !Materials.canPay(ref, store, plan.cost()) ? "Missing materials" : null;
        c.set("#ProcessReason.Text", reason != null ? reason : "Extracting uses up this genetic material");
        c.set("#ProcessReason.Style.TextColor", reason != null ? Widgets.BAD : Widgets.TEXT);
        c.set("#ExtractButton.Visible", true);
        c.set("#ExtractButton.Disabled", reason != null);
    }

    /** Extracts the picked gene into the Genetic Library, using up the genetic material. Returns a message, or null. */
    @Nullable
    static String extract(@Nonnull LabStationPage page, @Nonnull Ref<EntityStore> ref, @Nonnull Store<EntityStore> store) {
        ItemStack stack = page.sampleStack();
        Genome sample = page.sample();
        if (stack == null || sample == null || !sample.processed() || !LibraryTab.open(page, Journal.of(ref, store))) return null;
        int picked = page.extractIndex;
        if (picked < 0 || picked >= sample.genes().size()) return "Pick a gene to extract.";
        Gene gene = sample.genes().get(picked);
        com.genetics.library.Library.ExtractPlan plan = com.genetics.library.Library.extractPlan(gene, page.station.tier());
        if (plan.blocked() != null) return plan.blocked();
        if (!Materials.pay(ref, store, plan.cost())) return "You don't carry enough materials to extract this gene.";
        com.genetics.library.Library.of(ref, store).add(gene, sample.title());
        page.container.setItemStackForSlot(LabStationPage.SAMPLE_SLOT, Syringes.empty(stack));
        page.extractIndex = -1;
        return "Extracted " + GeneText.describe(gene, page.player().getLanguage()) + " into your Genetic Library.";
    }

    /** Processes the sample in the station with the current choices. Returns a message for the player, or null. */
    @Nullable
    static String process(@Nonnull LabStationPage page, @Nonnull Ref<EntityStore> ref, @Nonnull Store<EntityStore> store) {
        ItemStack stack = page.sampleStack();
        Genome sample = page.sample();
        String refusal = Processing.refusal(sample);
        if (refusal != null || stack == null || sample == null) return refusal != null ? refusal : "Put a filled syringe in the slot.";
        GeneJournal journal = Journal.of(ref, store);
        boolean[] permanent = Research.hasFeature(journal, Research.MAKE_PERMANENT) ? page.permanent : new boolean[0];
        if (page.keep.length != sample.genes().size()) return null;
        Processing.Plan plan = Processing.plan(sample, page.keep, permanent, page.station.tier());
        if (plan.result() == null) return "Keep at least one gene.";
        if (plan.blocked() != null) return plan.blocked();
        if (!Materials.pay(ref, store, plan.cost())) return "You don't carry enough materials to process this sample.";
        page.container.setItemStackForSlot(LabStationPage.SAMPLE_SLOT, Syringes.fill(stack, plan.result()));
        Journal.onIdentify(ref, store, page.player(), plan.result());
        return "Processed into genetic material: " + plan.result().genes().size() + " genes, Complexity "
                + GeneText.number(plan.result().complexity()) + ".";
    }

    /** The gauge legend, with a note when temporary effects (which wear off) aren't counted. */
    @Nonnull
    private static String legend(@Nonnull java.util.List<Gene> genes, double lasting, double capacity) {
        boolean temporary = genes.stream().anyMatch(g -> g instanceof Gene.Effect effect && !effect.permanent());
        String base = "Lasting genes " + GeneText.number(Widgets.round(lasting))
                + (lasting > capacity ? "     Over capacity " + GeneText.number(Widgets.round(lasting - capacity))
                : "     Free " + GeneText.number(Widgets.round(capacity - lasting)));
        return temporary ? base + "     Temporary effects wear off and don't use capacity" : base;
    }

    /** "Wolf Sample" / "Spliced Sample": the genes are shown, so it is no longer called unidentified here. */
    @Nonnull
    private static String rawName(@Nonnull Genome sample) {
        if (sample.source() == null) return "Sample";
        return sample.source().spliced() ? "Spliced Sample" : sample.source().mobName() + " Sample";
    }

    /** A gene's value: "+3.5%", "3:00", "permanent", or the mutation's triggers. */
    @Nonnull
    static String value(@Nonnull Gene gene) {
        return switch (gene) {
            case Gene.Stat stat -> (stat.percent() > 0 ? "+" : "") + GeneText.number(stat.percent()) + "%";
            case Gene.Effect effect -> effect.permanent() ? "permanent" : GeneText.duration(effect.duration());
            case Gene.Mutation ignored -> "mutation";
        };
    }
}
