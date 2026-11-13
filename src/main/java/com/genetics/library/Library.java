package com.genetics.library;

import com.genetics.codex.Balance;
import com.genetics.genome.ComplexityCalculator;
import com.genetics.genome.Gene;
import com.genetics.genome.GeneText;
import com.genetics.genome.Genome;
import com.genetics.process.Splicer;
import com.genetics.tier.ComplexityTier;
import com.genetics.tier.Tiers;
import com.hypixel.hytale.component.ComponentAccessor;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.server.core.inventory.MaterialQuantity;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * The Genetic Library rules (design doc §8.1):
 * <ul>
 *   <li><b>Extract</b> one gene from genetic material into the library; the cost uses that gene's Complexity tier.</li>
 *   <li><b>Combine</b> library entries (each at most once) into new genetic material. The same stat several times is
 *   <b>fused</b>: the best copy adds 100%, the next {@code FusionFalloff} of its value, the next that squared, and so
 *   on, while Complexity counts the full value of every bonus copy (kept on the gene as its {@code cost}).
 *   Drawbacks fuse the same way and lower Complexity by their fused value only. A bonus and a drawback of the same
 *   stat can't be combined. The same temporary effect adds its timers, a permanent effect is kept once, and two
 *   tiers of the same buff conflict. Several mutations multiply the material cost by {@code MutationCostBase} per
 *   extra mutation (x1, x3, x9, x27), up to {@code MaxMutations}.</li>
 *   <li><b>Refine</b> an entry past its mob's range: +{@code RefineStep} of its base value per level, for
 *   {@code RefineBaseCost} x 2^level of the refine item. Stats and temporary effects only.</li>
 * </ul>
 */
public final class Library {
    /** Source id of combined genomes: named "&lt;Tier&gt; Designed Gene". */
    public static final String SOURCE_ID = "Genetics_Library";

    private Library() {
    }

    @Nonnull
    public static GeneLibrary of(@Nonnull Ref<EntityStore> ref, @Nonnull ComponentAccessor<EntityStore> accessor) {
        GeneLibrary library = accessor.getComponent(ref, GeneLibrary.getComponentType());
        if (library == null) {
            library = new GeneLibrary();
            accessor.putComponent(ref, GeneLibrary.getComponentType(), library);
        }
        return library;
    }

    // ---- Extract

    /** What extracting one gene costs, and why this station can't do it (null if it can). */
    public record ExtractPlan(@Nullable ComplexityTier tier, @Nonnull List<MaterialQuantity> cost, @Nullable String blocked) {
    }

    @Nonnull
    public static ExtractPlan extractPlan(@Nonnull Gene gene, int stationTier) {
        double complexity = Math.max(ComplexityCalculator.MINIMUM, Math.abs(GeneText.complexity(gene)));
        ComplexityTier tier = Tiers.forComplexity(complexity);
        if (tier == null) return new ExtractPlan(null, List.of(), null);
        String blocked = Tiers.allows(stationTier, tier) ? null : tier.getName() + " Complexity needs a Lab Station T" + tier.getStationTier() + ".";
        return new ExtractPlan(tier, Tiers.cost(tier, complexity), blocked);
    }

    // ---- Refine

    public static boolean canRefine(@Nonnull GeneLibrary.Entry entry) {
        Gene base = entry.getBase();
        boolean kind = base instanceof Gene.Stat || (base instanceof Gene.Effect effect && !effect.permanent());
        return kind && entry.getLevel() < Balance.get().library().getRefineMaxLevel();
    }

    /** Refine items the next level costs. */
    public static int refineCost(@Nonnull GeneLibrary.Entry entry) {
        Balance.Library balance = Balance.get().library();
        return balance.getRefineBaseCost() * (1 << Math.min(entry.getLevel(), 20));
    }

    /** The entry's gene with its refine levels applied. */
    @Nullable
    public static Gene effective(@Nonnull GeneLibrary.Entry entry) {
        Gene base = entry.getBase();
        if (base == null || entry.getLevel() <= 0) return base;
        double factor = 1 + Balance.get().library().getRefineStep() * entry.getLevel();
        return switch (base) {
            case Gene.Stat stat -> new Gene.Stat(stat.stat(), round(stat.percent() * factor), stat.cost() * factor);
            case Gene.Effect effect -> effect.permanent() ? effect : new Gene.Effect(effect.effect(), false, Math.round(effect.duration() * factor));
            case Gene.Mutation mutation -> mutation;
        };
    }

    // ---- Combine

    /** What combining these entries gives and costs. {@code blocked}: why it can't be done (null if it can). */
    public record Plan(@Nonnull List<Gene> genes, @Nullable Genome result, int mutations, double multiplier,
                       @Nullable ComplexityTier tier, @Nonnull List<MaterialQuantity> cost, @Nullable String blocked) {
    }

