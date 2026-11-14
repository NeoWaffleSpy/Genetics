package com.genetics.journal;

import com.genetics.genome.Gene;
import com.genetics.genome.Genome;
import com.hypixel.hytale.codec.Codec;
import com.hypixel.hytale.codec.KeyedCodec;
import com.hypixel.hytale.codec.builder.BuilderCodec;
import com.hypixel.hytale.codec.codecs.map.MapCodec;
import com.hypixel.hytale.component.Component;
import com.hypixel.hytale.component.ComponentType;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

/**
 * The player's Catalogue (design doc §9.4; saved under its old name, the Gene Journal): every species sampled, the
 * genes found on each species, every buff and mutation found, the player's Science and the Research nodes they
 * bought (§9.5). Saved with the player.
 */
public class GeneJournal implements Component<EntityStore> {
    public static final String ID = "Genetics_GeneJournal";

    /** One sampled species. */
    public static final class Species {
        public static final BuilderCodec<Species> CODEC = BuilderCodec.builder(Species.class, Species::new)
                .append(new KeyedCodec<>("Name", Codec.STRING), (t, v) -> t.name = v, t -> t.name).add()
                .append(new KeyedCodec<>("Icon", Codec.STRING), (t, v) -> t.icon = v, t -> t.icon).add()
                .append(new KeyedCodec<>("FirstSampled", Codec.LONG), (t, v) -> t.firstSampled = v, t -> t.firstSampled).add()
                .append(new KeyedCodec<>("Samples", Codec.INTEGER), (t, v) -> t.samples = v, t -> t.samples).add()
                .build();

        private String name;
        private String icon;
        private long firstSampled;
        private int samples;

        public String getName() { return name; }
        @Nullable public String getIcon() { return icon; }
        public long getFirstSampled() { return firstSampled; }
        public int getSamples() { return samples; }
    }

    public static final BuilderCodec<GeneJournal> CODEC = BuilderCodec.builder(GeneJournal.class, GeneJournal::new)
            .append(new KeyedCodec<>("Species", new MapCodec<>(Species.CODEC, HashMap::new)),
                    (t, v) -> t.species = v != null ? new HashMap<>(v) : new HashMap<>(), t -> t.species).add()
            .append(new KeyedCodec<>("Genes", Codec.STRING_ARRAY), (t, v) -> t.genes = set(v), t -> t.genes.toArray(String[]::new)).add()
            .append(new KeyedCodec<>("Milestones", Codec.STRING_ARRAY), (t, v) -> t.milestones = set(v), t -> t.milestones.toArray(String[]::new)).add()
            .append(new KeyedCodec<>("SpeciesGenes", new MapCodec<>(Codec.STRING_ARRAY, HashMap::new)),
                    (t, v) -> t.speciesGenes = fromArrays(v), t -> toArrays(t.speciesGenes)).add()
            .append(new KeyedCodec<>("Buffs", Codec.STRING_ARRAY), (t, v) -> t.buffs = set(v), t -> t.buffs.toArray(String[]::new)).add()
            .append(new KeyedCodec<>("Mutations", Codec.STRING_ARRAY), (t, v) -> t.mutations = set(v), t -> t.mutations.toArray(String[]::new)).add()
            .append(new KeyedCodec<>("Completed", Codec.STRING_ARRAY), (t, v) -> t.completed = set(v), t -> t.completed.toArray(String[]::new)).add()
            .append(new KeyedCodec<>("Science", Codec.INTEGER), (t, v) -> t.science = v != null ? v : 0, t -> t.science).add()
            .append(new KeyedCodec<>("Researched", Codec.STRING_ARRAY), (t, v) -> t.researched = set(v), t -> t.researched.toArray(String[]::new)).add()
            .build();

    private static ComponentType<EntityStore, GeneJournal> componentType;

