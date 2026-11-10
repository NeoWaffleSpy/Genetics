package com.genetics.data;

import com.hypixel.hytale.assetstore.AssetExtraInfo;
import com.hypixel.hytale.assetstore.AssetRegistry;
import com.hypixel.hytale.assetstore.AssetStore;
import com.hypixel.hytale.assetstore.codec.AssetBuilderCodec;
import com.hypixel.hytale.assetstore.map.DefaultAssetMap;
import com.hypixel.hytale.assetstore.map.JsonAssetWithMap;
import com.hypixel.hytale.codec.Codec;
import com.hypixel.hytale.codec.KeyedCodec;
import com.hypixel.hytale.codec.codecs.array.ArrayCodec;
import com.hypixel.hytale.codec.codecs.map.MapCodec;
import com.hypixel.hytale.codec.validation.Validators;
import org.bson.BsonDocument;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.HashMap;
import java.util.Map;

/**
 * Mutation Codex entry: a special gene that never appears on a source mob (GDD §3.3).
 * One file per mutation, named after its id: {@code Server/Genetics/Mutations/TitanBlood.json}.
 *
 * <pre>
 * {
 *   "Name": "server.genetics.mutation.TitanBlood.name",
 *   "Complexity": 35,
 *   "Stats": { "Health": 20 },
 *   "Effects": [],
 *   "Behaviors": [ { "Type": "OnLowHealth", "Threshold": 0.25, "Apply": "…", "Duration": 6, "Cooldown": 60 } ],
 *   "Weight": 10,
 *   "MinTier": "Complex"
 * }
 * </pre>
 *
 * {@code Behaviors} are kept as raw documents until the shared Action system exists; each one names its {@code Type}.
 * {@code MinTier} is kept as a tier id until Complexity tiers are loaded.
 */
public class MutationDefinition implements JsonAssetWithMap<String, DefaultAssetMap<String, MutationDefinition>> {
    public static final String PATH = "Genetics/Mutations";

    private static final ArrayCodec<BsonDocument> BEHAVIORS_CODEC = new ArrayCodec<>(Codec.BSON_DOCUMENT, BsonDocument[]::new);
    private static final MapCodec<Double, HashMap<String, Double>> STATS_CODEC = new MapCodec<>(Codec.DOUBLE, HashMap::new);

    public static final AssetBuilderCodec<String, MutationDefinition> CODEC = AssetBuilderCodec.builder(
                    MutationDefinition.class, MutationDefinition::new, Codec.STRING, (t, k) -> t.id = k, t -> t.id, (t, data) -> t.data = data, t -> t.data
            )
            .documentation("A mutation: a special gene that only comes from splicing. The file name is the mutation id.")
            .<String>appendInherited(new KeyedCodec<>("Name", Codec.STRING), (t, v) -> t.name = v, t -> t.name, (t, parent) -> t.name = parent.name)
            .documentation("Translation key of the mutation's display name.")
            .add()
            .<Double>appendInherited(new KeyedCodec<>("Complexity", Codec.DOUBLE),
                    (t, v) -> t.complexity = v, t -> t.complexity, (t, parent) -> t.complexity = parent.complexity)
            .documentation("Complexity this mutation adds to a genome.")
            .addValidator(Validators.nonNull())
            .addValidator(Validators.min(0.0))
            .add()
            .<Map<String, Double>>appendInherited(new KeyedCodec<>("Stats", STATS_CODEC), (t, v) -> t.stats = v, t -> t.stats, (t, parent) -> t.stats = parent.stats)
            .documentation("Stat modifiers, in percent, keyed by vanilla stat id.")
            .add()
            .<String[]>appendInherited(new KeyedCodec<>("Effects", Codec.STRING_ARRAY), (t, v) -> t.effects = v, t -> t.effects, (t, parent) -> t.effects = parent.effects)
            .documentation("Vanilla effects the mutation keeps on permanently.")
            .add()
            .<BsonDocument[]>appendInherited(new KeyedCodec<>("Behaviors", BEHAVIORS_CODEC),
                    (t, v) -> t.behaviors = v, t -> t.behaviors, (t, parent) -> t.behaviors = parent.behaviors)
            .documentation("Triggers coded in Java, each referenced by its 'Type'.")
            .add()
            .<Double>appendInherited(new KeyedCodec<>("Weight", Codec.DOUBLE), (t, v) -> t.weight = v, t -> t.weight, (t, parent) -> t.weight = parent.weight)
            .documentation("How likely this mutation is to be picked when a mutation is rolled.")
            .addValidator(Validators.min(0.0))
            .add()
            .<String>appendInherited(new KeyedCodec<>("MinTier", Codec.STRING), (t, v) -> t.minTier = v, t -> t.minTier, (t, parent) -> t.minTier = parent.minTier)
            .documentation("Lowest Complexity tier a genome must reach for this mutation to be rolled.")
            .add()
            .validator((t, results) -> {
                t.getStats().keySet().forEach(stat -> References.checkStat(stat, results));
                for (String effect : t.getEffects()) References.checkEffect(effect, results);
                for (BsonDocument behavior : t.getBehaviors()) {
                    if (behavior == null || !behavior.isString("Type")) results.fail("Every behavior needs a 'Type'.");
                }
            })
            .build();

    private static AssetStore<String, MutationDefinition, DefaultAssetMap<String, MutationDefinition>> assetStore;

    protected AssetExtraInfo.Data data;
    protected String id;
    protected String name;
    protected double complexity;
    protected Map<String, Double> stats = Map.of();
    protected String[] effects = new String[0];
    protected BsonDocument[] behaviors = new BsonDocument[0];
    protected double weight = 1.0;
    protected String minTier;

    protected MutationDefinition() {
    }

    @Nonnull
    public static AssetStore<String, MutationDefinition, DefaultAssetMap<String, MutationDefinition>> getAssetStore() {
        if (assetStore == null) assetStore = AssetRegistry.getAssetStore(MutationDefinition.class);
        return assetStore;
    }

    @Nonnull
    public static DefaultAssetMap<String, MutationDefinition> getAssetMap() {
        return getAssetStore().getAssetMap();
    }

    @Override
    public String getId() {
        return id;
    }

    @Nullable
    public String getName() {
        return name;
    }

    public double getComplexity() {
        return complexity;
    }

    @Nonnull
    public Map<String, Double> getStats() {
        return stats != null ? stats : Map.of();
    }

    @Nonnull
    public String[] getEffects() {
        return effects != null ? effects : new String[0];
    }

    @Nonnull
    public BsonDocument[] getBehaviors() {
        return behaviors != null ? behaviors : new BsonDocument[0];
    }

    public double getWeight() {
        return weight;
    }

    @Nullable
    public String getMinTier() {
        return minTier;
    }
}