    @Nonnull
    public static Plan plan(@Nonnull List<GeneLibrary.Entry> chosen, int stationTier) {
        Balance.Library balance = Balance.get().library();
        if (chosen.isEmpty()) return new Plan(List.of(), null, 0, 1, null, List.of(), "Tick the genes to combine.");

        Map<String, List<Gene.Stat>> stats = new LinkedHashMap<>();
        Map<String, List<Gene.Effect>> effects = new LinkedHashMap<>();
        List<String> mutations = new ArrayList<>();
        for (GeneLibrary.Entry entry : chosen) {
            Gene gene = effective(entry);
            switch (gene) {
                case Gene.Stat stat -> stats.computeIfAbsent(stat.stat(), k -> new ArrayList<>()).add(stat);
                case Gene.Effect effect -> effects.computeIfAbsent(effect.effect(), k -> new ArrayList<>()).add(effect);
                case Gene.Mutation mutation -> {
                    if (!mutations.contains(mutation.mutation())) mutations.add(mutation.mutation());
                }
                case null -> {
                }
            }
        }

        List<Gene> genes = new ArrayList<>();
        String blocked = null;
        for (Map.Entry<String, List<Gene.Stat>> group : stats.entrySet()) {
            List<Gene.Stat> copies = group.getValue();
            boolean bonus = copies.stream().anyMatch(s -> !s.isDrawback());
            boolean drawback = copies.stream().anyMatch(Gene.Stat::isDrawback);
            String name = "Max " + GeneText.prettify(group.getKey());
            if (bonus && drawback && blocked == null) blocked = "A bonus and a drawback of " + name + " can't be combined.";
            if (copies.size() > balance.getMaxCopies() && blocked == null) blocked = "At most " + balance.getMaxCopies() + " copies of " + name + ".";
            genes.add(fuse(group.getKey(), copies, balance.getFusionFalloff()));
        }
        Map<String, String> families = new LinkedHashMap<>();
        for (Map.Entry<String, List<Gene.Effect>> group : effects.entrySet()) {
            String other = families.putIfAbsent(Splicer.family(group.getKey()), group.getKey());
            if (other != null && blocked == null) {
                blocked = GeneText.effectName(other, null) + " and " + GeneText.effectName(group.getKey(), null) + " are the same buff at two tiers.";
            }
            boolean permanent = group.getValue().stream().anyMatch(Gene.Effect::permanent);
            double duration = group.getValue().stream().mapToDouble(Gene.Effect::duration).sum();
            genes.add(new Gene.Effect(group.getKey(), permanent, permanent ? 0 : duration));
        }
        for (String mutation : mutations) genes.add(new Gene.Mutation(mutation));
        if (mutations.size() > balance.getMaxMutations() && blocked == null) blocked = "At most " + balance.getMaxMutations() + " mutations in one genome.";

        double complexity = ComplexityCalculator.compute(genes);
        Genome result = new Genome(Genome.FORMAT_VERSION, true, true, complexity,
                new Genome.Source(SOURCE_ID, "Designed", null, null, System.currentTimeMillis(), true), genes);
        double multiplier = Math.pow(balance.getMutationCostBase(), Math.max(0, mutations.size() - 1));
        ComplexityTier tier = Tiers.forComplexity(complexity);
        List<MaterialQuantity> cost = new ArrayList<>();
        if (tier != null) {
            for (MaterialQuantity m : Tiers.cost(tier, complexity)) {
                cost.add(new MaterialQuantity(m.getItemId(), m.getResourceTypeId(), null, (int) Math.ceil(m.getQuantity() * multiplier), null));
            }
            if (blocked == null && !Tiers.allows(stationTier, tier)) blocked = tier.getName() + " Complexity needs a Lab Station T" + tier.getStationTier() + ".";
        }
        return new Plan(genes, result, mutations.size(), multiplier, tier, cost, blocked);
    }

    /** Fuses copies of one stat: the best (largest) first at 100%, each next one at falloff^i. */
    @Nonnull
    static Gene.Stat fuse(@Nonnull String stat, @Nonnull List<Gene.Stat> copies, double falloff) {
        List<Gene.Stat> sorted = new ArrayList<>(copies);
        sorted.sort(Comparator.comparingDouble((Gene.Stat s) -> -Math.abs(s.percent())));
        double value = 0;
        double factor = 1;
        double fullCost = 0;
        for (Gene.Stat copy : sorted) {
            value += copy.percent() * factor;
            factor *= falloff;
            fullCost += copy.cost();
        }
        value = round(value);
        boolean drawback = value < 0;
        // Bonuses cost the full value of every copy; drawbacks only reduce Complexity by what they really do.
        return new Gene.Stat(stat, value, drawback ? Math.abs(value) : fullCost);
    }

    private static double round(double value) {
        return Math.round(value * 10) / 10.0;
    }
}
