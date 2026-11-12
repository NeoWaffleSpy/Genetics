package com.genetics.labstation;

import com.genetics.codex.Balance;
import com.genetics.codex.Codex;
import com.genetics.codex.MobGeneProfile;
import com.genetics.codex.MutationEntry;
import com.genetics.genome.GeneText;
import com.genetics.journal.Catalogue;
import com.genetics.journal.GeneJournal;
import com.genetics.journal.Journal;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.protocol.packets.interface_.CustomUIEventBindingType;
import com.hypixel.hytale.server.core.ui.builder.EventData;
import com.hypixel.hytale.server.core.ui.builder.UICommandBuilder;
import com.hypixel.hytale.server.core.ui.builder.UIEventBuilder;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * The Catalogue tab (design doc §9.4): creatures, genes, buffs and mutations, each with a found / total count.
 * Unknown entries stay "???" but say where or how to find them.
 */
final class CatalogueTab {
    private static final String ROW = "Genetics/CatalogueRow.ui";
    private static final String UNKNOWN_PREFIX = "profile:";

    private CatalogueTab() {
    }

    /** One line of the list. {@code icon}: an asset path for creatures; {@code type}: Stat/Drawback/Effect/Mutation/Unknown. */
    private record Entry(@Nonnull String id, @Nonnull String name, @Nonnull String meta, @Nonnull String count, boolean known,
                         @Nullable String icon, @Nonnull String type) {
    }

    static void build(@Nonnull LabStationPage page, @Nonnull Ref<EntityStore> ref, @Nonnull Store<EntityStore> store,
                      @Nonnull UICommandBuilder c, @Nonnull UIEventBuilder e) {
        GeneJournal journal = Journal.of(ref, store);
        for (String s : LabStationPage.SUBS) {
            c.set("#Sub" + s + "On.Visible", s.equals(page.sub));
            c.set("#Sub" + s + "Off.Visible", !s.equals(page.sub));
        }
        List<Entry> entries = entries(page.sub, journal, page.player().getLanguage());
        c.clear("#CatalogueList");
        for (int i = 0; i < entries.size(); i++) {
            Entry entry = entries.get(i);
            String row = "#CatalogueList[" + i + "]";
            c.append("#CatalogueList", ROW);
            c.set(row + " #Name.Text", entry.name());
            c.set(row + " #Name.Style.TextColor", entry.known() ? Widgets.STRONG : Widgets.FAINT);
            c.set(row + " #Meta.Text", entry.meta());
            c.set(row + " #Count.Text", entry.count());
            c.set(row + " #Icon.Visible", entry.known() && entry.icon() != null);
            if (entry.known() && entry.icon() != null) c.set(row + " #Icon.AssetPath", entry.icon());
            boolean typed = entry.known() && entry.icon() == null;
            c.set(row + " #IconUnknown.Visible", !entry.known());
            for (String t : Widgets.TYPES) c.set(row + " #Icon" + t + ".Visible", typed && t.equals(entry.type()));
            e.addEventBinding(CustomUIEventBindingType.Activating, row + " #Button", new EventData().append("Action", "Entry").append("Node", entry.id()), false);
        }
        if (page.catalogueSelected == null || entries.stream().noneMatch(x -> x.id().equals(page.catalogueSelected))) {
            page.catalogueSelected = entries.stream().filter(Entry::known).map(Entry::id).findFirst()
                    .orElse(entries.isEmpty() ? null : entries.getFirst().id());
        }
        long known = entries.stream().filter(Entry::known).count();
        c.set("#CatalogueProgress.Text", page.sub + "  " + known + " / " + entries.size() + "     Catalogue " + overall(journal) + "% complete");
        select(page, ref, store, c);
    }

