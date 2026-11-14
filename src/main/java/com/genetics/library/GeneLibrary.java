package com.genetics.library;

import com.genetics.genome.Gene;
import com.genetics.journal.Catalogue;
import com.hypixel.hytale.codec.Codec;
import com.hypixel.hytale.codec.KeyedCodec;
import com.hypixel.hytale.codec.builder.BuilderCodec;
import com.hypixel.hytale.codec.codecs.array.ArrayCodec;
import com.hypixel.hytale.component.Component;
import com.hypixel.hytale.component.ComponentType;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import org.bson.BsonDocument;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * The player's Genetic Library (design doc §8.1): one entry per gene (a stat, a drawback, an effect in its temporary
 * or permanent form, or a mutation), at the best value extracted so far. Entries are knowledge, not items: they can
 * be used in any number of combinations, at any amount up to that best value. Saved with the player.
 */
public class GeneLibrary implements Component<EntityStore> {
    public static final String ID = "Genetics_GeneLibrary";

    /** One extracted gene at its best value, and where that value came from. */
    public static final class Entry {
        public static final BuilderCodec<Entry> CODEC = BuilderCodec.builder(Entry.class, Entry::new)
                .append(new KeyedCodec<>("Id", Codec.STRING), (t, v) -> t.id = v, t -> t.id).add()
                .append(new KeyedCodec<>("Gene", Codec.STRING), (t, v) -> t.geneJson = v, t -> t.geneJson).add()
                .append(new KeyedCodec<>("Source", Codec.STRING), (t, v) -> t.source = v, t -> t.source).add()
                // Legacy: refine levels from the first Library version, baked into the gene when loaded.
                .append(new KeyedCodec<>("Level", Codec.INTEGER), (t, v) -> t.legacyLevel = v != null ? v : 0, t -> 0).add()
                .build();

        private String id;
        private String geneJson;
        private String source;
        private int legacyLevel;
        @Nullable
        private transient Gene cached;

        Entry() {
        }

        Entry(@Nonnull Gene gene, @Nonnull String source) {
            this.id = UUID.randomUUID().toString().substring(0, 8);
            set(gene, source);
        }

        private void set(@Nonnull Gene gene, @Nonnull String source) {
            this.geneJson = gene.toBson().toJson();
            this.source = source;
            this.legacyLevel = 0;
            this.cached = gene;
        }

        @Nonnull
        public String getId() {
            return id != null ? id : "";
        }

        /** The gene at its best value, or null if the saved data is unreadable. */
        @Nullable
        public Gene getGene() {
            if (cached == null && geneJson != null) {
                try {
                    cached = bake(Gene.fromBson(BsonDocument.parse(geneJson)), legacyLevel);
                } catch (RuntimeException e) {
                    cached = null;
                }
            }
            return cached;
        }

        @Nonnull
        public String getSource() {
            return source != null ? source : "Unknown";
        }

        Entry copy() {
            Entry e = new Entry();
            e.id = id;
            e.geneJson = geneJson;
            e.source = source;
            e.legacyLevel = legacyLevel;
            return e;
        }

        /** Applies an old refine level (+25% of the base value per level). */
        @Nullable
        private static Gene bake(@Nullable Gene gene, int level) {
            if (gene == null || level <= 0) return gene;
            double factor = 1 + 0.25 * level;
            return switch (gene) {
                case Gene.Stat stat -> new Gene.Stat(stat.stat(), Math.round(stat.percent() * factor * 10) / 10.0, stat.cost() * factor);
                case Gene.Effect effect -> effect.permanent() ? effect : new Gene.Effect(effect.effect(), false, Math.round(effect.duration() * factor));
                case Gene.Mutation mutation -> mutation;
            };
        }
    }

    public static final BuilderCodec<GeneLibrary> CODEC = BuilderCodec.builder(GeneLibrary.class, GeneLibrary::new)
            .append(new KeyedCodec<>("Entries", new ArrayCodec<>(Entry.CODEC, Entry[]::new)),
                    (t, v) -> t.entries = v != null ? new ArrayList<>(List.of(v)) : new ArrayList<>(), t -> t.entries.toArray(Entry[]::new)).add()
            .build();

