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
    private static final String GROUP = "Genetics/CatalogueGroup.ui";
    /** Order of the creature and buff groups; others follow alphabetically. */
    private static final List<String> GROUP_ORDER = List.of(
            "Oceans", "Zone 1 wildlife", "Zone 2 wildlife", "Zone 3 wildlife", "Zone 4 wildlife", "Tribes", "Goblins", "Scarak",
            "Undead", "Void", "Dragons",
            "Vitality", "Endurance", "Regen", "Stamina Regen", "Speed", "Might", "Hide", "Immunities", "Senses");

    /** Sub-tabs whose list is split into collapsible groups. */
    static boolean grouped(@Nonnull String sub) {
        return sub.equals("Creatures") || sub.equals("Buffs");
    }

    private static int groupRank(@Nonnull String group) {
        int i = GROUP_ORDER.indexOf(group);
        return i >= 0 ? i : GROUP_ORDER.size();
    }

    /** The group an entry is listed under: a creature's biome or tribe (its profile's Category), a buff's family. */
    @Nonnull
    private static String groupOf(@Nonnull String sub, @Nonnull Entry entry, @Nullable String language) {
        if (sub.equals("Buffs")) return buffFamily(entry.id().substring(Catalogue.EFFECT.length()));
        String id = entry.id().startsWith(UNKNOWN_PREFIX) ? entry.id().substring(UNKNOWN_PREFIX.length()) : entry.id();
        MobGeneProfile profile = Catalogue.profileOf(id);
        if (profile == null) return "Other";
        if (profile.getCategory() != null) return profile.getCategory();
        return profile.getZone() != null ? profile.getZone() : "Other";
    }

    /** A buff's family: its tiers together (Vitality T1-T3), immunities together. */
    @Nonnull
    static String buffFamily(@Nonnull String effect) {
        if (effect.startsWith("Immunity_")) return "Immunities";
        return switch (com.genetics.process.Splicer.family(effect)) {
            case "Meat_Buff" -> "Vitality";
            case "FruitVeggie_Buff" -> "Endurance";
            case "HealthRegen_Buff" -> "Regen";
            case "Food_Stamina_Regen_Small" -> "Stamina Regen";
            case "Genetics_Speed" -> "Speed";
            case "Genetics_Might" -> "Might";
            case "Genetics_Hide" -> "Hide";
            case "Genetics_NightVision" -> "Senses";
            default -> "Other";
        };
    }

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
        String language = page.player().getLanguage();
        List<Entry> entries = entries(page.sub, journal, language);
        if (page.catalogueSelected == null || entries.stream().noneMatch(x -> x.id().equals(page.catalogueSelected))) {
            page.catalogueSelected = entries.stream().filter(Entry::known).map(Entry::id).findFirst()
                    .orElse(entries.isEmpty() ? null : entries.getFirst().id());
        }
        boolean grouped = grouped(page.sub);
        if (grouped) {
            entries = new ArrayList<>(entries);
            entries.sort(Comparator.comparingInt((Entry x) -> groupRank(groupOf(page.sub, x, language))).thenComparing(x -> groupOf(page.sub, x, language)));
            // The first time a sub-tab is shown, open the group of the selected entry.
            if (page.catalogueSeen.add(page.sub)) {
                Entry selected = entries.stream().filter(x -> x.id().equals(page.catalogueSelected)).findFirst().orElse(null);
                if (selected != null) page.catalogueOpen.add(page.sub + ":" + groupOf(page.sub, selected, language));
            }
        }
        c.clear("#CatalogueList");
        page.catalogueRows.clear();
        int rows = 0;
        String currentGroup = null;
        for (Entry entry : entries) {
            if (grouped) {
                String group = groupOf(page.sub, entry, language);
                boolean open = page.catalogueOpen.contains(page.sub + ":" + group);
                if (!group.equals(currentGroup)) {
                    currentGroup = group;
                    String header = "#CatalogueList[" + rows++ + "]";
                    c.append("#CatalogueList", GROUP);
                    List<Entry> members = new ArrayList<>();
                    for (Entry x : entries) if (groupOf(page.sub, x, language).equals(group)) members.add(x);
                    c.set(header + " #Name.Text", group);
                    c.set(header + " #Arrow.Text", open ? "-" : "+");
                    c.set(header + " #Count.Text", members.stream().filter(Entry::known).count() + " / " + members.size());
                    e.addEventBinding(CustomUIEventBindingType.Activating, header + " #Button",
                            new EventData().append("Action", "Group").append("Node", page.sub + ":" + group), false);
                }
                if (!open) continue;
            }
            int i = rows++;
            page.catalogueRows.put(entry.id(), i);
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
        long known = entries.stream().filter(Entry::known).count();
        c.set("#CatalogueProgress.Text", page.sub + "  " + known + " / " + entries.size() + "     Catalogue " + overall(journal) + "% complete");
        select(page, ref, store, c);
    }

    /** Marks the selected row and fills the detail panel. */
    static void select(@Nonnull LabStationPage page, @Nonnull Ref<EntityStore> ref, @Nonnull Store<EntityStore> store, @Nonnull UICommandBuilder c) {
        GeneJournal journal = Journal.of(ref, store);
        String language = page.player().getLanguage();
        List<Entry> entries = entries(page.sub, journal, language);
        page.catalogueRows.forEach((id, i) -> c.set("#CatalogueList[" + i + "] #Selected.Visible", id.equals(page.catalogueSelected)));
        c.clear("#DetailGenes");
        c.set("#DetailText.Visible", false);
        c.set("#DetailText.Text", "");
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
            case "Mutations" -> mutationDetail(c, journal, selected, language);
            default -> geneDetail(c, journal, com.genetics.library.Library.of(ref, store), selected, language, page.sub.equals("Buffs"));
        }
    }

    // ---- Entries

    @Nonnull
    private static List<Entry> entries(@Nonnull String sub, @Nonnull GeneJournal journal, @Nullable String language) {
        return switch (sub) {
            case "Genes" -> geneEntries(journal, language, false);
            case "Buffs" -> geneEntries(journal, language, true);
            case "Mutations" -> mutationEntries(journal, language);
            default -> creatureEntries(journal, language);
        };
    }

    @Nonnull
    private static List<Entry> creatureEntries(@Nonnull GeneJournal journal, @Nullable String language) {
        List<Entry> out = new ArrayList<>();
        Map<String, String> zoneOf = new LinkedHashMap<>();
        for (Map.Entry<String, GeneJournal.Species> sampled : journal.getSpecies().entrySet()) {
            String id = sampled.getKey();
            MobGeneProfile profile = Catalogue.profileOf(id);
            int total = Catalogue.possibleGenes(profile).size();
            int found = (int) journal.genesOf(id).stream().filter(k -> !k.startsWith(Catalogue.MUTATION)).count();
            String zone = profile != null && profile.getZone() != null ? profile.getZone() : "Unknown zone";
            zoneOf.put(id, zone);
            String icon = Catalogue.speciesIcon(id);
            if (icon == null) icon = sampled.getValue().getIcon();
            out.add(new Entry(id, speciesName(journal, id, language), zone + "  ·  " + found + " / " + total + " genes"
                    + (journal.isCompleted(id) ? "  ·  complete" : ""), found + " / " + total, true, icon, "Creature"));
        }
        for (String species : Catalogue.allSpecies()) {
            if (journal.hasSampled(species)) continue;
            MobGeneProfile profile = Catalogue.profileOf(species);
            String zone = profile != null && profile.getZone() != null ? profile.getZone() : "Unknown zone";
            zoneOf.put(UNKNOWN_PREFIX + species, zone);
            out.add(new Entry(UNKNOWN_PREFIX + species, "???", zone + "  ·  not sampled",
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
            if (key.startsWith(Catalogue.MUTATION)) continue;
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

    private static final String HEADER = "Genetics/DetailHeader.ui";
    private static final String NOTE = "Genetics/DetailNote.ui";
    private static final List<String> SYRINGES = List.of("Crude Syringe", "Copper Syringe", "Thorium Syringe", "Adamantite Syringe");

    /** Appends rows, section titles and paragraphs to the detail list, keeping the child index. */
    private static final class Detail {
        private final UICommandBuilder c;
        private int n;

        Detail(@Nonnull UICommandBuilder c) {
            this.c = c;
        }

        void row(@Nonnull String type, @Nonnull String name, @Nonnull String value) {
            detailRow(c, "#DetailGenes[" + n++ + "]", type, name, value);
        }

        void header(@Nonnull String text) {
            c.append("#DetailGenes", HEADER);
            c.set("#DetailGenes[" + n++ + "] #Text.Text", text);
        }

        void note(@Nonnull String text) {
            if (text.isBlank()) return;
            c.append("#DetailGenes", NOTE);
            c.set("#DetailGenes[" + n++ + "] #Text.Text", text);
        }
    }

    private static void text(@Nonnull UICommandBuilder c, @Nonnull String text) {
        c.set("#DetailText.Visible", !text.isBlank());
        c.set("#DetailText.Text", text);
    }

    private static void creatureDetail(@Nonnull UICommandBuilder c, @Nonnull GeneJournal journal, @Nonnull Entry entry, @Nullable String language) {
        Balance.Science science = Balance.get().science();
        Detail d = new Detail(c);
        if (!entry.known()) {
            MobGeneProfile profile = Catalogue.profileOf(entry.id().substring(UNKNOWN_PREFIX.length()));
            c.set("#DetailName.Text", "???");
            c.set("#DetailMeta.Text", (profile != null && profile.getZone() != null ? profile.getZone() : "Unknown zone") + "  ·  not sampled yet");
            c.set("#DetailGenesHeader.Text", "Genes  ·  ? / " + Catalogue.possibleGenes(profile).size());
            if (profile != null) sampling(d, profile, language, false);
            c.set("#DetailMissing.Text", "Sample this creature with a syringe to discover it.");
            c.set("#DetailScience.Text", "First sample: +" + science.getFirstSample() + " Science");
            return;
        }
        String id = entry.id();
        MobGeneProfile profile = Catalogue.profileOf(id);
        GeneJournal.Species species = journal.getSpecies().get(id);
        Set<String> possible = Catalogue.possibleGenes(profile);
        Set<String> found = journal.genesOf(id);
        c.set("#DetailName.Text", entry.name());
        c.set("#DetailMeta.Text", (profile != null && profile.getZone() != null ? profile.getZone() : "Unknown zone")
                + "  ·  sampled " + (species != null ? species.getSamples() : 0) + " times");
        c.set("#DetailGenesHeader.Text", "Genes  ·  " + found.size() + " / " + possible.size());
        for (String key : found) {
            if (!key.startsWith(Catalogue.MUTATION)) d.row(Catalogue.type(key), Catalogue.name(key, language), Catalogue.range(profile, key));
        }
        if (profile != null) {
            d.header("Gene family  ·  " + Catalogue.speciesName(profile.getId(), language, profile.getId()));
            sampling(d, profile, language, true);
            List<String> themed = new ArrayList<>();
            for (MutationEntry mutation : MutationEntry.getAssetMap().getAssetMap().values()) {
                if (!List.of(mutation.getSpecies()).contains(profile.getId())) continue;
                themed.add(journal.getMutations().contains(mutation.getId()) ? GeneText.mutationName(mutation.getId(), language) : "??? (" + mutation.getMinTier() + ")");
            }
            if (!themed.isEmpty()) {
                d.header("Themed mutations");
                d.note(String.join(", ", themed) + ". Splicing this species' genes makes them likelier.");
            }
        }
        int missing = (int) possible.stream().filter(k -> !found.contains(k)).count();
        c.set("#DetailMissing.Text", missing == 0 ? "Every gene found." : missing + (missing == 1 ? " gene" : " genes") + " not found yet. Process more samples of it.");
        int earned = science.getFirstSample() + (int) found.stream().filter(k -> !k.startsWith(Catalogue.MUTATION)).count() * science.getNewGene()
                + (journal.isCompleted(id) ? science.getSpeciesComplete() : 0);
        c.set("#DetailScience.Text", "Science earned: " + earned + (journal.isCompleted(id) ? "  ·  complete" : "  ·  +" + science.getSpeciesComplete() + " more when every gene is found"));
    }

    /** How to sample a species: the syringe, and its signature mutation if it carries one. */
    private static void sampling(@Nonnull Detail d, @Nonnull MobGeneProfile profile, @Nullable String language, boolean known) {
        d.header("Sampling");
        int tier = profile.getSyringeTier();
        String syringe = tier > 0 ? SYRINGES.get(Math.min(tier, SYRINGES.size()) - 1) : "syringe strong enough for its health";
        String text = "Needs a " + syringe + (tier > 0 && tier < SYRINGES.size() ? " or better." : ".");
        if (profile.getSignatureMutation() != null) {
            String mutation = known ? GeneText.mutationName(profile.getSignatureMutation(), language) : "a signature mutation";
            text += " Carries " + mutation + " on " + Math.round(profile.getSignatureChance() * 100)
                    + "% of samples. Each one can only be sampled once.";
        }
        d.note(text);
    }

    private static void geneDetail(@Nonnull UICommandBuilder c, @Nonnull GeneJournal journal, @Nonnull com.genetics.library.GeneLibrary library,
                                   @Nonnull Entry entry, @Nullable String language, boolean buff) {
        String key = entry.id();
        Detail d = new Detail(c);
        c.set("#DetailName.Text", entry.name());
        if (!entry.known()) {
            c.set("#DetailMeta.Text", entry.meta());
            c.set("#DetailGenesHeader.Text", "");
            c.set("#DetailMissing.Text", "Process samples of more species to find it.");
            c.set("#DetailScience.Text", buff ? "New buff: +" + Balance.get().science().getNewBuff() + " Science" : "");
            return;
        }
        boolean drawback = entry.type().equals("Drawback");
        if (buff) {
            String effect = key.substring(Catalogue.EFFECT.length());
            double temporary = Codex.effectComplexity(effect, false);
            double permanent = Codex.effectComplexity(effect, true);
            c.set("#DetailMeta.Text", "Buff  ·  temporary +" + GeneText.number(temporary) + "  ·  permanent +" + GeneText.number(permanent) + " Complexity");
            text(c, effectDescription(effect, language));
            carriers(d, c, journal, key, entry.type());
            d.header("Complexity");
            double reference = Balance.get().splicing().getEffectReferenceDuration();
            d.note("Temporary: +" + GeneText.number(temporary) + " for up to " + GeneText.duration(reference)
                    + ", then more in proportion (a timer twice as long costs twice as much). Timers cap at "
                    + GeneText.duration(Balance.get().splicing().getMaxEffectDuration()) + ".");
            d.note("Permanent: +" + GeneText.number(permanent) + ". Splice samples until the timer reaches "
                    + GeneText.duration(Balance.get().permanence().getMinDuration()) + ", then tick Make permanent when processing (costs a Gene Stabilizer).");
            libraryNote(d, library, key, language);
        } else {
            String stat = key.substring(key.indexOf(':') + 1);
            double weight = Codex.statWeight(stat);
            String statName = "max " + GeneText.prettify(stat);
            c.set("#DetailMeta.Text", drawback ? "Drawback  ·  lowers " + statName : "Stat gene  ·  raises " + statName);
            text(c, drawback
                    ? "Lowers your " + statName + " by its percent, and makes the genome cheaper: a way to fit more power under your capacity."
                    : "Raises your " + statName + " by its percent. The same stat from two samples adds up when spliced.");
            carriers(d, c, journal, key, entry.type());
            d.header("Complexity");
            double drawbackWeight = weight * com.genetics.genome.ComplexityCalculator.DRAWBACK_WEIGHT;
            d.note(drawback
                    ? "-" + GeneText.number(drawbackWeight) + " per % (" + Math.round(com.genetics.genome.ComplexityCalculator.DRAWBACK_WEIGHT * 100)
                    + "% of the bonus cost). Drawbacks lower a genome's Complexity by at most "
                    + Math.round(com.genetics.genome.ComplexityCalculator.DRAWBACK_CAP * 100) + "%."
                    : "+" + GeneText.number(weight) + " per %: a +10% gene costs " + GeneText.number(weight * 10) + ".");
        }
        int more = possibleCarriers(key) - carrierIds(journal, key).size();
        c.set("#DetailMissing.Text", more > 0 ? more + " more species can carry it." : "");
        c.set("#DetailScience.Text", "");
    }

    /** "Found on" rows: the sampled species a gene or buff was found on, with their range. */
    private static void carriers(@Nonnull Detail d, @Nonnull UICommandBuilder c, @Nonnull GeneJournal journal, @Nonnull String key, @Nonnull String type) {
        List<String> carriers = carrierIds(journal, key);
        c.set("#DetailGenesHeader.Text", "Found on  ·  " + carriers.size());
        for (String id : carriers) d.row(type, speciesName(journal, id, null), Catalogue.range(Catalogue.profileOf(id), key));
    }

    private static void libraryNote(@Nonnull Detail d, @Nonnull com.genetics.library.GeneLibrary library, @Nonnull String key, @Nullable String language) {
        List<String> held = new ArrayList<>();
        for (com.genetics.library.GeneLibrary.Entry e : library.getEntries()) {
            if (e.getGene() != null && Catalogue.key(e.getGene()).equals(key)) held.add(GeneText.describe(e.getGene(), language));
        }
        d.header("Your Genetic Library");
        d.note(held.isEmpty() ? "Not extracted yet." : String.join(", ", held));
    }

    /** What an effect does, from its Effect Codex name key with ".desc" in place of ".name". */
    @Nonnull
    private static String effectDescription(@Nonnull String effect, @Nullable String language) {
        com.genetics.codex.EffectEntry entry = com.genetics.codex.EffectEntry.getAssetMap().getAsset(effect);
        String nameKey = entry != null ? entry.getName() : null;
        if (nameKey == null || !nameKey.endsWith(".name")) return "";
        return com.genetics.genome.Translations.resolve(language, nameKey.substring(0, nameKey.length() - ".name".length()) + ".desc", "");
    }

    private static void mutationDetail(@Nonnull UICommandBuilder c, @Nonnull GeneJournal journal, @Nonnull Entry entry, @Nullable String language) {
        MutationEntry mutation = MutationEntry.getAssetMap().getAsset(entry.id().substring(Catalogue.MUTATION.length()));
        Detail d = new Detail(c);
        c.set("#DetailName.Text", entry.name());
        c.set("#DetailMeta.Text", entry.meta());
        c.set("#DetailMissing.Text", "");
        boolean known = entry.known() && mutation != null;
        if (!known) {
            c.set("#DetailGenesHeader.Text", "Undiscovered");
            text(c, "Mutations appear when splicing: those themed to the spliced species are likelier. Some bosses carry one too.");
            c.set("#DetailScience.Text", "New mutation: +" + Balance.get().science().getNewMutation() + " Science");
        } else {
            List<String> parts = new ArrayList<>();
            if (mutation.getDescription() != null) parts.add(com.genetics.genome.Translations.resolve(language, mutation.getDescription(), ""));
            c.set("#DetailGenesHeader.Text", "What it does");
            text(c, String.join("\n", parts));
            d.header("Triggers");
            for (com.genetics.mutation.Behavior behavior : mutation.getBehaviors()) {
                String what = behavior.describe();
                String cooldown = behavior.getCooldown() >= 1 ? GeneText.duration(behavior.getCooldown()) + " cooldown" : "";
                d.row("Mutation", Character.toUpperCase(what.charAt(0)) + what.substring(1), cooldown);
            }
            c.set("#DetailScience.Text", "");
        }
        if (mutation == null) return;
        List<String> themed = new ArrayList<>();
        for (String species : mutation.getSpecies()) themed.add(Catalogue.speciesName(species, language, GeneText.prettify(species)));
        if (!themed.isEmpty()) {
            d.header("Themed to");
            d.note(String.join(", ", themed) + ". Splicing their genes makes it " + GeneText.number(Balance.get().splicing().getThemedMutationWeight()) + "x likelier.");
        }
        List<String> bosses = new ArrayList<>();
        for (MobGeneProfile profile : MobGeneProfile.getAssetMap().getAssetMap().values()) {
            if (mutation.getId().equals(profile.getSignatureMutation())) {
                bosses.add(Catalogue.speciesName(profile.getId(), language, profile.getId()) + " (" + Math.round(profile.getSignatureChance() * 100) + "% of samples)");
            }
        }
        if (!bosses.isEmpty()) {
            d.header("Carried by");
            d.note(String.join(", ", bosses) + ". Each one can only be sampled once.");
        }
        if (known) {
            d.header("Cost");
            d.note("Complexity +" + GeneText.number(mutation.getComplexity()) + ". Rolled only by " + mutation.getMinTier()
                    + " splices or above. In the Genetic Library, each extra mutation multiplies the cost by "
                    + GeneText.number(Balance.get().library().getMutationCostBase()) + ".");
        }
    }

    private static void detailRow(@Nonnull UICommandBuilder c, @Nonnull String row, @Nonnull String type, @Nonnull String name, @Nonnull String value) {
        c.append("#DetailGenes", Widgets.DETAIL_ROW);
        Widgets.typeIcon(c, row, type);
        c.set(row + " #Name.Text", name);
        c.set(row + " #Value.Text", value);
        c.set(row + " #Value.Style.TextColor", Widgets.valueColor(type));
    }

    /** A sampled species' name, from its profile (so it follows the language), else the name saved when sampled. */
    @Nonnull
    private static String speciesName(@Nonnull GeneJournal journal, @Nonnull String id, @Nullable String language) {
        GeneJournal.Species species = journal.getSpecies().get(id);
        return Catalogue.speciesName(id, language, species != null && species.getName() != null ? species.getName() : id);
    }

    // ---- Lookups

    /** Names of the sampled species a key was found on. */
    @Nonnull
    private static List<String> carriers(@Nonnull GeneJournal journal, @Nonnull String key) {
        List<String> names = new ArrayList<>();
        for (String id : carrierIds(journal, key)) names.add(speciesName(journal, id, null));
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
        for (String species : Catalogue.allSpecies()) if (Catalogue.possibleGenes(species).contains(key)) n++;
        return n;
    }

    /** Overall completion: species, genes, buffs and mutations found against what exists. */
    private static int overall(@Nonnull GeneJournal journal) {
        List<Entry> all = new ArrayList<>();
        all.addAll(creatureEntries(journal, null));
        all.addAll(geneEntries(journal, null, false));
        all.addAll(geneEntries(journal, null, true));
        all.addAll(mutationEntries(journal, null));
        if (all.isEmpty()) return 0;
        return (int) Math.round(100.0 * all.stream().filter(Entry::known).count() / all.size());
    }
}
