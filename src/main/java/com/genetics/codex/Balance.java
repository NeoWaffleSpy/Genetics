package com.genetics.codex;

import com.hypixel.hytale.assetstore.AssetExtraInfo;
import com.hypixel.hytale.assetstore.AssetRegistry;
import com.hypixel.hytale.assetstore.AssetStore;
import com.hypixel.hytale.assetstore.codec.AssetBuilderCodec;
import com.hypixel.hytale.assetstore.map.DefaultAssetMap;
import com.hypixel.hytale.assetstore.map.JsonAssetWithMap;
import com.hypixel.hytale.codec.Codec;
import com.hypixel.hytale.codec.KeyedCodec;
import com.hypixel.hytale.codec.builder.BuilderCodec;
import com.hypixel.hytale.codec.codecs.array.ArrayCodec;
import com.hypixel.hytale.server.core.inventory.MaterialQuantity;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.List;

/**
 * Balancing levers that aren't tied to a single stat, effect or mob (design doc §11): raw injection quality and
 * Genetic Sickness. Read from {@code Server/Genetics/Balance/Default.json}; another pack can override that file.
 * Every value has a built-in default, used when the file or a field is missing.
 */
public final class Balance implements JsonAssetWithMap<String, DefaultAssetMap<String, Balance>> {
    public static final String PATH = "Genetics/Balance";
    public static final String DEFAULT_ID = "Default";

    /** Raw injection quality (design doc §6). */
    public static final class Injection {
        public static final BuilderCodec<Injection> CODEC = BuilderCodec.builder(Injection.class, Injection::new)
                .append(new KeyedCodec<>("C0", Codec.DOUBLE), (t, v) -> t.c0 = v, t -> t.c0)
                .documentation("Complexity at which a gene has a 50% chance to come out as-is: chance = 1 / (1 + Complexity / C0).").add()
                .append(new KeyedCodec<>("WeakenChance", Codec.DOUBLE), (t, v) -> t.weakenChance = v, t -> t.weakenChance)
                .documentation("When a gene doesn't come out as-is: chance it is weakened.").add()
                .append(new KeyedCodec<>("DropChance", Codec.DOUBLE), (t, v) -> t.dropChance = v, t -> t.dropChance)
                .documentation("When a gene doesn't come out as-is: chance it is dropped. The rest turns into a drawback (stats) or is dropped (effects).").add()
                .append(new KeyedCodec<>("KeepMin", Codec.DOUBLE), (t, v) -> t.keepMin = v, t -> t.keepMin)
                .documentation("A weakened or inverted stat keeps at least this fraction of its value.").add()
                .append(new KeyedCodec<>("KeepMax", Codec.DOUBLE), (t, v) -> t.keepMax = v, t -> t.keepMax)
                .documentation("A weakened or inverted stat keeps at most this fraction of its value.").add()
                .append(new KeyedCodec<>("WeakenedPermanentDuration", Codec.DOUBLE), (t, v) -> t.weakenedPermanentDuration = v, t -> t.weakenedPermanentDuration)
                .documentation("Seconds a permanent effect lasts when it comes out weakened (it becomes temporary).").add()
                .build();

        private double c0 = 20;
        private double weakenChance = 0.5;
        private double dropChance = 0.3;
        private double keepMin = 0.3;
        private double keepMax = 0.7;
        private double weakenedPermanentDuration = 180;

        public double getC0() { return c0; }
        public double getWeakenChance() { return weakenChance; }
        public double getDropChance() { return dropChance; }
        public double getKeepMin() { return keepMin; }
        public double getKeepMax() { return keepMax; }
        public double getWeakenedPermanentDuration() { return weakenedPermanentDuration; }
    }