    /** Marks the selected row and fills the detail panel. */
    static void select(@Nonnull LabStationPage page, @Nonnull Ref<EntityStore> ref, @Nonnull Store<EntityStore> store, @Nonnull UICommandBuilder c) {
        GeneJournal journal = Journal.of(ref, store);
        String language = page.player().getLanguage();
        List<Entry> entries = entries(page.sub, journal, language);
        for (int i = 0; i < entries.size(); i++) {
            c.set("#CatalogueList[" + i + "] #Selected.Visible", entries.get(i).id().equals(page.catalogueSelected));
        }
        c.clear("#DetailGenes");
        Entry selected = entries.stream().filter(x -> x.id().equals(page.catalogueSelected)).findFirst().orElse(null);
        c.set("#DetailIcon.Visible", selected != null && selected.known() && selected.icon() != null);
        if (selected == null) {
            c.set("#DetailName.Text", "Nothing here yet");
            c.set("#DetailMeta.Text", "");
            c.set("#DetailGenesHeader.Text", "");
            c.set("#DetailMissing.Text", "");
            c.set("#DetailScience.Text", "");
            return;
        }
        if (selected.known() && selected.icon() != null) c.set("#DetailIcon.AssetPath", selected.icon());
        switch (page.sub) {
            case "Creatures" -> creatureDetail(c, journal, selected, language);
            case "Mutations" -> mutationDetail(c, selected, language);
            default -> geneDetail(c, journal, selected, language, page.sub.equals("Buffs"));
        }
    }

    // ---- Entries

    @Nonnull
    private static List<Entry> entries(@Nonnull String sub, @Nonnull GeneJournal journal, @Nullable String language) {
        return switch (sub) {
            case "Genes" -> geneEntries(journal, language, false);
            case "Buffs" -> geneEntries(journal, language, true);
            case "Mutations" -> mutationEntries(journal, language);
            default -> creatureEntries(journal);
        };
    }

    @Nonnull
    private static List<Entry> creatureEntries(@Nonnull GeneJournal journal) {
        List<Entry> out = new ArrayList<>();
        Map<String, String> zoneOf = new LinkedHashMap<>();
        for (Map.Entry<String, GeneJournal.Species> sampled : journal.getSpecies().entrySet()) {
            String id = sampled.getKey();
            MobGeneProfile profile = Codex.profileFor(id);
            int total = Catalogue.possibleGenes(profile).size();
            int found = journal.genesOf(id).size();
            String zone = profile != null && profile.getZone() != null ? profile.getZone() : "Unknown zone";
            zoneOf.put(id, zone);
            out.add(new Entry(id, sampled.getValue().getName(), zone + "  ·  " + found + " / " + total + " genes"
                    + (journal.isCompleted(id) ? "  ·  complete" : ""), found + " / " + total, true, sampled.getValue().getIcon(), "Creature"));
        }
        for (MobGeneProfile profile : MobGeneProfile.getAssetMap().getAssetMap().values()) {
            if (profile.getId().equals(Codex.DEFAULT_ID) || profile.getRoles().length == 0) continue;
            boolean sampled = false;
            for (String role : profile.getRoles()) if (journal.hasSampled(role)) sampled = true;
            if (sampled) continue;
            String zone = profile.getZone() != null ? profile.getZone() : "Unknown zone";
            zoneOf.put(UNKNOWN_PREFIX + profile.getId(), zone);
            out.add(new Entry(UNKNOWN_PREFIX + profile.getId(), "???", zone + "  ·  not sampled",
                    "0 / " + Catalogue.possibleGenes(profile).size(), false, null, "Unknown"));
        }
        out.sort(Comparator.comparing((Entry x) -> zoneOf.getOrDefault(x.id(), "~")).thenComparing(x -> !x.known()).thenComparing(Entry::name));
        return out;
    }

    /** Every stat/drawback (genes) or effect (buffs) key any profile can carry, plus those already found. */
    @Nonnull
    private static List<Entry> geneEntries(@Nonnull GeneJournal journal, @Nullable String language, boolean buffs) {
        Set<String> universe = new LinkedHashSet<>();
        for (MobGeneProfile profile : MobGeneProfile.getAssetMap().getAssetMap().values()) universe.addAll(Catalogue.possibleGenes(profile));
        for (Set<String> found : journal.getSpeciesGenes().values()) universe.addAll(found);
        for (String buff : journal.getBuffs()) universe.add(Catalogue.EFFECT + buff);
        List<Entry> out = new ArrayList<>();
        for (String key : universe) {
            boolean effect = key.startsWith(Catalogue.EFFECT);
            if (effect != buffs) continue;
            List<String> carriers = carriers(journal, key);
            boolean known = buffs ? journal.getBuffs().contains(key.substring(Catalogue.EFFECT.length())) || !carriers.isEmpty() : !carriers.isEmpty();
            String type = Catalogue.type(key);
            String name = known ? Catalogue.name(key, language) : "???";
            String meta = known ? (carriers.isEmpty() ? "Found in a splice" : "Found on " + String.join(", ", carriers))
                    : (type.equals("Drawback") ? "A drawback" : buffs ? "A buff" : "A stat gene") + " carried by " + possibleCarriers(key) + " species";
            out.add(new Entry(key, name, meta, known ? carriers.size() + " species" : "", known, null, type));
        }
        out.sort(Comparator.comparing((Entry x) -> !x.known()).thenComparing(Entry::name));
        return out;
    }

