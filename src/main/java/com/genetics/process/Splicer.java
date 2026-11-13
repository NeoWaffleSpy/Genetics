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
 * Splicing two genetic materials (design doc §7). Nothing is random except the mutation roll; the result is the two
 * inputs added together:
 * <ul>
 *   <li>The same stat in both inputs: the percentages are added (a drawback subtracts).</li>
 *   <li>The same temporary effect in both: the durations are added, and its Complexity grows with the duration
 *   ({@link com.genetics.genome.ComplexityCalculator#effect}).</li>
 *   <li>The same permanent effect in both: kept once.</li>
 *   <li>A gene only one input has: kept as it is.</li>
 *   <li>Mutations: all of one input's mutations are kept, never a mix: the input that has any, or the higher-Complexity
 *   input's. If neither input has one, a new one may be rolled: the chance rises with the two inputs' combined
 *   Complexity, and the mutation is drawn by Weight among those whose MinTier the result reaches.</li>
 * </ul>
 * The player can untick any line to leave it out, and must leave out one of every pair of effects that are the same
 * buff at different tiers (Meat Buff T1 and T2): a sample can't hold both. The result is an unidentified raw sample
 * ("Unidentified Spliced Sample", then "Moderate Spliced Gene" once processed): it has to be processed before it can be
 * injected in a controlled way or spliced again. The cost uses the tier of the two inputs' average Complexity.
 */
public final class Splicer {
    private Splicer() {
    }

    /** One gene: what A and B have (null if absent), what the result gets (null if nothing), and if the player keeps it. */
    public record Line(@Nonnull String key, @Nullable Gene fromA, @Nullable Gene fromB, @Nullable Gene result, boolean kept) {
    }

    /**
     * What splicing would give and cost. {@code blocked}: why it can't be done at this station; {@code conflict}: the
     * same buff at two tiers is still kept (both null if it can be done). The mutation chance is 0 when a mutation is
     * inherited.
     */
    public record Plan(@Nonnull List<Line> lines, @Nonnull Genome result, double mutationChance, @Nullable ComplexityTier tier,
                       @Nonnull List<MaterialQuantity> cost, @Nullable String blocked, @Nullable String conflict) {
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
        return plan(a, b, stationTier, null);
    }

    /** @param keep which lines (in {@link Plan#lines} order) the player keeps; null keeps them all */
    @Nonnull
    public static Plan plan(@Nonnull Genome a, @Nonnull Genome b, int stationTier, @Nullable boolean[] keep) {
        Map<String, Gene> genesA = byKey(a);
        Map<String, Gene> genesB = byKey(b);
        Map<String, Boolean> keys = new LinkedHashMap<>();
        genesA.keySet().forEach(k -> keys.put(k, true));
        genesB.keySet().forEach(k -> keys.put(k, true));

        boolean aLeads = a.complexity() >= b.complexity();
        // Mutations come from one input only: the one that has any, or the higher-Complexity one if both do.
        boolean aMutations = genesA.keySet().stream().anyMatch(k -> k.startsWith("Mutation:"));
        boolean bMutations = genesB.keySet().stream().anyMatch(k -> k.startsWith("Mutation:"));
        boolean mutationsFromA = aMutations && (!bMutations || aLeads);
        List<Line> lines = new ArrayList<>();
        List<Gene> genes = new ArrayList<>();
        boolean inherited = false;
        int index = 0;
        for (String key : keys.keySet()) {
            Gene ga = genesA.get(key);
            Gene gb = genesB.get(key);
            Gene merged = key.startsWith("Mutation:")
                    ? (mutationsFromA ? ga : gb)
                    : merge(ga, gb, aLeads);
            boolean kept = keep == null || index >= keep.length || keep[index];
            index++;
            lines.add(new Line(key, ga, gb, merged, kept));
            if (!kept || merged == null) continue;
            genes.add(merged);
            if (merged instanceof Gene.Mutation) inherited = true;
        }
        Genome result = Genome.raw(source(a, b), genes, false);

        double average = (a.complexity() + b.complexity()) / 2;
        ComplexityTier tier = Tiers.forComplexity(average);
        List<MaterialQuantity> cost = tier != null ? Tiers.cost(tier, average) : List.of();
        String blocked = tier != null && !Tiers.allows(stationTier, tier)
                ? tier.getName() + " Complexity needs a Lab Station T" + tier.getStationTier() + "." : null;
        double mutationChance = inherited ? 0 : Balance.get().splicing().mutationChance(a.complexity(), b.complexity());
        return new Plan(lines, result, mutationChance, tier, cost, blocked, conflict(genes));
    }

    /** "Untick Meat Buff T1 or Meat Buff T2: ..." for the first buff kept at two tiers, or null if there is none. */
    @Nullable
    private static String conflict(@Nonnull List<Gene> genes) {
        Map<String, String> byFamily = new LinkedHashMap<>();
        for (Gene gene : genes) {
            if (!(gene instanceof Gene.Effect effect)) continue;
            String other = byFamily.putIfAbsent(family(effect.effect()), effect.effect());
            if (other != null && !other.equals(effect.effect())) {
                return "Untick " + com.genetics.genome.GeneText.effectName(other, null) + " or "
                        + com.genetics.genome.GeneText.effectName(effect.effect(), null) + ": a sample can't hold the same buff at two tiers.";
            }
        }
        return null;
    }

    /** The buff an effect is a tier of: "Meat_Buff_T2" -> "Meat_Buff". Effects without a tier suffix are their own family. */
    @Nonnull
    public static String family(@Nonnull String effectId) {
        return effectId.replaceFirst("_T\\d+$", "");
    }

    @Nonnull
    public static Result splice(@Nonnull Genome a, @Nonnull Genome b, @Nullable boolean[] keep, @Nonnull Random random) {
        Plan plan = plan(a, b, Integer.MAX_VALUE, keep);
        if (plan.mutationChance() <= 0 || random.nextDouble() >= plan.mutationChance()) return new Result(plan.result(), null);

        MutationEntry entry = pickMutation(Tiers.rank(Tiers.forComplexity(plan.result().complexity())), random);
        if (entry == null) return new Result(plan.result(), null);
        List<Gene> genes = new ArrayList<>(plan.result().genes());
        genes.add(new Gene.Mutation(entry.getId()));
        return new Result(Genome.raw(plan.result().source(), genes, false), entry.getId());
    }

    /** One gene of the result, from what each input has (both are of the same kind, or null). */
    @Nullable
    private static Gene merge(@Nullable Gene a, @Nullable Gene b, boolean aLeads) {
        if (a == null) return b;
        if (b == null) return a;
        return switch (a) {
            case Gene.Stat sa -> {
                Gene.Stat sb = (Gene.Stat) b;
                double sum = Math.round((sa.percent() + sb.percent()) * 10) / 10.0;
                // Two bonuses keep their full (fused) cost; anything else costs what's left.
                double cost = !sa.isDrawback() && !sb.isDrawback() ? sa.cost() + sb.cost() : Math.abs(sum);
                yield sum == 0 ? null : new Gene.Stat(sa.stat(), sum, cost);
            }
            case Gene.Effect ea -> ea.permanent() ? ea : new Gene.Effect(ea.effect(), false,
                    Math.min(ea.duration() + ((Gene.Effect) b).duration(), Balance.get().splicing().getMaxEffectDuration()));
            case Gene.Mutation ignored -> aLeads ? a : b;
        };
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
                case Gene.Mutation mutation -> "Mutation:" + mutation.mutation();
            };
            out.merge(key, gene, (first, second) -> merge(first, second, true));
        }
        return out;
    }

    /**
     * The result keeps the higher-Complexity input's mob (sickness events name it) and is marked as spliced, so it is
     * named after its tier.
     */
    @Nullable
    private static Genome.Source source(@Nonnull Genome a, @Nonnull Genome b) {
        Genome.Source main = a.complexity() >= b.complexity() ? a.source() : b.source();
        if (main == null) main = a.source() != null ? a.source() : b.source();
        if (main == null) return new Genome.Source("Unknown", "Unknown", null, null, System.currentTimeMillis(), true);
        return new Genome.Source(main.mobId(), main.mobName(), main.model(), main.icon(), System.currentTimeMillis(), true);
    }
}