    /** One severity band of Genetic Sickness (design doc §9.2). */
    public static final class Band {
        public static final BuilderCodec<Band> CODEC = BuilderCodec.builder(Band.class, Band::new)
                .append(new KeyedCodec<>("Id", Codec.STRING), (t, v) -> t.id = v, t -> t.id).add()
                .append(new KeyedCodec<>("MinSickness", Codec.DOUBLE), (t, v) -> t.minSickness = v, t -> t.minSickness)
                .documentation("The band starts at this much sickness and runs up to the next band.").add()
                .append(new KeyedCodec<>("EventIntervalMin", Codec.DOUBLE), (t, v) -> t.eventIntervalMin = v, t -> t.eventIntervalMin)
                .documentation("Shortest time between two sickness events in this band, in seconds. 0 means no events.").add()
                .append(new KeyedCodec<>("EventIntervalMax", Codec.DOUBLE), (t, v) -> t.eventIntervalMax = v, t -> t.eventIntervalMax).add()
                .append(new KeyedCodec<>("Effect", Codec.STRING), (t, v) -> t.effect = v, t -> t.effect)
                .documentation("Effect shown on the player while in this band (an icon and a timer until fully healed).").add()
                .append(new KeyedCodec<>("Message", Codec.STRING), (t, v) -> t.message = v, t -> t.message)
                .documentation("Translation key sent to the player when they enter this band.").add()
                .build();

        private String id = "Healthy";
        private double minSickness;
        private double eventIntervalMin;
        private double eventIntervalMax;
        @Nullable
        private String effect;
        @Nullable
        private String message;

        Band() {
        }

        Band(String id, double minSickness, double intervalMin, double intervalMax, @Nullable String effect, @Nullable String message) {
            this.id = id;
            this.minSickness = minSickness;
            this.eventIntervalMin = intervalMin;
            this.eventIntervalMax = intervalMax;
            this.effect = effect;
            this.message = message;
        }

        public String getId() { return id; }
        public double getMinSickness() { return minSickness; }
        public double getEventIntervalMin() { return eventIntervalMin; }
        public double getEventIntervalMax() { return Math.max(eventIntervalMin, eventIntervalMax); }
        @Nullable public String getEffect() { return effect; }
        @Nullable public String getMessage() { return message; }
    }

    /** Genetic Sickness (design doc §9.2). */
    public static final class Sickness {
        public static final BuilderCodec<Sickness> CODEC = BuilderCodec.builder(Sickness.class, Sickness::new)
                .append(new KeyedCodec<>("PerComplexity", Codec.DOUBLE), (t, v) -> t.perComplexity = v, t -> t.perComplexity)
                .documentation("Sickness gained per point of Complexity when injecting a raw sample.").add()
                .append(new KeyedCodec<>("ProcessedMultiplier", Codec.DOUBLE), (t, v) -> t.processedMultiplier = v, t -> t.processedMultiplier)
                .documentation("Multiplier on sickness gained from genetic material (processed samples).").add()
                .append(new KeyedCodec<>("DecayPerSecond", Codec.DOUBLE), (t, v) -> t.decayPerSecond = v, t -> t.decayPerSecond)
                .documentation("Sickness lost per second while alive.").add()
                .append(new KeyedCodec<>("Bands", new ArrayCodec<>(Band.CODEC, Band[]::new)), (t, v) -> t.bands = v, t -> t.bands)
                .documentation("Severity bands, from lowest to highest.").add()
                .build();

        private double perComplexity = 4;
        private double processedMultiplier = 0.3;
        private double decayPerSecond = 0.55;
        private Band[] bands = {
                new Band("Healthy", 0, 0, 0, null, "server.genetics.sickness.band.Healthy"),
                new Band("Mild", 20, 60, 90, "Genetics_Sickness_Mild", "server.genetics.sickness.band.Mild"),
                new Band("Moderate", 50, 30, 45, "Genetics_Sickness_Moderate", "server.genetics.sickness.band.Moderate"),
                new Band("Severe", 80, 10, 20, "Genetics_Sickness_Severe", "server.genetics.sickness.band.Severe"),
        };

        public double getPerComplexity() { return perComplexity; }
        public double getProcessedMultiplier() { return processedMultiplier; }
        public double getDecayPerSecond() { return decayPerSecond; }
        @Nonnull public Band[] getBands() { return bands != null ? bands : new Band[0]; }