    @Nonnull
    private static List<Entry> mutationEntries(@Nonnull GeneJournal journal, @Nullable String language) {
        List<Entry> out = new ArrayList<>();
        for (MutationEntry mutation : MutationEntry.getAssetMap().getAssetMap().values()) {
            boolean known = journal.getMutations().contains(mutation.getId());
            String tier = mutation.getMinTier() != null ? mutation.getMinTier() : "?";
            out.add(new Entry(Catalogue.MUTATION + mutation.getId(),
                    known ? GeneText.mutationName(mutation.getId(), language) : "???",
                    known ? "Complexity " + GeneText.number(mutation.getComplexity()) + "  ·  from " + tier + " splices" : "Appears in " + tier + " splices or above",
                    known ? "found" : "", known, null, "Mutation"));
        }
        out.sort(Comparator.comparing((Entry x) -> !x.known()).thenComparing(Entry::name));
        return out;
    }

    // ---- Details

    private static void creatureDetail(@Nonnull UICommandBuilder c, @Nonnull GeneJournal journal, @Nonnull Entry entry, @Nullable String language) {
        Balance.Science science = Balance.get().science();
        if (!entry.known()) {
            MobGeneProfile profile = MobGeneProfile.getAssetMap().getAsset(entry.id().substring(UNKNOWN_PREFIX.length()));
            c.set("#DetailName.Text", "???");
            c.set("#DetailMeta.Text", (profile != null && profile.getZone() != null ? profile.getZone() : "Unknown zone") + "  ·  not sampled yet");
            c.set("#DetailGenesHeader.Text", "Genes  ·  ? / " + Catalogue.possibleGenes(profile).size());
            c.set("#DetailMissing.Text", "Sample this creature with a syringe to discover it.");
            c.set("#DetailScience.Text", "First sample: +" + science.getFirstSample() + " Science");
            return;
        }
        String id = entry.id();
        MobGeneProfile profile = Codex.profileFor(id);
        GeneJournal.Species species = journal.getSpecies().get(id);
        Set<String> possible = Catalogue.possibleGenes(profile);
        Set<String> found = journal.genesOf(id);
        c.set("#DetailName.Text", entry.name());
        c.set("#DetailMeta.Text", (profile != null && profile.getZone() != null ? profile.getZone() : "Unknown zone")
                + "  ·  sampled " + (species != null ? species.getSamples() : 0) + " times");
        c.set("#DetailGenesHeader.Text", "Genes  ·  " + found.size() + " / " + possible.size());
        int i = 0;
        for (String key : found) {
            String row = "#DetailGenes[" + i++ + "]";
            c.append("#DetailGenes", Widgets.GENE_ROW);
            Widgets.geneRow(c, row, Catalogue.type(key), Catalogue.name(key, language), Catalogue.range(profile, key), "", true, false);
        }
        int missing = (int) possible.stream().filter(k -> !found.contains(k)).count();
        c.set("#DetailMissing.Text", missing == 0 ? "Every gene found." : missing + (missing == 1 ? " gene" : " genes") + " not found yet. Process more samples of it.");
        int earned = science.getFirstSample() + found.size() * science.getNewGene() + (journal.isCompleted(id) ? science.getSpeciesComplete() : 0);
        c.set("#DetailScience.Text", "Science earned: " + earned + (journal.isCompleted(id) ? "  ·  complete" : "  ·  +" + science.getSpeciesComplete() + " more when every gene is found"));
    }

