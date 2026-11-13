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
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * The Genetic Library rules (design doc §8.1):
 * <ul>
 *   <li><b>Extract</b> one gene from genetic material into the library (the material is used up). The library keeps
 *   one entry per gene at the best value, so extracting only helps if the gene is new or better. The cost is the
 *   gene's Complexity tier materials, plus Gene Enhancers for stats and effects: one per
 *   {@code ComplexityPerEnhancer} points of the gene's Complexity.</li>
 *   <li><b>Combine</b> library entries into new genetic material, each at any amount from 0 to its best value (stats
 *   and temporary effects; permanent effects and mutations are all or nothing). A bonus and a drawback of the same
 *   stat can't be combined, two tiers of the same buff conflict, and a permanent effect replaces its temporary form.
 *   Several mutations multiply the material cost by {@code MutationCostBase} per extra mutation (x1, x3, x9, x27),
 *   up to {@code MaxMutations}.</li>
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

    /** What extracting one gene costs, and why it can't be done (null if it can). */
    public record ExtractPlan(@Nullable ComplexityTier tier, @Nonnull List<MaterialQuantity> cost, @Nullable String blocked) {
    }

    @Nonnull
    public static ExtractPlan extractPlan(@Nonnull Gene gene, int stationTier, @Nonnull GeneLibrary library) {
        double complexity = Math.max(ComplexityCalculator.MINIMUM, Math.abs(GeneText.complexity(gene)));
        ComplexityTier tier = Tiers.forComplexity(complexity);
        List<MaterialQuantity> cost = new ArrayList<>(tier != null ? Tiers.cost(tier, complexity) : List.of());
        int enhancers = enhancersFor(gene);
        if (enhancers > 0) cost.addFirst(new MaterialQuantity(Balance.get().library().getExtractItem(), null, null, enhancers, null));
        String blocked = null;
        if (!library.wouldImprove(gene)) {
            GeneLibrary.Entry existing = library.find(gene);
            blocked = "Your library already has this gene" + (existing != null && existing.getGene() != null
                    ? " at " + GeneText.describe(existing.getGene(), null) : "") + ".";
        } else if (tier != null && !Tiers.allows(stationTier, tier)) {
            blocked = tier.getName() + " Complexity needs a Lab Station T" + tier.getStationTier() + ".";
        }
        return new ExtractPlan(tier, cost, blocked);
    }

    /** Gene Enhancers to extract a stat or an effect (mutations need none). */
    public static int enhancersFor(@Nonnull Gene gene) {
        if (gene instanceof Gene.Mutation) return 0;
        double per = Balance.get().library().getComplexityPerEnhancer();
        if (per <= 0) return 0;
        return Math.max(1, (int) Math.ceil(Math.abs(GeneText.complexity(gene)) / per));
    }

    // ---- Combine

    /** One entry in a combination, at a fraction (0 to 1) of its best value. */
    public record Use(@Nonnull GeneLibrary.Entry entry, double fraction) {
    }

    /** True if an entry's amount can be chosen (stats and temporary effects). */
    public static boolean adjustable(@Nonnull Gene gene) {
        return gene instanceof Gene.Stat || (gene instanceof Gene.Effect effect && !effect.permanent());
    }

    /** The entry's gene at a fraction of its best value, or null if that rounds to nothing. */
    @Nullable
    public static Gene scaled(@Nonnull Gene gene, double fraction) {
        double f = Math.clamp(fraction, 0, 1);
        return switch (gene) {
            case Gene.Stat stat -> {
                double value = Math.round(stat.percent() * f * 10) / 10.0;
                yield value == 0 ? null : new Gene.Stat(stat.stat(), value, stat.cost() * f);
            }
            case Gene.Effect effect -> {
                if (effect.permanent()) yield effect;
                double seconds = Math.round(effect.duration() * f);
                yield seconds < 1 ? null : new Gene.Effect(effect.effect(), false, seconds);
            }
            case Gene.Mutation mutation -> mutation;
        };
    }

    /** What combining these entries gives and costs. {@code blocked}: why it can't be done (null if it can). */
    public record Plan(@Nonnull List<Gene> genes, @Nullable Genome result, int mutations, double multiplier,
                       @Nullable ComplexityTier tier, @Nonnull List<MaterialQuantity> cost, @Nullable String blocked) {
    }

    @Nonnull
    public static Plan plan(@Nonnull List<Use> uses, int stationTier) {
        Balance.Library balance = Balance.get().library();
        List<Gene> picked = new ArrayList<>();
        for (Use use : uses) {
            Gene gene = use.entry().getGene();
            Gene scaled = gene != null ? scaled(gene, use.fraction()) : null;
            if (scaled != null) picked.add(scaled);
        }
        if (picked.isEmpty()) return new Plan(List.of(), null, 0, 1, null, List.of(), "Tick the genes to combine.");

        List<Gene> genes = new ArrayList<>();
        String blocked = null;
        Map<String, Gene.Effect> effects = new LinkedHashMap<>();
        List<String> mutations = new ArrayList<>();
        for (Gene gene : picked) {
            switch (gene) {
                case Gene.Stat stat -> {
                    boolean clash = genes.stream().anyMatch(g -> g instanceof Gene.Stat other && other.stat().equals(stat.stat()));
                    if (clash && blocked == null) blocked = "A bonus and a drawback of Max " + GeneText.prettify(stat.stat()) + " can't be combined.";
                    genes.add(stat);
                }
                case Gene.Effect effect -> effects.merge(effect.effect(), effect, (a, b) -> a.permanent() ? a : b);
                case Gene.Mutation mutation -> {
                    if (!mutations.contains(mutation.mutation())) mutations.add(mutation.mutation());
                }
            }
        }
        Map<String, String> families = new LinkedHashMap<>();
        for (Gene.Effect effect : effects.values()) {
            String other = families.putIfAbsent(Splicer.family(effect.effect()), effect.effect());
            if (other != null && blocked == null) {
                blocked = GeneText.effectName(other, null) + " and " + GeneText.effectName(effect.effect(), null) + " are the same buff at two tiers.";
            }
            genes.add(effect);
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
}