        /** The highest band whose minimum is at or below this sickness, or null if there are no bands. */
        @Nullable
        public Band bandFor(double sickness) {
            Band found = null;
            for (Band band : getBands()) {
                if (sickness >= band.getMinSickness() && (found == null || band.getMinSickness() >= found.getMinSickness())) found = band;
            }
            return found;
        }

        /** Position of a band in severity order (0 = lowest), or -1 if unknown. */
        public int rank(@Nullable String bandId) {
            if (bandId == null) return -1;
            for (Band band : getBands()) {
                if (!band.getId().equals(bandId)) continue;
                int below = 0;
                for (Band other : getBands()) if (other.getMinSickness() < band.getMinSickness()) below++;
                return below;
            }
            return -1;
        }
    }

    /** Genome Capacity (design doc §3.4). The base capacity is the max of the Genetics_GenomeCapacity stat. */
    public static final class Capacity {
        public static final BuilderCodec<Capacity> CODEC = BuilderCodec.builder(Capacity.class, Capacity::new)
                .append(new KeyedCodec<>("OverflowSicknessPerPoint", Codec.DOUBLE), (t, v) -> t.overflowSicknessPerPoint = v, t -> t.overflowSicknessPerPoint)
                .documentation("Extra sickness per point of Complexity over capacity, on top of jumping straight to the Severe band.").add()
                .append(new KeyedCodec<>("OverflowBand", Codec.STRING), (t, v) -> t.overflowBand = v, t -> t.overflowBand)
                .documentation("Sickness band an over-capacity injection pushes the entity into.").add()
                .build();

        private double overflowSicknessPerPoint = 2;
        private String overflowBand = "Severe";

        public double getOverflowSicknessPerPoint() { return overflowSicknessPerPoint; }
        @Nonnull public String getOverflowBand() { return overflowBand != null ? overflowBand : "Severe"; }
    }

    /** Syringe tiers (design doc §6): which syringe a mob needs, for mobs whose profile doesn't say. */
    public static final class Syringes {
        public static final BuilderCodec<Syringes> CODEC = BuilderCodec.builder(Syringes.class, Syringes::new)
                .append(new KeyedCodec<>("HealthThresholds", Codec.DOUBLE_ARRAY), (t, v) -> t.healthThresholds = v, t -> t.healthThresholds)
                .documentation("A mob needs one syringe tier more for each threshold its max health is above. [120, 250, 600]: up to 120 HP needs tier 1, up to 250 tier 2, and so on.").add()
                .build();

        private double[] healthThresholds = {120, 250, 600};

        /** Syringe tier needed for a mob with this max health. */
        public int tierForHealth(double maxHealth) {
            int tier = 1;
            if (healthThresholds != null) for (double threshold : healthThresholds) if (maxHealth > threshold) tier++;
            return tier;
        }
    }

    /** A Gene Journal milestone (design doc §6): sampling enough species teaches recipes. */
    public static final class Milestone {
        public static final BuilderCodec<Milestone> CODEC = BuilderCodec.builder(Milestone.class, Milestone::new)
                .append(new KeyedCodec<>("Id", Codec.STRING), (t, v) -> t.id = v, t -> t.id).add()
                .append(new KeyedCodec<>("Species", Codec.INTEGER), (t, v) -> t.species = v, t -> t.species)
                .documentation("Number of different species sampled to reach the milestone.").add()
                .append(new KeyedCodec<>("Recipes", Codec.STRING_ARRAY), (t, v) -> t.recipes = v, t -> t.recipes)
                .documentation("Item ids whose recipes are taught (recipes with KnowledgeRequired).").add()
                .build();

        private String id;
        private int species;
        private String[] recipes = new String[0];

        Milestone() {
        }

        Milestone(String id, int species, String... recipes) {
            this.id = id;
            this.species = species;
            this.recipes = recipes;
        }

        public String getId() { return id != null ? id : "Species" + species; }
        public int getSpecies() { return species; }
        @Nonnull public String[] getRecipes() { return recipes != null ? recipes : new String[0]; }
    }

