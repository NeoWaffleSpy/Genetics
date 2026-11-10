package com.genetics.genes;

import com.hypixel.hytale.codec.Codec;
import com.hypixel.hytale.codec.KeyedCodec;
import com.hypixel.hytale.codec.builder.BuilderCodec;
import com.hypixel.hytale.codec.validation.Validators;
import com.hypixel.hytale.logger.HytaleLogger;
import com.hypixel.hytale.server.core.inventory.ItemStack;
import org.bson.BsonDocument;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.Arrays;
import java.util.List;

/**
 * The genome held in a syringe, stored by value in item metadata (GDD §2): a list of {@link Gene genes}, whether it is a
 * raw sample or processed genetic material, its Complexity, and where it came from. Nothing here references a live
 * entity or the data files, so the sample stays valid after the mob despawns or the Codices change.
 *
 * <pre>
 * "Genetics:Genes": {
 *   "Version": 1,
 *   "Processed": false,
 *   "Complexity": 0.0,
 *   "Source": { "Id": "Cow", "Name": "Cow", "Icon": "Icons/…", "SampledAt": 1762700000000 },
 *   "Genes": [ { "Type": "Stat", "Id": "Health", "Value": 6.0 }, … ]
 * }
 * </pre>
 *
 * <b>Versions.</b> {@code Version} is written by the codec ({@link #FORMAT_VERSION}). A sample written by a newer
 * version of the mod is left untouched: it still counts as present, so the syringe can't be overwritten, but it can't
 * be read. Version 0 is the old raw snapshot of the mob (stats and active effects, no {@code Version} key); it reads as
 * a raw sample that keeps the mob's identity and has no genes.
 */
public final class GeneSample {
    private static final HytaleLogger LOGGER = HytaleLogger.forEnclosingClass();

    /** Item metadata key the sample is stored under. */
    public static final String METADATA_KEY = "Genetics:Genes";
    /** Current format version. Bump it, and add a migration in {@link #read}, whenever the stored layout changes. */
    public static final int FORMAT_VERSION = 1;
    /** Complexity of a sample that hasn't been calculated yet. Calculated Complexity is never below 1 (GDD §3.1). */
    public static final double COMPLEXITY_UNKNOWN = 0.0;

    public static final BuilderCodec<GeneSample> CODEC = BuilderCodec.builder(GeneSample.class, GeneSample::new)
            .versioned()
            .codecVersion(FORMAT_VERSION)
            .append(new KeyedCodec<>("Processed", Codec.BOOLEAN), (s, v) -> s.processed = v, s -> s.processed)
            .documentation("False for a raw sample, true for genetic material.")
            .add()
            .append(new KeyedCodec<>("Complexity", Codec.DOUBLE), (s, v) -> s.complexity = v, s -> s.complexity)
            .documentation("Complexity score of the genome; 0 if not calculated yet.")
            .add()
            .append(new KeyedCodec<>("Source", SampleSource.CODEC), (s, v) -> s.source = v, s -> s.source)
            .documentation("The mob (or player) the genome came from.")
            .addValidator(Validators.nonNull())
            .add()
            .append(new KeyedCodec<>("Genes", Gene.ARRAY_CODEC), (s, v) -> s.genes = v, s -> s.genes)
            .documentation("The genome.")
            .add()
            .build();
    private static final KeyedCodec<GeneSample> KEYED_CODEC = new KeyedCodec<>(METADATA_KEY, CODEC);

    private boolean processed;
    private double complexity = COMPLEXITY_UNKNOWN;
    private SampleSource source;
    private Gene[] genes = new Gene[0];

    private GeneSample() {
    }

    public GeneSample(@Nonnull SampleSource source, @Nonnull List<Gene> genes, boolean processed, double complexity) {
        this.source = source;
        this.genes = genes.toArray(Gene[]::new);
        this.processed = processed;
        this.complexity = complexity;
    }

    /** A raw, unprocessed sample whose Complexity hasn't been calculated yet. */
    @Nonnull
    public static GeneSample raw(@Nonnull SampleSource source, @Nonnull List<Gene> genes) {
        return new GeneSample(source, genes, false, COMPLEXITY_UNKNOWN);
    }

    /** True if the stack holds a sample, readable or not. A syringe that holds one can't take another. */
    public static boolean isPresentOn(@Nullable ItemStack stack) {
        BsonDocument metadata = stack != null ? stack.getMetadata() : null;
        return metadata != null && metadata.containsKey(METADATA_KEY);
    }

    /** The sample stored on a stack, or {@code null} if it holds none or it can't be read. */
    @Nullable
    public static GeneSample read(@Nullable ItemStack stack) {
        BsonDocument metadata = stack != null ? stack.getMetadata() : null;
        if (metadata == null || !metadata.isDocument(METADATA_KEY)) return null;

        BsonDocument stored = metadata.getDocument(METADATA_KEY);
        if (!stored.containsKey("Version")) return fromLegacySnapshot(stored);
        try {
            return CODEC.decode(stored);
        } catch (RuntimeException e) {
            LOGGER.atWarning().log("Unreadable gene sample on " + stack.getItemId() + ": " + e.getMessage());
            return null;
        }
    }

    /** A copy of {@code stack} holding this sample. */
    @Nonnull
    public ItemStack writeTo(@Nonnull ItemStack stack) {
        return stack.withMetadata(KEYED_CODEC, this);
    }

    /** Version 0: the raw snapshot of the mob. Keeps who it came from; the snapshot's stats and effects aren't genes. */
    @Nonnull
    private static GeneSample fromLegacySnapshot(@Nonnull BsonDocument snapshot) {
        String mobId = string(snapshot, "MobId", "Unknown");
        BsonDocument model = snapshot.isDocument("Model") ? snapshot.getDocument("Model") : null;
        long sampledAt = snapshot.isNumber("SampledAt") ? snapshot.getNumber("SampledAt").longValue() : 0L;
        SampleSource source = new SampleSource(mobId, string(snapshot, "MobName", mobId), model != null ? string(model, "Icon", null) : null, sampledAt);
        return raw(source, List.of());
    }

    @Nullable
    private static String string(@Nonnull BsonDocument doc, @Nonnull String key, @Nullable String fallback) {
        return doc.isString(key) ? doc.getString(key).getValue() : fallback;
    }

    @Nonnull
    public SampleSource getSource() {
        return source;
    }

    @Nonnull
    public List<Gene> getGenes() {
        return genes != null ? Arrays.asList(genes) : List.of();
    }

    /** False for a raw sample, true for genetic material. */
    public boolean isProcessed() {
        return processed;
    }

    /** The genome's Complexity, or {@link #COMPLEXITY_UNKNOWN} if it hasn't been calculated. */
    public double getComplexity() {
        return complexity;
    }

    public boolean isComplexityKnown() {
        return complexity > COMPLEXITY_UNKNOWN;
    }
}