    private Map<String, Species> species = new HashMap<>();
    /** Old-format gene keys of every identified gene (kept for /genetics journal). */
    private Set<String> genes = new LinkedHashSet<>();
    private Set<String> milestones = new LinkedHashSet<>();
    /** Species id → Catalogue keys of the genes found on that species ({@link Catalogue#key}). */
    private Map<String, Set<String>> speciesGenes = new HashMap<>();
    private Set<String> buffs = new LinkedHashSet<>();
    private Set<String> mutations = new LinkedHashSet<>();
    /** Species whose every gene was found (their completion Science was paid). */
    private Set<String> completed = new LinkedHashSet<>();
    private int science;
    private Set<String> researched = new LinkedHashSet<>();
    /** Set once {@link #migrateSpecies} has run for this loaded component (not saved). */
    private boolean migrated;

    public static void setComponentType(@Nonnull ComponentType<EntityStore, GeneJournal> type) {
        componentType = type;
    }

    public static ComponentType<EntityStore, GeneJournal> getComponentType() {
        return componentType;
    }

    /** Records a sample of a species (a gene profile id). Returns true if it is the first sample of that species. */
    public boolean recordSample(@Nonnull String speciesId, @Nonnull String name, @Nonnull Genome.Source source) {
        Species entry = species.get(speciesId);
        boolean first = entry == null;
        if (first) {
            entry = new Species();
            entry.name = name;
            entry.icon = source.icon();
            entry.firstSampled = source.sampledAt();
            species.put(speciesId, entry);
        }
        entry.samples++;
        return first;
    }

    /**
     * Moves species saved under an NPC role (before species were grouped by gene profile) to their species id,
     * merging samples, genes and completion. {@code speciesOf} maps a saved key to its species (null: leave it).
     * Returns true if anything moved.
     */
    public boolean migrateSpecies(@Nonnull java.util.function.UnaryOperator<String> speciesOf,
                                  @Nonnull java.util.function.BinaryOperator<String> nameOf) {
        if (migrated) return false;
        migrated = true;
        boolean changed = false;
        for (Set<String> found : speciesGenes.values()) changed |= found.removeIf(k -> k.startsWith("Mutation:"));
        for (String key : new java.util.ArrayList<>(species.keySet())) {
            String target = speciesOf.apply(key);
            if (target == null) {
                // Sampled before every creature had a profile (e.g. copies of a player): not a species any more.
                species.remove(key);
                speciesGenes.remove(key);
                completed.remove(key);
                changed = true;
                continue;
            }
            if (target.equals(key)) continue;
            Species old = species.remove(key);
            Species into = species.get(target);
            if (into == null) {
                old.name = nameOf.apply(target, old.name);
                species.put(target, old);
            } else {
                into.samples += old.samples;
                into.firstSampled = Math.min(into.firstSampled, old.firstSampled);
            }
            Set<String> genesOld = speciesGenes.remove(key);
            if (genesOld != null) speciesGenes.computeIfAbsent(target, k -> new LinkedHashSet<>()).addAll(genesOld);
            // Completion is re-earned against the group's (larger) gene list.
            completed.remove(key);
            changed = true;
        }
        return changed;
    }

    /** Records the genes of an identified genome in the old-format list. Returns how many were new. */
    public int recordGenes(@Nonnull Genome genome) {
        int added = 0;
        for (Gene gene : genome.genes()) if (genes.add(key(gene))) added++;
        return added;
    }

    /** Adds a Catalogue key to a species. Returns true if it was new for that species. */
    public boolean addSpeciesGene(@Nonnull String speciesId, @Nonnull String catalogueKey) {
        return speciesGenes.computeIfAbsent(speciesId, k -> new LinkedHashSet<>()).add(catalogueKey);
    }

    public boolean addBuff(@Nonnull String effectId) {
        return buffs.add(effectId);
    }

    public boolean addMutation(@Nonnull String mutationId) {
        return mutations.add(mutationId);
    }

    public boolean markCompleted(@Nonnull String speciesId) {
        return completed.add(speciesId);
    }

    public boolean isCompleted(@Nonnull String speciesId) {
        return completed.contains(speciesId);
    }