    private static ComponentType<EntityStore, GeneLibrary> componentType;

    private List<Entry> entries = new ArrayList<>();
    private boolean normalized;

    public static void setComponentType(@Nonnull ComponentType<EntityStore, GeneLibrary> type) {
        componentType = type;
    }

    public static ComponentType<EntityStore, GeneLibrary> getComponentType() {
        return componentType;
    }

    /** What makes two genes the same library entry: the Catalogue key, plus the permanent form for effects. */
    @Nonnull
    public static String key(@Nonnull Gene gene) {
        return Catalogue.key(gene) + (gene instanceof Gene.Effect effect && effect.permanent() ? ":Permanent" : "");
    }

    /** How strong a gene is, to keep the best: |percent| for stats, duration for temporary effects. */
    public static double strength(@Nonnull Gene gene) {
        return switch (gene) {
            case Gene.Stat stat -> Math.abs(stat.percent());
            case Gene.Effect effect -> effect.permanent() ? 1 : effect.duration();
            case Gene.Mutation ignored -> 1;
        };
    }

    /** Entries sorted by type (stats, drawbacks, effects, mutations), then by name. */
    @Nonnull
    public List<Entry> getEntries() {
        normalize();
        List<Entry> out = new ArrayList<>(entries.stream().filter(e -> e.getGene() != null).toList());
        List<String> order = List.of("Stat", "Drawback", "Effect", "Mutation");
        out.sort(Comparator.comparingInt((Entry e) -> order.indexOf(Catalogue.type(Catalogue.key(e.getGene()))))
                .thenComparing(e -> Catalogue.name(Catalogue.key(e.getGene()), null))
                .thenComparing(e -> e.getGene() instanceof Gene.Effect effect && effect.permanent()));
        return Collections.unmodifiableList(out);
    }

    @Nullable
    public Entry get(@Nonnull String id) {
        for (Entry entry : entries) if (entry.getId().equals(id)) return entry;
        return null;
    }

    /** The entry for this gene's key, or null. */
    @Nullable
    public Entry find(@Nonnull Gene gene) {
        normalize();
        String key = key(gene);
        for (Entry entry : entries) if (entry.getGene() != null && key(entry.getGene()).equals(key)) return entry;
        return null;
    }

    /** True if extracting this gene would add or improve an entry. */
    public boolean wouldImprove(@Nonnull Gene gene) {
        Entry existing = find(gene);
        return existing == null || strength(gene) > strength(existing.getGene()) + 1e-9;
    }

    /** Adds the gene, or raises its entry to this value if it is better. Returns false if the library already had better. */
    public boolean add(@Nonnull Gene gene, @Nonnull String source) {
        Entry existing = find(gene);
        if (existing == null) {
            entries.add(new Entry(gene, source));
            return true;
        }
        if (!wouldImprove(gene)) return false;
        existing.set(gene, source);
        return true;
    }

    /** Removes every entry. Returns how many there were. */
    public int clear() {
        int n = entries.size();
        entries.clear();
        return n;
    }

    public boolean remove(@Nonnull String id) {
        return entries.removeIf(e -> e.getId().equals(id));
    }

    /** Merges duplicate entries from the first Library version, keeping the best of each gene. */
    private void normalize() {
        if (normalized) return;
        normalized = true;
        Map<String, Entry> best = new LinkedHashMap<>();
        for (Entry entry : entries) {
            Gene gene = entry.getGene();
            if (gene == null) continue;
            best.merge(key(gene), entry, (a, b) -> strength(b.getGene()) > strength(a.getGene()) ? b : a);
        }
        entries = new ArrayList<>(best.values());
    }

    @Nonnull
    @Override
    public Component<EntityStore> clone() {
        GeneLibrary copy = new GeneLibrary();
        for (Entry entry : entries) copy.entries.add(entry.copy());
        return copy;
    }
}
