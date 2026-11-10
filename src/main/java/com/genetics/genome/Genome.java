package com.genetics.genome;

import com.hypixel.hytale.server.core.inventory.ItemStack;
import org.bson.BsonArray;
import org.bson.BsonBoolean;
import org.bson.BsonDocument;
import org.bson.BsonDouble;
import org.bson.BsonInt32;
import org.bson.BsonInt64;
import org.bson.BsonString;
import org.bson.BsonValue;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.List;

/**
 * A genome, as stored in a syringe's item metadata:
 * <pre>
 * "Genetics:Genome": {
 *   "Version": 1,
 *   "Processed": false,                  raw sample (false) or genetic material (true)
 *   "Identified": true,                  false until identified at the Lab Station (genes hidden)
 *   "Complexity": 9.6,
 *   "Source": { "MobId": "Cow", "MobName": "Cow", "Model": "Cow", "Icon": "Icons/…", "SampledAt": 1762700000000 },
 *   "Genes": [
 *     { "Type": "Stat",     "Stat": "Health", "Percent": 6.5 },
 *     { "Type": "Stat",     "Stat": "Stamina", "Percent": -4.0 },
 *     { "Type": "Effect",   "Effect": "HealthRegen_Buff_T1", "Permanent": false, "Duration": 180.0 },
 *     { "Type": "Mutation", "Mutation": "TitanBlood" }
 *   ]
 * }
 * </pre>
 * Complexity is stored rather than recomputed so that it stays what it was when the sample was made or processed.
 */
public record Genome(int version, boolean processed, boolean identified, double complexity, @Nullable Source source, @Nonnull List<Gene> genes) {
    /** Item metadata key the genome is stored under. */
    public static final String METADATA_KEY = "Genetics:Genome";
    /** Metadata key of the raw mob snapshots stored before genomes existed. */
    public static final String LEGACY_METADATA_KEY = "Genetics:Genes";
    /** Current format version. Bump it, and migrate in {@link #fromBson}, when the format changes. */
    public static final int FORMAT_VERSION = 1;

    /** Where a sample came from. Kept so the Lab Station and later systems (sickness events) can name the mob. */
    public record Source(@Nonnull String mobId, @Nonnull String mobName, @Nullable String model, @Nullable String icon, long sampledAt) {
        @Nonnull
        BsonDocument toBson() {
            BsonDocument doc = new BsonDocument()
                    .append("MobId", new BsonString(mobId))
                    .append("MobName", new BsonString(mobName));
            if (model != null) doc.append("Model", new BsonString(model));
            if (icon != null) doc.append("Icon", new BsonString(icon));
            return doc.append("SampledAt", new BsonInt64(sampledAt));
        }

        @Nullable
        static Source fromBson(@Nonnull BsonDocument doc) {
            if (!doc.isString("MobId")) return null;
            String mobId = doc.getString("MobId").getValue();
            return new Source(
                    mobId,
                    doc.isString("MobName") ? doc.getString("MobName").getValue() : mobId,
                    doc.isString("Model") ? doc.getString("Model").getValue() : null,
                    doc.isString("Icon") ? doc.getString("Icon").getValue() : null,
                    doc.isNumber("SampledAt") ? doc.getNumber("SampledAt").longValue() : 0);
        }
    }

    public Genome {
        genes = List.copyOf(genes);
    }

    /** A new, identified raw sample whose Complexity is computed over all its genes. */
    @Nonnull
    public static Genome raw(@Nullable Source source, @Nonnull List<Gene> genes) {
        return raw(source, genes, true);
    }

    /** A new raw sample whose Complexity is computed over all its genes. */
    @Nonnull
    public static Genome raw(@Nullable Source source, @Nonnull List<Gene> genes, boolean identified) {
        return new Genome(FORMAT_VERSION, false, identified, ComplexityCalculator.compute(genes), source, genes);
    }

    /** This genome, marked as identified. */
    @Nonnull
    public Genome asIdentified() {
        return new Genome(version, processed, true, complexity, source, genes);
    }

    @Nonnull
    public BsonDocument toBson() {
        BsonArray geneArray = new BsonArray();
        for (Gene gene : genes) geneArray.add(gene.toBson());
        BsonDocument doc = new BsonDocument()
                .append("Version", new BsonInt32(version))
                .append("Processed", new BsonBoolean(processed))
                .append("Identified", new BsonBoolean(identified))
                .append("Complexity", new BsonDouble(complexity));
        if (source != null) doc.append("Source", source.toBson());
        return doc.append("Genes", geneArray);
    }

    /** Reads a genome. Invalid genes are skipped; returns {@code null} for a newer, unknown format. */
    @Nullable
    public static Genome fromBson(@Nonnull BsonDocument doc) {
        int version = doc.isNumber("Version") ? doc.getNumber("Version").intValue() : FORMAT_VERSION;
        if (version > FORMAT_VERSION) return null;

        List<Gene> genes = new ArrayList<>();
        if (doc.isArray("Genes")) {
            for (BsonValue value : doc.getArray("Genes")) {
                Gene gene = value.isDocument() ? Gene.fromBson(value.asDocument()) : null;
                if (gene != null) genes.add(gene);
            }
        }
        return new Genome(
                FORMAT_VERSION,
                doc.isBoolean("Processed") && doc.getBoolean("Processed").getValue(),
                !doc.isBoolean("Identified") || doc.getBoolean("Identified").getValue(),
                doc.isNumber("Complexity") ? doc.getNumber("Complexity").doubleValue() : ComplexityCalculator.compute(genes),
                doc.isDocument("Source") ? Source.fromBson(doc.getDocument("Source")) : null,
                genes);
    }

    /** The genome stored on a stack, or {@code null} if it holds none. */
    @Nullable
    public static Genome read(@Nullable ItemStack stack) {
        BsonDocument metadata = stack != null ? stack.getMetadata() : null;
        if (metadata == null || !metadata.isDocument(METADATA_KEY)) return null;
        return fromBson(metadata.getDocument(METADATA_KEY));
    }

    /** A copy of the stack holding this genome (and no legacy snapshot). */
    @Nonnull
    public ItemStack writeTo(@Nonnull ItemStack stack) {
        return stack.withMetadata(LEGACY_METADATA_KEY, (BsonValue) null).withMetadata(METADATA_KEY, toBson());
    }

    /** True if the stack holds a genome. */
    public static boolean isPresentOn(@Nullable ItemStack stack) {
        BsonDocument metadata = stack != null ? stack.getMetadata() : null;
        return metadata != null && metadata.isDocument(METADATA_KEY);
    }
}
