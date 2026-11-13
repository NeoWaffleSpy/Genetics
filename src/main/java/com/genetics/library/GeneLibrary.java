package com.genetics.library;

import com.genetics.genome.Gene;
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
import java.util.List;
import java.util.UUID;

/**
 * The player's Genetic Library (design doc §8.1): genes extracted from genetic material, each with its exact value
 * and refine level. Entries are knowledge, not items: they can be used in any number of combinations, so the same
 * stat extracted twice gives two entries (which is what stat fusion needs). Saved with the player.
 */
public class GeneLibrary implements Component<EntityStore> {
    public static final String ID = "Genetics_GeneLibrary";

    /** One extracted gene: its base value (before refining), where it came from, and how many times it was refined. */
    public static final class Entry {
        public static final BuilderCodec<Entry> CODEC = BuilderCodec.builder(Entry.class, Entry::new)
                .append(new KeyedCodec<>("Id", Codec.STRING), (t, v) -> t.id = v, t -> t.id).add()
                .append(new KeyedCodec<>("Gene", Codec.STRING), (t, v) -> t.geneJson = v, t -> t.geneJson).add()
                .append(new KeyedCodec<>("Source", Codec.STRING), (t, v) -> t.source = v, t -> t.source).add()
                .append(new KeyedCodec<>("Level", Codec.INTEGER), (t, v) -> t.level = v != null ? v : 0, t -> t.level).add()
                .build();

        private String id;
        private String geneJson;
        private String source;
        private int level;
        @Nullable
        private transient Gene cached;

        Entry() {
        }

        Entry(@Nonnull Gene gene, @Nonnull String source) {
            this.id = UUID.randomUUID().toString().substring(0, 8);
            this.geneJson = gene.toBson().toJson();
            this.source = source;
        }

        @Nonnull
        public String getId() {
            return id != null ? id : "";
        }

        /** The gene as extracted (before refining), or null if the saved data is unreadable. */
        @Nullable
        public Gene getBase() {
            if (cached == null && geneJson != null) {
                try {
                    cached = Gene.fromBson(BsonDocument.parse(geneJson));
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

        public int getLevel() {
            return level;
        }

        void refine() {
            level++;
        }

        Entry copy() {
            Entry e = new Entry();
            e.id = id;
            e.geneJson = geneJson;
            e.source = source;
            e.level = level;
            return e;
        }
    }

    public static final BuilderCodec<GeneLibrary> CODEC = BuilderCodec.builder(GeneLibrary.class, GeneLibrary::new)
            .append(new KeyedCodec<>("Entries", new ArrayCodec<>(Entry.CODEC, Entry[]::new)),
                    (t, v) -> t.entries = v != null ? new ArrayList<>(List.of(v)) : new ArrayList<>(), t -> t.entries.toArray(Entry[]::new)).add()
            .build();

    private static ComponentType<EntityStore, GeneLibrary> componentType;

    private List<Entry> entries = new ArrayList<>();

    public static void setComponentType(@Nonnull ComponentType<EntityStore, GeneLibrary> type) {
        componentType = type;
    }

    public static ComponentType<EntityStore, GeneLibrary> getComponentType() {
        return componentType;
    }

    /** Entries whose gene could be read, in extraction order. */
    @Nonnull
    public List<Entry> getEntries() {
        return Collections.unmodifiableList(entries.stream().filter(e -> e.getBase() != null).toList());
    }

    @Nullable
    public Entry get(@Nonnull String id) {
        for (Entry entry : entries) if (entry.getId().equals(id)) return entry;
        return null;
    }

    @Nonnull
    public Entry add(@Nonnull Gene gene, @Nonnull String source) {
        Entry entry = new Entry(gene, source);
        entries.add(entry);
        return entry;
    }

    /** Raises an entry's refine level by one. Returns false if there is no such entry. */
    public boolean refine(@Nonnull String id) {
        Entry entry = get(id);
        if (entry == null) return false;
        entry.refine();
        return true;
    }

    public boolean remove(@Nonnull String id) {
        return entries.removeIf(e -> e.getId().equals(id));
    }

    @Nonnull
    @Override
    public Component<EntityStore> clone() {
        GeneLibrary copy = new GeneLibrary();
        for (Entry entry : entries) copy.entries.add(entry.copy());
        return copy;
    }
}
