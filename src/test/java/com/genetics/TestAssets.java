package com.genetics;

import com.genetics.codex.Balance;
import com.genetics.codex.EffectEntry;
import com.genetics.codex.MobGeneProfile;
import com.genetics.codex.MutationEntry;
import com.genetics.codex.SicknessEvent;
import com.genetics.codex.StatEntry;
import com.genetics.research.ResearchNode;
import com.genetics.tier.ComplexityTier;
import com.hypixel.hytale.assetstore.AssetLoadResult;
import com.hypixel.hytale.assetstore.AssetRegistry;
import com.hypixel.hytale.assetstore.AssetStore;
import com.hypixel.hytale.assetstore.AssetUpdateQuery;
import com.hypixel.hytale.assetstore.codec.AssetCodec;
import com.hypixel.hytale.assetstore.map.DefaultAssetMap;
import com.hypixel.hytale.assetstore.map.JsonAssetWithMap;
import com.hypixel.hytale.event.EventBus;
import com.hypixel.hytale.event.IEventBus;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;

/**
 * Loads the mod's real data files (src/main/resources/Server/Genetics) into in-memory asset stores, through the same
 * codecs the server uses, so tests run the mod's logic against its shipped data without starting a server.
 * Test-only: this replaces the server's HytaleAssetStore, which needs a running server for events and file watching.
 */
public final class TestAssets {
    private static boolean loaded;
    private static final List<String> FAILURES = new ArrayList<>();

    private TestAssets() {
    }

    /** Loads every Genetics data folder once; returns the files that failed to decode (empty if all loaded). */
    public static synchronized List<String> load() {
        if (loaded) return FAILURES;
        loaded = true;
        // The polymorphic data types, as GeneticsPlugin.setup() registers them.
        com.genetics.action.Action.registerBuiltIns();
        com.genetics.mutation.Behavior.registerBuiltIns();
        com.genetics.tier.Scaling.registerBuiltIns();
        load(StatEntry.class, StatEntry.PATH, StatEntry.CODEC, StatEntry::getId);
        load(EffectEntry.class, EffectEntry.PATH, EffectEntry.CODEC, EffectEntry::getId);
        load(MutationEntry.class, MutationEntry.PATH, MutationEntry.CODEC, MutationEntry::getId);
        load(MobGeneProfile.class, MobGeneProfile.PATH, MobGeneProfile.CODEC, MobGeneProfile::getId);
        load(SicknessEvent.class, SicknessEvent.PATH, SicknessEvent.CODEC, SicknessEvent::getId);
        load(Balance.class, Balance.PATH, Balance.CODEC, Balance::getId);
        load(ComplexityTier.class, ComplexityTier.PATH, ComplexityTier.CODEC, ComplexityTier::getId);
        load(ResearchNode.class, ResearchNode.PATH, ResearchNode.CODEC, ResearchNode::getId);
        return FAILURES;
    }

    /** The mod's resources folder (set by Gradle; falls back to the working directory layout). */
    public static Path resources() {
        String prop = System.getProperty("genetics.resources");
        return prop != null ? Path.of(prop) : Path.of("src/main/resources");
    }

    private static <T extends JsonAssetWithMap<String, DefaultAssetMap<String, T>>> void load(
            Class<T> type, String path, AssetCodec<String, T> codec, Function<T, String> key) {
        Store<T> store = new Store.Builder<>(type, codec, key, path).build();
        AssetRegistry.register(store);
        Path dir = resources().resolve("Server").resolve(path);
        try (var files = Files.walk(dir)) {
            List<Path> paths = files.filter(p -> p.toString().endsWith(".json")).toList();
            AssetLoadResult<String, T> result = store.loadAssetsFromPaths("Genetics", paths);
            if (result.hasFailed()) FAILURES.add(path + ": " + result.getFailedToLoadPaths() + " " + result.getFailedToLoadKeys());
        } catch (Exception e) {
            FAILURES.add(path + ": " + e);
        }
    }

    /** An asset store with no server behind it: a plain event bus, no file monitoring, no client packets. */
    static final class Store<T extends JsonAssetWithMap<String, DefaultAssetMap<String, T>>>
            extends AssetStore<String, T, DefaultAssetMap<String, T>> {
        private static final EventBus BUS = new EventBus(false);

        private Store(Builder<T> builder) {
            super(builder);
        }

        @Override
        protected IEventBus getEventBus() {
            return BUS;
        }

        @Override
        public void addFileMonitor(String packKey, Path path) {
        }

        @Override
        public void removeFileMonitor(Path path) {
        }

        @Override
        protected void handleRemoveOrUpdate(Set<String> removed, Map<String, T> updated, AssetUpdateQuery query) {
        }

        static final class Builder<T extends JsonAssetWithMap<String, DefaultAssetMap<String, T>>>
                extends AssetStore.Builder<String, T, DefaultAssetMap<String, T>, Builder<T>> {
            Builder(Class<T> type, AssetCodec<String, T> codec, Function<T, String> key, String path) {
                super(String.class, type, new DefaultAssetMap<>());
                setPath(path);
                setCodec(codec);
                setKeyFunction(key);
            }

            @Override
            public Store<T> build() {
                return new Store<>(this);
            }
        }
    }
}
