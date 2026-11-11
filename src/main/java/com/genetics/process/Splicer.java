package com.genetics.process;

import com.genetics.codex.Balance;
import com.genetics.codex.MutationEntry;
import com.genetics.genome.Gene;
import com.genetics.genome.Genome;
import com.genetics.tier.ComplexityTier;
import com.genetics.tier.Tiers;
import com.hypixel.hytale.server.core.inventory.MaterialQuantity;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;

/**
 * Splicing two genetic materials (design doc §7):
 * <ul>
 *   <li>Each gene is decided on its own. When both inputs have it (same stat, same effect), one input's value wins at
 *   random, slightly biased toward the input with the higher Complexity ({@link Balance.Splicing#biasA}).</li>
 *   <li>A gene only one input has carries over with {@code SoloGeneChance}, shifted by the same bias.</li>
 *   <li>Mutations are decided the same way (a sample holds at most one). If the result has none, a new one may be
 *   rolled: the chance rises with the two inputs' combined Complexity, and the mutation is drawn by Weight among those
 *   whose MinTier the result reaches.</li>
 *   <li>The result is a raw sample: it has to be processed before it can be spliced again.</li>
 *   <li>The cost uses the tier of the two inputs' average Complexity.</li>
 * </ul>
 */
public final class Splicer {
    private Splicer() {
    }

    /** One gene's odds: its label, and the chance it comes from A, from B, or is lost. */
    public record Odds(@Nonnull String key, @Nullable Gene fromA, @Nullable Gene fromB, double chanceA, double chanceB) {
        public double chanceLost() {
            return Math.max(0, 1 - chanceA - chanceB);
        }
    }

    /** What splicing would cost, and why it can't be done at this station (null if it can). */
    public record Plan(@Nonnull List<Odds> odds, double mutationChance, @Nullable ComplexityTier tier, @Nonnull List<MaterialQuantity> cost,
                       @Nullable String blocked) {
    }

    /** The spliced sample, and the id of a mutation rolled by this splice (null if none was). */
    public record Result(@Nonnull Genome genome, @Nullable String newMutation) {
    }

    /** Why this genome can't be spliced, or null if it can. */
    @Nullable
    public static String refusal(@Nullable Genome genome) {
        if (genome == null) return "Put genetic material in the slot.";
        if (!genome.processed()) return "Only genetic material (processed samples) can be spliced.";
        return null;
    }

    @Nonnull
    public static Plan plan(@Nonnull Genome a, @Nonnull Genome b, int stationTier) {
        Balance.Splicing balance = Balance.get().splicing();
        double biasA = balance.biasA(a.complexity(), b.complexity());
        double solo = balance.getSoloGeneChance();
        List<Odds> odds = new ArrayList<>();
        Map<String, Gene> genesA = byKey(a);
        Map<String, Gene> genesB = byKey(b);
        Map<String, Boolean> keys = new LinkedHashMap<>();
        genesA.keySet().forEach(k -> keys.put(k, true));
        genesB.keySet().forEach(k -> keys.put(k, true));
        for (String key : keys.keySet()) {
            Gene ga = genesA.get(key);
            Gene gb = genesB.get(key);
            if (ga != null && gb != null) odds.add(new Odds(key, ga, gb, biasA, 1 - biasA));
            else if (ga != null) odds.add(new Odds(key, ga, null, soloChance(solo, biasA), 0));
            else odds.add(new Odds(key, null, gb, 0, soloChance(solo, 1 - biasA)));
        }

        double average = (a.complexity() + b.complexity()) / 2;
        ComplexityTier tier = Tiers.forComplexity(average);
        List<MaterialQuantity> cost = tier != null ? Tiers.cost(tier, average) : List.of();
        String blocked = tier != null && !Tiers.allows(stationTier, tier)
                ? tier.getName() + " Complexity needs a Lab Station T" + tier.getStationTier() + "." : null;
        return new Plan(odds, balance.mutationChance(a.complexity(), b.complexity()), tier, cost, blocked);
    }

    @Nonnull
    public static Result splice(@Nonnull Genome a, @Nonnull Genome b, @Nonnull Random random) {
        Plan plan = plan(a, b, Integer.MAX_VALUE);
        List<Gene> genes = new ArrayList<>();
        Gene.Mutation mutation = null;
        for (Odds odds : plan.odds()) {
            double roll = random.nextDouble();
            Gene picked = roll < odds.chanceA() ? odds.fromA() : roll < odds.chanceA() + odds.chanceB() ? odds.fromB() : null;
            if (picked instanceof Gene.Mutation m) {
                if (mutation == null) mutation = m;
            } else if (picked != null) {
                genes.add(picked);
            }
        }

        String rolled = null;
        if (mutation == null && random.nextDouble() < plan.mutationChance()) {
            ComplexityTier reached = Tiers.forComplexity(Genome.raw(null, genes).complexity());
            MutationEntry entry = pickMutation(Tiers.rank(reached), random);
            if (entry != null) {
                mutation = new Gene.Mutation(entry.getId());
                rolled = entry.getId();
            }
        }
        if (mutation != null) genes.add(mutation);
        return new Result(Genome.raw(source(a, b), genes, true), rolled);
    }

    /** Chance for a gene only one input has to carry over: SoloGeneChance shifted by that input's bias. */
    private static double soloChance(double solo, double bias) {
        return Math.clamp(solo + (bias - 0.5), 0, 1);
    }

    /** A random mutation by Weight, among those whose MinTier is at or below this tier rank. */
    @Nullable
    static MutationEntry pickMutation(int tierRank, @Nonnull Random random) {
        List<MutationEntry> candidates = new ArrayList<>();
        double total = 0;
        for (MutationEntry entry : MutationEntry.getAssetMap().getAssetMap().values()) {
            int minRank = Tiers.rank(entry.getMinTier());
            if (minRank < 0 || minRank > tierRank || entry.getWeight() <= 0) continue;
            candidates.add(entry);
            total += entry.getWeight();
        }
        double roll = random.nextDouble() * total;
        for (MutationEntry entry : candidates) {
            roll -= entry.getWeight();
            if (roll < 0) return entry;
        }
        return null;
    }

    /** Genes by what they modify: stats by stat, effects by effect and form, and the mutation (if any) under one key. */
    @Nonnull
    private static Map<String, Gene> byKey(@Nonnull Genome genome) {
        Map<String, Gene> out = new LinkedHashMap<>();
        for (Gene gene : genome.genes()) {
            String key = switch (gene) {
                case Gene.Stat stat -> "Stat:" + stat.stat();
                case Gene.Effect effect -> "Effect:" + effect.effect() + (effect.permanent() ? ":Permanent" : ":Temporary");
                case Gene.Mutation ignored -> "Mutation";
            };
            out.putIfAbsent(key, gene);
        }
        return out;
    }

    /** The result keeps the higher-Complexity input's mob (for sickness events) and names both. */
    @Nullable
    private static Genome.Source source(@Nonnull Genome a, @Nonnull Genome b) {
        Genome.Source main = a.complexity() >= b.complexity() ? a.source() : b.source();
        Genome.Source other = main == a.source() ? b.source() : a.source();
        if (main == null) return other;
        String name = other != null && !other.mobName().equals(main.mobName()) ? main.mobName() + "-" + other.mobName() + " Splice" : main.mobName();
        return new Genome.Source(main.mobId(), name, main.model(), main.icon(), System.currentTimeMillis());
    }
}
