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
 * The Gene Journal (design doc §9.4): a per-player log of every species sampled and every gene identified, plus the
 * milestones already reached. Saved with the player.
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
            .append(new KeyedCodec<>("Species", new MapCodec<>(Species.CODEC, HashMap::new)), (t, v) -> t.species = v != null ? new HashMap<>(v) : new HashMap<>(), t -> t.species).add()
            .append(new KeyedCodec<>("Genes", Codec.STRING_ARRAY), (t, v) -> t.genes = new LinkedHashSet<>(Arrays.asList(v)), t -> t.genes.toArray(String[]::new)).add()
            .append(new KeyedCodec<>("Milestones", Codec.STRING_ARRAY), (t, v) -> t.milestones = new LinkedHashSet<>(Arrays.asList(v)), t -> t.milestones.toArray(String[]::new)).add()
            .build();

    private static ComponentType<EntityStore, GeneJournal> componentType;

    private Map<String, Species> species = new HashMap<>();
    private Set<String> genes = new LinkedHashSet<>();
    private Set<String> milestones = new LinkedHashSet<>();

    public static void setComponentType(@Nonnull ComponentType<EntityStore, GeneJournal> type) {
        componentType = type;
    }

    public static ComponentType<EntityStore, GeneJournal> getComponentType() {
        return componentType;
    }

    /** Records a sample of a species. Returns true if it is the first sample of that species. */
    public boolean recordSample(@Nonnull Genome.Source source) {
        Species entry = species.get(source.mobId());
        boolean first = entry == null;
        if (first) {
            entry = new Species();
            entry.name = source.mobName();
            entry.icon = source.icon();
            entry.firstSampled = source.sampledAt();
            species.put(source.mobId(), entry);
        }
        entry.samples++;
        return first;
    }

    /** Records the genes of an identified genome. Returns how many were new. */
    public int recordGenes(@Nonnull Genome genome) {
        int added = 0;
        for (Gene gene : genome.genes()) if (genes.add(key(gene))) added++;
        return added;
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

    public int speciesCount() {
        return species.size();
    }

    public int geneCount() {
        return genes.size();
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
    @Override
    public Component<EntityStore> clone() {
        GeneJournal copy = new GeneJournal();
        copy.species = new HashMap<>(species);
        copy.genes = new LinkedHashSet<>(genes);
        copy.milestones = new LinkedHashSet<>(milestones);
        return copy;
    }
}