    /** Marks a milestone as reached. Returns false if it already was. */
    public boolean reach(@Nonnull String milestone) {
        return milestones.add(milestone);
    }

    public boolean hasSampled(@Nonnull String mobId) {
        return species.containsKey(mobId);
    }

    @Nonnull
    public Map<String, Species> getSpecies() {
        return Collections.unmodifiableMap(species);
    }

    @Nonnull
    public Set<String> genesOf(@Nonnull String speciesId) {
        Set<String> found = speciesGenes.get(speciesId);
        return found != null ? Collections.unmodifiableSet(found) : Set.of();
    }

    @Nonnull
    public Map<String, Set<String>> getSpeciesGenes() {
        return Collections.unmodifiableMap(speciesGenes);
    }

    @Nonnull
    public Set<String> getBuffs() {
        return Collections.unmodifiableSet(buffs);
    }

    @Nonnull
    public Set<String> getMutations() {
        return Collections.unmodifiableSet(mutations);
    }

    public int speciesCount() {
        return species.size();
    }

    public int geneCount() {
        return genes.size();
    }

    public int getScience() {
        return science;
    }

    public void addScience(int amount) {
        science = Math.max(0, science + amount);
    }

    /** Spends Science. Returns false (and spends nothing) if there isn't enough. */
    public boolean spendScience(int amount) {
        if (amount > science) return false;
        science -= amount;
        return true;
    }

    public boolean isResearched(@Nonnull String nodeId) {
        return researched.contains(nodeId);
    }

    public boolean markResearched(@Nonnull String nodeId) {
        return researched.add(nodeId);
    }

    public void resetResearch() {
        researched.clear();
    }

    /** Forgets every discovery (species, genes, buffs, mutations, completions). Science and research are kept. */
    public void clearDiscoveries() {
        species.clear();
        genes.clear();
        speciesGenes.clear();
        buffs.clear();
        mutations.clear();
        completed.clear();
    }

    @Nonnull
    public Set<String> getResearched() {
        return Collections.unmodifiableSet(researched);
    }

    @Nonnull
    private static String key(@Nonnull Gene gene) {
        return switch (gene) {
            case Gene.Stat stat -> "Stat:" + stat.stat() + (stat.isDrawback() ? "-" : "+");
            case Gene.Effect effect -> "Effect:" + effect.effect() + (effect.permanent() ? ":Permanent" : "");
            case Gene.Mutation mutation -> "Mutation:" + mutation.mutation();
        };
    }

    @Nonnull
    private static Set<String> set(@Nullable String[] values) {
        return values != null ? new LinkedHashSet<>(Arrays.asList(values)) : new LinkedHashSet<>();
    }

    @Nonnull
    private static Map<String, Set<String>> fromArrays(@Nullable Map<String, String[]> values) {
        Map<String, Set<String>> out = new HashMap<>();
        if (values != null) values.forEach((k, v) -> out.put(k, set(v)));
        return out;
    }

    @Nonnull
    private static Map<String, String[]> toArrays(@Nonnull Map<String, Set<String>> values) {
        Map<String, String[]> out = new HashMap<>();
        values.forEach((k, v) -> out.put(k, v.toArray(String[]::new)));
        return out;
    }

    @Nonnull
    @Override
    public Component<EntityStore> clone() {
        GeneJournal copy = new GeneJournal();
        copy.species = new HashMap<>(species);
        copy.genes = new LinkedHashSet<>(genes);
        copy.milestones = new LinkedHashSet<>(milestones);
        copy.speciesGenes = new HashMap<>();
        speciesGenes.forEach((k, v) -> copy.speciesGenes.put(k, new LinkedHashSet<>(v)));
        copy.buffs = new LinkedHashSet<>(buffs);
        copy.mutations = new LinkedHashSet<>(mutations);
        copy.completed = new LinkedHashSet<>(completed);
        copy.science = science;
        copy.researched = new LinkedHashSet<>(researched);
        return copy;
    }
}