    /** Splicing (design doc §7): which input wins each gene, and the mutation roll. */
    public static final class Splicing {
        public static final BuilderCodec<Splicing> CODEC = BuilderCodec.builder(Splicing.class, Splicing::new)
                .append(new KeyedCodec<>("BiasPer10Complexity", Codec.DOUBLE), (t, v) -> t.biasPer10 = v, t -> t.biasPer10)
                .documentation("How much the higher-Complexity input is favoured per 10 points of difference (0.05 = 5%).").add()
                .append(new KeyedCodec<>("BiasCap", Codec.DOUBLE), (t, v) -> t.biasCap = v, t -> t.biasCap)
                .documentation("Highest chance for one input to win a gene (0.65 = 65/35).").add()
                .append(new KeyedCodec<>("SoloGeneChance", Codec.DOUBLE), (t, v) -> t.soloGeneChance = v, t -> t.soloGeneChance)
                .documentation("Chance that a gene only one input has carries over, before the Complexity bias.").add()
                .append(new KeyedCodec<>("MutationBaseChance", Codec.DOUBLE), (t, v) -> t.mutationBaseChance = v, t -> t.mutationBaseChance)
                .documentation("Chance of a new mutation, before Complexity.").add()
                .append(new KeyedCodec<>("MutationPerComplexity", Codec.DOUBLE), (t, v) -> t.mutationPerComplexity = v, t -> t.mutationPerComplexity)
                .documentation("Extra mutation chance per point of the two inputs' combined Complexity.").add()
                .build();

        private double biasPer10 = 0.05;
        private double biasCap = 0.65;
        private double soloGeneChance = 0.5;
        private double mutationBaseChance = 0.02;
        private double mutationPerComplexity = 0.0005;

        public double getBiasPer10() { return biasPer10; }
        public double getBiasCap() { return Math.clamp(biasCap, 0.5, 1); }
        public double getSoloGeneChance() { return Math.clamp(soloGeneChance, 0, 1); }
        public double getMutationBaseChance() { return mutationBaseChance; }
        public double getMutationPerComplexity() { return mutationPerComplexity; }

        /** Chance that input A wins a gene both inputs have, from the two Complexities. */
        public double biasA(double complexityA, double complexityB) {
            double cap = getBiasCap();
            return Math.clamp(0.5 + biasPer10 * (complexityA - complexityB) / 10, 1 - cap, cap);
        }

        /** Chance of a new mutation for two inputs of these Complexities. */
        public double mutationChance(double complexityA, double complexityB) {
            return Math.clamp(mutationBaseChance + mutationPerComplexity * (complexityA + complexityB), 0, 1);
        }
    }

    /** One Lab Station upgrade (design doc §7): the tier it reaches and what it costs. */
    public static final class StationUpgrade {
        public static final BuilderCodec<StationUpgrade> CODEC = BuilderCodec.builder(StationUpgrade.class, StationUpgrade::new)
                .append(new KeyedCodec<>("Tier", Codec.INTEGER), (t, v) -> t.tier = v, t -> t.tier)
                .documentation("Station tier reached by this upgrade (from the tier below).").add()
                .append(new KeyedCodec<>("Materials", new ArrayCodec<>(MaterialQuantity.CODEC, MaterialQuantity[]::new)), (t, v) -> t.materials = v, t -> t.materials)
                .documentation("Items taken from the player's inventory, like a vanilla bench upgrade.").add()
                .build();

        private int tier;
        private MaterialQuantity[] materials = new MaterialQuantity[0];

        StationUpgrade() {
        }

        StationUpgrade(int tier, MaterialQuantity... materials) {
            this.tier = tier;
            this.materials = materials;
        }

        public int getTier() { return tier; }
        @Nonnull public List<MaterialQuantity> getMaterials() { return materials != null ? List.of(materials) : List.of(); }
    }