    private static void geneDetail(@Nonnull UICommandBuilder c, @Nonnull GeneJournal journal, @Nonnull Entry entry, @Nullable String language, boolean buff) {
        String key = entry.id();
        c.set("#DetailName.Text", entry.name());
        if (!entry.known()) {
            c.set("#DetailMeta.Text", entry.meta());
            c.set("#DetailGenesHeader.Text", "");
            c.set("#DetailMissing.Text", "Process samples of more species to find it.");
            c.set("#DetailScience.Text", buff ? "New buff: +" + Balance.get().science().getNewBuff() + " Science" : "");
            return;
        }
        String effect = buff ? key.substring(Catalogue.EFFECT.length()) : null;
        c.set("#DetailMeta.Text", buff ? "Complexity  ·  temporary +" + GeneText.number(Codex.effectComplexity(effect, false))
                + "  ·  permanent +" + GeneText.number(Codex.effectComplexity(effect, true))
                : entry.type().equals("Drawback") ? "Drawback: lowers the stat, and Complexity a little" : "Stat gene: raises the stat's maximum");
        List<String> carriers = carrierIds(journal, key);
        c.set("#DetailGenesHeader.Text", "Found on  ·  " + carriers.size());
        int i = 0;
        for (String id : carriers) {
            String row = "#DetailGenes[" + i++ + "]";
            c.append("#DetailGenes", Widgets.GENE_ROW);
            GeneJournal.Species species = journal.getSpecies().get(id);
            Widgets.geneRow(c, row, entry.type(), species != null ? species.getName() : id, Catalogue.range(Codex.profileFor(id), key), "", true, false);
        }
        int more = possibleCarriers(key) - carriers.size();
        c.set("#DetailMissing.Text", more > 0 ? more + " more species can carry it." : "");
        c.set("#DetailScience.Text", "");
    }

    private static void mutationDetail(@Nonnull UICommandBuilder c, @Nonnull Entry entry, @Nullable String language) {
        MutationEntry mutation = MutationEntry.getAssetMap().getAsset(entry.id().substring(Catalogue.MUTATION.length()));
        c.set("#DetailName.Text", entry.name());
        c.set("#DetailMeta.Text", entry.meta());
        if (!entry.known() || mutation == null) {
            c.set("#DetailGenesHeader.Text", "");
            c.set("#DetailMissing.Text", "New mutations only appear when splicing.");
            c.set("#DetailScience.Text", "New mutation: +" + Balance.get().science().getNewMutation() + " Science");
            return;
        }
        List<String> parts = new ArrayList<>();
        mutation.getStats().forEach((stat, value) -> parts.add("Max " + GeneText.prettify(stat) + " " + (value > 0 ? "+" : "") + GeneText.number(value) + "%"));
        for (String effect : mutation.getEffects()) parts.add(GeneText.effectName(effect, language));
        for (com.genetics.mutation.Behavior behavior : mutation.getBehaviors()) parts.add(behavior.describe());
        c.set("#DetailGenesHeader.Text", "What it does");
        c.set("#DetailMissing.Text", String.join("\n", parts));
        c.set("#DetailScience.Text", "");
    }

    // ---- Lookups

    /** Names of the sampled species a key was found on. */
    @Nonnull
    private static List<String> carriers(@Nonnull GeneJournal journal, @Nonnull String key) {
        List<String> names = new ArrayList<>();
        for (String id : carrierIds(journal, key)) {
            GeneJournal.Species species = journal.getSpecies().get(id);
            names.add(species != null ? species.getName() : id);
        }
        return names;
    }

    @Nonnull
    private static List<String> carrierIds(@Nonnull GeneJournal journal, @Nonnull String key) {
        List<String> ids = new ArrayList<>();
        for (Map.Entry<String, Set<String>> entry : journal.getSpeciesGenes().entrySet()) if (entry.getValue().contains(key)) ids.add(entry.getKey());
        ids.sort(Comparator.naturalOrder());
        return ids;
    }

    /** How many profiled species can carry a key. */
    private static int possibleCarriers(@Nonnull String key) {
        int n = 0;
        for (MobGeneProfile profile : MobGeneProfile.getAssetMap().getAssetMap().values()) {
            if (profile.getId().equals(Codex.DEFAULT_ID)) continue;
            if (Catalogue.possibleGenes(profile).contains(key)) n += Math.max(1, profile.getRoles().length);
        }
        return n;
    }

    /** Overall completion: species, genes, buffs and mutations found against what exists. */
    private static int overall(@Nonnull GeneJournal journal) {
        List<Entry> all = new ArrayList<>();
        all.addAll(creatureEntries(journal));
        all.addAll(geneEntries(journal, null, false));
        all.addAll(geneEntries(journal, null, true));
        all.addAll(mutationEntries(journal, null));
        if (all.isEmpty()) return 0;
        return (int) Math.round(100.0 * all.stream().filter(Entry::known).count() / all.size());
    }
}