    public static final AssetBuilderCodec<String, Balance> CODEC = AssetBuilderCodec.builder(
                    Balance.class, Balance::new, Codec.STRING, (t, k) -> t.id = k, t -> t.id, (t, d) -> t.data = d, t -> t.data)
            .append(new KeyedCodec<>("Injection", Injection.CODEC), (t, v) -> t.injection = v, t -> t.injection).add()
            .append(new KeyedCodec<>("Sickness", Sickness.CODEC), (t, v) -> t.sickness = v, t -> t.sickness).add()
            .append(new KeyedCodec<>("Capacity", Capacity.CODEC), (t, v) -> t.capacity = v, t -> t.capacity).add()
            .append(new KeyedCodec<>("Syringes", Syringes.CODEC), (t, v) -> t.syringes = v, t -> t.syringes).add()
            .append(new KeyedCodec<>("JournalMilestones", new ArrayCodec<>(Milestone.CODEC, Milestone[]::new)), (t, v) -> t.milestones = v, t -> t.milestones)
            .documentation("Gene Journal milestones, in any order.").add()
            .append(new KeyedCodec<>("Splicing", Splicing.CODEC), (t, v) -> t.splicing = v, t -> t.splicing).add()
            .append(new KeyedCodec<>("StationUpgrades", new ArrayCodec<>(StationUpgrade.CODEC, StationUpgrade[]::new)), (t, v) -> t.stationUpgrades = v, t -> t.stationUpgrades)
            .documentation("Lab Station upgrades, one per tier above 1.").add()
            .build();

    private static final Balance BUILT_IN = new Balance();
    private static AssetStore<String, Balance, DefaultAssetMap<String, Balance>> store;

    private AssetExtraInfo.Data data;
    private String id = DEFAULT_ID;
    private Injection injection = new Injection();
    private Sickness sickness = new Sickness();
    private Capacity capacity = new Capacity();
    private Syringes syringes = new Syringes();
    private Milestone[] milestones = {
            new Milestone("FirstSteps", 4, "Genetics_Syringe_Copper"),
            new Milestone("Naturalist", 8, "Genetics_Genome_Expansion_Moderate"),
    };
    private Splicing splicing = new Splicing();
    private StationUpgrade[] stationUpgrades = {
            new StationUpgrade(2,
                    new MaterialQuantity("Ingredient_Bar_Copper", null, null, 10, null),
                    new MaterialQuantity("Ingredient_Bar_Iron", null, null, 5, null),
                    new MaterialQuantity("Ingredient_Life_Essence", null, null, 10, null)),
    };

    private Balance() {
    }

    @Nonnull
    public static AssetStore<String, Balance, DefaultAssetMap<String, Balance>> getAssetStore() {
        if (store == null) store = AssetRegistry.getAssetStore(Balance.class);
        return store;
    }

    @Nonnull
    public static DefaultAssetMap<String, Balance> getAssetMap() {
        return getAssetStore().getAssetMap();
    }

    /** The active balance: the Default file, or the built-in values if it is missing. */
    @Nonnull
    public static Balance get() {
        Balance loaded = getAssetMap().getAsset(DEFAULT_ID);
        return loaded != null ? loaded : BUILT_IN;
    }

    @Override
    public String getId() {
        return id;
    }

    @Nonnull
    public Injection injection() {
        return injection != null ? injection : BUILT_IN.injection;
    }

    @Nonnull
    public Sickness sickness() {
        return sickness != null ? sickness : BUILT_IN.sickness;
    }

    @Nonnull
    public Capacity capacity() {
        return capacity != null ? capacity : BUILT_IN.capacity;
    }

    @Nonnull
    public Syringes syringes() {
        return syringes != null ? syringes : BUILT_IN.syringes;
    }

    @Nonnull
    public Milestone[] milestones() {
        return milestones != null ? milestones : BUILT_IN.milestones;
    }

    @Nonnull
    public Splicing splicing() {
        return splicing != null ? splicing : BUILT_IN.splicing;
    }

    /** The upgrade that reaches this station tier, or null if there is none. */
    @Nullable
    public StationUpgrade stationUpgrade(int tier) {
        StationUpgrade[] upgrades = stationUpgrades != null ? stationUpgrades : BUILT_IN.stationUpgrades;
        for (StationUpgrade upgrade : upgrades) if (upgrade.getTier() == tier) return upgrade;
        return null;
    }
}
